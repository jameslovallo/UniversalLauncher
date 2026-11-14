package com.example.data.service

import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONObject
import java.io.File

/**
 * Persists icon-theme preferences and per-theme custom icon memories in [SharedPreferences]:
 *
 * 1. The selected icon pack package name (`null` for the Default theme).
 * 2. Per-pack custom drawable overrides (`packPackage -> { itemId -> drawableName }`).
 * 3. Per-theme custom image/URL overrides (`themeKey -> { itemId -> iconUrl }`), giving
 *    the Default theme (`"__default__"`) and each custom icon pack their own independent
 *    memory of apps with customized icons (local photos or custom URLs).
 * 4. Original shortcut base icons (`itemId -> shortcutIconUrl`) so customizing or switching
 *    themes on pinned shortcuts never loses their original system icon.
 * 5. A snapshot of the active theme's resolved icon URLs so [LauncherViewModel] can populate
 *    `themedAppIcons` synchronously on Frame 0 before asynchronous IO finishes.
 */
class IconPackOverridesStore(private val prefs: SharedPreferences) {

    var selectedIconPack: String?
        get() = prefs.getString(KEY_SELECTED_PACK, null)?.takeIf { it.isNotBlank() }
        set(value) {
            val resolved = value?.takeIf { it.isNotBlank() }
            prefs.edit().apply {
                if (resolved == null) remove(KEY_SELECTED_PACK)
                else putString(KEY_SELECTED_PACK, resolved)
            }.apply()
        }

