package com.example.data.service

import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lightweight Google Drive REST API v3 client scoped strictly to app-created files
 * (`drive.file`). Uses OkHttp directly to keep APK size small and avoid extra SDK bloat.
 */
class GoogleDriveApiClient(
    private val tokenProvider: DriveAccessTokenProvider,
    private val httpClient: OkHttpClient = defaultDriveHttpClient(),
    private val driveApiBaseUrl: String = DEFAULT_DRIVE_API_BASE_URL,
    private val uploadApiBaseUrl: String = DEFAULT_UPLOAD_API_BASE_URL
) {

    data class DriveFileEntry(
        val id: String,
        val name: String,
        val mimeType: String,
        val modifiedTimeMs: Long,
        val size: Long,
        val sha256AppProperty: String? = null,
        val trashed: Boolean = false
    ) {
        val isFolder: Boolean
            get() = mimeType == FOLDER_MIME_TYPE
    }

    suspend fun getFileMetadata(preferredEmail: String?, fileId: String): DriveFileEntry? =
        executeWithAuth(preferredEmail) { token ->
            Request.Builder()
                .url("$driveApiBaseUrl/files/${encodeSegment(fileId)}?fields=$FILE_FIELDS")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
        }.use { response ->
            if (response.code == 404) return@use null
            ensureSuccess(response, "Failed to inspect Google Drive folder")
            val body = response.body?.string().orEmpty()
            parseFileEntry(JSONObject(body))
        }

    suspend fun listChildren(preferredEmail: String?, parentFolderId: String): List<DriveFileEntry> =
        listFilesByQuery(
            preferredEmail = preferredEmail,
            query = "'${escapeDriveQuery(parentFolderId)}' in parents and trashed = false"
        )

    suspend fun findRootFolders(
        preferredEmail: String?,
        folderName: String = DEFAULT_ROOT_FOLDER_NAME
    ): List<DriveFileEntry> = listFilesByQuery(
        preferredEmail = preferredEmail,
        query = "name = '${escapeDriveQuery(folderName)}' " +
            "and mimeType = '$FOLDER_MIME_TYPE' " +
            "and 'root' in parents " +
            "and trashed = false"
    )

    suspend fun createFolder(
        preferredEmail: String?,
        name: String,
        parentFolderId: String? = null
    ): DriveFileEntry {
        val metadata = JSONObject()
            .put("name", name)
            .put("mimeType", FOLDER_MIME_TYPE)
            .apply {
                if (!parentFolderId.isNullOrBlank()) {
                    put("parents", JSONArray().put(parentFolderId))
                }
            }
            .toString()

        return executeWithAuth(preferredEmail) { token ->
            Request.Builder()
                .url("$driveApiBaseUrl/files?fields=$FILE_FIELDS")
                .header("Authorization", "Bearer $token")
                .post(metadata.toRequestBody(JSON_MEDIA_TYPE))
                .build()
        }.use { response ->
            ensureSuccess(response, "Could not create '$name' folder in Google Drive")
            parseFileEntry(JSONObject(response.body?.string().orEmpty()))
                ?: throw IllegalStateException("Google Drive returned an invalid folder record")
        }
    }

    suspend fun downloadBytes(preferredEmail: String?, fileId: String): ByteArray =
        executeWithAuth(preferredEmail) { token ->
            Request.Builder()
                .url("$driveApiBaseUrl/files/${encodeSegment(fileId)}?alt=media")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
        }.use { response ->
            ensureSuccess(response, "Could not download file from Google Drive")
            response.body?.bytes() ?: ByteArray(0)
        }

    suspend fun createFile(
        preferredEmail: String?,
        parentFolderId: String,
        name: String,
        mimeType: String,
        bytes: ByteArray,
        sha256Hex: String? = null
    ): DriveFileEntry {
        val metadata = JSONObject()
            .put("name", name)
            .put("mimeType", mimeType)
            .put("parents", JSONArray().put(parentFolderId))
            .apply {
                if (!sha256Hex.isNullOrBlank()) {
                    put("appProperties", JSONObject().put(PROP_SHA256, sha256Hex))
                }
            }
            .toString()

        val body = buildMultipartUploadBody(metadata, mimeType, bytes)
        return executeWithAuth(preferredEmail) { token ->
            Request.Builder()
                .url("$uploadApiBaseUrl/files?uploadType=multipart&fields=$FILE_FIELDS")
                .header("Authorization", "Bearer $token")
                .post(body)
                .build()
        }.use { response ->
            ensureSuccess(response, "Could not upload '$name' to Google Drive")
            parseFileEntry(JSONObject(response.body?.string().orEmpty()))
                ?: throw IllegalStateException("Google Drive returned an invalid file record")
        }
    }

    suspend fun updateFile(
        preferredEmail: String?,
        fileId: String,
        mimeType: String,
        bytes: ByteArray,
        sha256Hex: String? = null
    ): DriveFileEntry {
        val metadata = JSONObject()
            .put("mimeType", mimeType)
            .apply {
                if (!sha256Hex.isNullOrBlank()) {
                    put("appProperties", JSONObject().put(PROP_SHA256, sha256Hex))
                }
            }
            .toString()

        val body = buildMultipartUploadBody(metadata, mimeType, bytes)
        return executeWithAuth(preferredEmail) { token ->
            Request.Builder()
                .url(
                    "$uploadApiBaseUrl/files/${encodeSegment(fileId)}" +
                        "?uploadType=multipart&fields=$FILE_FIELDS"
                )
                .header("Authorization", "Bearer $token")
                .patch(body)
                .build()
        }.use { response ->
            ensureSuccess(response, "Could not update file in Google Drive")
            parseFileEntry(JSONObject(response.body?.string().orEmpty()))
                ?: throw IllegalStateException("Google Drive returned an invalid file record")
        }
    }

    suspend fun deleteFile(preferredEmail: String?, fileId: String) {
        executeWithAuth(preferredEmail) { token ->
            Request.Builder()
                .url("$driveApiBaseUrl/files/${encodeSegment(fileId)}")
                .header("Authorization", "Bearer $token")
                .delete()
                .build()
        }.use { response ->
            if (response.code == 404) return@use
            ensureSuccess(response, "Could not delete file in Google Drive")
        }
    }

    private suspend fun listFilesByQuery(
        preferredEmail: String?,
        query: String
    ): List<DriveFileEntry> {
        val results = mutableListOf<DriveFileEntry>()
        var pageToken: String? = null
        do {
            val url = buildString {
                append("$driveApiBaseUrl/files?q=")
                append(URLEncoder.encode(query, "UTF-8"))
                append("&orderBy=modifiedTime%20desc")
                append("&pageSize=1000")
                append("&fields=")
                append(URLEncoder.encode("nextPageToken,files($FILE_FIELDS)", "UTF-8"))
                if (!pageToken.isNullOrBlank()) {
                    append("&pageToken=")
                    append(URLEncoder.encode(pageToken, "UTF-8"))
                }
            }
            executeWithAuth(preferredEmail) { token ->
                Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()
            }.use { response ->
                ensureSuccess(response, "Could not list Google Drive files")
                val json = JSONObject(response.body?.string().orEmpty())
                val files = json.optJSONArray("files")
                if (files != null) {
                    for (i in 0 until files.length()) {
                        parseFileEntry(files.optJSONObject(i))?.let { entry ->
                            if (!entry.trashed) results.add(entry)
                        }
                    }
                }
                pageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
            }
        } while (pageToken != null)
        return results
    }

    private suspend fun executeWithAuth(
        preferredEmail: String?,
        buildRequest: (accessToken: String) -> Request
    ): okhttp3.Response = withContext(Dispatchers.IO) {
        val initialToken = tokenProvider.getAccessToken(preferredEmail)
        val firstResponse = httpClient.newCall(buildRequest(initialToken)).execute()
        if (firstResponse.code != 401) {
            return@withContext firstResponse
        }
        firstResponse.close()
        val refreshedToken = tokenProvider.invalidateAndRefreshToken(initialToken, preferredEmail)
        httpClient.newCall(buildRequest(refreshedToken)).execute()
    }

    private fun buildMultipartUploadBody(
        metadataJson: String,
        contentMimeType: String,
        bytes: ByteArray
    ): MultipartBody = MultipartBody.Builder()
        .setType("multipart/related".toMediaType())
        .addPart(metadataJson.toRequestBody(JSON_MEDIA_TYPE))
        .addPart(bytes.toRequestBody(contentMimeType.toMediaType()))
        .build()

    private fun ensureSuccess(response: okhttp3.Response, fallbackMessage: String) {
        if (response.isSuccessful) return
        val errorBody = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
        val apiMessage = runCatching {
            JSONObject(errorBody).optJSONObject("error")?.optString("message")
        }.getOrNull()?.takeIf { it.isNotBlank() }
        if (response.code == 401) {
            throw DriveAuthorizationRequiredException(
                pendingIntent = null,
                message = apiMessage ?: "Google Drive session expired. Reconnect Google Drive in Settings."
            )
        }
        val detail = apiMessage ?: "$fallbackMessage (HTTP ${response.code})"
        throw IllegalStateException(detail)
    }

    private fun parseFileEntry(json: JSONObject?): DriveFileEntry? {
        if (json == null) return null
        val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
        val name = json.optString("name").takeIf { it.isNotBlank() } ?: return null
        val mimeType = json.optString("mimeType")
        val modifiedMs = parseRfc3339Millis(json.optString("modifiedTime"))
        val size = json.optString("size").toLongOrNull() ?: json.optLong("size", 0L)
        val sha256 = json.optJSONObject("appProperties")
            ?.optString(PROP_SHA256)
            ?.takeIf { it.isNotBlank() }
        val trashed = json.optBoolean("trashed", false)
        return DriveFileEntry(
            id = id,
            name = name,
            mimeType = mimeType,
            modifiedTimeMs = modifiedMs,
            size = size,
            sha256AppProperty = sha256,
            trashed = trashed
        )
    }

    companion object {
        const val DEFAULT_DRIVE_API_BASE_URL = "https://www.googleapis.com/drive/v3"
        const val DEFAULT_UPLOAD_API_BASE_URL = "https://www.googleapis.com/upload/drive/v3"
        const val DEFAULT_ROOT_FOLDER_NAME = "Nexus Launcher"
        const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
        const val PROP_SHA256 = "sha256"

        private const val FILE_FIELDS = "id,name,mimeType,modifiedTime,size,trashed,appProperties"
        private val JSON_MEDIA_TYPE = "application/json; charset=UTF-8".toMediaType()

        private fun defaultDriveHttpClient(): OkHttpClient = HttpClients.shared.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        private fun escapeDriveQuery(value: String): String =
            value.replace("\\", "\\\\").replace("'", "\\'")

        private fun encodeSegment(value: String): String =
            URLEncoder.encode(value, "UTF-8")

        private val rfc3339Formatters = ThreadLocal.withInitial {
            listOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSSX",
                "yyyy-MM-dd'T'HH:mm:ssX",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'"
            ).map { pattern ->
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
            }
        }

        internal fun parseRfc3339Millis(raw: String): Long {
            if (raw.isBlank()) return 0L
            val formatters = rfc3339Formatters.get() ?: return 0L
            for (formatter in formatters) {
                val parsed = runCatching { formatter.parse(raw)?.time }.getOrNull()
                if (parsed != null) return parsed
            }
            return 0L
        }
    }
}
