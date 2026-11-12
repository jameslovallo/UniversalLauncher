package com.example.data.service

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import com.example.data.model.GameItem
import com.example.data.repository.GameRepository
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

/** Result of a library-file sync or write. */
sealed interface LibrarySyncOutcome {
    data class Applied(val itemCount: Int) : LibrarySyncOutcome
    data class WroteLocal(val itemCount: Int) : LibrarySyncOutcome
    data object UpToDate : LibrarySyncOutcome
    data object NotConfigured : LibrarySyncOutcome
}

/**
 * Keeps the shareable game library synchronised with a file chosen through the Storage
 * Access Framework.
 *
 * The grant may be a single document or a folder (in which case `nexus-library.json` is
 * created inside it). Every document carries an `updatedAt` timestamp and the full set of
 * cloud/Moonlight/game-shortcut items, including tombstones. Local changes are written
 * back automatically; on launch, unlock or an explicit sync the file is read and wins when
 * it is newer than the last version seen on this device.
 */
class LibrarySyncService(
    private val context: Context,
    private val repository: GameRepository,
    private val prefs: SharedPreferences
) {
    private val resolver = context.contentResolver
    private val mutex = Mutex()

    val isConfigured: Boolean
        get() = prefs.getString(KEY_DOC_URI, null) != null ||
            prefs.getString(KEY_TREE_URI, null) != null

    fun targetLabel(): String? {
        if (!isConfigured) return null
        return prefs.getString(KEY_TARGET_LABEL, null)
            ?: (prefs.getString(KEY_DOC_URI, null) ?: prefs.getString(KEY_TREE_URI, null))
                ?.let(Uri::parse)
                ?.lastPathSegment
                ?.substringAfterLast('/')
    }

    fun lastSyncedAt(): Long? =
        prefs.getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0L }

    private data class ConfigSnapshot(
        val docUri: String?,
        val treeUri: String?,
        val label: String?,
        val updatedAt: Long,
        val signature: String?
    )

    private fun snapshotConfig(): ConfigSnapshot = ConfigSnapshot(
        docUri = prefs.getString(KEY_DOC_URI, null),
        treeUri = prefs.getString(KEY_TREE_URI, null),
        label = prefs.getString(KEY_TARGET_LABEL, null),
        updatedAt = prefs.getLong(KEY_UPDATED_AT, 0L),
        signature = prefs.getString(KEY_SIGNATURE, null)
    )

    private fun restoreConfig(snapshot: ConfigSnapshot) {
        prefs.edit()
            .putString(KEY_DOC_URI, snapshot.docUri)
            .putString(KEY_TREE_URI, snapshot.treeUri)
            .putString(KEY_TARGET_LABEL, snapshot.label)
            .putLong(KEY_UPDATED_AT, snapshot.updatedAt)
            .putString(KEY_SIGNATURE, snapshot.signature)
            .apply()
    }

    /** Uses an existing (or newly created) document as the sync target. */
    suspend fun configureFile(uri: Uri): LibrarySyncOutcome = withContext(Dispatchers.IO) {
        mutex.withLock {
            persistPermission(uri)
            val previous = snapshotConfig()
            prefs.edit()
                .putString(KEY_DOC_URI, uri.toString())
                .remove(KEY_TREE_URI)
                .putString(KEY_TARGET_LABEL, displayName(uri) ?: "Selected file")
                .putLong(KEY_UPDATED_AT, 0L)
                .putString(KEY_SIGNATURE, "")
                .apply()
            try {
                // Adopt whatever the chosen file already contains.
                val outcome = syncLocked(adoptExisting = true)
                releasePreviousPermission(previous, uri.toString())
                outcome
            } catch (error: Exception) {
                // A file that cannot be read or is not a Nexus library must not replace a
                // working sync target (or be written to).
                restoreConfig(previous)
                throw error
            }
        }
    }

    /** Uses a folder as the sync target, creating `nexus-library.json` inside it. */
    suspend fun configureFolder(treeUri: Uri): LibrarySyncOutcome = withContext(Dispatchers.IO) {
        mutex.withLock {
            persistPermission(treeUri)
            val previous = snapshotConfig()
            prefs.edit()
                .putString(KEY_TREE_URI, treeUri.toString())
                .remove(KEY_DOC_URI)
                .putString(KEY_TARGET_LABEL, treeDisplayName(treeUri) ?: "Selected folder")
                .putLong(KEY_UPDATED_AT, 0L)
                .putString(KEY_SIGNATURE, "")
                .apply()
            try {
                val target = resolveTarget()
                    ?: return@withLock LibrarySyncOutcome.NotConfigured
                // Make sure the file exists before the first read.
                writableDocumentUri(target)
                    ?: throw IllegalStateException("Could not create $FILE_NAME in that folder")
                val outcome = syncLocked(adoptExisting = true)
                releasePreviousPermission(previous, treeUri.toString())
                outcome
            } catch (error: Exception) {
                restoreConfig(previous)
                throw error
            }
        }
    }

    /** Reads the file and restores it when newer, otherwise writes local changes. */
    suspend fun syncNow(): LibrarySyncOutcome = withContext(Dispatchers.IO) {
        mutex.withLock { syncLocked(adoptExisting = false) }
    }

    fun disconnect() {
        val docs = listOfNotNull(
            prefs.getString(KEY_DOC_URI, null),
            prefs.getString(KEY_TREE_URI, null)
        )
        docs.forEach { stored -> releasePermission(Uri.parse(stored)) }
        prefs.edit()
            .remove(KEY_DOC_URI)
            .remove(KEY_TREE_URI)
            .remove(KEY_TARGET_LABEL)
            .remove(KEY_UPDATED_AT)
            .remove(KEY_SIGNATURE)
            .remove(KEY_LAST_SYNC)
            .apply()
    }

    /** Drops the grant for the target that was replaced, once the new one works. */
    private fun releasePreviousPermission(previous: ConfigSnapshot, current: String) {
        val old = previous.docUri ?: previous.treeUri ?: return
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
     * Watches the full library (including tombstones) and writes to the file whenever the
     * shareable content changes. The stored signature is updated after each write/apply,
     * so applying a remote file cannot trigger an endless write loop.
     *
     * @param onError called when a background write fails (for example because the user
     *   revoked access to the sync file). The library stays intact; the next write retries.
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
                    // A local edit happened. Move the local version forward right away so
                    // an unlock read of an older file cannot discard this pending change.
                    prefs.edit().putLong(KEY_UPDATED_AT, nextVersion()).apply()
                }
                .debounce(WRITE_DEBOUNCE_MS)
                .collect { (items, sig) ->
                    if (!isConfigured || sig == prefs.getString(KEY_SIGNATURE, "")) {
                        return@collect
                    }
                    withContext(Dispatchers.IO) {
                        mutex.withLock {
                            runCatching { writeLocked(items) }
                                .onFailure { onError(it.message ?: "Library sync failed") }
                        }
                    }
                }
        }
    }

    // ---- Core sync ----------------------------------------------------------------

    private suspend fun syncLocked(adoptExisting: Boolean): LibrarySyncOutcome {
        val target = resolveTarget() ?: return LibrarySyncOutcome.NotConfigured
        val snapshot = readDocument(target)
        val localItems = repository.getSyncableItemsSnapshot()

        if (snapshot == null || snapshot.items.isEmpty()) {
            // No usable backup yet: seed it from this device.
            return writeLocked(localItems, target, snapshot?.updatedAt ?: 0L)
        }

        if (!snapshot.authoritative) {
            // An export from an older build lists live games only, without tombstones.
            // Merge it in and upgrade the file instead of deleting local games that the
            // old format could not mention.
            repository.mergeSyncedLibrary(snapshot.items)
            return writeLocked(
                repository.getSyncableItemsSnapshot(),
                target,
                snapshot.updatedAt
            )
        }

        val localVersion = prefs.getLong(KEY_UPDATED_AT, 0L)
        val localSignature = signature(localItems)
        val differs = snapshot.signature != localSignature
        val fileIsNewer = adoptExisting || snapshot.updatedAt > localVersion

        if (differs && fileIsNewer) {
            repository.replaceSyncedLibrary(snapshot.items)
            val applied = repository.getSyncableItemsSnapshot()
            prefs.edit()
                .putLong(KEY_UPDATED_AT, maxOf(snapshot.updatedAt, nextVersion()))
                .putString(KEY_SIGNATURE, signature(applied))
                .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                .apply()
            return LibrarySyncOutcome.Applied(snapshot.items.size)
        }

        if (differs) {
            // This device has edits the file has not seen yet. Keep them, but never write
            // a version older than the file's so the next read here still sees them as new.
            return writeLocked(localItems, target, snapshot.updatedAt)
        }

        // Same content: adopt the file's version too, so a later local edit outranks it
        // even when the two devices' clocks disagree.
        prefs.edit()
            .putLong(KEY_UPDATED_AT, maxOf(localVersion, snapshot.updatedAt))
            .putString(KEY_SIGNATURE, localSignature)
            .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
            .apply()
        return LibrarySyncOutcome.UpToDate
    }

    private suspend fun writeLocked(
        items: List<GameItem>,
        resolvedTarget: Target? = null,
        minVersion: Long = 0L
    ): LibrarySyncOutcome {
        val target = resolvedTarget ?: resolveTarget()
            ?: return LibrarySyncOutcome.NotConfigured
        val uri = try {
            writableDocumentUri(target)
        } catch (error: SecurityException) {
            throw fileAccessError(error)
        }
        if (uri == null) {
            throw IllegalStateException("Could not open the sync file for writing")
        }
        val version = maxOf(nextVersion(), minVersion + 1L)
        val document = buildDocument(items, version).toString()
        try {
            resolver.openOutputStream(uri, "wt")?.use { stream ->
                stream.write(document.toByteArray(Charsets.UTF_8))
            } ?: throw IllegalStateException("Could not open the sync file for writing")
        } catch (error: SecurityException) {
            throw fileAccessError(error)
        } catch (error: FileNotFoundException) {
            throw fileAccessError(error)
        }

        prefs.edit()
            .putLong(KEY_UPDATED_AT, version)
            .putString(KEY_SIGNATURE, signature(items))
            .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
            .apply()
        return LibrarySyncOutcome.WroteLocal(items.size)
    }

    private fun nextVersion(): Long =
        maxOf(System.currentTimeMillis(), prefs.getLong(KEY_UPDATED_AT, 0L) + 1L)

    // ---- File access --------------------------------------------------------------

    private data class Target(val documentUri: Uri?, val treeUri: Uri?)

    private data class Snapshot(
        val updatedAt: Long,
        val items: List<GameItem>,
        val signature: String,
        val authoritative: Boolean
    )

    private fun resolveTarget(): Target? {
        prefs.getString(KEY_DOC_URI, null)?.let { return Target(Uri.parse(it), null) }
        prefs.getString(KEY_TREE_URI, null)?.let { return Target(null, Uri.parse(it)) }
        return null
    }

    private fun fileAccessError(cause: Exception): IllegalStateException =
        IllegalStateException(
            "Nexus cannot open the sync file. Choose it again in Settings.",
            cause
        )

    private fun readDocument(target: Target): Snapshot? {
        // A missing child of a folder grant is the normal "first run" case. Read errors on
        // a document that should exist are not: they must abort the sync rather than let
        // the seed write overwrite the user's backup.
        val uri = try {
            readableDocumentUri(target)
        } catch (error: SecurityException) {
            throw fileAccessError(error)
        }
        if (uri == null) return null
        val text = try {
            resolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: throw IllegalStateException("Could not read the sync file")
        } catch (error: FileNotFoundException) {
            throw fileAccessError(error)
        } catch (error: SecurityException) {
            throw fileAccessError(error)
        }
        if (text.isBlank()) return null
        return parseDocument(text)
    }

    private fun readableDocumentUri(target: Target): Uri? =
        target.documentUri ?: target.treeUri?.let(::findChildDocument)

    private fun writableDocumentUri(target: Target): Uri? =
        target.documentUri?.takeIf { documentExists(it) }
            ?: target.treeUri?.let { tree ->
                findChildDocument(tree) ?: createChildDocument(tree)
            }
            ?: target.documentUri

    private fun documentExists(uri: Uri): Boolean =
        runCatching {
            resolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        }.getOrDefault(false)

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

    private fun persistPermission(uri: Uri) {
        runCatching {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        resolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun treeDisplayName(treeUri: Uri): String? = runCatching {
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        displayName(documentUri)
    }.getOrNull()

    // ---- JSON ---------------------------------------------------------------------

    private fun buildDocument(items: List<GameItem>, updatedAt: Long): JSONObject =
        LibrarySyncCodec.buildDocument(items, updatedAt)

    private fun parseDocument(text: String): Snapshot =
        LibrarySyncCodec.parse(text).let {
            Snapshot(it.updatedAt, it.items, it.signature, it.authoritative)
        }

    // ---- Canonical content --------------------------------------------------------

    private fun syncable(rows: List<GameItem>): List<GameItem> = LibrarySyncCodec.syncable(rows)

    private fun signature(items: List<GameItem>): String = LibrarySyncCodec.signature(items)

    companion object {
        const val FILE_NAME = "nexus-library.json"

        private const val MIME_TYPE = "application/json"
        private const val WRITE_DEBOUNCE_MS = 750L

        private const val KEY_DOC_URI = "library_sync_doc_uri"
        private const val KEY_TREE_URI = "library_sync_tree_uri"
        private const val KEY_TARGET_LABEL = "library_sync_target_label"
        private const val KEY_UPDATED_AT = "library_sync_updated_at"
        private const val KEY_SIGNATURE = "library_sync_signature"
        private const val KEY_LAST_SYNC = "library_sync_last_sync"
    }
}
