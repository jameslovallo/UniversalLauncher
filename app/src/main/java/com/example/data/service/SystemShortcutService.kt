package com.example.data.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Process
import androidx.annotation.RequiresApi
import androidx.core.content.res.ResourcesCompat
import com.example.data.model.GameItem
import com.example.data.model.GameType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale

enum class SystemShortcutKind {
    /** App shortcut published via ShortcutManager / XML and hosted via LauncherApps. */
    LAUNCHER_SHORTCUT,

    /** Activity declaring Intent.ACTION_CREATE_SHORTCUT that returns a configured shortcut. */
    CREATE_SHORTCUT_ACTIVITY
}

data class SystemShortcutEntry(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val packageName: String,
    val appLabel: String,
    val appIconUrl: String? = null,
    val kind: SystemShortcutKind,
    val shortcutId: String? = null,
    val activityName: String? = null,
    val launchIntentUri: String? = null,
    val isGameApp: Boolean = false
)

data class SystemShortcutAppGroup(
    val packageName: String,
    val appLabel: String,
    val appIconUrl: String? = null,
    val shortcuts: List<SystemShortcutEntry>
)

data class SystemShortcutCatalog(
    val appGroups: List<SystemShortcutAppGroup>,
    val allShortcuts: List<SystemShortcutEntry>,
    val hasShortcutHostPermission: Boolean
)

/**
 * Discovers system and per-app shortcuts that can be added from Discover -> System Shortcut.
 *
 * Combines two standard Android shortcut sources without requiring `QUERY_ALL_PACKAGES`:
 * 1. [LauncherApps.getShortcuts] (static manifest, dynamic, cached, and pinned shortcuts)
 *    when Nexus holds shortcut host permission (default Home app).
 * 2. [Intent.ACTION_CREATE_SHORTCUT] activities (e.g., Settings shortcuts, Contacts, Drive),
 *    which work on any device regardless of default launcher status.
 */
object SystemShortcutService {
    const val SYSTEM_SHORTCUT_ID_PREFIX = "sys_shortcut_"
    const val CREATE_SHORTCUT_ID_PREFIX = "create_shortcut_"

    private const val SHORTCUT_PREVIEW_DIR = "shortcut_previews"
    private const val SHORTCUT_ICON_DIR = "shortcuts"

    suspend fun loadCatalog(context: Context): SystemShortcutCatalog = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val selfPkg = context.packageName
        val iconCache = AppIconStore.snapshot(context)
        val appMetaCache = HashMap<String, PackageMeta>()

