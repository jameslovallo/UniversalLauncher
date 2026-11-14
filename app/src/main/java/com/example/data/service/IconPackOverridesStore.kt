package com.example.data.service

import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONObject
import java.io.File

/**
 * Persists icon-pack preferences in [SharedPreferences]:
 *
 * 1. The selected icon pack package name (`null` for default device icons).
 * 2. Per-pack custom icon drawable overrides (`packPackage -> { itemId -> drawableName }`),
 *    preserved across theme switches so switching to "Default icons" and back to a pack
 *    restores every app's chosen icon from that pack.
 * 3. A snapshot of the active pack's resolved icon URLs so [LauncherViewModel] can populate
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

    fun getOverridesForPack(packPackage: String): Map<String, String> {
        val root = readOverridesJson()
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
        val root = readOverridesJson()
        val packObj = root.optJSONObject(packPackage) ?: JSONObject()
        packObj.put(itemId, drawableName)
        root.put(packPackage, packObj)
        prefs.edit().putString(KEY_OVERRIDES, root.toString()).apply()
    }

    fun removeOverride(packPackage: String, itemId: String) {
        if (packPackage.isBlank() || itemId.isBlank()) return
        val root = readOverridesJson()
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

    fun loadCachedIconMap(packPackage: String?): Map<String, String> {
        val resolvedPack = packPackage?.takeIf { it.isNotBlank() } ?: return emptyMap()
        if (prefs.getString(KEY_CACHED_PACK, null) != resolvedPack) return emptyMap()
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
        val resolvedPack = packPackage?.takeIf { it.isNotBlank() }
        if (resolvedPack == null || iconMap.isEmpty()) {
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
            .putString(KEY_CACHED_PACK, resolvedPack)
            .putString(KEY_CACHED_URLS, json.toString())
            .apply()
    }

    private fun readOverridesJson(): JSONObject {
        val raw = prefs.getString(KEY_OVERRIDES, null) ?: return JSONObject()
        return runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
    }

    private fun isReadableLocalIconUrl(url: String): Boolean {
        if (!url.startsWith("file:")) return true
        val path = runCatching { Uri.parse(url).path }.getOrNull() ?: return false
        val file = File(path)
        return file.exists() && file.length() > 0L
    }

    private companion object {
        const val KEY_SELECTED_PACK = "icon_pack_package"
        const val KEY_OVERRIDES = "icon_pack_custom_overrides"
        const val KEY_CACHED_PACK = "icon_pack_cached_package"
        const val KEY_CACHED_URLS = "icon_pack_cached_urls"
    }
}
