package com.example.data.service

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ProviderInfo
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.GameItem
import com.example.data.model.GameType
import com.example.data.repository.GameRepository
import java.io.File
import java.net.URLDecoder
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class GoogleDriveSyncTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository
    private lateinit var safProvider: FakeDriveDocumentsProvider

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
        safProvider = Robolectric.buildContentProvider(FakeDriveDocumentsProvider::class.java)
            .create(info)
            .get()
        safProvider.reset(File(context.cacheDir, "fake_saf_${System.nanoTime()}"))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `Google Drive sync creates Nexus Launcher folder exports posters retries 401 and restores on second device`() =
        runBlocking {
            val driveServer = InMemoryDriveServer()
            val tokenProvider = FakeDriveTokenProvider(driveServer)
            val apiClient = GoogleDriveApiClient(
                tokenProvider = tokenProvider,
                httpClient = OkHttpClient.Builder().addInterceptor(driveServer).build(),
                driveApiBaseUrl = "https://mock.drive.test/drive/v3",
                uploadApiBaseUrl = "https://mock.drive.test/upload/drive/v3"
            )
            val prefsA = context.getSharedPreferences("drive_a_${System.nanoTime()}", Context.MODE_PRIVATE)
            val syncA = LibrarySyncService(
                context = context,
                repository = repository,
                prefs = prefsA,
                driveTokenProvider = tokenProvider,
                driveApiClient = apiClient
            )

            // Seed Device A with a local game + local poster image.
            val posterBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
            val posterHash = LibrarySyncCodec.sha256Hex(posterBytes)
            val localArtDir = File(context.filesDir, "custom_art").apply { mkdirs() }
            val posterFile = File(localArtDir, "local_alan_${System.nanoTime()}.png").apply {
                writeBytes(posterBytes)
            }
            val gameA = GameItem(
                id = "gfn_alan_wake_2",
                title = "Alan Wake 2",
                type = GameType.GEFORCE_NOW,
                isGame = true,
                posterUrl = Uri.fromFile(posterFile).toString(),
                isFavorite = true,
                favoriteOrder = 1,
                customArtworkSource = "FILE"
            )
            db.gameDao().insertGame(gameA)

            // Force one 401 token expiry mid-sync to prove refresh + retry works transparently.
            driveServer.failNextCallWith401 = true

            val step = syncA.beginGoogleDriveAuthorization()
            assertTrue(step is DriveAuthorizationStep.Authorized)
            val auth = step as DriveAuthorizationStep.Authorized

            val outcomeA = syncA.configureGoogleDrive(auth.account)
            assertTrue(outcomeA is LibrarySyncOutcome.WroteLocal)
            assertEquals(LibrarySyncMode.GOOGLE_DRIVE, syncA.syncMode)
            assertEquals("Google Drive (player@example.com)", syncA.targetLabel())
            assertTrue("Expected token refresh on 401", tokenProvider.refreshCount >= 1)

            // Verify Drive has `Nexus Launcher` root folder, `nexus-library.json`, and `posters/poster_gfn_alan_wake_2.png`.
            val rootFolder = driveServer.nodes.values.firstOrNull {
                it.name == "Nexus Launcher" && it.parentId == "root" && it.isFolder
            }
            assertNotNull(rootFolder)
            val jsonNode = driveServer.childrenOf(rootFolder!!.id).firstOrNull {
                it.name == LibrarySyncService.FILE_NAME
            }
            assertNotNull(jsonNode)
            val postersDir = driveServer.childrenOf(rootFolder.id).firstOrNull {
                it.name == LibrarySyncService.POSTERS_DIR_NAME && it.isFolder
            }
            assertNotNull(postersDir)
            val uploadedPoster = driveServer.childrenOf(postersDir!!.id).firstOrNull {
                it.name == "poster_gfn_alan_wake_2.png"
            }
            assertNotNull(uploadedPoster)
            assertEquals(posterHash, uploadedPoster!!.appProperties["sha256"])

            // Restore onto a brand-new Device B sharing the same Google Drive account.
            val dbB = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            try {
                val repoB = GameRepository(dbB.gameDao(), InstalledAppsManager(context))
                val prefsB = context.getSharedPreferences("drive_b_${System.nanoTime()}", Context.MODE_PRIVATE)
                val syncB = LibrarySyncService(
                    context = context,
                    repository = repoB,
                    prefs = prefsB,
                    driveTokenProvider = tokenProvider,
                    driveApiClient = apiClient
                )
                val outcomeB = syncB.configureGoogleDrive(auth.account)
                assertTrue(outcomeB is LibrarySyncOutcome.Applied)

                val restored = repoB.getGameById("gfn_alan_wake_2")
                assertNotNull(restored)
                assertEquals("Alan Wake 2", restored!!.title)
                assertNotNull(restored.posterUrl)
                assertEquals(posterHash, LibrarySyncCodec.localFileSha256(restored.posterUrl!!))
                // Favorites remain per-device.
                assertFalse(restored.isFavorite)
            } finally {
                dbB.close()
            }
        }

    @Test
    fun `switching between Local Folder Google Drive and Disconnect is mutually exclusive and preserves all data`() =
        runBlocking {
            val driveServer = InMemoryDriveServer()
            val tokenProvider = FakeDriveTokenProvider(driveServer)
            val apiClient = GoogleDriveApiClient(
                tokenProvider = tokenProvider,
                httpClient = OkHttpClient.Builder().addInterceptor(driveServer).build(),
                driveApiBaseUrl = "https://mock.drive.test/drive/v3",
                uploadApiBaseUrl = "https://mock.drive.test/upload/drive/v3"
            )
            val prefs = context.getSharedPreferences("switch_test_${System.nanoTime()}", Context.MODE_PRIVATE)
            val syncService = LibrarySyncService(
                context = context,
                repository = repository,
                prefs = prefs,
                driveTokenProvider = tokenProvider,
                driveApiClient = apiClient
            )

            // Seed local library with favorite game + local poster + folder + shortcut.
            val localPosterBytes = byteArrayOf(9, 8, 7, 6, 5)
            val localArtDir = File(context.filesDir, "custom_art").apply { mkdirs() }
            val localPosterFile = File(localArtDir, "local_control_${System.nanoTime()}.png").apply {
                writeBytes(localPosterBytes)
            }
            val localGame = GameItem(
                id = "gfn_local_control",
                title = "Control",
                type = GameType.GEFORCE_NOW,
                isGame = true,
                posterUrl = Uri.fromFile(localPosterFile).toString(),
                isFavorite = true,
                favoriteOrder = 0
            )
            val localFolderApp = GameItem(
                id = "folder_streaming",
                title = "Cloud Clients",
                type = GameType.FOLDER,
                isGame = false,
                isFavorite = true
            )
            db.gameDao().insertGames(listOf(localGame, localFolderApp))

            // 1. Start with Local SAF Folder sync.
            val treeUri = DocumentsContract.buildTreeDocumentUri(
                FakeDriveDocumentsProvider.AUTHORITY,
                FakeDriveDocumentsProvider.ROOT_ID
            )
            val folderOutcome = syncService.configureFolder(treeUri)
            assertTrue(folderOutcome is LibrarySyncOutcome.WroteLocal)
            assertEquals(LibrarySyncMode.LOCAL_FOLDER, syncService.syncMode)

            // Pre-populate Google Drive with an existing game from another device (`gfn_drive_hades`).
            val drivePosterBytes = byteArrayOf(42, 43, 44, 45)
            val drivePosterHash = LibrarySyncCodec.sha256Hex(drivePosterBytes)
            val driveGame = GameItem(
                id = "gfn_drive_hades",
                title = "Hades",
                type = GameType.GEFORCE_NOW,
                isGame = true
            )
            val remoteFolder = driveServer.addFolder("root", "Nexus Launcher", 1_000L)
            val remotePosters = driveServer.addFolder(remoteFolder.id, "posters", 1_000L)
            driveServer.addFile(
                parentId = remotePosters.id,
                name = "poster_gfn_drive_hades.png",
                mimeType = "image/png",
                bytes = drivePosterBytes,
                modifiedMs = 1_000L,
                appProperties = mapOf("sha256" to drivePosterHash)
            )
            val driveDoc = LibrarySyncCodec.buildDocument(
                items = listOf(driveGame),
                updatedAt = 2_000L,
                localHashForUrl = { null }
            ).apply {
                getJSONArray("items").getJSONObject(0).put("posterHash", drivePosterHash)
            }.toString()
            driveServer.addFile(
                parentId = remoteFolder.id,
                name = LibrarySyncService.FILE_NAME,
                mimeType = "application/json",
                bytes = driveDoc.toByteArray(Charsets.UTF_8),
                modifiedMs = 2_000L
            )

            // 2. Switch from Local Folder -> Google Drive.
            val switchToDriveOutcome = syncService.configureGoogleDrive(
                DriveAuthAccount(email = "player@example.com", displayName = "Player")
            )
            assertTrue(switchToDriveOutcome is LibrarySyncOutcome.Merged)
            val mergedOutcome = switchToDriveOutcome as LibrarySyncOutcome.Merged
            assertEquals(1, mergedOutcome.addedFromRemote)
            assertEquals(LibrarySyncMode.GOOGLE_DRIVE, syncService.syncMode)
            assertNull("Local folder tree URI must be cleared when Drive is active", prefs.getString(LibrarySyncService.KEY_TREE_URI, null))

            // Both existing local game AND merged Drive game must exist, plus favorites & local folder intact!
            val controlAfterSwitch = repository.getGameById("gfn_local_control")
            val hadesAfterSwitch = repository.getGameById("gfn_drive_hades")
            val folderAfterSwitch = repository.getGameById("folder_streaming")
            assertNotNull(controlAfterSwitch)
            assertTrue(controlAfterSwitch!!.isFavorite)
            assertNotNull(hadesAfterSwitch)
            assertNotNull(hadesAfterSwitch!!.posterUrl)
            assertEquals(drivePosterHash, LibrarySyncCodec.localFileSha256(hadesAfterSwitch.posterUrl!!))
            assertNotNull(folderAfterSwitch)
            assertTrue(folderAfterSwitch!!.isFavorite)

            // Google Drive remote file now contains BOTH games!
            val syncedDriveJson = driveServer.childrenOf(remoteFolder.id)
                .single { it.name == LibrarySyncService.FILE_NAME }
                .bytes.toString(Charsets.UTF_8)
            val parsedDrive = LibrarySyncCodec.parse(syncedDriveJson)
            assertEquals(setOf("gfn_local_control", "gfn_drive_hades"), parsedDrive.items.map { it.id }.toSet())

            // 3. Switch from Google Drive -> Local Folder (with clean new tree).
            val secondTreeUri = DocumentsContract.buildTreeDocumentUri(
                FakeDriveDocumentsProvider.AUTHORITY,
                FakeDriveDocumentsProvider.ROOT_ID
            )
            // Clear existing SAF children so it's a fresh folder target.
            safProvider.reset(File(context.cacheDir, "fake_saf_2_${System.nanoTime()}"))
            val switchBackOutcome = syncService.configureFolder(secondTreeUri)
            assertTrue(switchBackOutcome is LibrarySyncOutcome.WroteLocal)
            assertEquals(LibrarySyncMode.LOCAL_FOLDER, syncService.syncMode)
            assertNull(syncService.driveAccountEmail())
            assertNull(prefs.getString(LibrarySyncService.KEY_DRIVE_FOLDER_ID, null))

            // All local items & posters are still in DB and exported to the local folder!
            assertNotNull(repository.getGameById("gfn_local_control"))
            assertNotNull(repository.getGameById("gfn_drive_hades"))
            assertNotNull(repository.getGameById("folder_streaming"))
            val safFiles = safProvider.childrenOf(FakeDriveDocumentsProvider.ROOT_ID)
            assertTrue(safFiles.any { it.name == LibrarySyncService.FILE_NAME })

            // 4. Disconnect -> clears sync mode, preserves 100% of library items and local poster files.
            syncService.disconnect()
            assertEquals(LibrarySyncMode.NONE, syncService.syncMode)
            assertFalse(syncService.isConfigured)
            assertNull(syncService.targetLabel())
            assertNotNull(repository.getGameById("gfn_local_control"))
            assertNotNull(repository.getGameById("gfn_drive_hades"))
            assertNotNull(repository.getGameById("folder_streaming"))
            assertTrue(localPosterFile.exists())
        }

    @Test
    fun `failed switch to Google Drive rolls back cleanly to active Local Folder without losing data`() =
        runBlocking {
            val prefs = context.getSharedPreferences("rollback_test_${System.nanoTime()}", Context.MODE_PRIVATE)
            val failingProvider = object : DriveAccessTokenProvider {
                override suspend fun beginInteractiveAuthorization(preferredEmail: String?) =
                    throw IllegalStateException("Cancelled")
                override suspend fun resolveAuthorizationResult(data: Intent?) =
                    throw IllegalStateException("Cancelled")
                override suspend fun getAccessToken(preferredEmail: String?): String =
                    throw IllegalStateException("Network unavailable")
                override suspend fun invalidateAndRefreshToken(invalidToken: String, preferredEmail: String?): String =
                    throw IllegalStateException("Network unavailable")
                override suspend fun revokeAccess(accountEmail: String?) = Unit
            }
            val syncService = LibrarySyncService(
                context = context,
                repository = repository,
                prefs = prefs,
                driveTokenProvider = failingProvider,
                driveApiClient = GoogleDriveApiClient(failingProvider)
            )

            db.gameDao().insertGame(
                GameItem(
                    id = "gfn_keep_me",
                    title = "Death Stranding",
                    type = GameType.GEFORCE_NOW,
                    isGame = true
                )
            )

            val treeUri = DocumentsContract.buildTreeDocumentUri(
                FakeDriveDocumentsProvider.AUTHORITY,
                FakeDriveDocumentsProvider.ROOT_ID
            )
            syncService.configureFolder(treeUri)
            assertEquals(LibrarySyncMode.LOCAL_FOLDER, syncService.syncMode)

            val failedResult = runCatching {
                syncService.configureGoogleDrive(DriveAuthAccount("user@example.com", "User"))
            }
            assertTrue(failedResult.isFailure)
            assertEquals(LibrarySyncMode.LOCAL_FOLDER, syncService.syncMode)
            assertTrue(syncService.isConfigured)
            assertNotNull(repository.getGameById("gfn_keep_me"))
        }

    private class FakeDriveTokenProvider(
        private val driveServer: InMemoryDriveServer
    ) : DriveAccessTokenProvider {
        var refreshCount = 0
        private var currentToken = "valid_token_1"

        override suspend fun beginInteractiveAuthorization(preferredEmail: String?): DriveAuthorizationStep =
            DriveAuthorizationStep.Authorized(
                accessToken = currentToken,
                account = DriveAuthAccount(email = "player@example.com", displayName = "Player")
            )

        override suspend fun resolveAuthorizationResult(data: Intent?): DriveAuthorizationStep.Authorized =
            DriveAuthorizationStep.Authorized(
                accessToken = currentToken,
                account = DriveAuthAccount(email = "player@example.com", displayName = "Player")
            )

        override suspend fun getAccessToken(preferredEmail: String?): String = currentToken

        override suspend fun invalidateAndRefreshToken(invalidToken: String, preferredEmail: String?): String {
            refreshCount++
            currentToken = "valid_token_${refreshCount + 1}"
            driveServer.validToken = currentToken
            return currentToken
        }

        override suspend fun revokeAccess(accountEmail: String?) = Unit
    }

    private class InMemoryDriveServer : Interceptor {
        data class Node(
            val id: String,
            val parentId: String,
            val name: String,
            val mimeType: String,
            var modifiedMs: Long,
            var bytes: ByteArray = ByteArray(0),
            val appProperties: MutableMap<String, String> = mutableMapOf(),
            var trashed: Boolean = false
        ) {
            val isFolder: Boolean
                get() = mimeType == GoogleDriveApiClient.FOLDER_MIME_TYPE

            fun toJson(): JSONObject = JSONObject().apply {
                put("id", id)
                put("name", name)
                put("mimeType", mimeType)
                put("modifiedTime", "2026-11-17T02:00:00.000Z")
                put("size", bytes.size.toString())
                put("trashed", trashed)
                if (appProperties.isNotEmpty()) {
                    put("appProperties", JSONObject(appProperties as Map<*, *>))
                }
            }
        }

        val nodes = linkedMapOf<String, Node>()
        var validToken = "valid_token_1"
        var failNextCallWith401 = false
        private var nextId = 1

        fun addFolder(parentId: String, name: String, modifiedMs: Long): Node {
            val id = "drv_${nextId++}"
            return Node(
                id = id,
                parentId = parentId,
                name = name,
                mimeType = GoogleDriveApiClient.FOLDER_MIME_TYPE,
                modifiedMs = modifiedMs
            ).also { nodes[id] = it }
        }

        fun addFile(
            parentId: String,
            name: String,
            mimeType: String,
            bytes: ByteArray,
            modifiedMs: Long,
            appProperties: Map<String, String> = emptyMap()
        ): Node {
            val id = "drv_${nextId++}"
            return Node(
                id = id,
                parentId = parentId,
                name = name,
                mimeType = mimeType,
                modifiedMs = modifiedMs,
                bytes = bytes,
                appProperties = appProperties.toMutableMap()
            ).also { nodes[id] = it }
        }

        fun childrenOf(parentId: String): List<Node> =
            nodes.values.filter { !it.trashed && it.parentId == parentId }

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            if (failNextCallWith401) {
                failNextCallWith401 = false
                return jsonResponse(request, 401, """{"error":{"message":"Invalid Credentials"}}""")
            }
            val authHeader = request.header("Authorization")
            if (authHeader != "Bearer $validToken") {
                return jsonResponse(request, 401, """{"error":{"message":"Invalid Credentials"}}""")
            }

            val path = request.url.encodedPath
            val isUpload = path.startsWith("/upload/drive/v3/files")
            val method = request.method

            return when {
                isUpload && method == "POST" -> handleMultipartCreate(request)
                isUpload && method == "PATCH" -> {
                    val fileId = path.removePrefix("/upload/drive/v3/files/").trim('/')
                    handleMultipartPatch(request, fileId)
                }
                path == "/drive/v3/files" && method == "GET" -> handleList(request)
                path == "/drive/v3/files" && method == "POST" -> handleCreateFolder(request)
                path.startsWith("/drive/v3/files/") && method == "GET" -> {
                    val fileId = URLDecoder.decode(path.removePrefix("/drive/v3/files/").trim('/'), "UTF-8")
                    val node = nodes[fileId]
                    if (node == null) {
                        jsonResponse(request, 404, """{"error":{"message":"File not found"}}""")
                    } else if (request.url.queryParameter("alt") == "media") {
                        Response.Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(node.bytes.toResponseBody(node.mimeType.toMediaType()))
                            .build()
                    } else {
                        jsonResponse(request, 200, node.toJson().toString())
                    }
                }
                path.startsWith("/drive/v3/files/") && method == "DELETE" -> {
                    val fileId = URLDecoder.decode(path.removePrefix("/drive/v3/files/").trim('/'), "UTF-8")
                    nodes.remove(fileId)
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(204)
                        .message("No Content")
                        .body(ByteArray(0).toResponseBody(null))
                        .build()
                }
                else -> jsonResponse(request, 400, """{"error":{"message":"Unsupported request: $method $path"}}""")
            }
        }

        private fun handleList(request: Request): Response {
            val q = request.url.queryParameter("q").orEmpty()
            val filtered = nodes.values.filter { node ->
                if (node.trashed) return@filter false
                when {
                    q.contains("'root' in parents") && q.contains(GoogleDriveApiClient.FOLDER_MIME_TYPE) ->
                        node.parentId == "root" && node.isFolder && node.name == "Nexus Launcher"
                    else -> {
                        val parentMatch = Regex("'([^']+)' in parents").find(q)?.groupValues?.get(1)
                        parentMatch == null || node.parentId == parentMatch
                    }
                }
            }
            val filesJson = JSONArray()
            filtered.forEach { filesJson.put(it.toJson()) }
            return jsonResponse(request, 200, JSONObject().put("files", filesJson).toString())
        }

        private fun handleCreateFolder(request: Request): Response {
            val bodyJson = JSONObject(readRequestBytes(request).toString(Charsets.UTF_8))
            val name = bodyJson.getString("name")
            val parentId = bodyJson.optJSONArray("parents")?.optString(0)?.takeIf { it.isNotBlank() } ?: "root"
            val created = addFolder(parentId, name, System.currentTimeMillis())
            return jsonResponse(request, 200, created.toJson().toString())
        }

        private fun handleMultipartCreate(request: Request): Response {
            val (metadata, contentBytes) = parseMultipart(request)
            val name = metadata.getString("name")
            val mime = metadata.optString("mimeType", "application/octet-stream")
            val parentId = metadata.optJSONArray("parents")?.optString(0)?.takeIf { it.isNotBlank() } ?: "root"
            val props = metadata.optJSONObject("appProperties")?.let { obj ->
                buildMap {
                    obj.keys().forEach { key -> put(key, obj.getString(key)) }
                }
            }.orEmpty()
            val created = addFile(
                parentId = parentId,
                name = name,
                mimeType = mime,
                bytes = contentBytes,
                modifiedMs = System.currentTimeMillis(),
                appProperties = props
            )
            return jsonResponse(request, 200, created.toJson().toString())
        }

        private fun handleMultipartPatch(request: Request, fileId: String): Response {
            val existing = nodes[fileId] ?: return jsonResponse(request, 404, """{"error":{"message":"Not found"}}""")
            val (metadata, contentBytes) = parseMultipart(request)
            existing.bytes = contentBytes
            existing.modifiedMs = System.currentTimeMillis()
            metadata.optJSONObject("appProperties")?.let { obj ->
                obj.keys().forEach { key -> existing.appProperties[key] = obj.getString(key) }
            }
            return jsonResponse(request, 200, existing.toJson().toString())
        }

        private fun parseMultipart(request: Request): Pair<JSONObject, ByteArray> {
            val boundary = request.body?.contentType()?.parameter("boundary")
                ?: throw IllegalStateException("Missing multipart boundary")
            val raw = readRequestBytes(request)
            val delimiter = "--$boundary".toByteArray(Charsets.ISO_8859_1)
            val parts = splitByBoundary(raw, delimiter)
            require(parts.size >= 2) { "Expected 2 multipart parts, got ${parts.size}" }
            val metaBody = stripHeaders(parts[0]).toString(Charsets.UTF_8)
            val contentBytes = stripHeaders(parts[1])
            return JSONObject(metaBody) to contentBytes
        }

        private fun splitByBoundary(raw: ByteArray, boundary: ByteArray): List<ByteArray> {
            val indices = mutableListOf<Int>()
            var i = 0
            while (i <= raw.size - boundary.size) {
                var match = true
                for (j in boundary.indices) {
                    if (raw[i + j] != boundary[j]) {
                        match = false
                        break
                    }
                }
                if (match) {
                    indices.add(i)
                    i += boundary.size
                } else {
                    i++
                }
            }
            val result = mutableListOf<ByteArray>()
            for (idx in 0 until indices.size - 1) {
                var start = indices[idx] + boundary.size
                if (start + 1 < raw.size && raw[start] == '\r'.code.toByte() && raw[start + 1] == '\n'.code.toByte()) {
                    start += 2
                }
                var end = indices[idx + 1]
                if (end - 2 >= start && raw[end - 2] == '\r'.code.toByte() && raw[end - 1] == '\n'.code.toByte()) {
                    end -= 2
                }
                if (end > start) {
                    result.add(raw.copyOfRange(start, end))
                }
            }
            return result
        }

        private fun stripHeaders(part: ByteArray): ByteArray {
            for (i in 0 until part.size - 3) {
                if (part[i] == '\r'.code.toByte() &&
                    part[i + 1] == '\n'.code.toByte() &&
                    part[i + 2] == '\r'.code.toByte() &&
                    part[i + 3] == '\n'.code.toByte()
                ) {
                    return part.copyOfRange(i + 4, part.size)
                }
            }
            return part
        }

        private fun readRequestBytes(request: Request): ByteArray {
            val buffer = Buffer()
            request.body?.writeTo(buffer)
            return buffer.readByteArray()
        }

        private fun jsonResponse(request: Request, code: Int, body: String): Response =
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code in 200..299) "OK" else "Error")
                .body(body.toResponseBody("application/json; charset=UTF-8".toMediaType()))
                .build()
    }
}
