package com.example.data.service

import android.content.ContentResolver
import android.content.Intent
import android.content.SharedPreferences
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import java.io.FileNotFoundException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

enum class LibrarySyncMode {
    NONE,
    GOOGLE_DRIVE,
    LOCAL_FOLDER;

    companion object {
        fun fromPrefs(prefs: SharedPreferences): LibrarySyncMode {
            val stored = prefs.getString(LibrarySyncService.KEY_SYNC_MODE, null)
            if (!stored.isNullOrBlank()) {
                return runCatching { valueOf(stored) }.getOrDefault(NONE)
            }
            // Migrate pre-existing folder sync configuration seamlessly.
            return if (prefs.getString(LibrarySyncService.KEY_TREE_URI, null) != null) {
                LOCAL_FOLDER
            } else {
                NONE
            }
        }
    }
}

internal data class RemotePosterEntry(
    val id: String,
    val name: String,
    val lastModified: Long,
    val size: Long,
    val sha256: String? = null
)

internal data class RemoteLibraryDoc(
    val handleId: String,
    val parsed: LibrarySyncCodec.Parsed?
)

internal interface LibrarySyncTransport {
    val mode: LibrarySyncMode

    suspend fun ensureReady()
    suspend fun readLibrary(forceRefresh: Boolean): RemoteLibraryDoc?
    suspend fun writeLibrary(existingHandleId: String?, jsonText: String): String
    suspend fun ensurePostersContainer(forceRefresh: Boolean = true): String?
    suspend fun listRemotePosters(forceRefresh: Boolean): MutableMap<String, RemotePosterEntry>
    suspend fun downloadPosterBytes(
        entry: RemotePosterEntry,
        expectedHash: String?,
        forceRefresh: Boolean
    ): ByteArray?

    suspend fun uploadPoster(
        postersContainerId: String,
        fileName: String,
        bytes: ByteArray,
        sha256Hex: String,
        existing: RemotePosterEntry?
    ): RemotePosterEntry?

    suspend fun deletePoster(entry: RemotePosterEntry)
}

/**
 * Storage Access Framework folder transport for local or third-party document provider folders.
 */
