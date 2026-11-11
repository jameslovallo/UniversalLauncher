package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.GameDao
import com.example.data.model.GameItem
import com.example.data.model.GameType
import com.example.data.service.GeForceNowService
import com.example.data.service.InstalledAppsManager
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

    // Serialises poster matching so startup sync and user actions cannot fetch the same
    // missing artwork more than once at a time.
    private val posterMatchMutex = Mutex()

    fun getGamesByType(type: GameType): Flow<List<GameItem>> = gameDao.getGamesByType(type)

    suspend fun getGameById(id: String): GameItem? = gameDao.getGameById(id)

    suspend fun initializeLibraryIfEmpty() {
        // Remove any old pre-populated items so only actual installed Android games remain
        gameDao.deletePrePopulatedItems()
        // Auto-scan installed apps and games
        syncInstalledApps()
        // Pinned shortcuts that the publisher removed or the user unpinned should not
        // linger as dead tiles.
        removeStalePinnedShortcuts()
    }

    /**
     * Drops pinned-shortcut tiles whose system shortcut no longer exists. Skipped when the
     * pinned list cannot be read (for example while another launcher is the default one).
     */
    suspend fun removeStalePinnedShortcuts() {
        val pinned = installedAppsManager.getPinnedShortcutKeys() ?: return
        gameDao.getGamesByTypeNow(GameType.SHORTCUT)
            .filter { it.id.startsWith(PinnedShortcutImporter.ID_PREFIX) }
            .forEach { item ->
                val key = item.packageName?.let { pkg ->
                    item.activityName?.let { shortcutId -> pkg to shortcutId }
                }
                if (key == null || key !in pinned) {
                    gameDao.deleteGameById(item.id)
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
        // A tombstone whose package is gone entirely can be dropped: if the user ever
        // reinstalls it, the app should show up in the library again from scratch.
        gameDao.getGamesByTypeNow(GameType.INSTALLED_APP)
            .filter { it.isDeleted && it.id !in installedIds }
            .forEach { gameDao.deleteGameById(it.id) }
        for (app in installed) {
            val existing = gameDao.getGameById(app.id)
            if (existing == null) {
                gameDao.insertGame(app)
            } else if (!existing.isDeleted) {
                // A customized item keeps its user-chosen name, icon and artwork; only the
                // launch target is refreshed from the package.
                val updated = if (existing.isCustomized) {
                    existing.copy(
                        packageName = app.packageName,
                        activityName = app.activityName
                    )
                } else {
                    existing.copy(
                        title = app.title,
                        packageName = app.packageName,
                        activityName = app.activityName,
                        iconUrl = app.iconUrl
                    )
                }
                gameDao.updateGame(updated)
            }
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

    suspend fun setIsGame(id: String, isGame: Boolean) {
        gameDao.setIsGame(id, isGame)
    }

    suspend fun setHidden(id: String, isHidden: Boolean) {
        gameDao.setHidden(id, isHidden)
    }

    suspend fun updateTitleAndArtwork(id: String, title: String, posterUrl: String?, iconUrl: String?) {
        gameDao.updateTitleAndArtwork(id, title, posterUrl, iconUrl)
    }

    suspend fun deleteGame(gameId: String) {
        // Keep the row as a tombstone instead of removing it, otherwise the installed-app
        // and catalog syncs re-add the tile on the next launcher start.
        gameDao.markDeleted(gameId)
    }

    companion object {
        fun create(context: Context): GameRepository {
            val db = AppDatabase.getDatabase(context)
            val installedAppsManager = InstalledAppsManager(context)
            return GameRepository(db.gameDao(), installedAppsManager)
        }
    }
}
