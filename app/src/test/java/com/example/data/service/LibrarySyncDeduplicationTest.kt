package com.example.data.service

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.GameItem
import com.example.data.model.GameType
import com.example.data.repository.GameRepository
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibrarySyncDeduplicationTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository
    private lateinit var provider: FakeDriveDocumentsProvider

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.MANAGE_DOCUMENTS)
        context = app
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = GameRepository(db.gameDao(), InstalledAppsManager(context))

        val info = ProviderInfo().apply {
            authority = FakeDriveDocumentsProvider.AUTHORITY
            readPermission = Manifest.permission.MANAGE_DOCUMENTS
            writePermission = Manifest.permission.MANAGE_DOCUMENTS
            grantUriPermissions = true
            exported = true
        }
        provider = Robolectric.buildContentProvider(FakeDriveDocumentsProvider::class.java)
            .create(info)
            .get()
        provider.reset(File(context.cacheDir, "fake_drive_${System.nanoTime()}"))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `duplicate nexus library json and posters folders resolve to most recent and remove duplicates`() =
        runBlocking {
            val prefs = context.getSharedPreferences(
                "sync_dedup_test_${System.nanoTime()}",
                Context.MODE_PRIVATE
            )
            val syncService = LibrarySyncService(context, repository, prefs)

            val nsfwNewBytes = byteArrayOf(10, 20, 30, 40, 50)
            val nsfwOldBytes = byteArrayOf(1, 2, 3)
            val extraSafePosterBytes = byteArrayOf(7, 8, 9, 10)
            val nsfwHash = LibrarySyncCodec.sha256Hex(nsfwNewBytes)
            val safeHash = LibrarySyncCodec.sha256Hex(extraSafePosterBytes)

            val olderGame = GameItem(
                id = "gfn_1",
                title = "Old Title",
                type = GameType.GEFORCE_NOW,
                isGame = true
            )
            val newerGame = GameItem(
                id = "gfn_1",
                title = "Control Ultimate",
                type = GameType.GEFORCE_NOW,
                isGame = true
            )

            // Seed two duplicate nexus-library.json files (older updatedAt=100, newer updatedAt=500).
            val olderJson = LibrarySyncCodec.buildDocument(listOf(olderGame), 100L).toString()
            val newerJson = LibrarySyncCodec.buildDocument(
                items = listOf(newerGame),
                updatedAt = 500L,
                localHashForUrl = { null }
            ).apply {
                val arr = getJSONArray("items")
                arr.getJSONObject(0).put("posterHash", safeHash)
                arr.getJSONObject(0).put("nsfwPosterHash", nsfwHash)
            }.toString()

            provider.addFile(
                parentId = FakeDriveDocumentsProvider.ROOT_ID,
                docId = "json_old",
                name = LibrarySyncService.FILE_NAME,
                mimeType = "application/json",
                bytes = olderJson.toByteArray(),
                lastModified = 9000L
            )
            provider.addFile(
                parentId = FakeDriveDocumentsProvider.ROOT_ID,
                docId = "json_new",
                name = LibrarySyncService.FILE_NAME,
                mimeType = "application/json",
                bytes = newerJson.toByteArray(),
                lastModified = 2000L
            )

            // Seed two duplicate posters/ folders + duplicate files inside the newer folder.
            provider.addDir(
                parentId = FakeDriveDocumentsProvider.ROOT_ID,
                docId = "posters_old",
                name = LibrarySyncService.POSTERS_DIR_NAME,
                lastModified = 1000L
            )
            provider.addDir(
                parentId = FakeDriveDocumentsProvider.ROOT_ID,
                docId = "posters_new",
                name = LibrarySyncService.POSTERS_DIR_NAME,
                lastModified = 5000L
            )
            // Unique file only present in the older duplicate folder -> must be merged into winner!
            provider.addFile(
                parentId = "posters_old",
                docId = "safe_in_old_dir",
                name = "poster_gfn_1.png",
                mimeType = "image/png",
                bytes = extraSafePosterBytes,
                lastModified = 1500L
            )
            // Duplicate NSFW file inside the newer folder: stale copy vs newer copy.
            provider.addFile(
                parentId = "posters_new",
                docId = "nsfw_stale",
                name = "poster_nsfw_gfn_1.png",
                mimeType = "image/png",
                bytes = nsfwOldBytes,
                lastModified = 1200L
            )
            provider.addFile(
                parentId = "posters_new",
                docId = "nsfw_latest",
                name = "poster_nsfw_gfn_1.png",
                mimeType = "image/png",
                bytes = nsfwNewBytes,
                lastModified = 6000L
            )

            val treeUri = DocumentsContract.buildTreeDocumentUri(
                FakeDriveDocumentsProvider.AUTHORITY,
                FakeDriveDocumentsProvider.ROOT_ID
            )
            val outcome = syncService.configureFolder(treeUri)
            assertTrue(outcome is LibrarySyncOutcome.Applied)

            // Only one nexus-library.json and one posters/ folder remain.
            val rootChildren = provider.childrenOf(FakeDriveDocumentsProvider.ROOT_ID)
            val jsonDocs = rootChildren.filter { it.name == LibrarySyncService.FILE_NAME }
            val posterDirs = rootChildren.filter { it.name == LibrarySyncService.POSTERS_DIR_NAME }
            assertEquals(1, jsonDocs.size)
            assertEquals("json_new", jsonDocs.single().docId)
            assertEquals(1, posterDirs.size)
            assertEquals("posters_new", posterDirs.single().docId)

            // Winner posters/ has exactly one copy of each poster, including the merged safe poster.
            val posterFiles = provider.childrenOf("posters_new")
            assertEquals(1, posterFiles.count { it.name == "poster_nsfw_gfn_1.png" })
            assertEquals(1, posterFiles.count { it.name == "poster_gfn_1.png" })

            // Applied game uses the newest title and imported both posters.
            val syncedGame = repository.getGameById("gfn_1")
            assertNotNull(syncedGame)
            assertEquals("Control Ultimate", syncedGame!!.title)
            assertNotNull(syncedGame.posterUrl)
            assertNotNull(syncedGame.nsfwPosterUrl)
            assertEquals(
                nsfwHash,
                LibrarySyncCodec.localFileSha256(syncedGame.nsfwPosterUrl!!)
            )
        }

    @Test
    fun `sync forces refresh of Drive posters folder and pulls missing image assets even when metadata matches`() =
        runBlocking {
            val prefs = context.getSharedPreferences(
                "sync_refresh_test_${System.nanoTime()}",
                Context.MODE_PRIVATE
            )
            val syncService = LibrarySyncService(context, repository, prefs)

            val staleBytes = byteArrayOf(1, 2, 3)
            val latestPosterBytes = byteArrayOf(11, 22, 33, 44, 55, 66)
            val latestHash = LibrarySyncCodec.sha256Hex(latestPosterBytes)

            val game = GameItem(
                id = "gfn_refresh",
                title = "Cyberpunk 2077",
                type = GameType.GEFORCE_NOW,
                isGame = true,
                customArtworkSource = "FILE"
            )
            val docJson = LibrarySyncCodec.buildDocument(
                items = listOf(game),
                updatedAt = 800L,
                localHashForUrl = { null }
            ).apply {
                getJSONArray("items").getJSONObject(0).put("posterHash", latestHash)
            }.toString()

            provider.addFile(
                parentId = FakeDriveDocumentsProvider.ROOT_ID,
                docId = "json_main",
                name = LibrarySyncService.FILE_NAME,
                mimeType = "application/json",
                bytes = docJson.toByteArray(),
                lastModified = 2000L
            )
            provider.addDir(
                parentId = FakeDriveDocumentsProvider.ROOT_ID,
                docId = "posters_dir",
                name = LibrarySyncService.POSTERS_DIR_NAME,
                lastModified = 2000L
            )
            provider.addFile(
                parentId = "posters_dir",
                docId = "poster_refresh_file",
                name = "poster_gfn_refresh.png",
                mimeType = "image/png",
                bytes = staleBytes,
                lastModified = 2500L
            )

            // Simulate Google Drive SAF caching:
            // 1) posters_dir hides its children until refresh() is called on the folder
            // 2) poster_refresh_file serves staleBytes until refresh() is called on that file.
            provider.hideChildrenUntilRefreshed("posters_dir")
            provider.updateFileOnRefresh("poster_refresh_file", latestPosterBytes)

            val treeUri = DocumentsContract.buildTreeDocumentUri(
                FakeDriveDocumentsProvider.AUTHORITY,
                FakeDriveDocumentsProvider.ROOT_ID
            )

            val outcome = syncService.configureFolder(treeUri)
            assertTrue(outcome is LibrarySyncOutcome.Applied)
            assertTrue(provider.refreshedIds.contains("posters_dir"))
            assertTrue(provider.refreshedIds.contains("poster_refresh_file"))

            val imported = repository.getGameById("gfn_refresh")
            assertNotNull(imported?.posterUrl)
            assertEquals(latestHash, LibrarySyncCodec.localFileSha256(imported!!.posterUrl!!))

            // Delete the local cached poster file to simulate missing local asset while
            // nexus-library.json metadata and version otherwise already match, then syncNow().
            val localFile = File(Uri.parse(imported.posterUrl!!).path!!)
            assertTrue(localFile.delete())

            provider.refreshedIds.clear()
            val secondOutcome = syncService.syncNow()
            assertTrue(secondOutcome is LibrarySyncOutcome.Applied)
            assertTrue(provider.refreshedIds.contains("posters_dir"))

            val restored = repository.getGameById("gfn_refresh")
            assertNotNull(restored?.posterUrl)
            assertEquals(latestHash, LibrarySyncCodec.localFileSha256(restored!!.posterUrl!!))
        }

    @Test
    fun `sync never overwrites or imports favorites for games or installed apps`() =
        runBlocking {
            val prefs = context.getSharedPreferences(
                "sync_no_favorites_test_${System.nanoTime()}",
                Context.MODE_PRIVATE
            )
            val syncService = LibrarySyncService(context, repository, prefs)

            // Seed local database with an installed app favorited on THIS device, plus a game
            // favorited on THIS device at order 2.
            val localApp = GameItem(
                id = "app_com.example.browser",
                title = "Browser",
                type = GameType.INSTALLED_APP,
                isGame = false,
                packageName = "com.example.browser",
                isFavorite = true,
                favoriteOrder = 0
            )
            val localGame = GameItem(
                id = "gfn_fav",
                title = "Control",
                type = GameType.GEFORCE_NOW,
                isGame = true,
                isFavorite = true,
                favoriteOrder = 2
            )
            db.gameDao().insertGames(listOf(localApp, localGame))

            // Remote sync JSON contains legacy favorite flags (different order / false locally)
            // and a renamed app + updated game.
            val remoteJson = """
                {
                  "format": "nexus_library",
                  "version": 2,
                  "updatedAt": 900,
                  "localPosters": true,
                  "items": [
                    {
                      "id": "gfn_fav",
                      "title": "Control Ultimate",
                      "type": "GEFORCE_NOW",
                      "isGame": true,
                      "isFavorite": false,
                      "favoriteOrder": 0
                    },
                    {
                      "id": "gfn_remote_only",
                      "title": "Hades",
                      "type": "GEFORCE_NOW",
                      "isGame": true,
                      "isFavorite": true,
                      "favoriteOrder": 1
                    }
                  ],
                  "appCustomizations": [
                    {
                      "id": "app_com.example.browser",
                      "title": "Custom Browser",
                      "type": "INSTALLED_APP",
                      "isGame": false,
                      "isCustomized": true,
                      "isFavorite": false,
                      "favoriteOrder": 0
                    }
                  ]
                }
            """.trimIndent()

            provider.addFile(
                parentId = FakeDriveDocumentsProvider.ROOT_ID,
                docId = "json_main",
                name = LibrarySyncService.FILE_NAME,
                mimeType = "application/json",
                bytes = remoteJson.toByteArray(),
                lastModified = 3000L
            )

            val treeUri = DocumentsContract.buildTreeDocumentUri(
                FakeDriveDocumentsProvider.AUTHORITY,
                FakeDriveDocumentsProvider.ROOT_ID
            )
            val outcome = syncService.configureFolder(treeUri)
            assertTrue(outcome is LibrarySyncOutcome.Applied)

            val syncedApp = repository.getGameById("app_com.example.browser")!!
            assertEquals("Custom Browser", syncedApp.title)
            assertTrue(syncedApp.isFavorite)
            assertEquals(0, syncedApp.favoriteOrder)

            val syncedExistingGame = repository.getGameById("gfn_fav")!!
            assertEquals("Control Ultimate", syncedExistingGame.title)
            assertTrue(syncedExistingGame.isFavorite)
            assertEquals(2, syncedExistingGame.favoriteOrder)

            val syncedRemoteGame = repository.getGameById("gfn_remote_only")!!
            assertEquals(false, syncedRemoteGame.isFavorite)
            assertEquals(0, syncedRemoteGame.favoriteOrder)
        }
}

