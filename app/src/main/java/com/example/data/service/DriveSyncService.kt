package com.example.data.service

import android.content.Context
import android.content.Intent
import com.example.data.model.GameItem
import com.example.data.model.GameType
import com.example.data.repository.GameRepository
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class DriveSyncSummary(
    val imported: Int,
    val total: Int
)

/**
 * Backs up and restores the GeForce NOW / Moonlight library with the user's Google
 * Drive app-data folder (`drive.appdata` scope — the "allow this app to access its own
 * data in Google Drive" permission). Nothing is visible in the user's normal Drive.
 *
 * The file is plain JSON keyed by the stable [GameItem] id, so merging is order
 * independent and deletions (tombstones) travel with the data.
 */
class DriveSyncService(
    private val context: Context,
    private val repository: GameRepository
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    fun signedInEmail(): String? = GoogleSignIn.getLastSignedInAccount(context)?.email

    fun signInIntent(): Intent =
        GoogleSignIn.getClient(context, signInOptions()).signInIntent

    /** @return the signed-in email, or null when the user cancelled. */
    fun completeSignIn(data: Intent?): String? {
        val account = GoogleSignIn.getSignedInAccountFromIntent(data)
            .getResult(ApiException::class.java)
            ?: return null
        return account.email ?: "Google account"
    }

    fun signOut() {
        GoogleSignIn.getClient(context, signInOptions()).signOut()
    }

    suspend fun sync(): DriveSyncSummary = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context)
            ?: throw IllegalStateException("Sign in with Google first")
        val email = account.email ?: throw IllegalStateException("Google account has no email")
        val token = GoogleAuthUtil.getToken(
            context,
            android.accounts.Account(email, "com.google"),
            "oauth2:$DRIVE_APP_DATA_SCOPE"
        )

        val remote = downloadRemote(token)
        val localSnapshot = syncableItems(repository.getAllItemsSnapshot())
        val localIds = localSnapshot.mapTo(HashSet()) { it.id }

        var imported = 0
        for (item in remote) {
            if (item.id in localIds) continue
            if (repository.getGameById(item.id) != null) continue
            repository.insertUserGame(item)
            imported++
        }

        val merged = syncableItems(repository.getAllItemsSnapshot())
        uploadRemote(token, merged)
        DriveSyncSummary(imported = imported, total = merged.size)
    }

    private fun signInOptions(): GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DRIVE_APP_DATA_SCOPE))
            .build()

    private fun syncableItems(items: List<GameItem>): List<GameItem> = items.filter {
        it.type == GameType.GEFORCE_NOW ||
            it.type == GameType.MOONLIGHT ||
            (it.type == GameType.SHORTCUT && it.isGame)
    }

    // ---- Drive REST ----------------------------------------------------------------

    private fun downloadRemote(token: String): List<GameItem> {
        val fileId = findRemoteFileId(token) ?: return emptyList()
        val request = authorizedGet(
            "https://www.googleapis.com/drive/v3/files/$fileId?alt=media",
            token
        )
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return emptyList()
            val array = JSONArray(body)
            return buildList {
                for (index in 0 until array.length()) {
                    jsonToItem(array.optJSONObject(index))?.let(::add)
                }
            }
        }
    }

    private fun uploadRemote(token: String, items: List<GameItem>) {
        val payload = JSONArray()
        items.forEach { payload.put(itemToJson(it)) }

        val existingId = findRemoteFileId(token)
        if (existingId == null) {
            val metadata = JSONObject()
                .put("name", FILE_NAME)
                .put("parents", JSONArray().put("appDataFolder"))
            val mediaType = "application/json; charset=UTF-8".toMediaType()
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addPart(metadata.toString().toRequestBody(mediaType))
                .addPart(payload.toString().toRequestBody(mediaType))
                .build()
            val request = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                .header("Authorization", "Bearer $token")
                .post(body)
                .build()
            executeOrThrow(request, "upload")
        } else {
            val mediaType = "application/json; charset=UTF-8".toMediaType()
            val request = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files/$existingId?uploadType=media")
                .header("Authorization", "Bearer $token")
                .patch(payload.toString().toRequestBody(mediaType))
                .build()
            executeOrThrow(request, "update")
        }
    }

    private fun findRemoteFileId(token: String): String? {
        val query = java.net.URLEncoder.encode("name='$FILE_NAME' and trashed=false", "UTF-8")
        val url = "https://www.googleapis.com/drive/v3/files" +
            "?spaces=appDataFolder&fields=files(id)&q=$query"
        val request = authorizedGet(url, token)
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val files = JSONObject(response.body?.string().orEmpty())
                .optJSONArray("files") ?: return null
            if (files.length() == 0) return null
            val id = files.getJSONObject(0).optString("id")
            return id.takeIf { it.isNotBlank() }
        }
    }

    private fun authorizedGet(url: String, token: String): Request =
        Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

    private fun executeOrThrow(request: Request, operation: String) {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val message = response.body?.string().orEmpty().take(160)
                throw IllegalStateException("Drive $operation failed (${response.code}): $message")
            }
        }
    }

    // ---- JSON ----------------------------------------------------------------------

    private fun itemToJson(item: GameItem): JSONObject = JSONObject().apply {
        put("id", item.id)
        put("title", item.title)
        put("type", item.type.name)
        put("isGame", item.isGame)
        item.packageName?.let { put("packageName", it) }
        item.activityName?.let { put("activityName", it) }
        item.launchUrl?.let { put("launchUrl", it) }
        item.gfnCmsId?.let { put("gfnCmsId", it) }
        item.posterUrl?.let { put("posterUrl", it) }
        item.heroBannerUrl?.let { put("heroBannerUrl", it) }
        item.iconUrl?.let { put("iconUrl", it) }
        put("genre", item.genre)
        put("publisher", item.publisher)
        put("description", item.description)
        put("isFavorite", item.isFavorite)
        put("favoriteOrder", item.favoriteOrder)
        put("isHidden", item.isHidden)
        put("isCustomized", item.isCustomized)
        put("isDeleted", item.isDeleted)
    }

    private fun jsonToItem(json: JSONObject?): GameItem? {
        if (json == null) return null
        return runCatching {
            GameItem(
                id = json.getString("id"),
                title = json.getString("title"),
                type = GameType.valueOf(json.getString("type")),
                isGame = json.optBoolean("isGame", true),
                packageName = json.optNullableString("packageName"),
                activityName = json.optNullableString("activityName"),
                launchUrl = json.optNullableString("launchUrl"),
                gfnCmsId = json.optNullableString("gfnCmsId"),
                posterUrl = json.optNullableString("posterUrl"),
                heroBannerUrl = json.optNullableString("heroBannerUrl"),
                iconUrl = json.optNullableString("iconUrl"),
                genre = json.optString("genre", "Game"),
                publisher = json.optString("publisher", ""),
                description = json.optString("description", ""),
                isFavorite = json.optBoolean("isFavorite", false),
                favoriteOrder = json.optInt("favoriteOrder", 0),
                isHidden = json.optBoolean("isHidden", false),
                isCustomized = json.optBoolean("isCustomized", false),
                isDeleted = json.optBoolean("isDeleted", false)
            )
        }.getOrNull()
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    companion object {
        private const val FILE_NAME = "nexus_launcher_library.json"
        private const val DRIVE_APP_DATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    }
}
