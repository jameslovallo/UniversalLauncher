package com.example.data.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File

/** An installed Android icon theme that can supply replacement app icons. */
data class IconPackInfo(
    val packageName: String,
    val label: String,
    /** Rendered `file://` URL of the pack's own launcher icon, used in Settings. */
    val iconUrl: String? = null
)

/** One drawable inside an icon pack, shown in the per-app icon picker. */
data class IconPackIcon(
    val drawableName: String,
    val label: String
)

/**
 * Reads installed Android icon packs (the themes sold on the Play Store for Nova, Lawnchair,
 * ADW and friends) and renders their drawables to cached PNG files.
 *
 * Icon packs are ordinary apps that advertise a theme intent action and ship an
 * `appfilter.xml` mapping `package` -> drawable. The drawables are usually adaptive or
 * vector XML that Coil cannot decode directly, so each icon is rendered once to a PNG in
 * app-private storage and referenced through a stable `file://` URL.
 *
 * Only packages that advertise a known theme action are considered, which keeps regular
 * apps with an `appfilter.xml` asset from showing up as themes.
 */
object IconPackManager {
    /** Theme actions used by the launchers that icon packs target. */
    private val THEME_ACTIONS = listOf(
        "org.adw.launcher.THEMES",
        "org.adwfreak.launcher.THEMES",
        "com.gau.go.launcherex.theme",
        "com.novalauncher.THEME",
        "com.anddoes.launcher.THEME",
        "com.teslacoilsw.launcher.THEME",
        "com.smartlauncher.THEMES",
        "com.fede.launcher.THEMES",
        "com.sec.android.app.launcher.THEMES",
        "com.sonyericsson.home.THEME",
        "com.huawei.android.thememanager.THEME",
        "com.oppo.launcher.THEME",
        "com.transparent.launcher.THEMES",
        "com.apex.launcher.THEME",
        "com.gtp.nextlauncher.theme",
        "ch.deletescape.lawnchair.ICONPACK",
        "app.lawnchair.ICONPACK"
    )

    private const val ICON_SIZE_PX = 192
    private const val PREVIEW_SIZE_PX = 128
    private const val PACK_DIR = "icon_packs"
    private const val CHOICE_DIR = "custom_icons"