class FakeDriveDocumentsProvider : DocumentsProvider() {
    data class Node(
        val docId: String,
        val parentId: String?,
        val name: String,
        val mimeType: String,
        var lastModified: Long,
        var file: File? = null
    )

    private lateinit var storageDir: File
    private val nodes = linkedMapOf<String, Node>()
    private val hiddenUntilRefresh = mutableSetOf<String>()
    private val pendingRefreshContent = mutableMapOf<String, ByteArray>()
    val refreshedIds = mutableSetOf<String>()
    private var nextId = 1

    fun reset(dir: File) {
        storageDir = dir.apply { deleteRecursively(); mkdirs() }
        nodes.clear()
        hiddenUntilRefresh.clear()
        pendingRefreshContent.clear()
        refreshedIds.clear()
        nodes[ROOT_ID] = Node(
            docId = ROOT_ID,
            parentId = null,
            name = "Drive Sync",
            mimeType = DocumentsContract.Document.MIME_TYPE_DIR,
            lastModified = 100L
        )
    }

    fun hideChildrenUntilRefreshed(dirId: String) {
        hiddenUntilRefresh.add(dirId)
    }

    fun updateFileOnRefresh(docId: String, newBytes: ByteArray) {
        pendingRefreshContent[docId] = newBytes
    }

