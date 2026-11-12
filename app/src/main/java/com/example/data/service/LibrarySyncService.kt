package com.example.data.service

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import com.example.data.model.GameItem
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
                .map { rows -> syncable(rows) }
                .map { items -> items to signature(items) }
                .distinctUntilChangedBy { it.second }
                .onEach { (_, sig) ->
                    if (!isConfigured || sig == prefs.getString(KEY_SIGNATURE, "")) {
                        return@onEach
                    }
                    prefs.edit().putLong(KEY_UPDATED_AT, nextVersion()).apply()
                }
                .debounce(WRITE_DEBOUNCE_MS)
                .collect { (items, sig) ->
                    if (!isConfigured || sig == prefs.getString(KEY_SIGNATURE, "")) {
                        return@collect
                    }
                    withContext(Dispatchers.IO) {
                        mutex.withLock {
                            val treeUri = resolveTreeUri() ?: return@withLock
                            runCatching { writeLocked(treeUri, items) }
                                .onFailure { onError(it.message ?: "Library folder sync failed") }
                        }
                    }
                }
        }
    }

    // ---- Core sync ----------------------------------------------------------------

    private suspend fun syncLocked(treeUri: Uri, adoptExisting: Boolean): LibrarySyncOutcome {
        val snapshot = readDocument(treeUri)
        val localItems = repository.getSyncableItemsSnapshot()

        if (snapshot == null || snapshot.items.isEmpty()) {
            // Seed folder from this device (writes json and exports all assigned posters).
            return writeLocked(treeUri, localItems, snapshot?.updatedAt ?: 0L)
        }

        if (!snapshot.authoritative) {
            val itemsToMerge = importPosters(snapshot.items, treeUri)
            repository.mergeSyncedLibrary(itemsToMerge)
            return writeLocked(
                treeUri,
                repository.getSyncableItemsSnapshot(),
                snapshot.updatedAt
            )
        }

        val localVersion = prefs.getLong(KEY_UPDATED_AT, 0L)
        val localSignature = signature(localItems)
        val differs = snapshot.signature != localSignature
        val fileIsNewer = adoptExisting || snapshot.updatedAt > localVersion

        if (differs && fileIsNewer) {
            val itemsToApply = importPosters(snapshot.items, treeUri)
            repository.replaceSyncedLibrary(itemsToApply)
            val applied = repository.getSyncableItemsSnapshot()
            prefs.edit()
                .putLong(KEY_UPDATED_AT, maxOf(snapshot.updatedAt, nextVersion()))
                .putString(KEY_SIGNATURE, signature(applied))
                .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                .apply()
            return LibrarySyncOutcome.Applied(snapshot.items.size)
        }

        if (differs) {
            return writeLocked(treeUri, localItems, snapshot.updatedAt)
        }

        // Even when metadata matches, ensure any missing posters in the folder are exported
        exportPosters(localItems, treeUri)

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
        minVersion: Long = 0L
    ): LibrarySyncOutcome {
        val docUri = try {
            writableDocumentUri(treeUri)
        } catch (error: SecurityException) {
            throw fileAccessError(error)
        } ?: throw IllegalStateException("Could not open $FILE_NAME in sync folder")

        val version = maxOf(nextVersion(), minVersion + 1L)
        val document = buildDocument(items, version).toString()

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
        exportPosters(items, treeUri)

        prefs.edit()
            .putLong(KEY_UPDATED_AT, version)
            .putString(KEY_SIGNATURE, signature(items))
            .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
            .apply()
        return LibrarySyncOutcome.WroteLocal(items.size)
    }

    private fun nextVersion(): Long =
        maxOf(System.currentTimeMillis(), prefs.getLong(KEY_UPDATED_AT, 0L) + 1L)

    // ---- Poster Export & Import ---------------------------------------------------

    /**
     * Exports all assigned game posters into a `posters/` folder inside the sync folder.
     */
    private fun exportPosters(items: List<GameItem>, treeUri: Uri) {
        runCatching {
            val postersFolderUri = findOrCreateChildDirectory(treeUri, POSTERS_DIR_NAME)
                ?: return
            val postersFolderDocId = DocumentsContract.getDocumentId(postersFolderUri)
            val existingFiles = listChildDocuments(treeUri, postersFolderDocId)

            items.forEach { item ->
                if (item.isDeleted || item.posterUrl.isNullOrBlank()) return@forEach
                val rawUrl = item.posterUrl
                val fileName = posterFileNameFor(item.id)
                val existingDocUri = existingFiles[fileName]

                if (existingDocUri != null && documentSize(existingDocUri) > 0L) {
                    return@forEach
                }

                val bytes = fetchPosterBytes(rawUrl) ?: return@forEach
                val docUri = existingDocUri ?: DocumentsContract.createDocument(
                    resolver,
                    postersFolderUri,
                    "image/png",
                    fileName
                ) ?: return@forEach

                resolver.openOutputStream(docUri, "wt")?.use { out ->
                    out.write(bytes)
                }
                existingFiles[fileName] = docUri
            }
        }
    }

    /**
     * Restores poster images from the sync folder's `posters/` directory into local storage
     * so that artwork displays reliably offline and across devices.
     */
    private fun importPosters(items: List<GameItem>, treeUri: Uri): List<GameItem> {
        return runCatching {
            val postersFolderUri = findChildDirectory(treeUri, POSTERS_DIR_NAME)
                ?: return items
            val postersFolderDocId = DocumentsContract.getDocumentId(postersFolderUri)
            val existingFiles = listChildDocuments(treeUri, postersFolderDocId)
            if (existingFiles.isEmpty()) return items

            val localDir = File(context.filesDir, "custom_art").apply { mkdirs() }

            items.map { item ->
                val fileName = posterFileNameFor(item.id)
                val posterDocUri = existingFiles[fileName]
                if (posterDocUri != null) {
                    val safeId = item.id.replace(Regex("[^A-Za-z0-9._-]"), "_")
                    val localFile = File(localDir, "$safeId-synced.png")
                    val copied = runCatching {
                        if (!localFile.exists() || localFile.length() == 0L) {
                            resolver.openInputStream(posterDocUri)?.use { input ->
                                localFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                        localFile.exists() && localFile.length() > 0L
                    }.getOrDefault(false)

                    if (copied) {
                        item.copy(
                            posterUrl = Uri.fromFile(localFile).toString(),
                            customArtworkSource = item.customArtworkSource ?: "SYNCED"
                        )
                    } else {
                        item
                    }
                } else {
                    item
                }
            }
        }.getOrDefault(items)
    }

    private fun posterFileNameFor(itemId: String): String {
        val safeId = itemId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return "poster_$safeId.png"
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
        val authoritative: Boolean
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

    private fun buildDocument(items: List<GameItem>, updatedAt: Long): JSONObject =
        LibrarySyncCodec.buildDocument(items, updatedAt)

    private fun parseDocument(text: String): Snapshot =
        LibrarySyncCodec.parse(text).let {
            Snapshot(it.updatedAt, it.items, it.signature, it.authoritative)
        }

    private fun syncable(rows: List<GameItem>): List<GameItem> = LibrarySyncCodec.syncable(rows)

    private fun signature(items: List<GameItem>): String = LibrarySyncCodec.signature(items)

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
