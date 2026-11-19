package com.example.data.service

import android.content.Context
import android.net.Uri
import android.content.SharedPreferences
import com.example.data.model.GameItem
import com.example.data.model.GameType
import com.example.data.repository.GameRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Result of a library folder or Google Drive sync or write. */
sealed interface LibrarySyncOutcome {
    data class Applied(val itemCount: Int) : LibrarySyncOutcome
    data class WroteLocal(val itemCount: Int) : LibrarySyncOutcome
    data class Merged(val itemCount: Int, val addedFromRemote: Int) : LibrarySyncOutcome
    data object UpToDate : LibrarySyncOutcome
    data object NotConfigured : LibrarySyncOutcome
}

/**
 * Keeps the shareable game library synchronised with either:
 * - **Google Drive API (`drive.file` scope)** — stores `nexus-library.json` and `posters/`
 *   inside an app-created `Nexus Launcher` folder, ideal for Android TV and mobile without SAF
 *   Drive provider quirks, OR
 * - **A local/custom folder via Storage Access Framework** — for users who prefer local or
 *   custom folder storage.
 *
 * The two sync methods are mutually exclusive; only one is active at a time. Switching sync
 * methods preserves existing Room library items, favorites, folders, and local artwork.
 */
class LibrarySyncService(
    private val context: Context,
    private val repository: GameRepository,
    private val prefs: SharedPreferences,
    private val driveTokenProvider: DriveAccessTokenProvider = PlayServicesDriveAuthManager(context),
    private val driveApiClient: GoogleDriveApiClient = GoogleDriveApiClient(driveTokenProvider)
) {
    private val resolver = context.contentResolver
    private val mutex = Mutex()

    val syncMode: LibrarySyncMode
        get() = when (LibrarySyncMode.fromPrefs(prefs)) {
            LibrarySyncMode.GOOGLE_DRIVE -> LibrarySyncMode.GOOGLE_DRIVE
            LibrarySyncMode.LOCAL_FOLDER ->
                if (prefs.getString(KEY_TREE_URI, null) != null) {
                    LibrarySyncMode.LOCAL_FOLDER
                } else {
                    LibrarySyncMode.NONE
                }
            LibrarySyncMode.NONE -> LibrarySyncMode.NONE
        }

    val isConfigured: Boolean
        get() = syncMode != LibrarySyncMode.NONE

    fun targetLabel(): String? = when (syncMode) {
        LibrarySyncMode.GOOGLE_DRIVE -> {
            val email = prefs.getString(KEY_DRIVE_ACCOUNT_EMAIL, null)
            if (!email.isNullOrBlank()) {
                "Google Drive ($email)"
            } else {
                prefs.getString(KEY_TARGET_LABEL, null) ?: "Google Drive (Nexus Launcher)"
            }
        }
        LibrarySyncMode.LOCAL_FOLDER -> {
            val treeUriStr = prefs.getString(KEY_TREE_URI, null) ?: return null
            prefs.getString(KEY_TARGET_LABEL, null)
                ?: SafFolderSyncTransport.treeDisplayName(resolver, Uri.parse(treeUriStr))
                ?: "Selected folder"
        }
        LibrarySyncMode.NONE -> null
    }

    fun driveAccountEmail(): String? =
        if (syncMode == LibrarySyncMode.GOOGLE_DRIVE) {
            prefs.getString(KEY_DRIVE_ACCOUNT_EMAIL, null)
        } else {
            null
        }

    fun lastSyncedAt(): Long? =
        prefs.getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0L }

    private data class ConfigSnapshot(
        val mode: String?,
        val treeUri: String?,
        val driveFolderId: String?,
        val driveAccountEmail: String?,
        val driveAccountName: String?,
        val label: String?,
        val updatedAt: Long,
        val signature: String?,
        val lastSync: Long
    )

    private fun snapshotConfig(): ConfigSnapshot = ConfigSnapshot(
        mode = prefs.getString(KEY_SYNC_MODE, null),
        treeUri = prefs.getString(KEY_TREE_URI, null),
        driveFolderId = prefs.getString(KEY_DRIVE_FOLDER_ID, null),
        driveAccountEmail = prefs.getString(KEY_DRIVE_ACCOUNT_EMAIL, null),
        driveAccountName = prefs.getString(KEY_DRIVE_ACCOUNT_NAME, null),
        label = prefs.getString(KEY_TARGET_LABEL, null),
        updatedAt = prefs.getLong(KEY_UPDATED_AT, 0L),
        signature = prefs.getString(KEY_SIGNATURE, null),
        lastSync = prefs.getLong(KEY_LAST_SYNC, 0L)
    )

    private fun restoreConfig(snapshot: ConfigSnapshot) {
        prefs.edit()
            .putString(KEY_SYNC_MODE, snapshot.mode)
            .putString(KEY_TREE_URI, snapshot.treeUri)
            .putString(KEY_DRIVE_FOLDER_ID, snapshot.driveFolderId)
            .putString(KEY_DRIVE_ACCOUNT_EMAIL, snapshot.driveAccountEmail)
            .putString(KEY_DRIVE_ACCOUNT_NAME, snapshot.driveAccountName)
            .putString(KEY_TARGET_LABEL, snapshot.label)
            .putLong(KEY_UPDATED_AT, snapshot.updatedAt)
            .putString(KEY_SIGNATURE, snapshot.signature)
            .putLong(KEY_LAST_SYNC, snapshot.lastSync)
            .apply()
    }

    /**
     * Configures a local/SAF folder as the exclusive sync target. If Google Drive sync was active,
     * switches cleanly to local folder sync without removing existing local library items.
     */
    suspend fun configureFolder(treeUri: Uri): LibrarySyncOutcome = withContext(Dispatchers.IO) {
        mutex.withLock {
            SafFolderSyncTransport.persistPermission(resolver, treeUri)
            val previous = snapshotConfig()
            val previousMode = LibrarySyncMode.fromPrefs(prefs)
            val label = SafFolderSyncTransport.treeDisplayName(resolver, treeUri) ?: "Selected folder"
            prefs.edit()
                .putString(KEY_SYNC_MODE, LibrarySyncMode.LOCAL_FOLDER.name)
                .putString(KEY_TREE_URI, treeUri.toString())
                .putString(KEY_TARGET_LABEL, label)
                .remove(KEY_DRIVE_FOLDER_ID)
                .remove(KEY_DRIVE_ACCOUNT_EMAIL)
                .remove(KEY_DRIVE_ACCOUNT_NAME)
                .putLong(KEY_UPDATED_AT, 0L)
                .putString(KEY_SIGNATURE, "")
                .apply()
            try {
                val transport = SafFolderSyncTransport(resolver, treeUri)
                transport.ensureReady()
                val outcome = syncLocked(
                    transport = transport,
                    adoptExisting = true,
                    previousVersion = if (previousMode == LibrarySyncMode.LOCAL_FOLDER) {
                        0L
                    } else {
                        previous.updatedAt
                    },
                    switchingMethod = previousMode != LibrarySyncMode.NONE &&
                        previousMode != LibrarySyncMode.LOCAL_FOLDER
                )
                if (previous.treeUri != null && previous.treeUri != treeUri.toString()) {
                    SafFolderSyncTransport.releasePermission(resolver, Uri.parse(previous.treeUri))
                }
                outcome
            } catch (error: Exception) {
                restoreConfig(previous)
                if (previous.treeUri != treeUri.toString()) {
                    SafFolderSyncTransport.releasePermission(resolver, treeUri)
                }
                throw error
            }
        }
    }

    /** Starts interactive Google Drive authorization (`drive.file` scope). */
    suspend fun beginGoogleDriveAuthorization(): DriveAuthorizationStep =
        withContext(Dispatchers.IO) {
            driveTokenProvider.beginInteractiveAuthorization(prefs.getString(KEY_DRIVE_ACCOUNT_EMAIL, null))
        }

    /**
     * Starts the TV/fallback Google account + `GoogleAuthUtil` authorization flow when Play
     * Services' `AuthorizationClient` aborts or is unsupported on the device.
     */
    suspend fun beginFallbackGoogleDriveAuthorization(): DriveAuthorizationStep? =
        withContext(Dispatchers.IO) {
            driveTokenProvider.beginFallbackAccountAuthorization(prefs.getString(KEY_DRIVE_ACCOUNT_EMAIL, null))
        }

    /** Finishes Google Drive authorization after Android consent ActivityResult. */
    suspend fun resolveGoogleDriveAuthorization(
        data: android.content.Intent?
    ): DriveAuthorizationStep.Authorized = withContext(Dispatchers.IO) {
        driveTokenProvider.resolveAuthorizationResult(data)
    }

    /**
     * Resolves an activity result from the fallback [android.accounts.AccountManager] picker or
     * [com.google.android.gms.auth.UserRecoverableAuthException] consent intent.
     */
    suspend fun resolveFallbackGoogleDriveAuthorization(
        resultCode: Int,
        data: android.content.Intent?,
        accountEmail: String?,
        isAccountPicker: Boolean
    ): DriveAuthorizationStep? = withContext(Dispatchers.IO) {
        driveTokenProvider.resolveFallbackActivityResult(
            resultCode = resultCode,
            data = data,
            accountEmail = accountEmail,
            isAccountPicker = isAccountPicker
        )
    }

    /** Polls Google's OAuth 2.0 device token endpoint for Android TV authorization completion. */
    suspend fun pollGoogleDriveDeviceAuthorization(deviceCode: String): DriveDevicePollResult =
        withContext(Dispatchers.IO) {
            driveTokenProvider.pollDeviceAuthorization(deviceCode)
        }

    /** Inspects a non-OK Play Services authorization result intent for the underlying [Throwable]. */
    fun inspectFailedGoogleDriveIntent(data: android.content.Intent?): Throwable? =
        driveTokenProvider.inspectFailedAuthorizationIntent(data)

    /** Formats an actionable diagnostic error explaining required OAuth / SHA-1 setup for Drive. */
    fun driveDiagnosticConfigurationMessage(reason: String): String =
        driveTokenProvider.diagnosticConfigurationMessage(reason)

    /**
     * Activates Google Drive API sync (`drive.file` scope) as the exclusive sync target inside an
     * app-owned `Nexus Launcher` folder. Releases any previously configured local folder permission
     * while keeping all existing local library items, favorites, and artwork intact.
     */
    suspend fun configureGoogleDrive(
        account: DriveAuthAccount? = null
    ): LibrarySyncOutcome = withContext(Dispatchers.IO) {
        mutex.withLock {
            val previous = snapshotConfig()
            val previousMode = LibrarySyncMode.fromPrefs(prefs)
            val resolvedEmail = account?.email ?: previous.driveAccountEmail
            val resolvedName = account?.displayName ?: previous.driveAccountName
            val label = if (!resolvedEmail.isNullOrBlank()) {
                "Google Drive ($resolvedEmail)"
            } else {
                "Google Drive (Nexus Launcher)"
            }

            prefs.edit()
                .putString(KEY_SYNC_MODE, LibrarySyncMode.GOOGLE_DRIVE.name)
                .putString(KEY_DRIVE_ACCOUNT_EMAIL, resolvedEmail)
                .putString(KEY_DRIVE_ACCOUNT_NAME, resolvedName)
                .putString(KEY_TARGET_LABEL, label)
                .remove(KEY_TREE_URI)
                .putLong(KEY_UPDATED_AT, 0L)
                .putString(KEY_SIGNATURE, "")
                .apply()

            try {
                val transport = buildDriveTransportLocked(resolvedEmail)
                transport.ensureReady()
                val outcome = syncLocked(
                    transport = transport,
                    adoptExisting = true,
                    previousVersion = if (previousMode == LibrarySyncMode.GOOGLE_DRIVE) {
                        0L
                    } else {
                        previous.updatedAt
                    },
                    switchingMethod = previousMode != LibrarySyncMode.NONE &&
                        previousMode != LibrarySyncMode.GOOGLE_DRIVE
                )
                previous.treeUri?.let { oldUri ->
                    SafFolderSyncTransport.releasePermission(resolver, Uri.parse(oldUri))
                }
                outcome
            } catch (error: Exception) {
                restoreConfig(previous)
                throw error
            }
        }
    }

    /** Synchronises with whichever sync target is currently active. */
    suspend fun syncNow(): LibrarySyncOutcome = withContext(Dispatchers.IO) {
        mutex.withLock {
            val transport = currentTransportLocked() ?: return@withLock LibrarySyncOutcome.NotConfigured
            syncLocked(
                transport = transport,
                adoptExisting = false,
                previousVersion = prefs.getLong(KEY_UPDATED_AT, 0L),
                switchingMethod = false
            )
        }
    }

    /**
     * Disconnects the active sync method (local folder or Google Drive) without deleting any local
     * library items, favorites, folders, or cached artwork files.
     */
    suspend fun disconnect() {
        val emailToRevoke = mutex.withLock {
            val mode = syncMode
            val email = prefs.getString(KEY_DRIVE_ACCOUNT_EMAIL, null)
            prefs.getString(KEY_TREE_URI, null)?.let { stored ->
                SafFolderSyncTransport.releasePermission(resolver, Uri.parse(stored))
            }
            prefs.edit()
                .putString(KEY_SYNC_MODE, LibrarySyncMode.NONE.name)
                .remove(KEY_TREE_URI)
                .remove(KEY_DRIVE_FOLDER_ID)
                .remove(KEY_DRIVE_ACCOUNT_EMAIL)
                .remove(KEY_DRIVE_ACCOUNT_NAME)
                .remove(KEY_TARGET_LABEL)
                .remove(KEY_UPDATED_AT)
                .remove(KEY_SIGNATURE)
                .remove(KEY_LAST_SYNC)
                .apply()
            if (mode == LibrarySyncMode.GOOGLE_DRIVE) email else null
        }
        if (emailToRevoke != null) {
            withContext(Dispatchers.IO) {
                driveTokenProvider.revokeAccess(emailToRevoke)
            }
        }
    }

    private data class LocalSyncPayload(
        val items: List<GameItem>,
        val appCustomizations: List<GameItem>,
        val installedAppIds: Set<String>
    )

    /**
     * Watches the full library and writes to the active sync target whenever shareable content changes.
     */
    @OptIn(FlowPreview::class)
    fun observeLibraryChanges(
        scope: CoroutineScope,
        onError: (Throwable) -> Unit = {}
    ) {
        scope.launch {
            repository.allItemsIncludingDeleted
                // Startup performs a burst of writes (installed-app scan, artwork backfill).
                // Debounce before mapping because building the payload hashes every local
                // artwork file; doing that per write cost far more than the sync itself.
                .debounce(WRITE_DEBOUNCE_MS)
                .mapNotNull { rows ->
                    // Nothing to push when no target is configured. Reading the flag here
                    // (rather than filtering the flow) still picks up a target configured
                    // while the launcher is running.
                    if (!isConfigured) return@mapNotNull null
                    val payload = LocalSyncPayload(
                        items = syncable(rows),
                        appCustomizations = syncableAppCustomizations(rows),
                        installedAppIds = rows
                            .filter { it.type == GameType.INSTALLED_APP }
                            .mapTo(HashSet()) { it.id }
                    )
                    payload to librarySignature(payload.items, payload.appCustomizations)
                }
                .distinctUntilChangedBy { it.second }
                .collect { (payload, sig) ->
                    if (sig == prefs.getString(KEY_SIGNATURE, "")) return@collect
                    withContext(Dispatchers.IO) {
                        mutex.withLock {
                            if (!isConfigured || sig == prefs.getString(KEY_SIGNATURE, "")) {
                                return@withLock
                            }
                            val transport = currentTransportLocked() ?: return@withLock
                            prefs.edit().putLong(KEY_UPDATED_AT, nextVersion()).apply()
                            runCatching {
                                writeLocked(
                                    transport = transport,
                                    items = payload.items,
                                    localApps = payload.appCustomizations,
                                    localInstalledAppIds = payload.installedAppIds
                                )
                            }.onFailure { error ->
                                onError(error)
                            }
                        }
                    }
                }
        }
    }

    private fun currentTransportLocked(): LibrarySyncTransport? = when (syncMode) {
        LibrarySyncMode.GOOGLE_DRIVE ->
            buildDriveTransportLocked(prefs.getString(KEY_DRIVE_ACCOUNT_EMAIL, null))
        LibrarySyncMode.LOCAL_FOLDER ->
            prefs.getString(KEY_TREE_URI, null)?.let { uriStr ->
                SafFolderSyncTransport(resolver, Uri.parse(uriStr))
            }
        LibrarySyncMode.NONE -> null
    }

    private fun buildDriveTransportLocked(preferredEmail: String?): GoogleDriveSyncTransport =
        GoogleDriveSyncTransport(
            api = driveApiClient,
            preferredEmail = preferredEmail,
            initialFolderId = prefs.getString(KEY_DRIVE_FOLDER_ID, null),
            onFolderResolved = { folderId ->
                prefs.edit().putString(KEY_DRIVE_FOLDER_ID, folderId).apply()
            }
        )

    // ---- Core sync ----------------------------------------------------------------

    private suspend fun syncLocked(
        transport: LibrarySyncTransport,
        adoptExisting: Boolean,
        previousVersion: Long,
        switchingMethod: Boolean
    ): LibrarySyncOutcome {
        val remoteDoc = transport.readLibrary(forceRefresh = true)
        val snapshot = remoteDoc?.parsed
        val allRows = repository.getAllItemsSnapshot()
        val localItems = syncable(allRows)
        val localApps = syncableAppCustomizations(allRows)
        val localInstalledAppIds = allRows
            .filter { it.type == GameType.INSTALLED_APP }
            .mapTo(HashSet()) { it.id }

        if (snapshot == null || (snapshot.items.isEmpty() && snapshot.appCustomizations.isEmpty())) {
            // Seed target from this device (writes json and exports all assigned posters).
            return writeLocked(
                transport = transport,
                items = localItems,
                localApps = localApps,
                localInstalledAppIds = localInstalledAppIds,
                minVersion = snapshot?.updatedAt ?: 0L,
                existingDocHandle = remoteDoc?.handleId,
                existingSnapshot = snapshot
            )
        }

        val relevantRemoteApps = snapshot.appCustomizations.filter { it.id in localInstalledAppIds }
        val relevantRemoteAppHashes = snapshot.appPosterHashes.filterKeys { it in localInstalledAppIds }

        if (!snapshot.authoritative) {
            val importedItems = importPosters(snapshot.items, snapshot.posterHashes, transport)
            val importedApps = importPosters(relevantRemoteApps, relevantRemoteAppHashes, transport)
            repository.mergeSyncedLibrary(importedItems.items)
            repository.applySyncedAppCustomizations(
                remoteApps = importedApps.items,
                tracksLocalPosters = snapshot.tracksLocalPosters,
                preserveLocalCustomizations = switchingMethod
            )
            val updatedRows = repository.getAllItemsSnapshot()
            val updatedInstalledIds = updatedRows
                .filter { it.type == GameType.INSTALLED_APP }
                .mapTo(HashSet()) { it.id }
            return writeLocked(
                transport = transport,
                items = syncable(updatedRows),
                localApps = syncableAppCustomizations(updatedRows),
                localInstalledAppIds = updatedInstalledIds,
                minVersion = snapshot.updatedAt,
                existingDocHandle = remoteDoc.handleId,
                existingSnapshot = snapshot
            )
        }

        val localVersion = prefs.getLong(KEY_UPDATED_AT, 0L)
        val remoteSignature = LibrarySyncCodec.librarySignature(
            items = snapshot.items,
            appCustomizations = relevantRemoteApps,
            itemHashOverrides = snapshot.posterHashes,
            appHashOverrides = relevantRemoteAppHashes,
            localHashForUrl = ::localHashForUrl
        )
        val localSignature = librarySignature(localItems, localApps)
        val differs = remoteSignature != localSignature
        val fileIsNewer = adoptExisting || snapshot.updatedAt > localVersion

        val remoteItemIds = snapshot.items.mapTo(HashSet()) { it.id }
        val remoteAppIds = relevantRemoteApps.mapTo(HashSet()) { it.id }
        val hasLocalOnlyItems = localItems.any { !it.isDeleted && it.id !in remoteItemIds }
        val hasLocalOnlyApps = localApps.any { it.id !in remoteAppIds }
        if (adoptExisting && (switchingMethod || hasLocalOnlyItems || hasLocalOnlyApps)) {
            return adoptMergingExistingLibrary(
                transport = transport,
                remoteDoc = remoteDoc,
                snapshot = snapshot,
                localItems = localItems,
                localApps = localApps,
                localInstalledAppIds = localInstalledAppIds,
                relevantRemoteApps = relevantRemoteApps,
                relevantRemoteAppHashes = relevantRemoteAppHashes,
                previousVersion = previousVersion,
                switchingMethod = switchingMethod
            )
        }

        if (differs && fileIsNewer) {
            val importedItems = importPosters(snapshot.items, snapshot.posterHashes, transport)
            val importedApps = importPosters(relevantRemoteApps, relevantRemoteAppHashes, transport)
            repository.replaceSyncedLibrary(importedItems.items, snapshot.tracksLocalPosters)
            repository.applySyncedAppCustomizations(importedApps.items, snapshot.tracksLocalPosters)
            val appliedRows = repository.getAllItemsSnapshot()
            val appliedSignature = librarySignature(
                items = syncable(appliedRows),
                appCustomizations = syncableAppCustomizations(appliedRows)
            )
            val missingAnyPoster = importedItems.missingExpectedPoster ||
                importedApps.missingExpectedPoster
            val storedVersion = if (missingAnyPoster) {
                (snapshot.updatedAt - 1L).coerceAtLeast(0L)
            } else {
                snapshot.updatedAt
            }
            prefs.edit()
                .putLong(KEY_UPDATED_AT, storedVersion)
                .putString(KEY_SIGNATURE, appliedSignature)
                .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                .apply()
            return LibrarySyncOutcome.Applied(snapshot.items.size)
        }

        if (differs) {
            if (snapshot.updatedAt >= localVersion) {
                val repairedItems = importPosters(
                    items = localItems,
                    posterHashes = snapshot.posterHashes,
                    transport = transport,
                    forceRefresh = true
                )
                val repairedApps = importPosters(
                    items = localApps,
                    posterHashes = snapshot.appPosterHashes,
                    transport = transport,
                    forceRefresh = true
                )
                val changedItems = repairedItems.items.filterIndexed { index, updated ->
                    updated != localItems[index]
                }
                val changedApps = repairedApps.items.filterIndexed { index, updated ->
                    updated != localApps[index]
                }
                if (changedItems.isNotEmpty() || changedApps.isNotEmpty()) {
                    repository.updateSyncedPosters(changedItems + changedApps)
                }
                val repairedSignature = librarySignature(repairedItems.items, repairedApps.items)
                if (repairedSignature == remoteSignature) {
                    val missingAnyPoster = repairedItems.missingExpectedPoster ||
                        repairedApps.missingExpectedPoster
                    val storedVersion = if (missingAnyPoster) {
                        (snapshot.updatedAt - 1L).coerceAtLeast(0L)
                    } else {
                        snapshot.updatedAt
                    }
                    prefs.edit()
                        .putLong(KEY_UPDATED_AT, storedVersion)
                        .putString(KEY_SIGNATURE, repairedSignature)
                        .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                        .apply()
                    return LibrarySyncOutcome.Applied(repairedItems.items.size)
                }
            }
            return writeLocked(
                transport = transport,
                items = localItems,
                localApps = localApps,
                localInstalledAppIds = localInstalledAppIds,
                minVersion = snapshot.updatedAt,
                existingDocHandle = remoteDoc.handleId,
                existingSnapshot = snapshot
            )
        }

        // Metadata matches, yet remote posters/ may have updated or missing assets locally.
        val reconciledItems = importPosters(
            items = localItems,
            posterHashes = snapshot.posterHashes,
            transport = transport,
            forceRefresh = true
        )
        val reconciledApps = importPosters(
            items = localApps,
            posterHashes = snapshot.appPosterHashes,
            transport = transport,
            forceRefresh = true
        )
        val changedItems = reconciledItems.items.filterIndexed { index, updated ->
            updated != localItems[index]
        }
        val changedApps = reconciledApps.items.filterIndexed { index, updated ->
            updated != localApps[index]
        }
        val pulledAny = reconciledItems.pulledCount > 0 || reconciledApps.pulledCount > 0
        if (changedItems.isNotEmpty() || changedApps.isNotEmpty()) {
            repository.updateSyncedPosters(changedItems + changedApps)
        }

        exportPosters(reconciledItems.items + reconciledApps.items, transport)

        val updatedRows = if (changedItems.isNotEmpty() || changedApps.isNotEmpty()) {
            repository.getAllItemsSnapshot()
        } else {
            allRows
        }
        val finalSignature = librarySignature(
            items = syncable(updatedRows),
            appCustomizations = syncableAppCustomizations(updatedRows)
        )
        val missingAnyPoster = reconciledItems.missingExpectedPoster ||
            reconciledApps.missingExpectedPoster
        val storedVersion = if (missingAnyPoster) {
            (snapshot.updatedAt - 1L).coerceAtLeast(0L)
        } else {
            maxOf(localVersion, snapshot.updatedAt)
        }
        prefs.edit()
            .putLong(KEY_UPDATED_AT, storedVersion)
            .putString(KEY_SIGNATURE, finalSignature)
            .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
            .apply()
        return if (changedItems.isNotEmpty() || changedApps.isNotEmpty() || pulledAny) {
            LibrarySyncOutcome.Applied(reconciledItems.items.size)
        } else {
            LibrarySyncOutcome.UpToDate
        }
    }

    /**
     * Merges a newly connected remote backup into an already-populated local library without
     * ever deleting local items, folders, favorites, or artwork, and writes any combined delta
     * back to the new sync target.
     */
    private suspend fun adoptMergingExistingLibrary(
        transport: LibrarySyncTransport,
        remoteDoc: RemoteLibraryDoc,
        snapshot: LibrarySyncCodec.Parsed,
        localItems: List<GameItem>,
        localApps: List<GameItem>,
        localInstalledAppIds: Set<String>,
        relevantRemoteApps: List<GameItem>,
        relevantRemoteAppHashes: Map<String, LibrarySyncCodec.PosterHashes>,
        previousVersion: Long,
        switchingMethod: Boolean
    ): LibrarySyncOutcome {
        val remoteIsNewer = !switchingMethod || snapshot.updatedAt > previousVersion
        val remoteItemIds = snapshot.items.mapTo(HashSet()) { it.id }
        val remoteAppIds = relevantRemoteApps.mapTo(HashSet()) { it.id }
        val hasLocalOnlyItems = localItems.any { !it.isDeleted && it.id !in remoteItemIds }
        val hasLocalOnlyApps = localApps.any { it.id !in remoteAppIds }

        val importedItems = importPosters(snapshot.items, snapshot.posterHashes, transport)
        val importedApps = importPosters(relevantRemoteApps, relevantRemoteAppHashes, transport)

        val addedFromRemote = repository.mergeSyncedLibraryPreservingExisting(
            remoteItems = importedItems.items,
            allowOverwriteMatching = remoteIsNewer
        )
        repository.applySyncedAppCustomizations(
            remoteApps = importedApps.items,
            tracksLocalPosters = snapshot.tracksLocalPosters,
            preserveLocalCustomizations = !remoteIsNewer || switchingMethod
        )

        val mergedRows = repository.getAllItemsSnapshot()
        val mergedSyncable = syncable(mergedRows)
        val mergedApps = syncableAppCustomizations(mergedRows)
        val mergedInstalledIds = mergedRows
            .filter { it.type == GameType.INSTALLED_APP }
            .mapTo(HashSet()) { it.id }

        val mergedRemoteApps = snapshot.appCustomizations.filter { it.id in mergedInstalledIds }
        val mergedRemoteAppHashes = snapshot.appPosterHashes.filterKeys { it in mergedInstalledIds }
        val remoteSignature = LibrarySyncCodec.librarySignature(
            items = snapshot.items,
            appCustomizations = mergedRemoteApps,
            itemHashOverrides = snapshot.posterHashes,
            appHashOverrides = mergedRemoteAppHashes,
            localHashForUrl = ::localHashForUrl
        )
        val mergedSignature = librarySignature(mergedSyncable, mergedApps)

        if (switchingMethod || hasLocalOnlyItems || hasLocalOnlyApps || mergedSignature != remoteSignature) {
            writeLocked(
                transport = transport,
                items = mergedSyncable,
                localApps = mergedApps,
                localInstalledAppIds = mergedInstalledIds,
                minVersion = maxOf(snapshot.updatedAt, previousVersion),
                existingDocHandle = remoteDoc.handleId,
                existingSnapshot = snapshot
            )
            return if (addedFromRemote > 0 || hasLocalOnlyItems || switchingMethod) {
                LibrarySyncOutcome.Merged(
                    itemCount = mergedSyncable.count { !it.isDeleted },
                    addedFromRemote = addedFromRemote
                )
            } else {
                LibrarySyncOutcome.WroteLocal(mergedSyncable.count { !it.isDeleted })
            }
        }

        val missingAnyPoster = importedItems.missingExpectedPoster ||
            importedApps.missingExpectedPoster
        val storedVersion = if (missingAnyPoster) {
            (snapshot.updatedAt - 1L).coerceAtLeast(0L)
        } else {
            snapshot.updatedAt
        }
        prefs.edit()
            .putLong(KEY_UPDATED_AT, storedVersion)
            .putString(KEY_SIGNATURE, mergedSignature)
            .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
            .apply()
        return LibrarySyncOutcome.Applied(snapshot.items.size)
    }

    private suspend fun writeLocked(
        transport: LibrarySyncTransport,
        items: List<GameItem>,
        localApps: List<GameItem>,
        localInstalledAppIds: Set<String>,
        minVersion: Long = 0L,
        existingDocHandle: String? = null,
        existingSnapshot: LibrarySyncCodec.Parsed? = null
    ): LibrarySyncOutcome {
        val currentRemote = if (existingSnapshot == null || existingDocHandle == null) {
            transport.readLibrary(forceRefresh = false)
        } else {
            null
        }
        val docHandle = existingDocHandle ?: currentRemote?.handleId
        val baseSnapshot = existingSnapshot ?: currentRemote?.parsed

        val preservedRemoteApps = baseSnapshot?.appCustomizations
            ?.filter { it.id !in localInstalledAppIds }
            .orEmpty()
        val preservedAppHashes = baseSnapshot?.appPosterHashes
            .orEmpty()
            .filterKeys { it !in localInstalledAppIds }
        val mergedApps = (localApps + preservedRemoteApps).sortedBy { it.id }

        val version = maxOf(nextVersion(), minVersion + 1L)
        val document = buildDocument(
            items = items,
            updatedAt = version,
            appCustomizations = mergedApps,
            appPosterHashOverrides = preservedAppHashes
        ).toString()

        transport.writeLibrary(docHandle, document)
        exportPosters(items + localApps, transport)

        prefs.edit()
            .putLong(KEY_UPDATED_AT, version)
            .putString(KEY_SIGNATURE, librarySignature(items, localApps))
            .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
            .apply()
        return LibrarySyncOutcome.WroteLocal(items.size)
    }

    private fun nextVersion(): Long =
        maxOf(System.currentTimeMillis(), prefs.getLong(KEY_UPDATED_AT, 0L) + 1L)

    // ---- Poster Export & Import ---------------------------------------------------

    private suspend fun exportPosters(items: List<GameItem>, transport: LibrarySyncTransport) {
        runCatching {
            val postersContainerId = transport.ensurePostersContainer(forceRefresh = true) ?: return
            val existingFiles = transport.listRemotePosters(forceRefresh = true)

            items.forEach { item ->
                exportSinglePosterSlot(
                    transport = transport,
                    postersContainerId = postersContainerId,
                    existingFiles = existingFiles,
                    fileName = posterFileNameFor(item.id),
                    rawUrl = if (item.isDeleted) null else item.posterUrl
                )
                exportSinglePosterSlot(
                    transport = transport,
                    postersContainerId = postersContainerId,
                    existingFiles = existingFiles,
                    fileName = nsfwPosterFileNameFor(item.id),
                    rawUrl = if (item.isDeleted) null else item.nsfwPosterUrl
                )
            }
        }
    }

    private suspend fun exportSinglePosterSlot(
        transport: LibrarySyncTransport,
        postersContainerId: String,
        existingFiles: MutableMap<String, RemotePosterEntry>,
        fileName: String,
        rawUrl: String?
    ) {
        val existingEntry = existingFiles[fileName]
        if (rawUrl.isNullOrBlank()) {
            if (existingEntry != null) {
                transport.deletePoster(existingEntry)
                existingFiles.remove(fileName)
            }
            return
        }

        val isLocal = rawUrl.startsWith("file:") || rawUrl.startsWith("content:")
        if (!isLocal && existingEntry != null && existingEntry.size > 0L) {
            return
        }

        val bytes = fetchPosterBytes(rawUrl) ?: return
        val sha256 = LibrarySyncCodec.sha256Hex(bytes)
        if (existingEntry != null &&
            existingEntry.sha256 == sha256 &&
            existingEntry.size == bytes.size.toLong()
        ) {
            return
        }

        val uploaded = transport.uploadPoster(
            postersContainerId = postersContainerId,
            fileName = fileName,
            bytes = bytes,
            sha256Hex = sha256,
            existing = existingEntry
        )
        if (uploaded != null) {
            existingFiles[fileName] = uploaded
        }
    }

    private data class ImportResult(
        val items: List<GameItem>,
        val missingExpectedPoster: Boolean,
        val pulledCount: Int = 0
    )

    private suspend fun importPosters(
        items: List<GameItem>,
        posterHashes: Map<String, LibrarySyncCodec.PosterHashes>,
        transport: LibrarySyncTransport,
        forceRefresh: Boolean = true
    ): ImportResult {
        return runCatching {
            val existingEntries = transport.listRemotePosters(forceRefresh = forceRefresh)
            val localDir = File(context.filesDir, "custom_art").apply { mkdirs() }
            var missingExpectedPoster = false
            var pulledCount = 0

            val updatedItems = items.map { item ->
                val safeId = item.id.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val expectedHashes = posterHashes[item.id]
                var updated = item

                val posterEntry = existingEntries[posterFileNameFor(item.id)]
                if (slotNeedsPosterPull(item.posterUrl, expectedHashes?.posterHash, posterEntry)) {
                    val importedPoster = copyPosterFromTransport(
                        transport = transport,
                        posterEntry = posterEntry,
                        localDir = localDir,
                        filePrefix = "$safeId-synced",
                        expectedHash = expectedHashes?.posterHash,
                        forceRefresh = forceRefresh
                    )
                    if (importedPoster != null) {
                        pulledCount++
                        updated = updated.copy(
                            posterUrl = importedPoster,
                            customArtworkSource = updated.customArtworkSource ?: "SYNCED"
                        )
                    } else if (expectedHashes?.posterHash != null) {
                        missingExpectedPoster = true
                    }
                }

                val nsfwEntry = existingEntries[nsfwPosterFileNameFor(item.id)]
                if (slotNeedsPosterPull(item.nsfwPosterUrl, expectedHashes?.nsfwPosterHash, nsfwEntry)) {
                    val importedNsfw = copyPosterFromTransport(
                        transport = transport,
                        posterEntry = nsfwEntry,
                        localDir = localDir,
                        filePrefix = "$safeId-nsfw-synced",
                        expectedHash = expectedHashes?.nsfwPosterHash,
                        forceRefresh = forceRefresh
                    )
                    if (importedNsfw != null) {
                        pulledCount++
                        updated = updated.copy(nsfwPosterUrl = importedNsfw)
                    } else if (expectedHashes?.nsfwPosterHash != null) {
                        missingExpectedPoster = true
                    }
                }

                updated
            }
            ImportResult(updatedItems, missingExpectedPoster, pulledCount)
        }.getOrElse {
            val expectsAnyPoster = posterHashes.values.any {
                it.posterHash != null || it.nsfwPosterHash != null
            }
            ImportResult(items, missingExpectedPoster = expectsAnyPoster, pulledCount = 0)
        }
    }

    private fun slotNeedsPosterPull(
        currentUrl: String?,
        expectedHash: String?,
        remoteEntry: RemotePosterEntry?
    ): Boolean {
        if (currentUrl.isNullOrBlank()) return true
        if (!currentUrl.startsWith("file:")) return false

        val path = runCatching { Uri.parse(currentUrl).path }.getOrNull() ?: return true
        val localFile = File(path)
        if (!localFile.exists() || localFile.length() <= 0L) return true

        if (!expectedHash.isNullOrBlank()) {
            return localHashForUrl(currentUrl) != expectedHash
        }
        return remoteEntry != null &&
            currentUrl.contains("-synced") &&
            remoteEntry.lastModified > localFile.lastModified()
    }

    private suspend fun copyPosterFromTransport(
        transport: LibrarySyncTransport,
        posterEntry: RemotePosterEntry?,
        localDir: File,
        filePrefix: String,
        expectedHash: String?,
        forceRefresh: Boolean
    ): String? {
        if (posterEntry == null) return null

        if (!expectedHash.isNullOrBlank()) {
            val expectedTarget = File(localDir, "$filePrefix-${expectedHash.take(12)}.png")
            if (expectedTarget.exists() && expectedTarget.length() > 0L) {
                val existingHash = runCatching {
                    LibrarySyncCodec.sha256Hex(expectedTarget.readBytes())
                }.getOrNull()
                if (existingHash == expectedHash) {
                    cleanupOlderSyncedFiles(localDir, filePrefix, expectedTarget)
                    return Uri.fromFile(expectedTarget).toString()
                }
            }
        }

        val remoteBytes = transport.downloadPosterBytes(posterEntry, expectedHash, forceRefresh)
            ?.takeIf { it.isNotEmpty() } ?: return null
        val actualHash = LibrarySyncCodec.sha256Hex(remoteBytes)
        if (!expectedHash.isNullOrBlank() && actualHash != expectedHash) {
            return null
        }

        val targetFile = File(localDir, "$filePrefix-${actualHash.take(12)}.png")
        val written = runCatching {
            val needsWrite = !targetFile.exists() ||
                targetFile.length() != remoteBytes.size.toLong() ||
                LibrarySyncCodec.sha256Hex(targetFile.readBytes()) != actualHash
            if (needsWrite) {
                targetFile.writeBytes(remoteBytes)
            }
            cleanupOlderSyncedFiles(localDir, filePrefix, targetFile)
            targetFile.exists() && targetFile.length() > 0L
        }.getOrDefault(false)
        return if (written) Uri.fromFile(targetFile).toString() else null
    }

    private fun cleanupOlderSyncedFiles(
        localDir: File,
        filePrefix: String,
        keepFile: File
    ) {
        localDir.listFiles { file ->
            file != keepFile &&
                (file.name == "$filePrefix.png" || file.name.startsWith("$filePrefix-"))
        }?.forEach { it.delete() }
    }

    private fun posterFileNameFor(itemId: String): String {
        val safeId = itemId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return "poster_$safeId.png"
    }

    private fun nsfwPosterFileNameFor(itemId: String): String {
        val safeId = itemId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return "poster_nsfw_$safeId.png"
    }

    private fun fetchPosterBytes(rawUrl: String): ByteArray? = runCatching {
        when {
            rawUrl.startsWith("file:") -> {
                val path = Uri.parse(rawUrl).path ?: return null
                File(path).takeIf { it.exists() && it.length() > 0 }?.readBytes()
            }
            rawUrl.startsWith("content:") -> {
                resolver.openInputStream(Uri.parse(rawUrl))?.use { it.readBytes() }
            }
            rawUrl.startsWith("http://") || rawUrl.startsWith("https://") -> {
                val conn = java.net.URL(rawUrl).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 4000
                conn.readTimeout = 5000
                conn.instanceFollowRedirects = true
                if (conn.responseCode in 200..299) {
                    conn.inputStream.use { it.readBytes() }
                } else null
            }
            else -> null
        }
    }.getOrNull()

    // ---- JSON & Codec -------------------------------------------------------------

    private fun buildDocument(
        items: List<GameItem>,
        updatedAt: Long,
        appCustomizations: List<GameItem> = emptyList(),
        appPosterHashOverrides: Map<String, LibrarySyncCodec.PosterHashes> = emptyMap()
    ): JSONObject = LibrarySyncCodec.buildDocument(
        items = items,
        updatedAt = updatedAt,
        localHashForUrl = ::localHashForUrl,
        appCustomizations = appCustomizations,
        appPosterHashOverrides = appPosterHashOverrides
    )

    private fun syncable(rows: List<GameItem>): List<GameItem> = LibrarySyncCodec.syncable(rows)

    private fun syncableAppCustomizations(rows: List<GameItem>): List<GameItem> =
        LibrarySyncCodec.syncableAppCustomizations(rows)

    private fun librarySignature(
        items: List<GameItem>,
        appCustomizations: List<GameItem>
    ): String = LibrarySyncCodec.librarySignature(
        items = items,
        appCustomizations = appCustomizations,
        localHashForUrl = ::localHashForUrl
    )

    private fun localHashForUrl(url: String): String? = when {
        url.startsWith("file:") -> LibrarySyncCodec.localFileSha256(url)
        url.startsWith("content:") -> runCatching {
            resolver.openInputStream(Uri.parse(url))?.use { stream ->
                val bytes = stream.readBytes()
                if (bytes.isEmpty()) null else LibrarySyncCodec.sha256Hex(bytes)
            }
        }.getOrNull()
        else -> null
    }

    companion object {
        const val FILE_NAME = "nexus-library.json"
        const val POSTERS_DIR_NAME = "posters"

        private const val WRITE_DEBOUNCE_MS = 750L

        internal const val KEY_SYNC_MODE = "library_sync_mode"
        internal const val KEY_TREE_URI = "library_sync_tree_uri"
        internal const val KEY_DRIVE_FOLDER_ID = "library_sync_drive_folder_id"
        internal const val KEY_DRIVE_ACCOUNT_EMAIL = "library_sync_drive_account_email"
        internal const val KEY_DRIVE_ACCOUNT_NAME = "library_sync_drive_account_name"
        private const val KEY_TARGET_LABEL = "library_sync_target_label"
        private const val KEY_UPDATED_AT = "library_sync_updated_at"
        private const val KEY_SIGNATURE = "library_sync_signature"
        private const val KEY_LAST_SYNC = "library_sync_last_sync"
    }
}