    fun addDir(parentId: String, docId: String, name: String, lastModified: Long) {
        nodes[docId] = Node(
            docId = docId,
            parentId = parentId,
            name = name,
            mimeType = DocumentsContract.Document.MIME_TYPE_DIR,
            lastModified = lastModified
        )
    }

    fun addFile(
        parentId: String,
        docId: String,
        name: String,
        mimeType: String,
        bytes: ByteArray,
        lastModified: Long
    ) {
        val f = File(storageDir, "$docId.bin").apply { writeBytes(bytes) }
        nodes[docId] = Node(
            docId = docId,
            parentId = parentId,
            name = name,
            mimeType = mimeType,
            lastModified = lastModified,
            file = f
        )
    }

    fun childrenOf(parentId: String): List<Node> =
        nodes.values.filter { it.parentId == parentId }

    override fun onCreate(): Boolean = true

    override fun refresh(
        uri: Uri,
        args: Bundle?,
        signal: CancellationSignal?
    ): Boolean {
        val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return false
        refreshedIds.add(docId)
        hiddenUntilRefresh.remove(docId)
        pendingRefreshContent.remove(docId)?.let { freshBytes ->
            nodes[docId]?.file?.writeBytes(freshBytes)
        }
        return true
    }

    override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(emptyArray())

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_PROJECTION)
        nodes[documentId]?.let { addRow(cursor, it) }
        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_PROJECTION)
        if (parentDocumentId !in hiddenUntilRefresh) {
            nodes.values.filter { it.parentId == parentDocumentId }.forEach { addRow(cursor, it) }
        }
        return cursor
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        var currentId: String? = documentId
        while (currentId != null) {
            if (currentId == parentDocumentId) return true
            currentId = nodes[currentId]?.parentId
        }
        return false
    }

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String
    ): String {
        val id = "created_${nextId++}"
        val file = if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
            null
        } else {
            File(storageDir, "$id.bin").apply { createNewFile() }
        }
        nodes[id] = Node(
            docId = id,
            parentId = parentDocumentId,
            name = displayName,
            mimeType = mimeType,
            lastModified = System.currentTimeMillis(),
            file = file
        )
        return id
    }

    override fun deleteDocument(documentId: String) {
        nodes.values.filter { it.parentId == documentId }.map { it.docId }.forEach {
            deleteDocument(it)
        }
        nodes.remove(documentId)?.file?.delete()
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        val node = nodes[documentId] ?: throw java.io.FileNotFoundException(documentId)
        val file = node.file ?: throw java.io.FileNotFoundException(documentId)
        if (mode.contains("w")) {
            node.lastModified = System.currentTimeMillis()
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun getDocumentType(documentId: String): String =
        nodes[documentId]?.mimeType ?: "application/octet-stream"

    private fun addRow(cursor: MatrixCursor, node: Node) {
        val builder = cursor.newRow()
        cursor.columnNames.forEach { col ->
            when (col) {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID -> builder.add(node.docId)
                DocumentsContract.Document.COLUMN_DISPLAY_NAME -> builder.add(node.name)
                DocumentsContract.Document.COLUMN_MIME_TYPE -> builder.add(node.mimeType)
                DocumentsContract.Document.COLUMN_LAST_MODIFIED -> builder.add(node.lastModified)
                DocumentsContract.Document.COLUMN_SIZE -> builder.add(node.file?.length() ?: 0L)
                DocumentsContract.Document.COLUMN_FLAGS -> builder.add(
                    DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
                        DocumentsContract.Document.FLAG_SUPPORTS_WRITE or
                        DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
                )
                else -> builder.add(null)
            }
        }
    }

    companion object {
        const val AUTHORITY = "com.example.test.fakedrive"
        const val ROOT_ID = "sync_root"
        private val DEFAULT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE
        )
    }
}
