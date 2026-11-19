package com.example.data.service

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.app.PendingIntent
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import com.example.BuildConfig
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
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class DriveAuthAccount(
    val email: String? = null,
    val displayName: String? = null
)

data class DriveDeviceCodeSession(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val verificationUrlComplete: String? = null,
    val expiresInSeconds: Int = 1800,
    val intervalSeconds: Int = 5
) {
    val qrTargetUrl: String
        get() = verificationUrlComplete?.takeIf { it.isNotBlank() }
            ?: run {
                val encodedCode = URLEncoder.encode(userCode, "UTF-8")
                if (verificationUrl.contains('?')) {
                    "$verificationUrl&user_code=$encodedCode"
                } else {
                    "$verificationUrl?user_code=$encodedCode"
                }
            }
}

sealed interface DriveDevicePollResult {
    data class Authorized(
        val accessToken: String,
        val account: DriveAuthAccount? = null
    ) : DriveDevicePollResult

    data object Pending : DriveDevicePollResult
    data object SlowDown : DriveDevicePollResult
    data object Denied : DriveDevicePollResult
    data object Expired : DriveDevicePollResult
}

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

    data class NeedsDeviceCodeVerification(
        val session: DriveDeviceCodeSession
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

    suspend fun pollDeviceAuthorization(deviceCode: String): DriveDevicePollResult =
        DriveDevicePollResult.Expired

    fun inspectFailedAuthorizationIntent(data: Intent?): Throwable? = null

    fun diagnosticConfigurationMessage(reason: String): String = reason
}

/**
 * Authenticates the user for the least-privilege `https://www.googleapis.com/auth/drive.file`
 * scope.
 *
 * - On phones/tablets: uses Google Play Services [AuthorizationClient] with a live [Activity]
 *   context and falls back to [AccountManager] + [GoogleAuthUtil] if needed.
 * - On Android TV: Google blocks Play Services' embedded consent webview with Error 400
 *   (`invalid_request`), so TV devices use Google's OAuth 2.0 Device Authorization Grant
 *   (`https://oauth2.googleapis.com/device/code` -> `google.com/device` QR/code) whenever TV
 *   OAuth credentials are configured, avoiding the broken webview consent prompt.
 */