    var hasMigratedDefaultThemeIcons: Boolean
        get() = prefs.getBoolean(KEY_DEFAULT_MIGRATED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_DEFAULT_MIGRATED, value).apply()
        }

    fun getOverridesForPack(packPackage: String): Map<String, String> {
        val root = readJson(KEY_OVERRIDES)
        val packObj = root.optJSONObject(packPackage) ?: return emptyMap()
        val result = LinkedHashMap<String, String>(packObj.length())
        val keys = packObj.keys()
        while (keys.hasNext()) {
            val itemId = keys.next()
            val drawable = packObj.optString(itemId)
            if (itemId.isNotBlank() && drawable.isNotBlank()) {
                result[itemId] = drawable
            }
        }
        return result
    }

    fun getOverride(packPackage: String, itemId: String): String? =
        getOverridesForPack(packPackage)[itemId]

    fun setOverride(packPackage: String, itemId: String, drawableName: String) {
        if (packPackage.isBlank() || itemId.isBlank() || drawableName.isBlank()) return
        val root = readJson(KEY_OVERRIDES)
        val packObj = root.optJSONObject(packPackage) ?: JSONObject()
        packObj.put(itemId, drawableName)
        root.put(packPackage, packObj)
        prefs.edit().putString(KEY_OVERRIDES, root.toString()).apply()
        // A pack drawable override replaces any custom URL/photo set for this app in the same pack.
        removeCustomUrlOverride(packPackage, itemId)
    }

    fun removeOverride(packPackage: String, itemId: String) {
        if (packPackage.isBlank() || itemId.isBlank()) return
        val root = readJson(KEY_OVERRIDES)
        val packObj = root.optJSONObject(packPackage) ?: return
        if (!packObj.has(itemId)) return
        packObj.remove(itemId)
        if (packObj.length() == 0) {
            root.remove(packPackage)
        } else {
            root.put(packPackage, packObj)
        }
        prefs.edit().putString(KEY_OVERRIDES, root.toString()).apply()
    }

    /**
     * Returns `itemId -> iconUrl` overrides remembered for [packPackage] (or the Default
     * theme when [packPackage] is `null`), omitting local `file://` targets that no longer exist.
     */
    fun getCustomUrlOverrides(packPackage: String?): Map<String, String> {
        val key = themeKey(packPackage)
        val root = readJson(KEY_CUSTOM_URL_OVERRIDES)
        val themeObj = root.optJSONObject(key) ?: return emptyMap()
        val result = LinkedHashMap<String, String>(themeObj.length())
        val keys = themeObj.keys()
        while (keys.hasNext()) {
            val itemId = keys.next()
            val url = themeObj.optString(itemId)
            if (itemId.isNotBlank() && url.isNotBlank() && isReadableLocalIconUrl(url)) {
                result[itemId] = url
            }
        }
        return result
    }

    fun getCustomUrlOverride(packPackage: String?, itemId: String): String? =
        getCustomUrlOverrides(packPackage)[itemId]

    fun setCustomUrlOverride(packPackage: String?, itemId: String, url: String) {
        val sanitizedUrl = url.trim()
        if (itemId.isBlank() || sanitizedUrl.isBlank()) return
        val key = themeKey(packPackage)
        val root = readJson(KEY_CUSTOM_URL_OVERRIDES)
        val themeObj = root.optJSONObject(key) ?: JSONObject()
        themeObj.put(itemId, sanitizedUrl)
        root.put(key, themeObj)
        prefs.edit().putString(KEY_CUSTOM_URL_OVERRIDES, root.toString()).apply()
        val resolvedPack = packPackage?.takeIf { it.isNotBlank() }
        if (resolvedPack != null) {
            removeOverride(resolvedPack, itemId)
        }
    }

    fun removeCustomUrlOverride(packPackage: String?, itemId: String) {
        if (itemId.isBlank()) return
        val key = themeKey(packPackage)
        val root = readJson(KEY_CUSTOM_URL_OVERRIDES)
        val themeObj = root.optJSONObject(key) ?: return
        if (!themeObj.has(itemId)) return
        themeObj.remove(itemId)
        if (themeObj.length() == 0) {
            root.remove(key)
        } else {
            root.put(key, themeObj)
        }
        prefs.edit().putString(KEY_CUSTOM_URL_OVERRIDES, root.toString()).apply()
    }

    /**
     * Remembers a shortcut's original `/shortcuts/` icon URL before it is replaced by a
     * custom image so resetting or switching themes can restore it.
     */
    fun saveBaseIconUrlIfUnset(itemId: String, url: String?) {
        if (itemId.isBlank() || url.isNullOrBlank() || !url.contains("/shortcuts/")) return
        val root = readJson(KEY_BASE_SHORTCUT_ICONS)
        if (root.has(itemId)) return
        root.put(itemId, url)
        prefs.edit().putString(KEY_BASE_SHORTCUT_ICONS, root.toString()).apply()
    }

    fun getBaseIconUrl(itemId: String): String? {
        if (itemId.isBlank()) return null
        val url = readJson(KEY_BASE_SHORTCUT_ICONS).optString(itemId)
        return url.takeIf { it.isNotBlank() && isReadableLocalIconUrl(it) }
    }

    fun loadCachedIconMap(packPackage: String?): Map<String, String> {
        val resolvedKey = themeKey(packPackage)
        if (prefs.getString(KEY_CACHED_PACK, null) != resolvedKey) return emptyMap()
        val raw = prefs.getString(KEY_CACHED_URLS, null) ?: return emptyMap()
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        val result = LinkedHashMap<String, String>(json.length())
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val url = json.optString(key)
            if (key.isBlank() || url.isBlank()) continue
            if (isReadableLocalIconUrl(url)) {
                result[key] = url
            }
        }
        return result
    }

    fun saveCachedIconMap(packPackage: String?, iconMap: Map<String, String>) {
        val resolvedKey = themeKey(packPackage)
        if (iconMap.isEmpty()) {
            prefs.edit()
                .remove(KEY_CACHED_PACK)
                .remove(KEY_CACHED_URLS)
                .apply()
            return
        }
        val json = JSONObject()
        iconMap.forEach { (key, url) ->
            if (key.isNotBlank() && url.isNotBlank()) {
                json.put(key, url)
            }
        }
        prefs.edit()
            .putString(KEY_CACHED_PACK, resolvedKey)
            .putString(KEY_CACHED_URLS, json.toString())
            .apply()
    }

    private fun readJson(prefKey: String): JSONObject {
        val raw = prefs.getString(prefKey, null) ?: return JSONObject()
        return runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
    }

    private fun isReadableLocalIconUrl(url: String): Boolean {
        if (!url.startsWith("file:")) return true
        val path = runCatching { Uri.parse(url).path }.getOrNull() ?: return false
        val file = File(path)
        return file.exists() && file.length() > 0L
    }

    companion object {
        const val DEFAULT_THEME_KEY = "__default__"

        fun themeKey(packPackage: String?): String =
            packPackage?.takeIf { it.isNotBlank() } ?: DEFAULT_THEME_KEY

        private const val KEY_SELECTED_PACK = "icon_pack_package"
        private const val KEY_DEFAULT_MIGRATED = "icon_theme_default_migrated"
        private const val KEY_OVERRIDES = "icon_pack_custom_overrides"
        private const val KEY_CUSTOM_URL_OVERRIDES = "icon_theme_custom_url_overrides"
        private const val KEY_BASE_SHORTCUT_ICONS = "icon_theme_base_shortcut_icons"
        private const val KEY_CACHED_PACK = "icon_pack_cached_package"
        private const val KEY_CACHED_URLS = "icon_pack_cached_urls"
    }
}
