package com.example.data.service

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.example.NexusApplication
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import java.io.IOException
import java.security.MessageDigest
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

    data class NeedsIntentResolution(
        val intent: Intent,
        val accountEmail: String? = null,
        val isAccountPicker: Boolean = false
    ) : DriveAuthorizationStep
}

class DriveAuthorizationRequiredException(
    val pendingIntent: PendingIntent? = null,
    val recoveryIntent: Intent? = null,
    message: String = "Google Drive authorization required. Reconnect Google Drive in Settings."
) : IllegalStateException(message)

class DriveOAuthConfigurationException(
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)

interface DriveAccessTokenProvider {
    suspend fun beginInteractiveAuthorization(preferredEmail: String? = null): DriveAuthorizationStep
    suspend fun resolveAuthorizationResult(data: Intent?): DriveAuthorizationStep.Authorized
    suspend fun getAccessToken(preferredEmail: String? = null): String
    suspend fun invalidateAndRefreshToken(invalidToken: String, preferredEmail: String? = null): String
    suspend fun revokeAccess(accountEmail: String?)

    suspend fun beginFallbackAccountAuthorization(preferredEmail: String? = null): DriveAuthorizationStep? = null

    suspend fun resolveFallbackActivityResult(
        resultCode: Int,
        data: Intent?,
        accountEmail: String?,
        isAccountPicker: Boolean
    ): DriveAuthorizationStep? = null

    fun inspectFailedAuthorizationIntent(data: Intent?): Throwable? = null

    fun diagnosticConfigurationMessage(reason: String): String = reason
}

/**
 * Authenticates the user for the least-privilege `https://www.googleapis.com/auth/drive.file`
 * scope using Google Play Services [AuthorizationClient] with a live [Activity] context, and
 * automatically falls back to Android's TV-compatible [AccountManager] + [GoogleAuthUtil] flow
 * when [AuthorizationClient] aborts or is unsupported on the device (common on Android TV).
 */
