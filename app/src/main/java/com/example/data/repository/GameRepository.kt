package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.GameDao
import com.example.data.model.GameItem
import com.example.data.model.GameType
import com.example.data.service.GeForceNowService
import com.example.data.service.InstalledAppsManager
import com.example.data.service.LibrarySyncCodec
import com.example.data.service.PinnedShortcutImporter
import com.example.data.service.SteamGridDbService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class GameRepository(
    private val gameDao: GameDao,
    private val installedAppsManager: InstalledAppsManager
) {
    val allGames: Flow<List<GameItem>> = gameDao.getAllGames()
    val favoriteGames: Flow<List<GameItem>> = gameDao.getFavoriteGames()
    val favoriteApps: Flow<List<GameItem>> = gameDao.getFavoriteApps()
    val recentGames: Flow<List<GameItem>> = gameDao.getRecentGames()
    val hiddenGames: Flow<List<GameItem>> = gameDao.getHiddenGames()

    /** Every row, including hidden and tombstoned entries, for library-file sync. */
    val allItemsIncludingDeleted: Flow<List<GameItem>> = gameDao.getAllItemsFlow()

    // Serialises poster matching so startup sync and user actions cannot fetch the same
    // missing artwork more than once at a time.
    private val posterMatchMutex = Mutex()

    fun getGamesByType(type: GameType): Flow<List<GameItem>> = gameDao.getGamesByType(type)

    suspend fun getGameById(id: String): GameItem? = gameDao.getGameById(id)

    suspend fun initializeLibraryIfEmpty(purgeLegacyCatalog: Boolean = false) {
        // The first-run cleanup drops hardcoded entries from very old builds. It must not
        // repeat, because it also matches the live GeForce NOW catalog the user added
        // deliberately (and which the library sync file restores).
        if (purgeLegacyCatalog) {
            gameDao.deletePrePopulatedItems()
        }
        // Auto-scan installed apps and games
        syncInstalledApps()
        // Pinned shortcuts that the publisher removed or the user unpinned should not
        // linger as dead tiles.
        removeStalePinnedShortcuts()
    }

    /**
     * Drops pinned-shortcut tiles whose system shortcut no longer exists.
     *
     * Only rows pinned on this device are removed. Shortcuts restored from a sync file
     * have `isPinned = false` and are left alone, because they were pinned on another
     * device. Existing rows from older builds are promoted to `isPinned = true` while
     * their shortcut is still live. Skipped when the pinned list cannot be read (for
     * example while another launcher is the default one).
     */
    suspend fun removeStalePinnedShortcuts() {
        val pinned = installedAppsManager.getPinnedShortcutKeys() ?: return
        gameDao.getGamesByTypeNow(GameType.SHORTCUT)
            .filter { it.id.startsWith(PinnedShortcutImporter.ID_PREFIX) }
            .forEach { item ->
                val key = item.packageName?.let { pkg ->
                    item.activityName?.let { shortcutId -> pkg to shortcutId }
                }
                val isLive = key != null && key in pinned
                when {
                    isLive && !item.isPinned -> gameDao.setPinned(item.id, true)
                    !isLive && item.isPinned -> gameDao.deleteGameById(item.id)
                }
            }
    }

    suspend fun insertUserGame(game: GameItem) {
        gameDao.insertGame(game)
    }

    suspend fun syncGfnCatalog() {
        val liveTitles = GeForceNowService.DEFAULT_GFN_LIBRARY
        for (gfnGame in liveTitles) {
            val existing = gameDao.getGameById(gfnGame.id)
            if (existing == null) {
                gameDao.insertGame(gfnGame)
            } else {
                // Keep favorite and custom artwork, update launchUrl and metadata
                val updated = existing.copy(
                    launchUrl = gfnGame.launchUrl,
                    gfnCmsId = gfnGame.gfnCmsId,
                    description = if (existing.description.isBlank()) gfnGame.description else existing.description,
                    posterUrl = existing.posterUrl ?: gfnGame.posterUrl,
                    heroBannerUrl = existing.heroBannerUrl ?: gfnGame.heroBannerUrl
                )
                gameDao.updateGame(updated)
            }
        }
    }

    /**
     * Older GFN entries were saved with the square catalog icon as their poster.
     * Resolve and store the real portrait box art for any such entries.
     */
    suspend fun backfillGfnArtwork() {
        val gfnGames = gameDao.getGamesByTypeNow(GameType.GEFORCE_NOW)
        val needsArtwork = gfnGames.filter { game ->
            !game.isDeleted &&
            (game.customArtworkSource == null || game.customArtworkSource == "GFN") &&
            (game.posterUrl.isNullOrBlank() || game.posterUrl.contains("GAME_ICON_01"))
        }
        for (game in needsArtwork) {
            val boxArt = GeForceNowService.resolveBoxArtForCmsId(game.gfnCmsId) ?: continue
            gameDao.updateArtwork(game.id, boxArt, game.heroBannerUrl, "GFN")
        }
    }

    suspend fun syncInstalledApps() {
        val installed = installedAppsManager.getInstalledApps()
        val installedIds = installed.mapTo(HashSet()) { it.id }
        val existingApps = gameDao.getGamesByTypeNow(GameType.INSTALLED_APP)
        val existingById = existingApps.associateBy { it.id }

        // Drop any INSTALLED_APP row whose package is no longer installed (both live apps
        // uninstalled outside the launcher and tombstones so a reinstall starts fresh).
        // Keep live rows when the package query came back completely empty so a transient
        // pre-unlock PackageManager state (or unit test without mocked apps) cannot wipe data.
        val hasInstalledApps = installedIds.isNotEmpty()
        val toDeleteIds = existingApps
            .filter { (hasInstalledApps || it.isDeleted) && it.id !in installedIds }
            .map { it.id }

        val toUpsert = mutableListOf<GameItem>()
        for (app in installed) {
            val existing = existingById[app.id] ?: gameDao.getGameById(app.id)
            mergeInstalledAppUpdate(app, existing)?.let { toUpsert += it }
        }

        if (toUpsert.isNotEmpty() || toDeleteIds.isNotEmpty()) {
            gameDao.syncInstalledAppChanges(toUpsert, toDeleteIds)
        }
    }

    /**
     * Removes the Room row(s) and cached icon/banner files for [packageName] in O(1) when
     * the system reports the package has been uninstalled, avoiding a full-device scan.
     */
    suspend fun removeUninstalledPackage(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val appId = "app_$packageName"
        val existingApp = gameDao.getGameById(appId)
        val pinnedShortcuts = gameDao.getGamesByPackageNameNow(packageName)
            .filter { it.type == GameType.SHORTCUT && it.isPinned }
        val toDeleteIds = buildList {
            if (existingApp != null) add(existingApp.id)
            pinnedShortcuts.forEach { add(it.id) }
        }.distinct()

        if (toDeleteIds.isNotEmpty()) {
            gameDao.deleteGamesByIds(toDeleteIds)
        }
        installedAppsManager.evictPackageCache(packageName)
        return toDeleteIds.isNotEmpty()
    }

    /**
     * Syncs a single installed/updated [packageName] in O(1) without rescanning every app
     * on the device. Removes its entry if the package no longer exposes a launcher activity.
     */
    suspend fun syncInstalledPackage(packageName: String): GameItem? {
        if (packageName.isBlank()) return null
        val app = installedAppsManager.getInstalledApp(packageName)
        if (app == null) {
            removeUninstalledPackage(packageName)
            return null
        }
        val existing = gameDao.getGameById(app.id)
        val updated = mergeInstalledAppUpdate(app, existing) ?: return null
        gameDao.insertGame(updated)
        return updated
    }

    private fun mergeInstalledAppUpdate(app: GameItem, existing: GameItem?): GameItem? =
        when {
            existing == null -> app
            existing.isDeleted -> null
            else -> {
                // A customized item keeps its user-chosen name, icon and artwork; only the
                // launch target is refreshed from the package.
                val keepCustomIcon = !existing.iconUrl.isNullOrBlank() &&
                    !existing.iconUrl.contains("/app_icons/")
                val merged = if (existing.isCustomized) {
                    existing.copy(
                        packageName = app.packageName,
                        activityName = app.activityName,
                        iconUrl = if (keepCustomIcon) existing.iconUrl else app.iconUrl,
                        heroBannerUrl = existing.heroBannerUrl ?: app.heroBannerUrl
                    )
                } else {
                    existing.copy(
                        title = app.title,
                        packageName = app.packageName,
                        activityName = app.activityName,
                        iconUrl = app.iconUrl,
                        heroBannerUrl = app.heroBannerUrl
                    )
                }
                merged.takeIf { it != existing }
            }
        }

    /**
     * One-time migration for existing databases that previously categorized installed
     * GeForce NOW, Amazon Luna, or Moonlight client apps as Games.
     */
    suspend fun recategorizeStreamingClientsAsApps(packages: Set<String>) {
        if (packages.isEmpty()) return
        val updates = gameDao.getGamesByTypeNow(GameType.INSTALLED_APP)
            .filter { !it.isDeleted && it.isGame && it.packageName in packages }
            .map { item ->
                item.copy(
                    isGame = false,
                    genre = if (item.genre == "Installed Game") "Installed Application" else item.genre,
                    tags = if (item.tags == "Installed,Game,Android") "Installed,Android,App" else item.tags
                )
            }
        if (updates.isNotEmpty()) {
            gameDao.updateItemsInTransaction(updates)
        }
    }

    /**
     * Installed games are detected without any artwork. Resolve a real portrait poster for
     * each one once, in small parallel batches, and store it as SteamGridDB artwork. Items
     * that already have artwork (including user-chosen artwork) are never touched.
     */
    suspend fun matchMissingGamePosters() {
        if (posterMatchMutex.isLocked) return
        posterMatchMutex.withLock {
            val missing = gameDao.getInstalledGamesMissingPoster()
            if (missing.isEmpty()) return
            missing.chunked(3).forEach { batch ->
                coroutineScope {
                    batch.map { game ->
                        async {
                            val poster = SteamGridDbService.findPosterForGame(game.title)
                                ?: return@async
                            // Re-check after the network call so a user-chosen poster set in
                            // the meantime is never overwritten.
                            val fresh = gameDao.getGameById(game.id) ?: return@async
                            if (!fresh.posterUrl.isNullOrBlank() ||
                                !fresh.customArtworkSource.isNullOrBlank()
                            ) {
                                return@async
                            }
                            gameDao.updateArtwork(
                                fresh.id,
                                poster,
                                fresh.heroBannerUrl,
                                "STEAMGRIDDB"
                            )
                        }
                    }.awaitAll()
                }
                delay(120)
            }
        }
    }

    /**
     * Match a single item on demand, e.g. when the user moves an installed app into the
     * Games tab. Safe to call repeatedly; no-ops when the item already has artwork.
     */
    suspend fun ensureAutoPoster(item: GameItem) {
        if (!item.isGame) return
        // Installed games and pinned game shortcuts both start without box art.
        if (item.type != GameType.INSTALLED_APP && item.type != GameType.SHORTCUT) return
        if (!item.posterUrl.isNullOrBlank() || !item.customArtworkSource.isNullOrBlank()) return

        posterMatchMutex.withLock {
            val fresh = gameDao.getGameById(item.id) ?: return
            if (!fresh.isGame || !fresh.posterUrl.isNullOrBlank() ||
                !fresh.customArtworkSource.isNullOrBlank()
            ) {
                return
            }
            val poster = SteamGridDbService.findPosterForGame(fresh.title) ?: return
            gameDao.updateArtwork(fresh.id, poster, fresh.heroBannerUrl, "STEAMGRIDDB")
        }
    }

    suspend fun addCustomShortcut(
        title: String,
        url: String,
        posterUrl: String?,
        type: GameType,
        genre: String = "Web Shortcut",
        isGame: Boolean = false
    ) {
        val id = "custom_${System.currentTimeMillis()}"
        val item = GameItem(
            id = id,
            title = title,
            type = type,
            isGame = isGame,
            launchUrl = url,
            posterUrl = posterUrl,
            genre = genre,
            publisher = "Custom Link",
            description = "Custom game or shortcut ($url)",
            tags = "Custom,Shortcut"
        )
        gameDao.insertGame(item)
    }

    suspend fun updateArtwork(gameId: String, posterUrl: String, heroUrl: String?, source: String) {
        gameDao.updateArtwork(gameId, posterUrl, heroUrl, source)
    }

    suspend fun toggleFavorite(gameId: String, currentStatus: Boolean) {
        gameDao.setFavorite(gameId, !currentStatus)
    }

    /** The next free position at the end of the favorites view. */
    suspend fun nextFavoriteOrder(): Int =
        (gameDao.getFavoriteAppsNow().maxOfOrNull { it.favoriteOrder } ?: -1) + 1

    suspend fun setFavoriteOrder(gameId: String, order: Int) {
        gameDao.setFavoriteOrder(gameId, order)
    }

    /** Persists the visual order of the favorites view after a drag. */
    suspend fun saveFavoriteOrder(items: List<GameItem>) {
        items.forEachIndexed { index, item ->
            gameDao.setFavoriteOrder(item.id, index)
        }
    }

    suspend fun recordPlay(gameId: String) {
        gameDao.recordPlay(gameId, System.currentTimeMillis())
    }

    /** Drops a game out of the recently-played ordering without deleting the tile. */
    suspend fun clearPlayHistory(gameId: String) {
        gameDao.clearLastPlayed(gameId)
    }

    suspend fun setIsGame(id: String, isGame: Boolean) {
        gameDao.setIsGame(id, isGame)
        if (isGame) {
            gameDao.setFolderId(id, null)
        }
    }

    suspend fun createFolder(title: String, initialMemberId: String? = null): GameItem {
        val resolvedTitle = title.trim().ifBlank { "Folder" }
        val folder = GameItem(
            id = "folder_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(4)}",
            title = resolvedTitle,
            type = GameType.FOLDER,
            isGame = false,
            genre = "Folder",
            publisher = "Nexus",
            description = "App folder"
        )
        gameDao.insertGame(folder)
        if (initialMemberId != null) {
            gameDao.moveItemToFolder(initialMemberId, folder.id)
        }
        return folder
    }

    suspend fun moveItemToFolder(itemId: String, folderId: String?) {
        gameDao.moveItemToFolder(itemId, folderId)
    }

    suspend fun setFolderMembers(folderId: String, memberIds: Set<String>) {
        gameDao.setFolderMembers(folderId, memberIds)
    }

    suspend fun deleteFolder(folderId: String) {
        gameDao.deleteFolderAndUnassignMembers(folderId)
    }

    suspend fun setHidden(id: String, isHidden: Boolean) {
        gameDao.setHidden(id, isHidden)
    }

    suspend fun updateTitle(id: String, title: String) {
        gameDao.updateTitle(id, title)
    }

    suspend fun updateTitleAndArtwork(id: String, title: String, posterUrl: String?, iconUrl: String?) {
        gameDao.updateTitleAndArtwork(id, title, posterUrl, iconUrl)
    }

    suspend fun updateIconUrl(id: String, iconUrl: String?) {
        gameDao.updateIconUrl(id, iconUrl)
    }

    suspend fun updateItemsBatch(items: List<GameItem>) {
        if (items.isNotEmpty()) {
            gameDao.updateItemsInTransaction(items)
        }
    }

    suspend fun updateTitleAndNsfwPoster(id: String, title: String, nsfwPosterUrl: String?) {
        gameDao.updateTitleAndNsfwPoster(id, title, nsfwPosterUrl)
    }

    suspend fun updateTitleAndLogoOverlay(
        id: String,
        title: String,
        overlayLogo: Boolean,
        logoUrl: String?
    ) {
        gameDao.updateTitleAndLogoOverlay(id, title, overlayLogo, logoUrl)
    }

    suspend fun updateLogoUrl(id: String, logoUrl: String?) {
        gameDao.updateLogoUrl(id, logoUrl)
    }

    suspend fun updateTitleAndNsfwOverlay(
        id: String,
        title: String,
        overlayNsfwLogo: Boolean,
        nsfwLogoUrl: String?
    ) {
        gameDao.updateTitleAndNsfwOverlay(id, title, overlayNsfwLogo, nsfwLogoUrl)
    }

    suspend fun updateNsfwLogoUrl(id: String, nsfwLogoUrl: String?) {
        gameDao.updateNsfwLogoUrl(id, nsfwLogoUrl)
    }

    suspend fun deleteGame(gameId: String) {
        // Keep the row as a tombstone instead of removing it, otherwise the installed-app
        // and catalog syncs re-add the tile on the next launcher start.
        gameDao.markDeleted(gameId)
    }

    /**
     * Rows that travel between devices in the library file: cloud games, Moonlight games
     * and game shortcuts. Installed apps stay local because they depend on what is
     * installed on each device. Tombstones are included so deletions propagate.
     */
    suspend fun getSyncableItemsSnapshot(): List<GameItem> =
        gameDao.getAllItemsNow().filter(::isSyncableItem)

    suspend fun getAllItemsSnapshot(): List<GameItem> =
        gameDao.getAllItemsNow()

    /**
     * Replaces the local shareable library with [remoteItems] from the sync file.
     *
     * Device-specific state is preserved: locally pinned shortcuts stay pinned, local
     * artwork files survive when the backup has no artwork for that item, and play stats
     * never move backwards. Installed apps and other non-syncable rows are untouched.
     */
    suspend fun replaceSyncedLibrary(
        remoteItems: List<GameItem>,
        tracksLocalPosters: Boolean = false,
        remoteUpdatedAt: Long = 0L
    ) {
        val remoteById = remoteItems.associateBy { it.id }
        val local = gameDao.getAllItemsNow()
        val localById = local.associateBy { it.id }

        // The file is authoritative: rows it no longer contains are removed locally.
        local.filter { isSyncableItem(it) && it.id !in remoteById }
            .forEach { gameDao.deleteGameById(it.id) }

        remoteItems.forEach { remote ->
            val existing = localById[remote.id]
            val resolvedPosterUrl = remote.posterUrl
                ?: if (tracksLocalPosters) null
                else existing?.posterUrl?.takeIf {
                    it.startsWith("file:") && !it.contains("-synced")
                }
            val resolvedNsfwPosterUrl = remote.nsfwPosterUrl
                ?: if (tracksLocalPosters) null
                else existing?.nsfwPosterUrl?.takeIf {
                    it.startsWith("file:") && !it.contains("-nsfw-synced")
                }
            gameDao.insertGame(
                remote.copy(
                    isPinned = existing?.isPinned ?: false,
                    isFavorite = existing?.isFavorite ?: false,
                    favoriteOrder = existing?.favoriteOrder ?: 0,
                    posterUrl = resolvedPosterUrl,
                    nsfwPosterUrl = resolvedNsfwPosterUrl,
                    logoUrl = remote.logoUrl,
                    overlayLogo = remote.overlayLogo,
                    nsfwLogoUrl = remote.nsfwLogoUrl,
                    overlayNsfwLogo = remote.overlayNsfwLogo,
                    heroBannerUrl = remote.heroBannerUrl
                        ?: existing?.heroBannerUrl?.takeIf { it.startsWith("file:") },
                    iconUrl = remote.iconUrl
                        ?: existing?.iconUrl?.takeIf { it.startsWith("file:") },
                    lastPlayedTimestamp = resolveSyncedLastPlayedTimestamp(
                        remoteLastPlayed = remote.lastPlayedTimestamp,
                        existingLastPlayed = existing?.lastPlayedTimestamp ?: 0L,
                        remoteUpdatedAt = remoteUpdatedAt
                    ),
                    playCount = maxOf(remote.playCount, existing?.playCount ?: 0)
                )
            )
        }
    }

    /**
     * Applies synced artwork and customization settings onto `INSTALLED_APP` rows that are
     * actually installed on this device without creating tiles for apps only installed on
     * another device.
     */
    suspend fun applySyncedAppCustomizations(
        remoteApps: List<GameItem>,
        tracksLocalPosters: Boolean = false,
        preserveLocalCustomizations: Boolean = false,
        remoteUpdatedAt: Long = 0L
    ) {
        if (remoteApps.isEmpty()) return
        val localById = gameDao.getGamesByTypeNow(GameType.INSTALLED_APP).associateBy { it.id }
        remoteApps.forEach { remote ->
            val existing = localById[remote.id] ?: return@forEach
            if (existing.isDeleted) return@forEach
            val keepLocal = preserveLocalCustomizations && LibrarySyncCodec.isSyncableAppCustomization(existing)
            val resolvedPosterUrl = if (keepLocal) {
                existing.posterUrl ?: remote.posterUrl
            } else {
                remote.posterUrl
                    ?: if (tracksLocalPosters) null
                    else existing.posterUrl?.takeIf {
                        it.startsWith("file:") && !it.contains("-synced")
                    }
            }
            val resolvedNsfwPosterUrl = if (keepLocal) {
                existing.nsfwPosterUrl ?: remote.nsfwPosterUrl
            } else {
                remote.nsfwPosterUrl
                    ?: if (tracksLocalPosters) null
                    else existing.nsfwPosterUrl?.takeIf {
                        it.startsWith("file:") && !it.contains("-nsfw-synced")
                    }
            }
            gameDao.insertGame(
                existing.copy(
                    title = if (!keepLocal && remote.isCustomized && remote.title.isNotBlank()) {
                        remote.title
                    } else {
                        existing.title
                    },
                    isGame = if (keepLocal) existing.isGame else remote.isGame,
                    isFavorite = existing.isFavorite,
                    favoriteOrder = existing.favoriteOrder,
                    isHidden = if (keepLocal) existing.isHidden else remote.isHidden,
                    isCustomized = existing.isCustomized || remote.isCustomized,
                    posterUrl = resolvedPosterUrl,
                    nsfwPosterUrl = resolvedNsfwPosterUrl,
                    logoUrl = if (keepLocal) existing.logoUrl ?: remote.logoUrl else remote.logoUrl,
                    overlayLogo = if (keepLocal) existing.overlayLogo else remote.overlayLogo,
                    nsfwLogoUrl = if (keepLocal) existing.nsfwLogoUrl ?: remote.nsfwLogoUrl else remote.nsfwLogoUrl,
                    overlayNsfwLogo = if (keepLocal) existing.overlayNsfwLogo else remote.overlayNsfwLogo,
                    heroBannerUrl = if (keepLocal) {
                        existing.heroBannerUrl ?: remote.heroBannerUrl
                    } else {
                        remote.heroBannerUrl
                            ?: existing.heroBannerUrl?.takeIf { it.startsWith("file:") }
                    },
                    customArtworkSource = if (keepLocal) {
                        existing.customArtworkSource ?: remote.customArtworkSource
                    } else {
                        remote.customArtworkSource
                    },
                    lastPlayedTimestamp = resolveSyncedLastPlayedTimestamp(
                        remoteLastPlayed = remote.lastPlayedTimestamp,
                        existingLastPlayed = existing.lastPlayedTimestamp,
                        remoteUpdatedAt = remoteUpdatedAt
                    ),
                    playCount = maxOf(existing.playCount, remote.playCount)
                )
            )
        }
    }

    private fun resolveSyncedLastPlayedTimestamp(
        remoteLastPlayed: Long,
        existingLastPlayed: Long,
        remoteUpdatedAt: Long
    ): Long = when {
        remoteLastPlayed > 0L -> maxOf(remoteLastPlayed, existingLastPlayed)
        existingLastPlayed > 0L && remoteUpdatedAt > 0L && remoteUpdatedAt >= existingLastPlayed -> 0L
        else -> existingLastPlayed
    }

    suspend fun updateSyncedPosters(items: List<GameItem>) {
        if (items.isNotEmpty()) {
            gameDao.insertGames(items)
        }
    }

    /**
     * Non-destructively merges [remoteItems] into the existing local library when connecting
     * or switching sync methods so local games, shortcuts, favorites, and artwork are never lost.
     *
     * Returns the number of net-new live items added from [remoteItems].
     */
    suspend fun mergeSyncedLibraryPreservingExisting(
        remoteItems: List<GameItem>,
        allowOverwriteMatching: Boolean
    ): Int {
        val local = gameDao.getAllItemsNow()
        val localById = local.associateBy { it.id }
        var addedCount = 0

        remoteItems.forEach { remote ->
            if (!isSyncableItem(remote)) return@forEach
            val existing = localById[remote.id]
            if (existing == null) {
                if (!remote.isDeleted) {
                    gameDao.insertGame(
                        remote.copy(
                            isPinned = false,
                            isFavorite = false,
                            favoriteOrder = 0
                        )
                    )
                    addedCount++
                }
                return@forEach
            }

            if (existing.isDeleted || remote.isDeleted) {
                return@forEach
            }

            val merged = if (allowOverwriteMatching) {
                remote.copy(
                    isPinned = existing.isPinned,
                    isFavorite = existing.isFavorite,
                    favoriteOrder = existing.favoriteOrder,
                    posterUrl = remote.posterUrl ?: existing.posterUrl,
                    nsfwPosterUrl = remote.nsfwPosterUrl ?: existing.nsfwPosterUrl,
                    logoUrl = remote.logoUrl ?: existing.logoUrl,
                    overlayLogo = remote.overlayLogo || existing.overlayLogo,
                    nsfwLogoUrl = remote.nsfwLogoUrl ?: existing.nsfwLogoUrl,
                    overlayNsfwLogo = remote.overlayNsfwLogo || existing.overlayNsfwLogo,
                    heroBannerUrl = remote.heroBannerUrl ?: existing.heroBannerUrl,
                    iconUrl = remote.iconUrl ?: existing.iconUrl,
                    customArtworkSource = remote.customArtworkSource ?: existing.customArtworkSource,
                    lastPlayedTimestamp = maxOf(remote.lastPlayedTimestamp, existing.lastPlayedTimestamp),
                    playCount = maxOf(remote.playCount, existing.playCount)
                )
            } else {
                existing.copy(
                    posterUrl = existing.posterUrl ?: remote.posterUrl,
                    nsfwPosterUrl = existing.nsfwPosterUrl ?: remote.nsfwPosterUrl,
                    logoUrl = existing.logoUrl ?: remote.logoUrl,
                    nsfwLogoUrl = existing.nsfwLogoUrl ?: remote.nsfwLogoUrl,
                    heroBannerUrl = existing.heroBannerUrl ?: remote.heroBannerUrl,
                    iconUrl = existing.iconUrl ?: remote.iconUrl,
                    customArtworkSource = existing.customArtworkSource ?: remote.customArtworkSource,
                    lastPlayedTimestamp = maxOf(existing.lastPlayedTimestamp, remote.lastPlayedTimestamp),
                    playCount = maxOf(existing.playCount, remote.playCount)
                )
            }
            if (merged != existing) {
                gameDao.insertGame(merged)
            }
        }
        return addedCount
    }

    /**
     * Adds live items from an older export file without removing or overwriting local
     * rows. Used once when such a file is adopted, before it is rewritten in the current
     * format.
     */
    suspend fun mergeSyncedLibrary(remoteItems: List<GameItem>) {
        val knownIds = gameDao.getAllItemsNow().mapTo(HashSet()) { it.id }
        remoteItems
            .filter { !it.isDeleted && isSyncableItem(it) }
            .forEach { remote ->
                if (knownIds.add(remote.id)) {
                    gameDao.insertGame(
                        remote.copy(
                            isPinned = false,
                            isFavorite = false,
                            favoriteOrder = 0
                        )
                    )
                }
            }
    }

    private fun isSyncableItem(item: GameItem): Boolean = LibrarySyncCodec.isSyncable(item)

    companion object {
        fun create(context: Context): GameRepository {
            val db = AppDatabase.getDatabase(context)
            val installedAppsManager = InstalledAppsManager(context)
            return GameRepository(db.gameDao(), installedAppsManager)
        }
    }
}