    /** All installed icon themes, sorted by display name. */
    suspend fun installedIconPacks(context: Context): List<IconPackInfo> =
        withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            val found = LinkedHashMap<String, IconPackInfo>()
            for (action in THEME_ACTIONS) {
                queryThemeActivities(packageManager, action).forEach { resolveInfo ->
                    val packageName = resolveInfo.activityInfo?.packageName ?: return@forEach
                    if (packageName == context.packageName || found.containsKey(packageName)) {
                        return@forEach
                    }
                    val label = runCatching { resolveInfo.loadLabel(packageManager).toString() }
                        .getOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?: packageName
                    found[packageName] = IconPackInfo(
                        packageName = packageName,
                        label = label,
                        iconUrl = packAppIconUrl(context, packageName)
                    )
                }
            }
            found.values.sortedBy { it.label.lowercase() }
        }

    /**
     * Resolves themed icon files for every package in [packages].
     *
     * Packages the theme has no entry for are omitted so callers can fall back to the
     * device icon. Rendering is cached per pack version, so re-selecting the same theme
     * is effectively free.
     */
    suspend fun iconMap(
        context: Context,
        packPackage: String,
        packages: Collection<String>
    ): Map<String, String> = withContext(Dispatchers.IO) {
        val packContext = packageContext(context, packPackage) ?: return@withContext emptyMap()
        val resources = packContext.resources
        val appFilter = parseAppFilter(packContext, packPackage).packages
        if (appFilter.isEmpty()) return@withContext emptyMap()

        val dir = packDirectory(context, packPackage).apply { mkdirs() }
        packages.distinct().mapNotNull { packageName ->
            val drawableName = appFilter[packageName] ?: return@mapNotNull null
            val file = File(dir, "${sanitize(packageName)}.png")
            if (!file.exists() || file.length() == 0L) {
                renderToFile(resources, packPackage, drawableName, ICON_SIZE_PX, file)
                    ?: return@mapNotNull null
            }
            packageName to Uri.fromFile(file).toString()
        }.toMap()
    }

    /** Every drawable the theme offers, without rendering previews yet. */
    suspend fun icons(context: Context, packPackage: String): List<IconPackIcon> =
        withContext(Dispatchers.IO) {
            val packContext = packageContext(context, packPackage) ?: return@withContext emptyList()
            parseAppFilter(packContext, packPackage).drawables
                .distinct()
                .sorted()
                .map { drawableName -> IconPackIcon(drawableName, prettify(drawableName)) }
        }

    /** Renders (and caches) a small preview for the icon picker grid. */
    suspend fun previewUrl(
        context: Context,
        packPackage: String,
        drawableName: String
    ): String? = withContext(Dispatchers.IO) {
        val packContext = packageContext(context, packPackage) ?: return@withContext null
        val dir = File(packDirectory(context, packPackage), "_previews").apply { mkdirs() }
        val file = File(dir, "${sanitize(drawableName)}.png")
        if (!file.exists() || file.length() == 0L) {
            renderToFile(
                packContext.resources,
                packPackage,
                drawableName,
                PREVIEW_SIZE_PX,
                file
            ) ?: return@withContext null
        }
        Uri.fromFile(file).toString()
    }

    /**
     * Renders the drawable chosen in the per-app picker. The result is written outside the
     * auto-generated `app_icons` cache, so it counts as a user customization and is never
     * replaced by the active theme.
     */
    suspend fun renderIconChoice(
        context: Context,
        packPackage: String,
        drawableName: String,
        itemId: String
    ): String? = withContext(Dispatchers.IO) {
        val packContext = packageContext(context, packPackage) ?: return@withContext null
        val dir = File(context.filesDir, CHOICE_DIR).apply { mkdirs() }
        val prefix = "${sanitize(itemId)}-"
        val file = File(dir, "$prefix${System.currentTimeMillis()}.png")
        renderToFile(packContext.resources, packPackage, drawableName, ICON_SIZE_PX, file)
            ?: return@withContext null
        dir.listFiles { candidate -> candidate.name.startsWith(prefix) && candidate != file }
            ?.forEach { it.delete() }
        Uri.fromFile(file).toString()
    }

    private fun queryThemeActivities(
        packageManager: PackageManager,
        action: String
    ): List<android.content.pm.ResolveInfo> {
        val intent = Intent(action)
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(intent, 0)
            }
        }.getOrDefault(emptyList())
    }

    private fun packageContext(context: Context, packageName: String): Context? =
        runCatching {
            context.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY)
        }.getOrNull()

    private fun packVersion(context: Context, packPackage: String): Long =
        runCatching {
            context.packageManager.getPackageInfo(packPackage, 0).lastUpdateTime
        }.getOrDefault(0L)

    private fun packDirectory(context: Context, packPackage: String): File {
        val name = sanitize(packPackage)
        val dir = File(context.filesDir, "$PACK_DIR/$name-${packVersion(context, packPackage)}")
        // Drop the previous version's rendered icons so a pack update does not leave
        // hundreds of stale PNGs behind.
        runCatching {
            dir.parentFile?.listFiles { candidate ->
                candidate.isDirectory && candidate != dir && candidate.name.startsWith("$name-")
            }?.forEach { it.deleteRecursively() }
        }
        return dir
    }

    private fun packAppIconUrl(context: Context, packPackage: String): String? =
        runCatching {
            val dir = File(context.filesDir, "$PACK_DIR/_pack_icons").apply { mkdirs() }
            val name = sanitize(packPackage)
            val file = File(dir, "$name-${packVersion(context, packPackage)}.png")
            dir.listFiles { candidate ->
                candidate != file && candidate.name.startsWith("$name-")
            }?.forEach { it.delete() }
            if (!file.exists() || file.length() == 0L) {
                val bitmap = context.packageManager
                    .getApplicationIcon(packPackage)
                    .toBitmap(ICON_SIZE_PX, ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
                file.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                bitmap.recycle()
            }
            Uri.fromFile(file).toString()
        }.getOrNull()

    private fun renderToFile(
        resources: Resources,
        packPackage: String,
        drawableName: String,
        sizePx: Int,
        target: File
    ): File? = runCatching {
        val id = resources.getIdentifier(drawableName, "drawable", packPackage)
            .takeIf { it != 0 }
            ?: resources.getIdentifier(drawableName, "mipmap", packPackage)
        if (id == 0) return null
        val drawable = ResourcesCompat.getDrawable(resources, id, null) ?: return null
        val bitmap = drawable.toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        target.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
        if (target.length() == 0L) {
            target.delete()
            null
        } else {
            target
        }
    }.getOrNull()

    internal data class AppFilter(
        val packages: Map<String, String>,
        val drawables: List<String>
    )

    /**
     * Parses `appfilter.xml` into `package -> drawable` plus every drawable name it
     * mentions. Packs store the file either as a compiled XML resource or as an asset;
     * both are supported. Entries can identify the package through `package="..."` or
     * `component="ComponentInfo{pkg/activity}"`.
     */
    private fun parseAppFilter(packContext: Context, packPackage: String): AppFilter =
        openAppFilter(packContext, packPackage)?.let(::parseAppFilter)
            ?: AppFilter(emptyMap(), emptyList())

    internal fun parseAppFilter(parser: XmlPullParser): AppFilter {
        val packages = LinkedHashMap<String, String>()
        val drawables = LinkedHashSet<String>()
        runCatching {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "item") {
                    val drawable = parser.getAttributeValue(null, "drawable")
                        ?: parser.getAttributeValue(null, "icon")
                    val packageName = parser.getAttributeValue(null, "package")
                        ?: parser.getAttributeValue(null, "component")
                            ?.substringAfter('{', "")
                            ?.substringBefore('/')
                            ?.trim()
                            ?.trimEnd('}')
                    if (!drawable.isNullOrBlank()) {
                        drawables += drawable
                        if (!packageName.isNullOrBlank()) {
                            packages[packageName] = drawable
                        }
                    }
                }
                event = parser.next()
            }
        }
        return AppFilter(packages, drawables.toList())
    }

    private fun openAppFilter(packContext: Context, packPackage: String): XmlPullParser? {
        val resourceId = packContext.resources.getIdentifier("appfilter", "xml", packPackage)
        if (resourceId != 0) {
            runCatching { packContext.resources.getXml(resourceId) }.getOrNull()?.let { return it }
        }
        return runCatching {
            val stream = packContext.assets.open("appfilter.xml")
            android.util.Xml.newPullParser().apply {
                setInput(stream, null)
            }
        }.getOrNull()
    }

    private fun prettify(drawableName: String): String =
        drawableName
            .substringAfterLast('.')
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercaseChar() }
            }

    private fun sanitize(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