internal class SafFolderSyncTransport(
    private val resolver: ContentResolver,
    private val treeUri: Uri
) : LibrarySyncTransport {

    override val mode: LibrarySyncMode = LibrarySyncMode.LOCAL_FOLDER

    override suspend fun ensureReady() {
        resolveOrCreateLibraryDocument()
            ?: throw IllegalStateException(
                "Could not access or create ${LibrarySyncService.FILE_NAME} in that folder"
            )
    }

    override suspend fun readLibrary(forceRefresh: Boolean): RemoteLibraryDoc? =
        findAndDeduplicateLibraryDocument(forceRefresh)

    override suspend fun writeLibrary(existingHandleId: String?, jsonText: String): String {
        val docUri = existingHandleId?.let(Uri::parse)
            ?: resolveOrCreateLibraryDocument()?.handleId?.let(Uri::parse)
            ?: throw IllegalStateException(
                "Could not open ${LibrarySyncService.FILE_NAME} in sync folder"
            )
        try {
            resolver.openOutputStream(docUri, "wt")?.use { stream ->
                stream.write(jsonText.toByteArray(Charsets.UTF_8))
            } ?: throw IllegalStateException(
                "Could not open ${LibrarySyncService.FILE_NAME} for writing"
            )
        } catch (error: SecurityException) {
            throw fileAccessError(error)
        } catch (error: FileNotFoundException) {
            throw fileAccessError(error)
        }
        return docUri.toString()
    }

    override suspend fun ensurePostersContainer(forceRefresh: Boolean): String? =
        resolveCanonicalDirectory(
            dirName = LibrarySyncService.POSTERS_DIR_NAME,
            createIfMissing = true,
            forceRefresh = forceRefresh
        )?.toString()

    override suspend fun listRemotePosters(
        forceRefresh: Boolean
    ): MutableMap<String, RemotePosterEntry> {
        val postersFolderUri = resolveCanonicalDirectory(
            dirName = LibrarySyncService.POSTERS_DIR_NAME,
            createIfMissing = false,
            forceRefresh = forceRefresh
        ) ?: return mutableMapOf()
        val postersFolderDocId = DocumentsContract.getDocumentId(postersFolderUri)
        val deduped = deduplicateNamedFiles(
            listChildEntries(postersFolderDocId).filter { !it.isDirectory }
        )
        return deduped.mapValuesTo(mutableMapOf()) { (_, entry) -> entry.toRemotePosterEntry() }
    }

    override suspend fun downloadPosterBytes(
        entry: RemotePosterEntry,
        expectedHash: String?,
        forceRefresh: Boolean
    ): ByteArray? {
        val posterDocUri = Uri.parse(entry.id)
        if (forceRefresh) {
            refreshDocumentUri(posterDocUri)
        }
        var remoteBytes = runCatching {
            resolver.openInputStream(posterDocUri)?.use { it.readBytes() }
        }.getOrNull()?.takeIf { it.isNotEmpty() }

        var actualHash = remoteBytes?.let(LibrarySyncCodec::sha256Hex)
        val staleOrEmpty = remoteBytes == null ||
            (!expectedHash.isNullOrBlank() && actualHash != expectedHash)
        if (staleOrEmpty) {
            // Re-query the provider briefly in case cloud-backed SAF served a stale cache.
            refreshDocumentUri(posterDocUri)
            delay(PROVIDER_FILE_RETRY_DELAY_MS)
            remoteBytes = runCatching {
                resolver.openInputStream(posterDocUri)?.use { it.readBytes() }
            }.getOrNull()?.takeIf { it.isNotEmpty() }
        }
        return remoteBytes
    }

    override suspend fun uploadPoster(
        postersContainerId: String,
        fileName: String,
        bytes: ByteArray,
        sha256Hex: String,
        existing: RemotePosterEntry?
    ): RemotePosterEntry? {
        val existingDocUri = existing?.id?.let(Uri::parse)
        if (existingDocUri != null && documentMatchesBytes(existingDocUri, bytes, sha256Hex)) {
            return existing
        }

        val postersFolderUri = Uri.parse(postersContainerId)
        val docUri = existingDocUri ?: DocumentsContract.createDocument(
            resolver,
            postersFolderUri,
            "image/png",
            fileName
        ) ?: return null

        val wrote = writeBytesToDocument(docUri, bytes)
        if (!wrote && existingDocUri != null) {
            runCatching { DocumentsContract.deleteDocument(resolver, existingDocUri) }
            val recreated = DocumentsContract.createDocument(
                resolver,
                postersFolderUri,
                "image/png",
                fileName
            ) ?: return null
            return if (writeBytesToDocument(recreated, bytes)) {
                RemotePosterEntry(
                    id = recreated.toString(),
                    name = fileName,
                    lastModified = System.currentTimeMillis(),
                    size = bytes.size.toLong(),
                    sha256 = sha256Hex
                )
            } else {
                null
            }
        }
        return if (wrote) {
            RemotePosterEntry(
                id = docUri.toString(),
                name = fileName,
                lastModified = System.currentTimeMillis(),
                size = bytes.size.toLong(),
                sha256 = sha256Hex
            )
        } else {
            null
        }
    }

    override suspend fun deletePoster(entry: RemotePosterEntry) {
        runCatching { DocumentsContract.deleteDocument(resolver, Uri.parse(entry.id)) }
    }

    private fun documentMatchesBytes(
        docUri: Uri,
        bytes: ByteArray,
        expectedSha256: String
    ): Boolean {
        val remoteSize = documentSize(docUri)
        if (remoteSize > 0L && remoteSize != bytes.size.toLong()) return false
        val remoteBytes = runCatching {
            resolver.openInputStream(docUri)?.use { it.readBytes() }
        }.getOrNull() ?: return false
        return remoteBytes.size == bytes.size &&
            LibrarySyncCodec.sha256Hex(remoteBytes) == expectedSha256
    }

    private fun documentSize(uri: Uri): Long = runCatching {
        queryDocumentCursor(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_SIZE)
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        } ?: 0L
    }.getOrDefault(0L)

    private fun writeBytesToDocument(docUri: Uri, bytes: ByteArray): Boolean =
        runCatching {
            val stream = runCatching { resolver.openOutputStream(docUri, "wt") }.getOrNull()
                ?: resolver.openOutputStream(docUri, "w")
                ?: return false
            stream.use { out -> out.write(bytes) }
            true
        }.getOrDefault(false)

    private data class ChildEntry(
        val docId: String,
        val uri: Uri,
        val name: String,
        val mimeType: String,
        val lastModified: Long,
        val size: Long
    ) {
        val isDirectory: Boolean
            get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR

        fun toRemotePosterEntry() = RemotePosterEntry(
            id = uri.toString(),
            name = name,
            lastModified = lastModified,
            size = size
        )
    }

    private suspend fun resolveOrCreateLibraryDocument(): RemoteLibraryDoc? {
        val existing = findAndDeduplicateLibraryDocument(forceRefresh = false)
        if (existing != null) return existing
        val created = createLibraryDocument() ?: return null
        return RemoteLibraryDoc(handleId = created.toString(), parsed = null)
    }

    private suspend fun findAndDeduplicateLibraryDocument(forceRefresh: Boolean): RemoteLibraryDoc? {
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        if (forceRefresh) {
            forceRefreshFolder(rootDocId)
        }
        val candidates = try {
            listChildEntries(rootDocId)
                .filter { !it.isDirectory && it.name == LibrarySyncService.FILE_NAME }
        } catch (error: SecurityException) {
            throw fileAccessError(error)
        }
        if (candidates.isEmpty()) return null

        if (candidates.size == 1) {
            val only = candidates.single()
            if (forceRefresh) refreshDocumentUri(only.uri)
            return RemoteLibraryDoc(
                handleId = only.uri.toString(),
                parsed = readAndParseCandidate(only.uri)
            )
        }

        data class ScoredCandidate(
            val entry: ChildEntry,
            val parsed: LibrarySyncCodec.Parsed?,
            val parseError: Throwable?
        )

        val scored = candidates.map { entry ->
            var parsed: LibrarySyncCodec.Parsed? = null
            var error: Throwable? = null
            try {
                if (forceRefresh) refreshDocumentUri(entry.uri)
                parsed = readAndParseCandidate(entry.uri)
            } catch (t: Throwable) {
                error = t
            }
            ScoredCandidate(entry, parsed, error)
        }

        val winner = scored.maxWith(
            compareBy<ScoredCandidate>(
                { it.parsed != null },
                { it.parsed?.updatedAt ?: -1L },
                { it.entry.lastModified },
                { it.entry.size }
            )
        )

        scored.forEach { candidate ->
            if (candidate.entry.uri != winner.entry.uri) {
                runCatching { DocumentsContract.deleteDocument(resolver, candidate.entry.uri) }
            }
        }

        if (winner.parsed == null && winner.parseError != null) {
            throw winner.parseError
        }
        return RemoteLibraryDoc(handleId = winner.entry.uri.toString(), parsed = winner.parsed)
    }

    private fun readAndParseCandidate(docUri: Uri): LibrarySyncCodec.Parsed? {
        val text = try {
            resolver.openInputStream(docUri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: throw IllegalStateException(
                "Could not read ${LibrarySyncService.FILE_NAME} in sync folder"
            )
        } catch (error: FileNotFoundException) {
            throw fileAccessError(error)
        } catch (error: SecurityException) {
            throw fileAccessError(error)
        }
        if (text.isBlank()) return null
        return LibrarySyncCodec.parse(text)
    }

    private fun createLibraryDocument(): Uri? = runCatching {
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        DocumentsContract.createDocument(
            resolver,
            parentUri,
            "application/json",
            LibrarySyncService.FILE_NAME
        )
    }.getOrNull()

    private suspend fun resolveCanonicalDirectory(
        dirName: String,
        createIfMissing: Boolean,
        forceRefresh: Boolean
    ): Uri? {
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        if (forceRefresh) {
            forceRefreshFolder(rootDocId)
        }
        val dirs = runCatching {
            listChildEntries(rootDocId).filter { it.isDirectory && it.name == dirName }
        }.getOrDefault(emptyList())

        if (dirs.isEmpty()) {
            if (!createIfMissing) return null
            return runCatching {
                val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)
                DocumentsContract.createDocument(
                    resolver,
                    parentUri,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    dirName
                )
            }.getOrNull()
        }

        if (dirs.size == 1) {
            val onlyDir = dirs.single()
            if (forceRefresh) {
                forceRefreshFolder(onlyDir.docId)
            }
            return onlyDir.uri
        }

        data class DirWithChildren(
            val dir: ChildEntry,
            val children: Map<String, ChildEntry>,
            val effectiveLastModified: Long
        )

        val inspected = dirs.map { dir ->
            if (forceRefresh) {
                forceRefreshFolder(dir.docId)
            }
            val rawChildren = runCatching {
                listChildEntries(dir.docId).filter { !it.isDirectory }
            }.getOrDefault(emptyList())
            val deduped = deduplicateNamedFiles(rawChildren)
            val newestChildTs = deduped.values.maxOfOrNull { it.lastModified } ?: 0L
            DirWithChildren(
                dir = dir,
                children = deduped,
                effectiveLastModified = maxOf(dir.lastModified, newestChildTs)
            )
        }

        val winner = inspected.maxWith(
            compareBy<DirWithChildren>(
                { it.children.isNotEmpty() },
                { it.effectiveLastModified },
                { it.children.size },
                { it.dir.lastModified }
            )
        )
        val winnerFiles = winner.children.toMutableMap()

        inspected.forEach { candidate ->
            if (candidate.dir.uri == winner.dir.uri) return@forEach

            candidate.children.forEach { (name, dupFile) ->
                val existingInWinner = winnerFiles[name]
                val shouldCopyToWinner = existingInWinner == null ||
                    (existingInWinner.size <= 0L && dupFile.size > 0L) ||
                    (dupFile.size > 0L && dupFile.lastModified > existingInWinner.lastModified)
                if (shouldCopyToWinner) {
                    val copiedUri = copyFileEntryToDirectory(
                        source = dupFile,
                        destFolderUri = winner.dir.uri,
                        existingTargetUri = existingInWinner?.uri
                    )
                    if (copiedUri != null) {
                        winnerFiles[name] = dupFile.copy(uri = copiedUri)
                    }
                }
            }

            runCatching {
                listChildEntries(candidate.dir.docId).forEach { child ->
                    runCatching { DocumentsContract.deleteDocument(resolver, child.uri) }
                }
            }
            runCatching { DocumentsContract.deleteDocument(resolver, candidate.dir.uri) }
        }

        return winner.dir.uri
    }

    private fun copyFileEntryToDirectory(
        source: ChildEntry,
        destFolderUri: Uri,
        existingTargetUri: Uri?
    ): Uri? {
        val bytes = runCatching {
            resolver.openInputStream(source.uri)?.use { it.readBytes() }
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null

        if (existingTargetUri != null && writeBytesToDocument(existingTargetUri, bytes)) {
            return existingTargetUri
        }
        if (existingTargetUri != null) {
            runCatching { DocumentsContract.deleteDocument(resolver, existingTargetUri) }
        }
        val mime = source.mimeType
            .takeIf { it.isNotBlank() && it != DocumentsContract.Document.MIME_TYPE_DIR }
            ?: "image/png"
        val created = runCatching {
            DocumentsContract.createDocument(resolver, destFolderUri, mime, source.name)
        }.getOrNull() ?: return null
        return if (writeBytesToDocument(created, bytes)) created else null
    }

    private fun deduplicateNamedFiles(entries: List<ChildEntry>): Map<String, ChildEntry> {
        val byName = LinkedHashMap<String, MutableList<ChildEntry>>()
        entries.forEach { entry ->
            byName.getOrPut(entry.name) { mutableListOf() }.add(entry)
        }
        val result = LinkedHashMap<String, ChildEntry>(byName.size)
        byName.forEach { (name, group) ->
            if (group.size == 1) {
                result[name] = group.single()
            } else {
                val winner = group.maxWith(
                    compareBy<ChildEntry>(
                        { it.size > 0L },
                        { it.lastModified },
                        { it.size }
                    )
                )
                group.forEach { entry ->
                    if (entry.uri != winner.uri) {
                        runCatching { DocumentsContract.deleteDocument(resolver, entry.uri) }
                    }
                }
                result[name] = winner
            }
        }
        return result
    }

    private fun listChildEntries(parentDocId: String): List<ChildEntry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val result = mutableListOf<ChildEntry>()
        queryDocumentCursor(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_SIZE
            )
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            while (cursor.moveToNext()) {
                val docId = cursor.optString(idIdx) ?: continue
                val name = cursor.optString(nameIdx)?.takeIf { it.isNotBlank() } ?: continue
                val mime = cursor.optString(mimeIdx).orEmpty()
                val lastModified = cursor.optLong(modIdx, 0L)
                val size = cursor.optLong(sizeIdx, 0L)
                result.add(
                    ChildEntry(
                        docId = docId,
                        uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId),
                        name = name,
                        mimeType = mime,
                        lastModified = lastModified,
                        size = size
                    )
                )
            }
        }
        return result
    }

    private fun Cursor.optString(index: Int): String? =
        if (index >= 0 && !isNull(index)) getString(index) else null

    private fun Cursor.optLong(index: Int, default: Long): Long =
        if (index >= 0 && !isNull(index)) getLong(index) else default

    private suspend fun forceRefreshFolder(docId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        runCatching { resolver.refresh(docUri, Bundle.EMPTY, null) }
        runCatching { resolver.refresh(childrenUri, Bundle.EMPTY, null) }
        awaitProviderLoadingIfNeeded(childrenUri)
    }

    private fun refreshDocumentUri(docUri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { resolver.refresh(docUri, Bundle.EMPTY, null) }
        }
    }

    private suspend fun awaitProviderLoadingIfNeeded(childrenUri: Uri) {
        var isLoading = false
        var notifyUri: Uri? = null
        runCatching {
            queryDocumentCursor(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            )?.use { cursor ->
                val extras = cursor.extras
                isLoading = extras?.getBoolean(DocumentsContract.EXTRA_LOADING, false) == true
                if (isLoading) {
                    notifyUri = cursor.notificationUri ?: childrenUri
                }
            }
        }
        if (!isLoading) return

        val targetUri = notifyUri ?: childrenUri
        val changed = CompletableDeferred<Unit>()
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                changed.complete(Unit)
            }

            override fun onChange(selfChange: Boolean, uri: Uri?) {
                changed.complete(Unit)
            }
        }
        val registered = runCatching {
            resolver.registerContentObserver(targetUri, true, observer)
            true
        }.getOrDefault(false)
        try {
            if (registered) {
                withTimeoutOrNull(PROVIDER_LOADING_TIMEOUT_MS) {
                    changed.await()
                }
            } else {
                delay(PROVIDER_FILE_RETRY_DELAY_MS)
            }
        } finally {
            if (registered) {
                runCatching { resolver.unregisterContentObserver(observer) }
            }
        }
    }

    private fun queryDocumentCursor(uri: Uri, projection: Array<String>): Cursor? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            resolver.query(uri, projection, Bundle.EMPTY, null)
        } else {
            @Suppress("DEPRECATION")
            resolver.query(uri, projection, null, null, null)
        }

    private fun fileAccessError(cause: Exception): IllegalStateException =
        IllegalStateException(
            "Nexus cannot access the sync folder. Please re-select it in Settings.",
            cause
        )

    companion object {
        private const val PROVIDER_LOADING_TIMEOUT_MS = 3_000L
        private const val PROVIDER_FILE_RETRY_DELAY_MS = 250L

        fun persistPermission(resolver: ContentResolver, uri: Uri) {
            runCatching {
                resolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }

        fun releasePermission(resolver: ContentResolver, uri: Uri) {
            runCatching {
                resolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }

        fun treeDisplayName(resolver: ContentResolver, treeUri: Uri): String? = runCatching {
            val documentUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri)
            )
            val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                resolver.query(
                    documentUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                    Bundle.EMPTY,
                    null
                )
            } else {
                @Suppress("DEPRECATION")
                resolver.query(
                    documentUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                    null,
                    null,
                    null
                )
            }
            cursor?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull()
    }
}