        suspend fun metaFor(pkg: String): PackageMeta {
            appMetaCache[pkg]?.let { return it }
            val appInfo = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(pkg, 0)
                }
            }.getOrNull()
            val label = appInfo?.let {
                runCatching { pm.getApplicationLabel(it).toString().trim() }.getOrNull()
            }?.takeIf { it.isNotBlank() } ?: pkg
            val versionStamp = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0L)).lastUpdateTime
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0).lastUpdateTime
                }
            }.getOrDefault(0L)
            val iconUrl = AppIconStore.iconUrl(context, pkg, versionStamp, iconCache)
            val isGame = if (appInfo != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appInfo.category == ApplicationInfo.CATEGORY_GAME
                } else {
                    @Suppress("DEPRECATION")
                    (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
                }
            } else {
                false
            }
            return PackageMeta(label, iconUrl, isGame).also { appMetaCache[pkg] = it }
        }

        val collected = LinkedHashMap<String, SystemShortcutEntry>()
        var hasHostPermission = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            val launcherApps = context.getSystemService(LauncherApps::class.java)
            hasHostPermission = launcherApps?.hasShortcutHostPermission() == true
            if (launcherApps != null && hasHostPermission) {
                val shortcuts = queryLauncherShortcuts(launcherApps)
                for (info in shortcuts) {
                    val pkg = info.`package` ?: continue
                    val shortcutId = info.id
                    if (pkg == selfPkg || shortcutId.isBlank() || !info.isEnabled) continue
                    val key = "launcher:$pkg:$shortcutId"
                    if (key in collected) continue
                    val meta = metaFor(pkg)
                    val shortTitle = info.shortLabel?.toString()?.trim()?.takeIf { it.isNotBlank() }
                    val longTitle = info.longLabel?.toString()?.trim()?.takeIf { it.isNotBlank() }
                    val title = shortTitle ?: longTitle ?: shortcutId
                    val subtitle = longTitle?.takeIf { !it.equals(title, ignoreCase = true) }
                    val intentUri = runCatching {
                        info.intent?.toUri(Intent.URI_INTENT_SCHEME)
                    }.getOrNull()
                    collected[key] = SystemShortcutEntry(
                        id = key,
                        title = title,
                        subtitle = subtitle,
                        packageName = pkg,
                        appLabel = meta.label,
                        appIconUrl = meta.iconUrl,
                        kind = SystemShortcutKind.LAUNCHER_SHORTCUT,
                        shortcutId = shortcutId,
                        launchIntentUri = intentUri,
                        isGameApp = meta.isGame
                    )
                }
            }
        }

        val createShortcutActivities = queryCreateShortcutActivities(pm)
        for (resolveInfo in createShortcutActivities) {
            val actInfo = resolveInfo.activityInfo ?: continue
            val pkg = actInfo.packageName ?: continue
            val actName = actInfo.name ?: continue
            if (pkg == selfPkg || !actInfo.exported) continue
            val key = "create:$pkg:$actName"
            if (key in collected) continue
            val meta = metaFor(pkg)
            val resolvedLabel = runCatching {
                resolveInfo.loadLabel(pm).toString().trim()
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: meta.label
            val subtitle = if (resolvedLabel.equals(meta.label, ignoreCase = true)) {
                "Configure shortcut"
            } else {
                "Configure in ${meta.label}"
            }
            collected[key] = SystemShortcutEntry(
                id = key,
                title = resolvedLabel,
                subtitle = subtitle,
                packageName = pkg,
                appLabel = meta.label,
                appIconUrl = meta.iconUrl,
                kind = SystemShortcutKind.CREATE_SHORTCUT_ACTIVITY,
                activityName = actName,
                isGameApp = meta.isGame
            )
        }

        buildCatalogFromEntries(
            entries = collected.values.toList(),
            hasShortcutHostPermission = hasHostPermission
        )
    }

    internal fun buildCatalogFromEntries(
        entries: List<SystemShortcutEntry>,
        hasShortcutHostPermission: Boolean
    ): SystemShortcutCatalog {
        val grouped = entries.groupBy { it.packageName }
        val appGroups = grouped.values.map { groupEntries ->
            val first = groupEntries.first()
            val sortedShortcuts = groupEntries.sortedWith(
                compareBy<SystemShortcutEntry> { it.title.lowercase(Locale.ROOT) }
                    .thenBy { it.id }
            )
            SystemShortcutAppGroup(
                packageName = first.packageName,
                appLabel = first.appLabel,
                appIconUrl = first.appIconUrl,
                shortcuts = sortedShortcuts
            )
        }.sortedWith(
            compareBy<SystemShortcutAppGroup> { it.appLabel.lowercase(Locale.ROOT) }
                .thenBy { it.packageName }
        )
        val allSorted = entries.sortedWith(
            compareBy<SystemShortcutEntry> { it.title.lowercase(Locale.ROOT) }
                .thenBy { it.appLabel.lowercase(Locale.ROOT) }
                .thenBy { it.id }
        )
        return SystemShortcutCatalog(
            appGroups = appGroups,
            allShortcuts = allSorted,
            hasShortcutHostPermission = hasShortcutHostPermission
        )
    }

    internal fun filterShortcuts(
        catalog: SystemShortcutCatalog,
        query: String
    ): List<SystemShortcutEntry> {
        val trimmed = query.trim().lowercase(Locale.ROOT)
        if (trimmed.isBlank()) return catalog.allShortcuts
        val tokens = trimmed.split(WHITESPACE_REGEX).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return catalog.allShortcuts

        return catalog.allShortcuts
            .mapNotNull { entry ->
                val titleLower = entry.title.lowercase(Locale.ROOT)
                val subtitleLower = entry.subtitle.orEmpty().lowercase(Locale.ROOT)
                val appLower = entry.appLabel.lowercase(Locale.ROOT)
                val pkgLower = entry.packageName.lowercase(Locale.ROOT)
                val matchesAllTokens = tokens.all { token ->
                    titleLower.contains(token) ||
                        subtitleLower.contains(token) ||
                        appLower.contains(token) ||
                        pkgLower.contains(token)
                }
                if (!matchesAllTokens) return@mapNotNull null

                val rank = when {
                    titleLower.startsWith(trimmed) -> 0
                    titleLower.contains(trimmed) -> 1
                    appLower.startsWith(trimmed) -> 2
                    appLower.contains(trimmed) -> 3
                    else -> 4
                }
                rank to entry
            }
            .sortedWith(
                compareBy<Pair<Int, SystemShortcutEntry>> { it.first }
                    .thenBy { it.second.title.lowercase(Locale.ROOT) }
                    .thenBy { it.second.appLabel.lowercase(Locale.ROOT) }
            )
            .map { it.second }
    }

    suspend fun resolvePreviewIconUrl(
        context: Context,
        entry: SystemShortcutEntry
    ): String? = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, SHORTCUT_PREVIEW_DIR).apply { mkdirs() }
        val fileName = "${sanitize(entry.id)}.png"
        val file = File(dir, fileName)
        if (file.exists() && file.length() > 0L) {
            return@withContext Uri.fromFile(file).toString()
        }

        val drawable: Drawable? = when (entry.kind) {
            SystemShortcutKind.LAUNCHER_SHORTCUT -> {
                val shortcutId = entry.shortcutId
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 && !shortcutId.isNullOrBlank()) {
                    loadLauncherShortcutDrawable(context, entry.packageName, shortcutId)
                } else {
                    null
                }
            }
            SystemShortcutKind.CREATE_SHORTCUT_ACTIVITY -> {
                val actName = entry.activityName
                if (!actName.isNullOrBlank()) {
                    runCatching {
                        context.packageManager.getActivityIcon(ComponentName(entry.packageName, actName))
                    }.getOrNull()
                } else {
                    null
                }
            }
        }

        if (drawable != null && saveDrawableToFile(drawable, file)) {
            Uri.fromFile(file).toString()
        } else {
            entry.appIconUrl
        }
    }

    suspend fun createGameItemFromLauncherShortcut(
        context: Context,
        entry: SystemShortcutEntry
    ): GameItem = withContext(Dispatchers.IO) {
        val shortcutId = entry.shortcutId ?: entry.id
        val iconFile = File(
            File(context.filesDir, SHORTCUT_ICON_DIR).apply { mkdirs() },
            "sys_${sanitize(entry.packageName)}_${sanitize(shortcutId)}.png"
        )
        val savedIconUrl = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            val drawable = loadLauncherShortcutDrawable(context, entry.packageName, shortcutId)
            if (drawable != null && saveDrawableToFile(drawable, iconFile)) {
                Uri.fromFile(iconFile).toString()
            } else {
                entry.appIconUrl
            }
        } else {
            entry.appIconUrl
        }

        val isGame = entry.packageName in MOONLIGHT_PACKAGES || entry.isGameApp
        GameItem(
            id = "$SYSTEM_SHORTCUT_ID_PREFIX${sanitize(entry.packageName)}_${sanitize(shortcutId)}",
            title = entry.title,
            type = GameType.SHORTCUT,
            isGame = isGame,
            packageName = entry.packageName,
            activityName = shortcutId,
            launchUrl = entry.launchIntentUri,
            iconUrl = savedIconUrl,
            isPinned = false,
            genre = if (isGame) "Game Shortcut" else "System Shortcut",
            publisher = entry.appLabel,
            description = "Shortcut from ${entry.appLabel}",
            tags = if (isGame) "Shortcut,Game,System" else "Shortcut,App,System"
        )
    }

    suspend fun createGameItemFromCreateShortcutResult(
        context: Context,
        providerEntry: SystemShortcutEntry,
        resultIntent: Intent
    ): GameItem? = withContext(Dispatchers.IO) {
        val targetIntent = extractShortcutIntent(resultIntent) ?: return@withContext null
        val launchUri = runCatching { targetIntent.toUri(Intent.URI_INTENT_SCHEME) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return@withContext null

        val title = resultIntent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: providerEntry.title

        val targetPackage = targetIntent.`package`
            ?.takeIf { it.isNotBlank() }
            ?: targetIntent.component?.packageName?.takeIf { it.isNotBlank() }
            ?: providerEntry.packageName

        val itemHash = sha256Short("$targetPackage|$launchUri|$title")
        val iconFile = File(
            File(context.filesDir, SHORTCUT_ICON_DIR).apply { mkdirs() },
            "create_$itemHash.png"
        )
        val savedIconUrl = saveCreateShortcutIcon(context, resultIntent, iconFile)
            ?: providerEntry.appIconUrl

        val isGameApp = if (targetPackage == providerEntry.packageName) {
            providerEntry.isGameApp
        } else {
            isPackageGameApp(context.packageManager, targetPackage)
        }
        val isGame = targetPackage in MOONLIGHT_PACKAGES || isGameApp

        GameItem(
            id = "$CREATE_SHORTCUT_ID_PREFIX$itemHash",
            title = title,
            type = GameType.SHORTCUT,
            isGame = isGame,
            packageName = targetPackage,
            activityName = null,
            launchUrl = launchUri,
            iconUrl = savedIconUrl,
            isPinned = false,
            genre = if (isGame) "Game Shortcut" else "System Shortcut",
            publisher = providerEntry.appLabel,
            description = "System shortcut from ${providerEntry.appLabel}",
            tags = if (isGame) "Shortcut,Game,System" else "Shortcut,App,System"
        )
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun queryLauncherShortcuts(launcherApps: LauncherApps): List<ShortcutInfo> {
        var flags = LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
            LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            flags = flags or LauncherApps.ShortcutQuery.FLAG_MATCH_CACHED
        }
        val query = LauncherApps.ShortcutQuery().setQueryFlags(flags)
        return runCatching {
            launcherApps.getShortcuts(query, Process.myUserHandle()).orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun queryCreateShortcutActivities(pm: PackageManager): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_CREATE_SHORTCUT)
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }
        }.getOrDefault(emptyList())
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun loadLauncherShortcutDrawable(
        context: Context,
        packageName: String,
        shortcutId: String
    ): Drawable? = runCatching {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return null
        if (!launcherApps.hasShortcutHostPermission()) return null
        var flags = LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
            LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            flags = flags or LauncherApps.ShortcutQuery.FLAG_MATCH_CACHED
        }
        val query = LauncherApps.ShortcutQuery()
            .setPackage(packageName)
            .setShortcutIds(listOf(shortcutId))
            .setQueryFlags(flags)
        val info = launcherApps.getShortcuts(query, Process.myUserHandle())?.firstOrNull()
            ?: return null
        launcherApps.getShortcutIconDrawable(info, context.resources.displayMetrics.densityDpi)
    }.getOrNull()

    private fun saveCreateShortcutIcon(
        context: Context,
        resultIntent: Intent,
        targetFile: File
    ): String? {
        val bitmap = extractShortcutBitmap(resultIntent)
        if (bitmap != null && saveBitmapToFile(bitmap, targetFile)) {
            return Uri.fromFile(targetFile).toString()
        }
        val iconRes = extractShortcutIconResource(resultIntent) ?: return null
        val drawable = runCatching {
            val res = context.packageManager.getResourcesForApplication(iconRes.packageName)
            val resId = res.getIdentifier(iconRes.resourceName, null, null)
            if (resId != 0) ResourcesCompat.getDrawable(res, resId, null) else null
        }.getOrNull() ?: return null
        return if (saveDrawableToFile(drawable, targetFile)) {
            Uri.fromFile(targetFile).toString()
        } else {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun extractShortcutIntent(resultIntent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            resultIntent.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT, Intent::class.java)
        } else {
            resultIntent.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT)
        }

    @Suppress("DEPRECATION")
    private fun extractShortcutBitmap(resultIntent: Intent): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            resultIntent.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON, Bitmap::class.java)
        } else {
            resultIntent.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON)
        }

    @Suppress("DEPRECATION")
    private fun extractShortcutIconResource(resultIntent: Intent): Intent.ShortcutIconResource? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            resultIntent.getParcelableExtra(
                Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                Intent.ShortcutIconResource::class.java
            )
        } else {
            resultIntent.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE)
        }

    private fun isPackageGameApp(pm: PackageManager, packageName: String): Boolean = runCatching {
        val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(packageName, 0)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appInfo.category == ApplicationInfo.CATEGORY_GAME
        } else {
            @Suppress("DEPRECATION")
            (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
        }
    }.getOrDefault(false)

    private fun saveDrawableToFile(drawable: Drawable, file: File): Boolean = runCatching {
        val size = maxOf(192, maxOf(drawable.intrinsicWidth, drawable.intrinsicHeight))
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        saveBitmapToFile(bitmap, file).also {
            bitmap.recycle()
        }
    }.getOrDefault(false)

    private fun saveBitmapToFile(bitmap: Bitmap, file: File): Boolean = runCatching {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        file.exists() && file.length() > 0L
    }.getOrDefault(false)

    private fun sanitize(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)

    private fun sha256Short(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    private data class PackageMeta(
        val label: String,
        val iconUrl: String?,
        val isGame: Boolean
    )

    private val WHITESPACE_REGEX = Regex("\\s+")
}
