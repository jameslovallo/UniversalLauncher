package com.example.data.service

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.media.tv.TvContract
import android.os.Build
import android.os.Process
import com.example.data.model.GameItem
import com.example.data.model.GameType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Imports Moonlight's own game entries instead of asking the user to pin shortcuts by hand.
 *
 * Moonlight publishes game launch data through two interfaces that third-party apps can read:
 *
 *  - The Android TV preview channel. For every game Moonlight has launched it stores a
 *    [TvContract.PreviewPrograms] row whose `COLUMN_INTENT_URI` is the serialized
 *    `ShortcutTrampoline` launch intent and whose `COLUMN_POSTER_ART_URI` points at
 *    Moonlight's exported box-art provider. Reading other apps' rows needs the runtime
 *    `READ_TV_LISTINGS` permission, and the TV provider only exists on Android TV.
 *  - Pinned shortcuts. [LauncherApps] exposes the same launch intent for games the user
 *    pinned from Moonlight's context menu. This is only readable while this app is the
 *    default launcher, but once imported the persisted `intent:` URI keeps working,
 *    because `ShortcutTrampoline` is exported.
 *
 * Shortcut intents come from Moonlight's `ServerHelper.createAppShortcutIntent()` and carry
 * `UUID`, `Name`, `AppId`, `AppName` and `HDR` extras. Any fork that keeps the upstream
 * class names (stock Moonlight, Artemis/Moonlight Noir, the unofficial builds) works.
 */
class MoonlightService(private val context: Context) {

    data class ScanResult(
        val games: List<GameItem> = emptyList(),
        val installed: Boolean = false,
        val shortcutsBlocked: Boolean = false,
        val tvListingsDenied: Boolean = false,
        val tvProviderAvailable: Boolean = false,
        val unsupportedAndroidVersion: Boolean = false
    )

    private data class TvScan(
        val games: List<GameItem> = emptyList(),
        val listingsDenied: Boolean = false,
        val providerAvailable: Boolean = false
    )

    private data class ShortcutScan(
        val games: List<GameItem> = emptyList(),
        val blocked: Boolean = false
    )

    /**
     * Scan both interfaces and merge the results. Games that appear in both are kept once,
     * preferring the entry that resolved box art.
     */
    suspend fun scan(): ScanResult = withContext(Dispatchers.IO) {
        val packages = KNOWN_PACKAGES.filter { isInstalled(it) }
        if (packages.isEmpty()) {
            return@withContext ScanResult(installed = false)
        }

        // Moonlight only creates game shortcuts and TV channel programs on Android 8.0+.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return@withContext ScanResult(installed = true, unsupportedAndroidVersion = true)
        }

        val tv = readTvChannelGames()
        val shortcuts = readShortcutGames(packages)

        val merged = LinkedHashMap<String, GameItem>()
        for (game in tv.games + shortcuts.games) {
            val key = "${game.packageName}|${game.activityName}|${game.gfnCmsId}"
            val existing = merged[key]
            if (existing == null || (existing.posterUrl.isNullOrBlank() && !game.posterUrl.isNullOrBlank())) {
                merged[key] = game
            }
        }