/**
 * Direct Google Drive REST API v3 transport using only the app-scoped `drive.file` permission.
 * Stores `nexus-library.json` and `posters/` inside a dedicated `Nexus Launcher` folder.
 */
internal class GoogleDriveSyncTransport(
    private val api: GoogleDriveApiClient,
    private val preferredEmail: String?,
    private val initialFolderId: String?,
    private val onFolderResolved: (String) -> Unit
) : LibrarySyncTransport {

    override val mode: LibrarySyncMode = LibrarySyncMode.GOOGLE_DRIVE

    @Volatile
    private var resolvedRootFolderId: String? = initialFolderId

    override suspend fun ensureReady() {
        resolveOrCreateRootFolderId()
    }

    override suspend fun readLibrary(forceRefresh: Boolean): RemoteLibraryDoc? {
        val rootId = resolveOrCreateRootFolderId()
        val candidates = api.listChildren(preferredEmail, rootId)
            .filter { !it.isFolder && it.name == LibrarySyncService.FILE_NAME }
        if (candidates.isEmpty()) return null

        if (candidates.size == 1) {
            val only = candidates.single()
            val text = api.downloadBytes(preferredEmail, only.id).toString(Charsets.UTF_8)
            return RemoteLibraryDoc(
                handleId = only.id,
                parsed = text.takeIf { it.isNotBlank() }?.let(LibrarySyncCodec::parse)
            )
        }

        data class ScoredCandidate(
            val entry: GoogleDriveApiClient.DriveFileEntry,
            val parsed: LibrarySyncCodec.Parsed?,
            val error: Throwable?
        )

        val scored = candidates.map { entry ->
            var parsed: LibrarySyncCodec.Parsed? = null
            var error: Throwable? = null
            try {
                val text = api.downloadBytes(preferredEmail, entry.id).toString(Charsets.UTF_8)
                if (text.isNotBlank()) parsed = LibrarySyncCodec.parse(text)
            } catch (t: Throwable) {
                error = t
            }
            ScoredCandidate(entry, parsed, error)
        }

        val winner = scored.maxWith(
            compareBy<ScoredCandidate>(
                { it.parsed != null },
                { it.parsed?.updatedAt ?: -1L },
                { it.entry.modifiedTimeMs },
                { it.entry.size }
            )
        )

        scored.forEach { candidate ->
            if (candidate.entry.id != winner.entry.id) {
                runCatching { api.deleteFile(preferredEmail, candidate.entry.id) }
            }
        }

        if (winner.parsed == null && winner.error != null) {
            throw winner.error
        }
        return RemoteLibraryDoc(handleId = winner.entry.id, parsed = winner.parsed)
    }

    override suspend fun writeLibrary(existingHandleId: String?, jsonText: String): String {
        val rootId = resolveOrCreateRootFolderId()
        val bytes = jsonText.toByteArray(Charsets.UTF_8)
        val sha256 = LibrarySyncCodec.sha256Hex(bytes)
        if (!existingHandleId.isNullOrBlank()) {
            val updated = runCatching {
                api.updateFile(
                    preferredEmail = preferredEmail,
                    fileId = existingHandleId,
                    mimeType = "application/json",
                    bytes = bytes,
                    sha256Hex = sha256
                )
            }.getOrNull()
            if (updated != null) return updated.id
        }

        val existingDoc = readLibrary(forceRefresh = false)
        if (existingDoc != null) {
            return api.updateFile(
                preferredEmail = preferredEmail,
                fileId = existingDoc.handleId,
                mimeType = "application/json",
                bytes = bytes,
                sha256Hex = sha256
            ).id
        }

        return api.createFile(
            preferredEmail = preferredEmail,
            parentFolderId = rootId,
            name = LibrarySyncService.FILE_NAME,
            mimeType = "application/json",
            bytes = bytes,
            sha256Hex = sha256
        ).id
    }

    override suspend fun ensurePostersContainer(forceRefresh: Boolean): String? {
        val rootId = resolveOrCreateRootFolderId()
        return resolveCanonicalPostersFolderId(rootId, createIfMissing = true)
    }

    override suspend fun listRemotePosters(
        forceRefresh: Boolean
    ): MutableMap<String, RemotePosterEntry> {
        val rootId = resolveOrCreateRootFolderId()
        val postersFolderId = resolveCanonicalPostersFolderId(rootId, createIfMissing = false)
            ?: return mutableMapOf()
        val files = api.listChildren(preferredEmail, postersFolderId).filter { !it.isFolder }
        return deduplicatePosterFiles(files)
            .mapValuesTo(mutableMapOf()) { (_, entry) -> entry.toRemotePosterEntry() }
    }

    override suspend fun downloadPosterBytes(
        entry: RemotePosterEntry,
        expectedHash: String?,
        forceRefresh: Boolean
    ): ByteArray? {
        val bytes = runCatching {
            api.downloadBytes(preferredEmail, entry.id)
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        return bytes
    }

    override suspend fun uploadPoster(
        postersContainerId: String,
        fileName: String,
        bytes: ByteArray,
        sha256Hex: String,
        existing: RemotePosterEntry?
    ): RemotePosterEntry? {
        if (existing != null) {
            if (existing.sha256 == sha256Hex && existing.size == bytes.size.toLong()) {
                return existing
            }
            if (existing.sha256.isNullOrBlank() && existing.size == bytes.size.toLong()) {
                val remoteBytes = runCatching { api.downloadBytes(preferredEmail, existing.id) }.getOrNull()
                if (remoteBytes != null && LibrarySyncCodec.sha256Hex(remoteBytes) == sha256Hex) {
                    return existing.copy(sha256 = sha256Hex)
                }
            }
            return runCatching {
                api.updateFile(
                    preferredEmail = preferredEmail,
                    fileId = existing.id,
                    mimeType = "image/png",
                    bytes = bytes,
                    sha256Hex = sha256Hex
                ).toRemotePosterEntry()
            }.getOrNull()
        }

        return runCatching {
            api.createFile(
                preferredEmail = preferredEmail,
                parentFolderId = postersContainerId,
                name = fileName,
                mimeType = "image/png",
                bytes = bytes,
                sha256Hex = sha256Hex
            ).toRemotePosterEntry()
        }.getOrNull()
    }

    override suspend fun deletePoster(entry: RemotePosterEntry) {
        runCatching { api.deleteFile(preferredEmail, entry.id) }
    }

    private suspend fun resolveOrCreateRootFolderId(): String {
        resolvedRootFolderId?.let { cachedId ->
            val existing = runCatching { api.getFileMetadata(preferredEmail, cachedId) }.getOrNull()
            if (existing != null && existing.isFolder && !existing.trashed) {
                return existing.id
            }
            resolvedRootFolderId = null
        }

        val candidates = api.findRootFolders(preferredEmail)
        val chosen = when (candidates.size) {
            0 -> api.createFolder(preferredEmail, GoogleDriveApiClient.DEFAULT_ROOT_FOLDER_NAME)
            1 -> candidates.single()
            else -> {
                // Prefer a folder that already contains nexus-library.json, otherwise newest.
                candidates.firstOrNull { folder ->
                    api.listChildren(preferredEmail, folder.id)
                        .any { !it.isFolder && it.name == LibrarySyncService.FILE_NAME }
                } ?: candidates.maxBy { it.modifiedTimeMs }
            }
        }
        resolvedRootFolderId = chosen.id
        onFolderResolved(chosen.id)
        return chosen.id
    }

    private suspend fun resolveCanonicalPostersFolderId(
        rootId: String,
        createIfMissing: Boolean
    ): String? {
        val dirs = api.listChildren(preferredEmail, rootId)
            .filter { it.isFolder && it.name == LibrarySyncService.POSTERS_DIR_NAME }
        if (dirs.isEmpty()) {
            if (!createIfMissing) return null
            return api.createFolder(
                preferredEmail = preferredEmail,
                name = LibrarySyncService.POSTERS_DIR_NAME,
                parentFolderId = rootId
            ).id
        }
        if (dirs.size == 1) {
            return dirs.single().id
        }

        data class DirInfo(
            val folder: GoogleDriveApiClient.DriveFileEntry,
            val files: Map<String, GoogleDriveApiClient.DriveFileEntry>,
            val effectiveModifiedMs: Long
        )

        val inspected = dirs.map { folder ->
            val deduped = deduplicatePosterFiles(
                api.listChildren(preferredEmail, folder.id).filter { !it.isFolder }
            )
            val newestChild = deduped.values.maxOfOrNull { it.modifiedTimeMs } ?: 0L
            DirInfo(
                folder = folder,
                files = deduped,
                effectiveModifiedMs = maxOf(folder.modifiedTimeMs, newestChild)
            )
        }
        val winner = inspected.maxWith(
            compareBy<DirInfo>(
                { it.files.isNotEmpty() },
                { it.effectiveModifiedMs },
                { it.files.size },
                { it.folder.modifiedTimeMs }
            )
        )
        val winnerFiles = winner.files.toMutableMap()
        inspected.forEach { candidate ->
            if (candidate.folder.id == winner.folder.id) return@forEach
            candidate.files.forEach { (name, dupFile) ->
                val existingInWinner = winnerFiles[name]
                val shouldCopy = existingInWinner == null ||
                    (existingInWinner.size <= 0L && dupFile.size > 0L) ||
                    (dupFile.size > 0L && dupFile.modifiedTimeMs > existingInWinner.modifiedTimeMs)
                if (shouldCopy) {
                    val bytes = runCatching { api.downloadBytes(preferredEmail, dupFile.id) }
                        .getOrNull()?.takeIf { it.isNotEmpty() }
                    if (bytes != null) {
                        val sha = dupFile.sha256AppProperty ?: LibrarySyncCodec.sha256Hex(bytes)
                        val uploaded = if (existingInWinner != null) {
                            runCatching {
                                api.updateFile(
                                    preferredEmail = preferredEmail,
                                    fileId = existingInWinner.id,
                                    mimeType = "image/png",
                                    bytes = bytes,
                                    sha256Hex = sha
                                )
                            }.getOrNull()
                        } else {
                            runCatching {
                                api.createFile(
                                    preferredEmail = preferredEmail,
                                    parentFolderId = winner.folder.id,
                                    name = name,
                                    mimeType = "image/png",
                                    bytes = bytes,
                                    sha256Hex = sha
                                )
                            }.getOrNull()
                        }
                        if (uploaded != null) {
                            winnerFiles[name] = uploaded
                        }
                    }
                }
            }
            runCatching { api.deleteFile(preferredEmail, candidate.folder.id) }
        }
        return winner.folder.id
    }

    private suspend fun deduplicatePosterFiles(
        entries: List<GoogleDriveApiClient.DriveFileEntry>
    ): Map<String, GoogleDriveApiClient.DriveFileEntry> {
        val byName = LinkedHashMap<String, MutableList<GoogleDriveApiClient.DriveFileEntry>>()
        entries.forEach { entry ->
            byName.getOrPut(entry.name) { mutableListOf() }.add(entry)
        }
        val result = LinkedHashMap<String, GoogleDriveApiClient.DriveFileEntry>(byName.size)
        byName.forEach { (name, group) ->
            if (group.size == 1) {
                result[name] = group.single()
            } else {
                val winner = group.maxWith(
                    compareBy<GoogleDriveApiClient.DriveFileEntry>(
                        { it.size > 0L },
                        { it.modifiedTimeMs },
                        { it.size }
                    )
                )
                group.forEach { entry ->
                    if (entry.id != winner.id) {
                        runCatching { api.deleteFile(preferredEmail, entry.id) }
                    }
                }
                result[name] = winner
            }
        }
        return result
    }

    private fun GoogleDriveApiClient.DriveFileEntry.toRemotePosterEntry() = RemotePosterEntry(
        id = id,
        name = name,
        lastModified = modifiedTimeMs,
        size = size,
        sha256 = sha256AppProperty
    )
}