class PlayServicesDriveAuthManager(
    context: Context,
    private val httpClient: OkHttpClient = defaultAuthHttpClient(),
    private val activityProvider: () -> Activity? = { NexusApplication.currentForegroundActivity() }
) : DriveAccessTokenProvider {

    private val appContext = context.applicationContext

    private fun authorizationClient(): AuthorizationClient =
        activityProvider()?.let { Identity.getAuthorizationClient(it) }
            ?: Identity.getAuthorizationClient(appContext)

    override suspend fun beginInteractiveAuthorization(
        preferredEmail: String?
    ): DriveAuthorizationStep {
        val result = try {
            authorizeSilently()
        } catch (error: Throwable) {
            if (shouldFallbackToAccountAuth(error)) {
                return beginFallbackAccountAuthorization(preferredEmail)
                    ?: throw error
            }
            throw error
        }

        if (result.hasResolution()) {
            val pendingIntent = result.pendingIntent
                ?: return beginFallbackAccountAuthorization(preferredEmail)
                    ?: throw IllegalStateException("Google Drive consent prompt could not be opened.")
            return DriveAuthorizationStep.NeedsUserConsent(pendingIntent)
        }
        val token = result.accessToken?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Google Drive did not return an access token.")
        val account = fetchAccountInfo(token)
            ?: preferredEmail?.let { DriveAuthAccount(email = it) }
        return DriveAuthorizationStep.Authorized(token, account)
    }

    override suspend fun beginFallbackAccountAuthorization(
        preferredEmail: String?
    ): DriveAuthorizationStep? {
        val trimmedEmail = preferredEmail?.trim()?.takeIf { it.isNotBlank() }
        if (trimmedEmail != null) {
            return resolveTokenForEmail(trimmedEmail)
        }
        val pickerIntent = AccountManager.newChooseAccountIntent(
            null,
            null,
            arrayOf(GOOGLE_ACCOUNT_TYPE),
            null,
            null,
            null,
            null
        )
        return DriveAuthorizationStep.NeedsIntentResolution(
            intent = pickerIntent,
            accountEmail = null,
            isAccountPicker = true
        )
    }

    override suspend fun resolveFallbackActivityResult(
        resultCode: Int,
        data: Intent?,
        accountEmail: String?,
        isAccountPicker: Boolean
    ): DriveAuthorizationStep? {
        if (resultCode != Activity.RESULT_OK) {
            return null
        }
        val targetEmail = if (isAccountPicker) {
            data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)?.trim()?.takeIf { it.isNotBlank() }
                ?: return null
        } else {
            accountEmail?.trim()?.takeIf { it.isNotBlank() }
                ?: return null
        }
        return resolveTokenForEmail(targetEmail)
    }

    override suspend fun resolveAuthorizationResult(data: Intent?): DriveAuthorizationStep.Authorized {
        val result = try {
            authorizationClient().getAuthorizationResultFromIntent(data)
        } catch (error: ApiException) {
            throw mapApiException(error, duringInteractiveUi = true)
        }
        val token = result.accessToken?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Google Drive did not return an access token.")
        val account = fetchAccountInfo(token)
        return DriveAuthorizationStep.Authorized(token, account)
    }

    override fun inspectFailedAuthorizationIntent(data: Intent?): Throwable? {
        if (data == null) return null
        return try {
            authorizationClient().getAuthorizationResultFromIntent(data)
            null
        } catch (error: ApiException) {
            mapApiException(error, duringInteractiveUi = false)
        } catch (error: Throwable) {
            error
        }
    }

    override fun diagnosticConfigurationMessage(reason: String): String {
        val sha1 = computeAppSigningSha1(appContext)
        val pkg = appContext.packageName
        return buildString {
            append(reason)
            append(" Verify Google Drive API is enabled and an Android OAuth client exists for ")
            append(pkg)
            if (!sha1.isNullOrBlank()) {
                append(" (SHA-1: ")
                append(sha1)
                append(")")
            }
            append(" in Google Cloud Console, and add your Google account under OAuth Test Users if the app is in Testing mode.")
        }
    }

    override suspend fun getAccessToken(preferredEmail: String?): String {
        val primaryResult = runCatching { authorizeSilently() }
        primaryResult.getOrNull()?.let { result ->
            if (!result.hasResolution()) {
                val token = result.accessToken?.takeIf { it.isNotBlank() }
                if (token != null) return token
            }
        }

        val email = preferredEmail?.trim()?.takeIf { it.isNotBlank() }
        if (email != null) {
            val account = Account(email, GOOGLE_ACCOUNT_TYPE)
            val fallbackResult = withContext(Dispatchers.IO) {
                runCatching {
                    GoogleAuthUtil.getToken(appContext, account, OAUTH2_DRIVE_FILE_SCOPE)
                }
            }
            fallbackResult.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
            val fallbackError = fallbackResult.exceptionOrNull()
            if (fallbackError is UserRecoverableAuthException) {
                throw DriveAuthorizationRequiredException(
                    pendingIntent = primaryResult.getOrNull()?.pendingIntent,
                    recoveryIntent = fallbackError.intent
                )
            }
            if (fallbackError is IOException) {
                throw IllegalStateException("Network error while connecting to Google Drive", fallbackError)
            }
            if (fallbackError is GoogleAuthException) {
                throw DriveOAuthConfigurationException(
                    diagnosticConfigurationMessage("Google Play Services rejected Drive OAuth (${fallbackError.message ?: "auth error"})."),
                    fallbackError
                )
            }
        }

        primaryResult.exceptionOrNull()?.let { throw it }
        throw DriveAuthorizationRequiredException(primaryResult.getOrNull()?.pendingIntent)
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
        val tokensToRevoke = linkedSetOf<String>()
        runCatching { authorizeSilently() }.getOrNull()?.accessToken
            ?.takeIf { it.isNotBlank() }
            ?.let { tokensToRevoke += it }
        val email = accountEmail?.trim()?.takeIf { it.isNotBlank() }
        if (email != null) {
            withContext(Dispatchers.IO) {
                runCatching {
                    GoogleAuthUtil.getToken(appContext, Account(email, GOOGLE_ACCOUNT_TYPE), OAUTH2_DRIVE_FILE_SCOPE)
                }.getOrNull()?.takeIf { it.isNotBlank() }?.let { tokensToRevoke += it }
            }
        }
        if (tokensToRevoke.isEmpty()) return
        withContext(Dispatchers.IO) {
            tokensToRevoke.forEach { token ->
                runCatching { GoogleAuthUtil.clearToken(appContext, token) }
                runCatching {
                    val request = Request.Builder()
                        .url("$REVOKE_URL?token=${java.net.URLEncoder.encode(token, "UTF-8")}")
                        .post("".toRequestBody(null))
                        .build()
                    httpClient.newCall(request).execute().close()
                }
            }
        }
    }

    private suspend fun resolveTokenForEmail(email: String): DriveAuthorizationStep {
        val account = Account(email, GOOGLE_ACCOUNT_TYPE)
        return withContext(Dispatchers.IO) {
            try {
                val token = GoogleAuthUtil.getToken(appContext, account, OAUTH2_DRIVE_FILE_SCOPE)
                if (token.isBlank()) {
                    throw IllegalStateException("Google Drive did not return an access token.")
                }
                val profile = fetchAccountInfo(token)
                DriveAuthorizationStep.Authorized(
                    accessToken = token,
                    account = DriveAuthAccount(
                        email = profile?.email ?: email,
                        displayName = profile?.displayName
                    )
                )
            } catch (recoverable: UserRecoverableAuthException) {
                val intent = recoverable.intent
                    ?: throw DriveOAuthConfigurationException(
                        diagnosticConfigurationMessage("Google Drive consent screen could not be opened."),
                        recoverable
                    )
                DriveAuthorizationStep.NeedsIntentResolution(
                    intent = intent,
                    accountEmail = email,
                    isAccountPicker = false
                )
            } catch (ioError: IOException) {
                throw IllegalStateException(
                    "Network error while connecting to Google Drive",
                    ioError
                )
            } catch (authError: GoogleAuthException) {
                throw DriveOAuthConfigurationException(
                    diagnosticConfigurationMessage(
                        "Google Drive OAuth error (${authError.message ?: authError.javaClass.simpleName})."
                    ),
                    authError
                )
            }
        }
    }

    private suspend fun authorizeSilently(): AuthorizationResult {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
            .build()
        return try {
            authorizationClient().authorize(request).awaitResult()
        } catch (error: ApiException) {
            throw mapApiException(error, duringInteractiveUi = false)
        } catch (cancelled: CancellationException) {
            throw DriveOAuthConfigurationException(
                "Google Play Services aborted Drive authorization before showing consent.",
                cancelled
            )
        }
    }

    private fun shouldFallbackToAccountAuth(error: Throwable): Boolean = when (error) {
        is DriveOAuthConfigurationException -> true
        is CancellationException -> true
        is DriveAuthorizationRequiredException -> error.pendingIntent == null
        is ApiException -> error.statusCode in FALLBACK_ELIGIBLE_STATUS_CODES
        else -> false
    }

    private suspend fun fetchAccountInfo(accessToken: String): DriveAuthAccount? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(DRIVE_ABOUT_URL)
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    val body = response.body?.string()?.takeIf { it.isNotBlank() }
                        ?: return@runCatching null
                    val user = JSONObject(body).optJSONObject("user") ?: return@runCatching null
                    val email = user.optString("emailAddress").takeIf { it.isNotBlank() }
                    val name = user.optString("displayName").takeIf { it.isNotBlank() }
                    if (email == null && name == null) null else DriveAuthAccount(email, name)
                }
            }.getOrNull()
        }

    private fun mapApiException(
        error: ApiException,
        duringInteractiveUi: Boolean
    ): Exception = when (error.statusCode) {
        CommonStatusCodes.CANCELED ->
            if (duringInteractiveUi) {
                CancellationException("Google Drive sign-in cancelled")
            } else {
                // Play Services returns status 16 directly when the consent flow is unsupported
                // (e.g. Android TV) or aborted due to missing Android OAuth / SHA-1 config.
                DriveOAuthConfigurationException(
                    diagnosticConfigurationMessage("Google Play Services aborted Drive authorization (code 16)."),
                    error
                )
            }
        CommonStatusCodes.NETWORK_ERROR ->
            IllegalStateException("Network error while connecting to Google Drive", error)
        CommonStatusCodes.SIGN_IN_REQUIRED ->
            DriveAuthorizationRequiredException(null)
        CommonStatusCodes.DEVELOPER_ERROR ->
            DriveOAuthConfigurationException(
                diagnosticConfigurationMessage("Google Drive OAuth configuration mismatch (code 10 DEVELOPER_ERROR)."),
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
        else -> {
            val detail = error.message?.takeIf { it.isNotBlank() }
                ?: "Google Drive sign-in failed (code ${error.statusCode})"
            if (error.statusCode == SIGN_IN_FAILED_STATUS_CODE || error.statusCode == CommonStatusCodes.INTERNAL_ERROR) {
                DriveOAuthConfigurationException(
                    diagnosticConfigurationMessage("Google Drive OAuth failed (code ${error.statusCode})."),
                    error
                )
            } else {
                IllegalStateException(detail, error)
            }
        }
    }

    companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        private const val OAUTH2_DRIVE_FILE_SCOPE = "oauth2:$DRIVE_FILE_SCOPE"
        private const val GOOGLE_ACCOUNT_TYPE = "com.google"
        private const val DRIVE_ABOUT_URL =
            "https://www.googleapis.com/drive/v3/about?fields=user(displayName,emailAddress)"
        private const val REVOKE_URL = "https://oauth2.googleapis.com/revoke"
        private const val SIGN_IN_FAILED_STATUS_CODE = 12500

        private val FALLBACK_ELIGIBLE_STATUS_CODES = setOf(
            CommonStatusCodes.CANCELED,
            CommonStatusCodes.DEVELOPER_ERROR,
            CommonStatusCodes.INTERNAL_ERROR,
            CommonStatusCodes.SIGN_IN_REQUIRED,
            CommonStatusCodes.API_NOT_CONNECTED,
            SIGN_IN_FAILED_STATUS_CODE,
            12501
        )

        private fun defaultAuthHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        internal fun computeAppSigningSha1(context: Context): String? = runCatching {
            val pm = context.packageManager
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                val signingInfo = info.signingInfo
                if (signingInfo?.hasMultipleSigners() == true) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo?.signingCertificateHistory
                }
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
            }
            val certBytes = signatures?.firstOrNull()?.toByteArray() ?: return@runCatching null
            val digest = MessageDigest.getInstance("SHA-1").digest(certBytes)
            digest.joinToString(":") { b -> "%02X".format(b) }
        }.getOrNull()
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
