package com.example.data.service

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import com.example.data.model.GameItem
import com.example.data.model.GameType
import com.example.data.repository.GameRepository
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Result of a library folder sync or write. */
sealed interface LibrarySyncOutcome {
    data class Applied(val itemCount: Int) : LibrarySyncOutcome
    data class WroteLocal(val itemCount: Int) : LibrarySyncOutcome
    data object UpToDate : LibrarySyncOutcome
    data object NotConfigured : LibrarySyncOutcome
}

/**
 * Keeps the shareable game library synchronised with a folder chosen through the Storage
 * Access Framework.
 *
 * Inside the chosen folder:
 * - `nexus-library.json` records game metadata, launch URLs, custom titles, tags, and tombstones.
 * - `posters/` stores all assigned game poster images so custom artwork and covers travel
 *   intact across devices.
 *
 * Local changes are written back automatically; on launch, unlock or explicit sync, the folder
 * is read and wins when it is newer than the version seen on this device.
 */
class LibrarySyncService(
    private val context: Context,
    private val repository: GameRepository,
    private val prefs: SharedPreferences
) {
    private val resolver = context.contentResolver
    private val mutex = Mutex()

    val isConfigured: Boolean
        get() = prefs.getString(KEY_TREE_URI, null) != null

    fun targetLabel(): String? {
        if (!isConfigured) return null
        val treeUriStr = prefs.getString(KEY_TREE_URI, null) ?: return null
        return prefs.getString(KEY_TARGET_LABEL, null)
            ?: runCatching { treeDisplayName(Uri.parse(treeUriStr)) }.getOrNull()
            ?: "Selected folder"
    }

    fun lastSyncedAt(): Long? =
        prefs.getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0L }

    private data class ConfigSnapshot(
        val treeUri: String?,
        val label: String?,
        val updatedAt: Long,
        val signature: String?
    )

    private fun snapshotConfig(): ConfigSnapshot = ConfigSnapshot(
        treeUri = prefs.getString(KEY_TREE_URI, null),
        label = prefs.getString(KEY_TARGET_LABEL, null),
        updatedAt = prefs.getLong(KEY_UPDATED_AT, 0L),
        signature = prefs.getString(KEY_SIGNATURE, null)
    )

    private fun restoreConfig(snapshot: ConfigSnapshot) {
        prefs.edit()
            .putString(KEY_TREE_URI, snapshot.treeUri)
            .putString(KEY_TARGET_LABEL, snapshot.label)
            .putLong(KEY_UPDATED_AT, snapshot.updatedAt)
            .putString(KEY_SIGNATURE, snapshot.signature)
            .apply()
    }

    /** Uses a folder as the sync target, creating `nexus-library.json` and `posters/` inside it. */
    suspend fun configureFolder(treeUri: Uri): LibrarySyncOutcome = withContext(Dispatchers.IO) {
        mutex.withLock {
            persistPermission(treeUri)
            val previous = snapshotConfig()
            prefs.edit()
                .putString(KEY_TREE_URI, treeUri.toString())
                .putString(KEY_TARGET_LABEL, treeDisplayName(treeUri) ?: "Selected folder")
                .putLong(KEY_UPDATED_AT, 0L)
                .putString(KEY_SIGNATURE, "")
                .apply()
            try {
                // Ensure the metadata file exists before the first read.
                writableDocumentUri(treeUri)
                    ?: throw IllegalStateException("Could not access or create $FILE_NAME in that folder")
                val outcome = syncLocked(treeUri, adoptExisting = true)
                releasePreviousPermission(previous, treeUri.toString())
                outcome
            } catch (error: Exception) {
                restoreConfig(previous)
                throw error
            }
        }
    }

    /** Reads the folder and restores it when newer, otherwise writes local changes and exports posters. */
    suspend fun syncNow(): LibrarySyncOutcome = withContext(Dispatchers.IO) {
        mutex.withLock {
            val treeUri = resolveTreeUri() ?: return@withLock LibrarySyncOutcome.NotConfigured
            syncLocked(treeUri, adoptExisting = false)
        }
    }

    fun disconnect() {
        prefs.getString(KEY_TREE_URI, null)?.let { stored ->
            releasePermission(Uri.parse(stored))
        }
        prefs.edit()
            .remove(KEY_TREE_URI)
            .remove(KEY_TARGET_LABEL)
            .remove(KEY_UPDATED_AT)
            .remove(KEY_SIGNATURE)
            .remove(KEY_LAST_SYNC)
            .apply()
    }

    private fun releasePreviousPermission(previous: ConfigSnapshot, current: String) {
        val old = previous.treeUri ?: return
        if (old != current) releasePermission(Uri.parse(old))
    }

    private fun releasePermission(uri: Uri) {
        runCatching {
            resolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    private data class LocalSyncPayload(
        val items: List<GameItem>,
        val appCustomizations: List<GameItem>,
        val installedAppIds: Set<String>
    )

    /**
     * Watches the full library and writes to the sync folder whenever shareable content changes.
     */
    @OptIn(FlowPreview::class)
    fun observeLibraryChanges(
        scope: CoroutineScope,
        onError: (String) -> Unit = {}
    ) {
        scope.launch {
            repository.allItemsIncludingDeleted
                .map { rows ->
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
                .onEach { (_, sig) ->
                    if (!isConfigured || sig == prefs.getString(KEY_SIGNATURE, "")) {
                        return@onEach
                    }
                    prefs.edit().putLong(KEY_UPDATED_AT, nextVersion()).apply()
                }
                .debounce(WRITE_DEBOUNCE_MS)
                .collect { (payload, sig) ->
                    if (!isConfigured || sig == prefs.getString(KEY_SIGNATURE, "")) {
                        return@collect
                    }
                    withContext(Dispatchers.IO) {
                        mutex.withLock {
                            val treeUri = resolveTreeUri() ?: return@withLock
                            runCatching {
                                writeLocked(
                                    treeUri = treeUri,
                                    items = payload.items,
                                    localApps = payload.appCustomizations,
                                    localInstalledAppIds = payload.installedAppIds
                                )
                            }.onFailure {
                                onError(it.message ?: "Library folder sync failed")
                            }
                        }
                    }
                }
        }
    }

    // ---- Core sync ----------------------------------------------------------------

    private suspend fun syncLocked(treeUri: Uri, adoptExisting: Boolean): LibrarySyncOutcome {
        val snapshot = readDocument(treeUri)
        val allRows = repository.getAllItemsSnapshot()
        val localItems = syncable(allRows)
        val localApps = syncableAppCustomizations(allRows)
        val localInstalledAppIds = allRows
            .filter { it.type == GameType.INSTALLED_APP }
            .mapTo(HashSet()) { it.id }

        if (snapshot == null || (snapshot.items.isEmpty() && snapshot.appCustomizations.isEmpty())) {
            // Seed folder from this device (writes json and exports all assigned posters).
            return writeLocked(
                treeUri = treeUri,
                items = localItems,
                localApps = localApps,
                localInstalledAppIds = localInstalledAppIds,
                minVersion = snapshot?.updatedAt ?: 0L,
                existingSnapshot = snapshot
            )
        }

        val relevantRemoteApps = snapshot.appCustomizations.filter { it.id in localInstalledAppIds }
        val relevantRemoteAppHashes = snapshot.appPosterHashes.filterKeys { it in localInstalledAppIds }

        if (!snapshot.authoritative) {
            val importedItems = importPosters(snapshot.items, snapshot.posterHashes, treeUri)
            val importedApps = importPosters(relevantRemoteApps, relevantRemoteAppHashes, treeUri)
            repository.mergeSyncedLibrary(importedItems.items)
            repository.applySyncedAppCustomizations(importedApps.items, snapshot.tracksLocalPosters)
            val updatedRows = repository.getAllItemsSnapshot()
            val updatedInstalledIds = updatedRows
                .filter { it.type == GameType.INSTALLED_APP }
                .mapTo(HashSet()) { it.id }
            return writeLocked(
                treeUri = treeUri,
                items = syncable(updatedRows),
                localApps = syncableAppCustomizations(updatedRows),
                localInstalledAppIds = updatedInstalledIds,
                minVersion = snapshot.updatedAt,
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

        if (differs && fileIsNewer) {
            val importedItems = importPosters(snapshot.items, snapshot.posterHashes, treeUri)
            val importedApps = importPosters(relevantRemoteApps, relevantRemoteAppHashes, treeUri)
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
                // Keep localVersion behind snapshot.updatedAt so a partially synced Drive
                // posters/ folder gets imported on the next sync instead of being overwritten.
                (snapshot.updatedAt - 1L).coerceAtLeast(0L)
            } else {
                // Store the remote file's own timestamp (not local wall clock) so clock skew
                // on this device never causes it to reject or overwrite newer updates from another.
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
            return writeLocked(
                treeUri = treeUri,
                items = localItems,
                localApps = localApps,
                localInstalledAppIds = localInstalledAppIds,
                minVersion = snapshot.updatedAt,
                existingSnapshot = snapshot
            )
        }

        // Even when metadata matches, ensure any missing posters in the folder are exported
        exportPosters(localItems + localApps, treeUri)

        prefs.edit()
            .putLong(KEY_UPDATED_AT, maxOf(localVersion, snapshot.updatedAt))
            .putString(KEY_SIGNATURE, localSignature)
            .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
            .apply()
        return LibrarySyncOutcome.UpToDate
    }

    private suspend fun writeLocked(
        treeUri: Uri,
        items: List<GameItem>,
        localApps: List<GameItem>,
        localInstalledAppIds: Set<String>,
        minVersion: Long = 0L,
        existingSnapshot: Snapshot? = null
    ): LibrarySyncOutcome {
        val docUri = try {
            writableDocumentUri(treeUri)
        } catch (error: SecurityException) {
            throw fileAccessError(error)
        } ?: throw IllegalStateException("Could not open $FILE_NAME in sync folder")

        // Preserve remote appCustomizations for packages not installed on this device so we
        // never erase another device's installed-game posters when writing local edits.
        val baseSnapshot = existingSnapshot ?: runCatching { readDocument(treeUri) }.getOrNull()
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

        try {
            resolver.openOutputStream(docUri, "wt")?.use { stream ->
                stream.write(document.toByteArray(Charsets.UTF_8))
            } ?: throw IllegalStateException("Could not open $FILE_NAME for writing")
        } catch (error: SecurityException) {
            throw fileAccessError(error)
        } catch (error: FileNotFoundException) {
            throw fileAccessError(error)
        }

        // Export all assigned game posters to posters/ directory
        exportPosters(items + localApps, treeUri)

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

    /**
     * Exports all assigned game posters (default and optional NSFW) into a `posters/` folder
     * inside the sync folder, and removes exported poster files when artwork is cleared or deleted.
     */
    private fun exportPosters(items: List<GameItem>, treeUri: Uri) {
        runCatching {
            val postersFolderUri = findOrCreateChildDirectory(treeUri, POSTERS_DIR_NAME)
                ?: return
            val postersFolderDocId = DocumentsContract.getDocumentId(postersFolderUri)
            val existingFiles = listChildDocuments(treeUri, postersFolderDocId)

            items.forEach { item ->
                exportSinglePosterSlot(
                    postersFolderUri = postersFolderUri,
                    existingFiles = existingFiles,
                    fileName = posterFileNameFor(item.id),
                    rawUrl = if (item.isDeleted) null else item.posterUrl
                )
                exportSinglePosterSlot(
                    postersFolderUri = postersFolderUri,
                    existingFiles = existingFiles,
                    fileName = nsfwPosterFileNameFor(item.id),
                    rawUrl = if (item.isDeleted) null else item.nsfwPosterUrl
                )
            }
        }
    }

    private fun exportSinglePosterSlot(
        postersFolderUri: Uri,
        existingFiles: MutableMap<String, Uri>,
        fileName: String,
        rawUrl: String?
    ) {
        val existingDocUri = existingFiles[fileName]
        if (rawUrl.isNullOrBlank()) {
            if (existingDocUri != null) {
                runCatching { DocumentsContract.deleteDocument(resolver, existingDocUri) }
                existingFiles.remove(fileName)
            }
            return
        }

        val isLocal = rawUrl.startsWith("file:") || rawUrl.startsWith("content:")
        if (!isLocal && existingDocUri != null && documentSize(existingDocUri) > 0L) {
            return
        }

        val bytes = fetchPosterBytes(rawUrl) ?: return
        if (existingDocUri != null && documentMatchesBytes(existingDocUri, bytes)) {
            return
        }

        val docUri = existingDocUri ?: DocumentsContract.createDocument(
            resolver,
            postersFolderUri,
            "image/png",
            fileName
        ) ?: return

        val wrote = writeBytesToDocument(docUri, bytes)
        if (!wrote && existingDocUri != null) {
            runCatching { DocumentsContract.deleteDocument(resolver, existingDocUri) }
            existingFiles.remove(fileName)
            val recreated = DocumentsContract.createDocument(
                resolver,
                postersFolderUri,
                "image/png",
                fileName
            ) ?: return
            if (writeBytesToDocument(recreated, bytes)) {
                existingFiles[fileName] = recreated
            }
            return
        }
        existingFiles[fileName] = docUri
    }

    private fun documentMatchesBytes(docUri: Uri, bytes: ByteArray): Boolean {
        val remoteSize = documentSize(docUri)
        if (remoteSize > 0L && remoteSize != bytes.size.toLong()) return false
        val remoteBytes = runCatching {
            resolver.openInputStream(docUri)?.use { it.readBytes() }
        }.getOrNull() ?: return false
        return remoteBytes.size == bytes.size &&
            LibrarySyncCodec.sha256Hex(remoteBytes) == LibrarySyncCodec.sha256Hex(bytes)
    }

    private fun writeBytesToDocument(docUri: Uri, bytes: ByteArray): Boolean =
        runCatching {
            val stream = runCatching { resolver.openOutputStream(docUri, "wt") }.getOrNull()
                ?: resolver.openOutputStream(docUri, "w")
                ?: return false
            stream.use { out -> out.write(bytes) }
            true
        }.getOrDefault(false)

    private data class ImportResult(
        val items: List<GameItem>,
        val missingExpectedPoster: Boolean
    )

    /**
     * Restores local poster images from the sync folder's `posters/` directory when the JSON
     * metadata omits a shareable URL (for example `file:` artwork picked on another device).
     */
    private fun importPosters(
        items: List<GameItem>,
        posterHashes: Map<String, LibrarySyncCodec.PosterHashes>,
        treeUri: Uri
    ): ImportResult {
        return runCatching {
            val postersFolderUri = findChildDirectory(treeUri, POSTERS_DIR_NAME)
            val existingFiles = if (postersFolderUri != null) {
                val postersFolderDocId = DocumentsContract.getDocumentId(postersFolderUri)
                listChildDocuments(treeUri, postersFolderDocId)
            } else {
                emptyMap()
            }

            val localDir = File(context.filesDir, "custom_art").apply { mkdirs() }
            var missingExpectedPoster = false

            val updatedItems = items.map { item ->
                val safeId = item.id.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val expectedHashes = posterHashes[item.id]
                var updated = item

                if (item.posterUrl.isNullOrBlank()) {
                    val importedPoster = copyPosterFromSyncFolder(
                        posterDocUri = existingFiles[posterFileNameFor(item.id)],
                        localDir = localDir,
                        filePrefix = "$safeId-synced",
                        expectedHash = expectedHashes?.posterHash
                    )
                    if (importedPoster != null) {
                        updated = updated.copy(
                            posterUrl = importedPoster,
                            customArtworkSource = updated.customArtworkSource ?: "SYNCED"
                        )
                    } else if (expectedHashes?.posterHash != null) {
                        missingExpectedPoster = true
                    }
                }

                if (item.nsfwPosterUrl.isNullOrBlank()) {
                    val importedNsfw = copyPosterFromSyncFolder(
                        posterDocUri = existingFiles[nsfwPosterFileNameFor(item.id)],
                        localDir = localDir,
                        filePrefix = "$safeId-nsfw-synced",
                        expectedHash = expectedHashes?.nsfwPosterHash
                    )
                    if (importedNsfw != null) {
                        updated = updated.copy(nsfwPosterUrl = importedNsfw)
                    } else if (expectedHashes?.nsfwPosterHash != null) {
                        missingExpectedPoster = true
                    }
                }

                updated
            }
            ImportResult(updatedItems, missingExpectedPoster)
        }.getOrElse {
            val expectsAnyPoster = posterHashes.values.any {
                it.posterHash != null || it.nsfwPosterHash != null
            }
            ImportResult(items, missingExpectedPoster = expectsAnyPoster)
        }
    }

    private fun copyPosterFromSyncFolder(
        posterDocUri: Uri?,
        localDir: File,
        filePrefix: String,
        expectedHash: String?
    ): String? {
        if (posterDocUri == null) return null

        // Fast path: if the expected hash already matches an imported local file, reuse it.
        if (!expectedHash.isNullOrBlank()) {
            val expectedTarget = File(localDir, "${filePrefix}-${expectedHash.take(12)}.png")
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

        val remoteBytes = runCatching {
            resolver.openInputStream(posterDocUri)?.use { it.readBytes() }
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null

        val actualHash = LibrarySyncCodec.sha256Hex(remoteBytes)
        if (!expectedHash.isNullOrBlank() && actualHash != expectedHash) {
            return null
        }
        val targetFile = File(localDir, "${filePrefix}-${actualHash.take(12)}.png")
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

    private fun documentSize(uri: Uri): Long = runCatching {
        resolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        } ?: 0L
    }.getOrDefault(0L)

    // ---- File / Directory Access --------------------------------------------------

    private data class Snapshot(
        val updatedAt: Long,
        val items: List<GameItem>,
        val signature: String,
        val authoritative: Boolean,
        val tracksLocalPosters: Boolean,
        val posterHashes: Map<String, LibrarySyncCodec.PosterHashes>,
        val appCustomizations: List<GameItem>,
        val appPosterHashes: Map<String, LibrarySyncCodec.PosterHashes>
    )

    private fun resolveTreeUri(): Uri? =
        prefs.getString(KEY_TREE_URI, null)?.let(Uri::parse)

    private fun fileAccessError(cause: Exception): IllegalStateException =
        IllegalStateException(
            "Nexus cannot access the sync folder. Please re-select it in Settings.",
            cause
        )

    private fun readDocument(treeUri: Uri): Snapshot? {
        val docUri = try {
            findChildDocument(treeUri)
        } catch (error: SecurityException) {
            throw fileAccessError(error)
        } ?: return null

        val text = try {
            resolver.openInputStream(docUri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: throw IllegalStateException("Could not read $FILE_NAME in sync folder")
        } catch (error: FileNotFoundException) {
            throw fileAccessError(error)
        } catch (error: SecurityException) {
            throw fileAccessError(error)
        }
        if (text.isBlank()) return null
        return parseDocument(text)
    }

    private fun writableDocumentUri(treeUri: Uri): Uri? =
        findChildDocument(treeUri) ?: createChildDocument(treeUri)

    private fun findChildDocument(treeUri: Uri): Uri? {
        val parentId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        return resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            ),
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == FILE_NAME) {
                    return@use DocumentsContract.buildDocumentUriUsingTree(
                        treeUri,
                        cursor.getString(0)
                    )
                }
            }
            null
        }
    }

    private fun createChildDocument(treeUri: Uri): Uri? = runCatching {
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        DocumentsContract.createDocument(resolver, parentUri, MIME_TYPE, FILE_NAME)
    }.getOrNull()

    private fun findChildDirectory(treeUri: Uri, dirName: String): Uri? {
        val parentId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        return resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1)
                val mime = cursor.getString(2)
                if (name == dirName && mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    return@use DocumentsContract.buildDocumentUriUsingTree(
                        treeUri,
                        cursor.getString(0)
                    )
                }
            }
            null
        }
    }

    private fun findOrCreateChildDirectory(treeUri: Uri, dirName: String): Uri? {
        findChildDirectory(treeUri, dirName)?.let { return it }
        return runCatching {
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri)
            )
            DocumentsContract.createDocument(
                resolver,
                parentUri,
                DocumentsContract.Document.MIME_TYPE_DIR,
                dirName
            )
        }.getOrNull()
    }

    private fun listChildDocuments(treeUri: Uri, parentDocId: String): MutableMap<String, Uri> {
        val result = mutableMapOf<String, Uri>()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            ),
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val docId = cursor.getString(0)
                val name = cursor.getString(1)
                if (!name.isNullOrBlank()) {
                    result[name] = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                }
            }
        }
        return result
    }

    private fun persistPermission(uri: Uri) {
        runCatching {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    private fun treeDisplayName(treeUri: Uri): String? = runCatching {
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        resolver.query(
            documentUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
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

    private fun parseDocument(text: String): Snapshot =
        LibrarySyncCodec.parse(text).let {
            Snapshot(
                updatedAt = it.updatedAt,
                items = it.items,
                signature = it.signature,
                authoritative = it.authoritative,
                tracksLocalPosters = it.tracksLocalPosters,
                posterHashes = it.posterHashes,
                appCustomizations = it.appCustomizations,
                appPosterHashes = it.appPosterHashes
            )
        }

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

        private const val MIME_TYPE = "application/json"
        private const val WRITE_DEBOUNCE_MS = 750L

        private const val KEY_TREE_URI = "library_sync_tree_uri"
        private const val KEY_TARGET_LABEL = "library_sync_target_label"
        private const val KEY_UPDATED_AT = "library_sync_updated_at"
        private const val KEY_SIGNATURE = "library_sync_signature"
        private const val KEY_LAST_SYNC = "library_sync_last_sync"
    }
}