        ScanResult(
            games = merged.values.sortedBy { it.title.lowercase() },
            installed = true,
            shortcutsBlocked = shortcuts.blocked,
            tvListingsDenied = tv.listingsDenied,
            tvProviderAvailable = tv.providerAvailable
        )
    }

    private fun isInstalled(packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    // --- Android TV preview channel -------------------------------------------------------

    private fun readTvChannelGames(): TvScan {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return TvScan()
        }

        val listingsPermissionGranted = context.checkSelfPermission(READ_TV_LISTINGS) ==
            PackageManager.PERMISSION_GRANTED

        val games = mutableListOf<GameItem>()
        var providerAvailable = false
        var denied = false

        try {
            // Query without a selection: without ACCESS_ALL_EPG_DATA (signature|privileged)
            // the provider throws if the caller passes a selection, so filter in code.
            context.contentResolver.query(
                TvContract.PreviewPrograms.CONTENT_URI,
                arrayOf(
                    TvContract.PreviewPrograms.COLUMN_TITLE,
                    TvContract.PreviewPrograms.COLUMN_INTENT_URI,
                    TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI
                ),
                null,
                null,
                null
            )?.use { cursor ->
                providerAvailable = true
                val titleIndex = cursor.getColumnIndex(TvContract.PreviewPrograms.COLUMN_TITLE)
                val intentIndex = cursor.getColumnIndex(TvContract.PreviewPrograms.COLUMN_INTENT_URI)
                val posterIndex = cursor.getColumnIndex(TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI)

                while (cursor.moveToNext()) {
                    val intentUri = cursor.stringAt(intentIndex) ?: continue
                    val intent = runCatching {
                        Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME)
                    }.getOrNull() ?: continue

                    if (!isMoonlightGameIntent(intent)) continue

                    val game = buildGame(
                        intent = intent,
                        intentUri = intentUri,
                        fallbackTitle = cursor.stringAt(titleIndex),
                        posterUri = cursor.stringAt(posterIndex),
                        idPrefix = "moonlight_tv"
                    ) ?: continue
                    games += game
                }
            }
        } catch (_: SecurityException) {
            denied = true
        } catch (_: IllegalArgumentException) {
            // No TvProvider on this device (phones, some Google TV builds).
            providerAvailable = false
        } catch (_: UnsupportedOperationException) {
            providerAvailable = false
        } catch (_: Exception) {
            providerAvailable = false
        }

        return TvScan(
            games = games,
            listingsDenied = denied || (providerAvailable && !listingsPermissionGranted),
            providerAvailable = providerAvailable
        )
    }

    // --- Pinned shortcuts -----------------------------------------------------------------

    private fun readShortcutGames(packages: List<String>): ShortcutScan {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return ShortcutScan()
        }

        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return ShortcutScan()
        // Reading another app's shortcuts requires shortcut-host permission, which an app
        // only holds while it is the default launcher. Surface that instead of crashing.
        if (!launcherApps.hasShortcutHostPermission()) {
            return ShortcutScan(blocked = true)
        }

        val games = mutableListOf<GameItem>()
        var blocked = false

        for (packageName in packages) {
            val query = LauncherApps.ShortcutQuery().apply {
                setPackage(packageName)
                setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
                )
            }

            val shortcuts: List<ShortcutInfo> = try {
                launcherApps.getShortcuts(query, Process.myUserHandle()) ?: emptyList()
            } catch (_: SecurityException) {
                blocked = true
                continue
            } catch (_: Exception) {
                continue
            }

            for (shortcut in shortcuts) {
                val intent = shortcut.intent ?: continue
                if (!isMoonlightGameIntent(intent)) continue

                val game = buildGame(
                    intent = intent,
                    intentUri = intent.toUri(Intent.URI_INTENT_SCHEME),
                    fallbackTitle = shortcut.shortLabel?.toString(),
                    posterUri = null,
                    idPrefix = "moonlight_shortcut"
                ) ?: continue
                games += game
            }
        }

        return ShortcutScan(games = games, blocked = blocked)
    }

    // --- Shared helpers -------------------------------------------------------------------

    private fun isMoonlightGameIntent(intent: Intent): Boolean {
        val component = intent.component ?: return false
        // Forks keep the upstream internal class names even when they change applicationId.
        if (!component.className.startsWith(MOONLIGHT_INTERNAL_PREFIX)) return false
        if (!component.className.endsWith(SHORTCUT_TRAMPOLINE_CLASS)) return false
        val appId = intent.getStringExtra(EXTRA_APP_ID) ?: return false
        return appId.toIntOrNull() != null
    }

    private fun buildGame(
        intent: Intent,
        intentUri: String,
        fallbackTitle: String?,
        posterUri: String?,
        idPrefix: String
    ): GameItem? {
        val packageName = intent.component?.packageName ?: return null
        val appId = intent.getStringExtra(EXTRA_APP_ID) ?: return null
        val uuid = intent.getStringExtra(EXTRA_UUID)
        val title = intent.getStringExtra(EXTRA_APP_NAME) ?: fallbackTitle
        if (title.isNullOrBlank()) return null
        val pcName = intent.getStringExtra(EXTRA_PC_NAME)

        // Moonlight's exported box-art provider: content://poster.<pkg>/boxart/<uuid>/<appId>
        val boxArt = posterUri?.takeIf { it.isNotBlank() }
            ?: boxArtUri(packageName, uuid, appId)

        return GameItem(
            id = "${idPrefix}_${packageName}_${uuid ?: "pc"}_$appId",
            title = title,
            type = GameType.SHORTCUT,
            isGame = true,
            packageName = packageName,
            activityName = uuid,
            launchUrl = intentUri,
            gfnCmsId = appId,
            posterUrl = boxArt,
            iconUrl = boxArt,
            genre = "Moonlight",
            publisher = pcName ?: "Moonlight",
            description = if (pcName != null) {
                "Moonlight game on $pcName"
            } else {
                "Moonlight game ($appId)"
            },
            tags = "Moonlight,Streaming,Game"
        )
    }

    private fun boxArtUri(packageName: String, uuid: String?, appId: String): String? {
        if (uuid.isNullOrBlank()) return null
        return "content://poster.$packageName/boxart/$uuid/$appId"
    }

    private fun android.database.Cursor.stringAt(index: Int): String? {
        if (index < 0 || isNull(index)) return null
        return getString(index)
    }

    companion object {
        /** Stock Moonlight plus known applicationIds (Artemis/Noir, rooted, unofficial, debug). */
        val KNOWN_PACKAGES = listOf(
            "com.limelight",
            "com.limelight.noir",
            "com.limelight.unofficial",
            "com.limelight.noirdebug",
            "com.limelight.root"
        )

        private const val READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS"

        private const val MOONLIGHT_INTERNAL_PREFIX = "com.limelight."
        private const val SHORTCUT_TRAMPOLINE_CLASS = "ShortcutTrampoline"

        private const val EXTRA_UUID = "UUID"
        private const val EXTRA_PC_NAME = "Name"
        private const val EXTRA_APP_ID = "AppId"
        private const val EXTRA_APP_NAME = "AppName"
    }
}
