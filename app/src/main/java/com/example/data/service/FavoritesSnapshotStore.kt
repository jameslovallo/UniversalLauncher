package com.example.data.service

import android.content.SharedPreferences
import com.example.data.model.GameItem
import com.example.data.model.GameType
import org.json.JSONArray
import org.json.JSONObject

/**
 * Remembers the favorites that were last shown, so the home screen can draw them on its
 * very first frame after the process was killed.
 *
 * Low-memory devices (notably Android TV boxes) routinely kill the launcher while an app or
 * game is in the foreground. Coming back then cold-starts the process, and
 * `repository.favoriteApps` stays on its `emptyList()` initial value until Room opens the
 * database and emits. On those devices that gap is visible: the favorites row disappears and
 * the layout jumps once the rows arrive.
 *
 * The snapshot is only a starting value. Every real Room emission is written back here and
 * replaces the cached list, so the cache can never outlive the database by more than the
 * brief window before that first emission.
 *
 * Unlike [LibrarySyncCodec] this cache is local-only: it keeps raw `file://` artwork URLs and
 * every field of [GameItem], because it has to reproduce the exact rows the UI already had.
 */
internal object FavoritesSnapshotStore {

    private const val KEY_SNAPSHOT = "favorites_snapshot_v1"

    /** Overwrites the cached favorites with [items]. Call with the latest Room emission. */
    fun persist(prefs: SharedPreferences, items: List<GameItem>) {
        if (items.isEmpty()) {
            // Keep the preferences file small when the user has no favorites. An empty
            // snapshot carries no information, and restore() already falls back to empty.
            prefs.edit().remove(KEY_SNAPSHOT).apply()
            return
        }
        runCatching {
            val array = JSONArray()
            items.forEach { array.put(it.toJson()) }
            prefs.edit().putString(KEY_SNAPSHOT, array.toString()).apply()
        }
    }

    /**
     * Returns the cached favorites, or an empty list when nothing usable is stored.
     * Individual malformed entries are skipped instead of discarding the whole cache.
     */
    fun restore(prefs: SharedPreferences): List<GameItem> {
        val raw = prefs.getString(KEY_SNAPSHOT, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val items = ArrayList<GameItem>(array.length())
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index) ?: continue
            json.toGameItem()?.let(items::add)
        }
        return items
    }

    private fun GameItem.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("type", type.name)
        put("isGame", isGame)
        putNullable("packageName", packageName)
        putNullable("activityName", activityName)
        putNullable("launchUrl", launchUrl)
        putNullable("gfnCmsId", gfnCmsId)
        putNullable("posterUrl", posterUrl)
        putNullable("nsfwPosterUrl", nsfwPosterUrl)
        putNullable("logoUrl", logoUrl)
        put("overlayLogo", overlayLogo)
        putNullable("nsfwLogoUrl", nsfwLogoUrl)
        put("overlayNsfwLogo", overlayNsfwLogo)
        putNullable("heroBannerUrl", heroBannerUrl)
        putNullable("iconUrl", iconUrl)
        put("genre", genre)
        put("publisher", publisher)
        put("description", description)
        put("isFavorite", isFavorite)
        put("lastPlayedTimestamp", lastPlayedTimestamp)
        put("playCount", playCount)
        putNullable("customArtworkSource", customArtworkSource)
        put("tags", tags)
        put("isPinned", isPinned)
        put("isHidden", isHidden)
        put("isDeleted", isDeleted)
        put("isCustomized", isCustomized)
        put("favoriteOrder", favoriteOrder)
        putNullable("folderId", folderId)
    }

    private fun JSONObject.toGameItem(): GameItem? = runCatching {
        GameItem(
            id = getString("id"),
            title = getString("title"),
            type = GameType.valueOf(getString("type")),
            isGame = optBoolean("isGame", true),
            packageName = optNullableString("packageName"),
            activityName = optNullableString("activityName"),
            launchUrl = optNullableString("launchUrl"),
            gfnCmsId = optNullableString("gfnCmsId"),
            posterUrl = optNullableString("posterUrl"),
            nsfwPosterUrl = optNullableString("nsfwPosterUrl"),
            logoUrl = optNullableString("logoUrl"),
            overlayLogo = optBoolean("overlayLogo", false),
            nsfwLogoUrl = optNullableString("nsfwLogoUrl"),
            overlayNsfwLogo = optBoolean("overlayNsfwLogo", false),
            heroBannerUrl = optNullableString("heroBannerUrl"),
            iconUrl = optNullableString("iconUrl"),
            genre = optString("genre", "Game"),
            publisher = optString("publisher", ""),
            description = optString("description", ""),
            isFavorite = optBoolean("isFavorite", true),
            lastPlayedTimestamp = optLong("lastPlayedTimestamp", 0L),
            playCount = optInt("playCount", 0),
            customArtworkSource = optNullableString("customArtworkSource"),
            tags = optString("tags", ""),
            isPinned = optBoolean("isPinned", false),
            isHidden = optBoolean("isHidden", false),
            isDeleted = optBoolean("isDeleted", false),
            isCustomized = optBoolean("isCustomized", false),
            favoriteOrder = optInt("favoriteOrder", 0),
            folderId = optNullableString("folderId")
        )
    }.getOrNull()

    private fun JSONObject.putNullable(name: String, value: String?) {
        if (value != null) put(name, value)
    }

    private fun JSONObject.optNullableString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name)
    }
}