class PlayServicesDriveAuthManager(
    context: Context,
    private val httpClient: OkHttpClient = defaultAuthHttpClient(),
    private val activityProvider: () -> Activity? = { NexusApplication.currentForegroundActivity() },
    private val isTelevisionDevice: Boolean? = null,
    tvClientId: String = BuildConfig.GOOGLE_TV_OAUTH_CLIENT_ID,
    tvClientSecret: String = BuildConfig.GOOGLE_TV_OAUTH_CLIENT_SECRET,
    private val deviceAuthBaseUrl: String = DEFAULT_OAUTH_BASE_URL,
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) : DriveAccessTokenProvider {

    private val appContext = context.applicationContext
    private val resolvedTvClientId = sanitizeSecretValue(tvClientId)
    private val resolvedTvClientSecret = sanitizeSecretValue(tvClientSecret)
    private val devicePrefs: SharedPreferences by lazy {
        appContext.getSharedPreferences(DEVICE_AUTH_PREFS, Context.MODE_PRIVATE)
    }
    private val deviceTokenMutex = Mutex()

    private val hasDeviceFlowCredentials: Boolean
        get() = !resolvedTvClientId.isNullOrBlank() && !resolvedTvClientSecret.isNullOrBlank()

    private fun isTelevision(): Boolean =
        isTelevisionDevice ?: (
            detectTelevisionDevice(appContext) ||
                (activityProvider()?.let { detectTelevisionDevice(it) } == true)
            )

    private fun authorizationClient(): AuthorizationClient =
        activityProvider()?.let { Identity.getAuthorizationClient(it) }
            ?: Identity.getAuthorizationClient(appContext)

    override suspend fun beginInteractiveAuthorization(
        preferredEmail: String?
    ): DriveAuthorizationStep {
        if (hasStoredDeviceRefreshToken() && hasDeviceFlowCredentials) {
            val refreshedOrValid = resolveExistingDeviceAuthStep(preferredEmail)
            if (refreshedOrValid != null) {
                return refreshedOrValid
            }
        }

        if (isTelevision()) {
            return beginTvDeviceOrExplain()
        }

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

    private suspend fun resolveExistingDeviceAuthStep(
        preferredEmail: String?
    ): DriveAuthorizationStep.Authorized? {
        val token = try {
            getDeviceAccessToken(forceRefresh = false)
        } catch (configError: DriveOAuthConfigurationException) {
            throw configError
        } catch (_: DriveAuthorizationRequiredException) {
            return null
        } catch (_: Throwable) {
            null
        }
        if (token.isNullOrBlank()) return null

        val probe = fetchAccountInfoWithStatus(token)
        if (!probe.unauthorized) {
            val account = probe.account
                ?: storedDeviceAccount()
                ?: preferredEmail?.let { DriveAuthAccount(email = it) }
            return DriveAuthorizationStep.Authorized(token, account)
        }

        val freshToken = try {
            getDeviceAccessToken(forceRefresh = true)
        } catch (configError: DriveOAuthConfigurationException) {
            throw configError
        } catch (_: DriveAuthorizationRequiredException) {
            // Refresh token was revoked; fall through cleanly to interactive Device Code Flow.
            return null
        }
        val refreshedProbe = fetchAccountInfoWithStatus(freshToken)
        if (refreshedProbe.unauthorized) {
            clearDeviceTokens()
            return null
        }
        val account = refreshedProbe.account
            ?: storedDeviceAccount()
            ?: preferredEmail?.let { DriveAuthAccount(email = it) }
        return DriveAuthorizationStep.Authorized(freshToken, account)
    }

    private suspend fun beginTvDeviceOrExplain(): DriveAuthorizationStep {
        if (!hasDeviceFlowCredentials) {
            throw DriveOAuthConfigurationException(tvConfigurationMissingMessage())
        }
        val session = startDeviceCodeSession()
        return DriveAuthorizationStep.NeedsDeviceCodeVerification(session)
    }

    override suspend fun pollDeviceAuthorization(deviceCode: String): DriveDevicePollResult =
        withContext(Dispatchers.IO) {
            val clientId = resolvedTvClientId
                ?: throw DriveOAuthConfigurationException(tvConfigurationMissingMessage())
            val clientSecret = resolvedTvClientSecret
                ?: throw DriveOAuthConfigurationException(tvConfigurationMissingMessage())

            val body = FormBody.Builder()
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("device_code", deviceCode)
                .add("grant_type", GRANT_TYPE_DEVICE_CODE)
                .build()
            val request = Request.Builder()
                .url("${deviceAuthBaseUrl.trimEnd('/')}/token")
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(raw) }.getOrNull() ?: JSONObject()
                if (response.isSuccessful) {
                    val accessToken = json.optString("access_token").takeIf { it.isNotBlank() }
                        ?: throw IllegalStateException("Google OAuth did not return an access token.")
                    val refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() }
                    val expiresIn = json.optInt("expires_in", 3600).coerceAtLeast(60)
                    val account = fetchAccountInfo(accessToken)
                    saveDeviceTokens(
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        expiresInSeconds = expiresIn,
                        account = account
                    )
                    return@withContext DriveDevicePollResult.Authorized(accessToken, account)
                }

                when (val error = json.optString("error")) {
                    "authorization_pending" -> DriveDevicePollResult.Pending
                    "slow_down" -> DriveDevicePollResult.SlowDown
                    "access_denied", "authorization_declined" -> DriveDevicePollResult.Denied
                    "expired_token", "invalid_grant" -> DriveDevicePollResult.Expired
                    "invalid_client", "unauthorized_client" -> throw DriveOAuthConfigurationException(
                        "Google rejected the TV OAuth client ($error). Verify GOOGLE_TV_OAUTH_CLIENT_ID " +
                            "and GOOGLE_TV_OAUTH_CLIENT_SECRET come from a 'TVs and Limited Input devices' " +
                            "OAuth client in Google Cloud Console."
                    )
                    "invalid_scope" -> throw DriveOAuthConfigurationException(
                        "Google Drive scope was rejected for TV device sign-in. Ensure Google Drive API " +
                            "is enabled and drive.file is configured on your OAuth consent screen."
                    )
                    else -> {
                        val description = json.optString("error_description").takeIf { it.isNotBlank() }
                            ?: error.takeIf { it.isNotBlank() }
                            ?: "HTTP ${response.code}"
                        throw IllegalStateException("Google Drive sign-in failed: $description")
                    }
                }
            }
        }

    private suspend fun startDeviceCodeSession(): DriveDeviceCodeSession = withContext(Dispatchers.IO) {
        val clientId = resolvedTvClientId
            ?: throw DriveOAuthConfigurationException(tvConfigurationMissingMessage())
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("scope", DRIVE_FILE_SCOPE)
            .build()
        val request = Request.Builder()
            .url("${deviceAuthBaseUrl.trimEnd('/')}/device/code")
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull() ?: JSONObject()
            if (!response.isSuccessful) {
                val err = json.optString("error")
                val desc = json.optString("error_description").takeIf { it.isNotBlank() } ?: err
                if (err == "invalid_client" || err == "unauthorized_client" || response.code in 400..401) {
                    throw DriveOAuthConfigurationException(
                        "Google rejected the TV OAuth client (${desc.ifBlank { "HTTP ${response.code}" }}). " +
                            "Ensure GOOGLE_TV_OAUTH_CLIENT_ID is an OAuth client of type 'TVs and Limited Input devices' " +
                            "in Google Cloud Console."
                    )
                }
                throw IllegalStateException(
                    "Unable to start Google Drive device sign-in (${desc.ifBlank { "HTTP ${response.code}" }})."
                )
            }
            val deviceCode = json.optString("device_code").takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Google device sign-in response omitted device_code.")
            val userCode = json.optString("user_code").takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Google device sign-in response omitted user_code.")
            val verificationUrl = json.optString("verification_url").takeIf { it.isNotBlank() }
                ?: json.optString("verification_uri").takeIf { it.isNotBlank() }
                ?: DEFAULT_VERIFICATION_URL
            val verificationUrlComplete = json.optString("verification_url_complete").takeIf { it.isNotBlank() }
                ?: json.optString("verification_uri_complete").takeIf { it.isNotBlank() }
            val expiresIn = json.optInt("expires_in", 1800).coerceAtLeast(60)
            val interval = json.optInt("interval", 5).coerceAtLeast(5)
            DriveDeviceCodeSession(
                deviceCode = deviceCode,
                userCode = userCode,
                verificationUrl = verificationUrl,
                verificationUrlComplete = verificationUrlComplete,
                expiresInSeconds = expiresIn,
                intervalSeconds = interval
            )
        }
    }

    override suspend fun beginFallbackAccountAuthorization(
        preferredEmail: String?
    ): DriveAuthorizationStep? {
        if (isTelevision()) {
            return beginTvDeviceOrExplain()
        }
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

    private fun tvConfigurationMissingMessage(): String =
        "Android TV requires Google's QR/Device Sign-In flow (Google blocks TV Play Services consent webviews with Error 400). " +
            "In Google Cloud Console (project gen-lang-client-0969064187), create an OAuth 2.0 Client ID of type " +
            "'TVs and Limited Input devices' and set GOOGLE_TV_OAUTH_CLIENT_ID and GOOGLE_TV_OAUTH_CLIENT_SECRET " +
            "in AI Studio Secrets (or GitHub Actions Secrets when building via CI)."

    override suspend fun getAccessToken(preferredEmail: String?): String {
        if (hasStoredDeviceRefreshToken() && hasDeviceFlowCredentials) {
            return getDeviceAccessToken(forceRefresh = false)
        }

        if (isTelevision()) {
            throw DriveAuthorizationRequiredException(
                message = if (hasDeviceFlowCredentials) {
                    "Google Drive authorization required. Reconnect Google Drive in Settings."
                } else {
                    tvConfigurationMissingMessage()
                }
            )
        }

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
        if (hasStoredDeviceRefreshToken() && hasDeviceFlowCredentials) {
            return getDeviceAccessToken(forceRefresh = true)
        }
        if (isTelevision()) {
            throw DriveAuthorizationRequiredException(
                message = if (hasDeviceFlowCredentials) {
                    "Google Drive authorization required. Reconnect Google Drive in Settings."
                } else {
                    tvConfigurationMissingMessage()
                }
            )
        }
        withContext(Dispatchers.IO) {
            runCatching { GoogleAuthUtil.clearToken(appContext, invalidToken) }
        }
        return getAccessToken(preferredEmail)
    }

    override suspend fun revokeAccess(accountEmail: String?) {
        val tokensToRevoke = linkedSetOf<String>()
        devicePrefs.getString(KEY_DEVICE_REFRESH_TOKEN, null)?.takeIf { it.isNotBlank() }?.let {
            tokensToRevoke += it
        }
        devicePrefs.getString(KEY_DEVICE_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() }?.let {
            tokensToRevoke += it
        }
        clearDeviceTokens()

        if (!isTelevision()) {
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
        }
        if (tokensToRevoke.isEmpty()) return
        withContext(Dispatchers.IO) {
            tokensToRevoke.forEach { token ->
                runCatching { GoogleAuthUtil.clearToken(appContext, token) }
                runCatching {
                    val request = Request.Builder()
                        .url("${deviceAuthBaseUrl.trimEnd('/')}/revoke?token=${URLEncoder.encode(token, "UTF-8")}")
                        .post("".toRequestBody(null))
                        .build()
                    httpClient.newCall(request).execute().close()
                }
            }
        }
    }

    private fun hasStoredDeviceRefreshToken(): Boolean =
        !devicePrefs.getString(KEY_DEVICE_REFRESH_TOKEN, null).isNullOrBlank()

    private fun storedDeviceAccount(): DriveAuthAccount? {
        val email = devicePrefs.getString(KEY_DEVICE_ACCOUNT_EMAIL, null)?.takeIf { it.isNotBlank() }
        val name = devicePrefs.getString(KEY_DEVICE_ACCOUNT_NAME, null)?.takeIf { it.isNotBlank() }
        return if (email == null && name == null) null else DriveAuthAccount(email, name)
    }

    private fun saveDeviceTokens(
        accessToken: String,
        refreshToken: String?,
        expiresInSeconds: Int,
        account: DriveAuthAccount?
    ) {
        val expiresAtMs = clockMillis() + (expiresInSeconds * 1000L)
        devicePrefs.edit().apply {
            putString(KEY_DEVICE_ACCESS_TOKEN, accessToken)
            putLong(KEY_DEVICE_EXPIRES_AT_MS, expiresAtMs)
            if (!refreshToken.isNullOrBlank()) {
                putString(KEY_DEVICE_REFRESH_TOKEN, refreshToken)
            }
            if (account != null) {
                putString(KEY_DEVICE_ACCOUNT_EMAIL, account.email)
                putString(KEY_DEVICE_ACCOUNT_NAME, account.displayName)
            }
        }.apply()
    }

    private fun clearDeviceTokens() {
        devicePrefs.edit()
            .remove(KEY_DEVICE_REFRESH_TOKEN)
            .remove(KEY_DEVICE_ACCESS_TOKEN)
            .remove(KEY_DEVICE_EXPIRES_AT_MS)
            .remove(KEY_DEVICE_ACCOUNT_EMAIL)
            .remove(KEY_DEVICE_ACCOUNT_NAME)
            .apply()
    }

    private suspend fun getDeviceAccessToken(forceRefresh: Boolean): String =
        deviceTokenMutex.withLock {
            if (!forceRefresh) {
                val cached = devicePrefs.getString(KEY_DEVICE_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() }
                val expiresAt = devicePrefs.getLong(KEY_DEVICE_EXPIRES_AT_MS, 0L)
                if (cached != null && clockMillis() + TOKEN_EXPIRY_LEEWAY_MS < expiresAt) {
                    return@withLock cached
                }
            }

            val refreshToken = devicePrefs.getString(KEY_DEVICE_REFRESH_TOKEN, null)?.takeIf { it.isNotBlank() }
                ?: throw DriveAuthorizationRequiredException()
            val clientId = resolvedTvClientId
                ?: throw DriveOAuthConfigurationException(tvConfigurationMissingMessage())
            val clientSecret = resolvedTvClientSecret
                ?: throw DriveOAuthConfigurationException(tvConfigurationMissingMessage())

            withContext(Dispatchers.IO) {
                val body = FormBody.Builder()
                    .add("client_id", clientId)
                    .add("client_secret", clientSecret)
                    .add("refresh_token", refreshToken)
                    .add("grant_type", "refresh_token")
                    .build()
                val request = Request.Builder()
                    .url("${deviceAuthBaseUrl.trimEnd('/')}/token")
                    .post(body)
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    val json = runCatching { JSONObject(raw) }.getOrNull() ?: JSONObject()
                    if (response.isSuccessful) {
                        val freshToken = json.optString("access_token").takeIf { it.isNotBlank() }
                            ?: throw IllegalStateException("Google OAuth refresh did not return an access token.")
                        val newRefreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() }
                        val expiresIn = json.optInt("expires_in", 3600).coerceAtLeast(60)
                        saveDeviceTokens(
                            accessToken = freshToken,
                            refreshToken = newRefreshToken ?: refreshToken,
                            expiresInSeconds = expiresIn,
                            account = null
                        )
                        return@withContext freshToken
                    }
                    val error = json.optString("error")
                    if (error == "invalid_grant") {
                        clearDeviceTokens()
                        throw DriveAuthorizationRequiredException(
                            message = "Google Drive access expired or was revoked. Reconnect Google Drive in Settings."
                        )
                    }
                    val description = json.optString("error_description").takeIf { it.isNotBlank() }
                        ?: error.takeIf { it.isNotBlank() }
                        ?: "HTTP ${response.code}"
                    throw IllegalStateException("Google Drive token refresh failed: $description")
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

    private data class AccountProbeResult(
        val account: DriveAuthAccount?,
        val unauthorized: Boolean
    )

    private suspend fun fetchAccountInfo(accessToken: String): DriveAuthAccount? =
        fetchAccountInfoWithStatus(accessToken).account

    private suspend fun fetchAccountInfoWithStatus(accessToken: String): AccountProbeResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(DRIVE_ABOUT_URL)
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.code == 401) {
                        return@runCatching AccountProbeResult(account = null, unauthorized = true)
                    }
                    if (!response.isSuccessful) {
                        return@runCatching AccountProbeResult(account = null, unauthorized = false)
                    }
                    val body = response.body?.string()?.takeIf { it.isNotBlank() }
                        ?: return@runCatching AccountProbeResult(account = null, unauthorized = false)
                    val user = JSONObject(body).optJSONObject("user")
                        ?: return@runCatching AccountProbeResult(account = null, unauthorized = false)
                    val email = user.optString("emailAddress").takeIf { it.isNotBlank() }
                    val name = user.optString("displayName").takeIf { it.isNotBlank() }
                    val account = if (email == null && name == null) null else DriveAuthAccount(email, name)
                    AccountProbeResult(account = account, unauthorized = false)
                }
            }.getOrDefault(AccountProbeResult(account = null, unauthorized = false))
        }

    private fun mapApiException(
        error: ApiException,
        duringInteractiveUi: Boolean
    ): Exception = when (error.statusCode) {
        CommonStatusCodes.CANCELED ->
            if (duringInteractiveUi) {
                CancellationException("Google Drive sign-in cancelled")
            } else {
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
        private const val DEFAULT_OAUTH_BASE_URL = "https://oauth2.googleapis.com"
        private const val DEFAULT_VERIFICATION_URL = "https://www.google.com/device"
        private const val GRANT_TYPE_DEVICE_CODE = "urn:ietf:params:oauth:grant-type:device_code"
        private const val SIGN_IN_FAILED_STATUS_CODE = 12500
        private const val TOKEN_EXPIRY_LEEWAY_MS = 60_000L

        private const val DEVICE_AUTH_PREFS = "nexus_drive_device_auth"
        private const val KEY_DEVICE_REFRESH_TOKEN = "refresh_token"
        private const val KEY_DEVICE_ACCESS_TOKEN = "access_token"
        private const val KEY_DEVICE_EXPIRES_AT_MS = "expires_at_ms"
        private const val KEY_DEVICE_ACCOUNT_EMAIL = "account_email"
        private const val KEY_DEVICE_ACCOUNT_NAME = "account_name"

        private val FALLBACK_ELIGIBLE_STATUS_CODES = setOf(
            CommonStatusCodes.CANCELED,
            CommonStatusCodes.DEVELOPER_ERROR,
            CommonStatusCodes.INTERNAL_ERROR,
            CommonStatusCodes.SIGN_IN_REQUIRED,
            CommonStatusCodes.API_NOT_CONNECTED,
            SIGN_IN_FAILED_STATUS_CODE,
            12501
        )

        private fun defaultAuthHttpClient(): OkHttpClient = HttpClients.shared.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        internal fun sanitizeSecretValue(raw: String?): String? {
            var trimmed = raw?.trim().orEmpty()
            if (trimmed.length >= 2 &&
                ((trimmed.startsWith('"') && trimmed.endsWith('"')) ||
                    (trimmed.startsWith('\'') && trimmed.endsWith('\'')))
            ) {
                trimmed = trimmed.substring(1, trimmed.length - 1).trim()
            }
            if (trimmed.isBlank()) return null
            if (trimmed.startsWith("YOUR_", ignoreCase = true)) return null
            if (trimmed.startsWith("MY_", ignoreCase = true)) return null
            if (trimmed.equals("null", ignoreCase = true)) return null
            return trimmed
        }

        internal fun detectTelevisionDevice(context: Context): Boolean {
            val isConfigTv = (context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
                Configuration.UI_MODE_TYPE_TELEVISION
            val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
            val isTvUiMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
            val hasLeanback = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
            @Suppress("DEPRECATION")
            val hasLegacyTelevision = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
            return isConfigTv || isTvUiMode || hasLeanback || hasLegacyTelevision
        }

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
