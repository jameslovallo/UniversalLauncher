package com.example.data.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class DriveAuthAccount(
    val email: String? = null,
    val displayName: String? = null
)

sealed interface DriveAuthorizationStep {
    data class Authorized(
        val accessToken: String,
        val account: DriveAuthAccount? = null
    ) : DriveAuthorizationStep

    data class NeedsUserConsent(
        val pendingIntent: PendingIntent
    ) : DriveAuthorizationStep
}

class DriveAuthorizationRequiredException(
    val pendingIntent: PendingIntent? = null,
    message: String = "Google Drive authorization required. Reconnect Google Drive in Settings."
) : IllegalStateException(message)

interface DriveAccessTokenProvider {
    suspend fun beginInteractiveAuthorization(preferredEmail: String? = null): DriveAuthorizationStep
    suspend fun resolveAuthorizationResult(data: Intent?): DriveAuthorizationStep.Authorized
    suspend fun getAccessToken(preferredEmail: String? = null): String
    suspend fun invalidateAndRefreshToken(invalidToken: String, preferredEmail: String? = null): String
    suspend fun revokeAccess(accountEmail: String?)
}

/**
 * Authenticates the user with Google Play Services [AuthorizationClient] using only the
 * least-privilege `https://www.googleapis.com/auth/drive.file` scope so Nexus can store
 * and sync its own `Nexus Launcher` folder on both Android phones and Android TV without
 * broad Drive permissions or client-secret prompts.
 */
class PlayServicesDriveAuthManager(
    context: Context,
    private val httpClient: OkHttpClient = defaultAuthHttpClient()
) : DriveAccessTokenProvider {

    private val appContext = context.applicationContext
    private val authClient: AuthorizationClient
        get() = Identity.getAuthorizationClient(appContext)

    override suspend fun beginInteractiveAuthorization(
        preferredEmail: String?
    ): DriveAuthorizationStep {
        val result = authorizeSilently()
        if (result.hasResolution()) {
            val pendingIntent = result.pendingIntent
                ?: throw IllegalStateException("Google Drive consent prompt could not be opened.")
            return DriveAuthorizationStep.NeedsUserConsent(pendingIntent)
        }
        val token = result.accessToken?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Google Drive did not return an access token.")
        val account = fetchAccountInfo(token)
        return DriveAuthorizationStep.Authorized(token, account)
    }

    override suspend fun resolveAuthorizationResult(data: Intent?): DriveAuthorizationStep.Authorized {
        val result = try {
            authClient.getAuthorizationResultFromIntent(data)
        } catch (error: ApiException) {
            throw mapApiException(error)
        }
        val token = result.accessToken?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Google Drive did not return an access token.")
        val account = fetchAccountInfo(token)
        return DriveAuthorizationStep.Authorized(token, account)
    }

    override suspend fun getAccessToken(preferredEmail: String?): String {
        val result = authorizeSilently()
        if (result.hasResolution()) {
            throw DriveAuthorizationRequiredException(result.pendingIntent)
        }
        return result.accessToken?.takeIf { it.isNotBlank() }
            ?: throw DriveAuthorizationRequiredException(null)
    }

    override suspend fun invalidateAndRefreshToken(
        invalidToken: String,
        preferredEmail: String?
    ): String {
        withContext(Dispatchers.IO) {
            runCatching { GoogleAuthUtil.clearToken(appContext, invalidToken) }
        }
        return getAccessToken(preferredEmail)
    }

    override suspend fun revokeAccess(accountEmail: String?) {
        val result = runCatching { authorizeSilently() }.getOrNull() ?: return
        val token = result.accessToken?.takeIf { it.isNotBlank() } ?: return
        withContext(Dispatchers.IO) {
            runCatching {
                GoogleAuthUtil.clearToken(appContext, token)
            }
            runCatching {
                val request = Request.Builder()
                    .url("$REVOKE_URL?token=${java.net.URLEncoder.encode(token, "UTF-8")}")
                    .post("".toRequestBody(null))
                    .build()
                httpClient.newCall(request).execute().close()
            }
        }
    }

    private suspend fun authorizeSilently(): AuthorizationResult {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(requestedScopes())
            .build()
        return try {
            authClient.authorize(request).awaitResult()
        } catch (error: ApiException) {
            throw mapApiException(error)
        }
    }

    private fun requestedScopes(): List<Scope> = listOf(
        Scope(DRIVE_FILE_SCOPE),
        Scope(EMAIL_SCOPE),
        Scope(PROFILE_SCOPE)
    )

    private suspend fun fetchAccountInfo(accessToken: String): DriveAuthAccount? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(USERINFO_URL)
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    val body = response.body?.string()?.takeIf { it.isNotBlank() }
                        ?: return@runCatching null
                    val json = JSONObject(body)
                    val email = json.optString("email").takeIf { it.isNotBlank() }
                    val name = json.optString("name").takeIf { it.isNotBlank() }
                    if (email == null && name == null) null else DriveAuthAccount(email, name)
                }
            }.getOrNull()
        }

    private fun mapApiException(error: ApiException): Exception = when (error.statusCode) {
        CommonStatusCodes.CANCELED -> CancellationException("Google Drive sign-in cancelled")
        CommonStatusCodes.NETWORK_ERROR ->
            IllegalStateException("Network error while connecting to Google Drive", error)
        CommonStatusCodes.SIGN_IN_REQUIRED ->
            DriveAuthorizationRequiredException(null)
        CommonStatusCodes.DEVELOPER_ERROR ->
            IllegalStateException(
                "Google Drive OAuth client is not configured for this app build.",
                error
            )
        ConnectionResult.SERVICE_MISSING,
        ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED,
        ConnectionResult.SERVICE_DISABLED,
        CommonStatusCodes.API_NOT_CONNECTED ->
            IllegalStateException(
                "Google Play Services is required to sign in with Google Drive on this device.",
                error
            )
        else -> IllegalStateException(
            error.statusMessage?.takeIf { it.isNotBlank() }
                ?: "Google Drive sign-in failed (code ${error.statusCode})",
            error
        )
    }

    companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        private const val EMAIL_SCOPE = "email"
        private const val PROFILE_SCOPE = "profile"
        private const val USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo"
        private const val REVOKE_URL = "https://oauth2.googleapis.com/revoke"

        private fun defaultAuthHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result ->
        if (cont.isActive) cont.resume(result)
    }
    addOnFailureListener { error ->
        if (cont.isActive) cont.resumeWithException(error)
    }
    addOnCanceledListener {
        if (cont.isActive) cont.cancel()
    }
}
