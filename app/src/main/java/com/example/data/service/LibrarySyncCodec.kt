package com.example.data.service

import com.example.data.model.GameItem
import com.example.data.model.GameType
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Builds and parses the JSON document used to sync the game library between devices.
 *
 * The document lists every shareable item (cloud games, Moonlight games and game
 * shortcuts, including tombstones) with an `updatedAt` version. Device-local details are
 * deliberately excluded from both the file and the comparison [signature]:
 *
 * - `isPinned`, play counts and last-played times
 * - `file:` artwork, which only exists on the device that chose it
 *
 * That keeps two devices from fighting over the file because of state that cannot travel.
 */
internal object LibrarySyncCodec {

    data class Parsed(
        val updatedAt: Long,
        val items: List<GameItem>,
        val signature: String,
        /**
         * True when the file was written by a build that stores the full library
         * (including tombstones). Older exports only list live games, so adopting them as
         * authoritative would delete local games they never knew about.
         */
        val authoritative: Boolean = false
    )

    const val FORMAT_NAME = "nexus_library"
    const val FORMAT_VERSION = 2

    fun isSyncable(item: GameItem): Boolean = when (item.type) {
        GameType.GEFORCE_NOW, GameType.MOONLIGHT -> true
        GameType.SHORTCUT -> item.isGame
        else -> false
    }

    /** Shareable rows in a stable order. */
    fun syncable(rows: List<GameItem>): List<GameItem> =
        rows.filter(::isSyncable).sortedBy { it.id }

    fun buildDocument(items: List<GameItem>, updatedAt: Long): JSONObject {
        val payload = JSONArray()
        items.sortedBy { it.id }.forEach { payload.put(itemToJson(it)) }
        return JSONObject()
            .put("format", FORMAT_NAME)
            .put("version", FORMAT_VERSION)
            .put("updatedAt", updatedAt)
            // Kept for compatibility with the old export format.
            .put("exportedAt", updatedAt)
            .put("items", payload)
    }

    /**
     * @throws IllegalStateException when the text is not a Nexus library document.
     */
    fun parse(text: String): Parsed {
        val trimmed = text.trim()
        val array: JSONArray
        val updatedAt: Long
        val version: Int
        if (trimmed.startsWith("[")) {
            // Bare array written by older builds; its age and deletions are unknown.
            array = parseArrayOrFail(trimmed)
            updatedAt = 0L
            version = 0
        } else {
            val document = try {
                JSONObject(trimmed)
            } catch (error: JSONException) {
                throw IllegalStateException("That file is not a Nexus library file", error)
            }
            val format = document.optString("format")
            if (format.isNotBlank() && format != FORMAT_NAME) {
                throw IllegalStateException("That file is not a Nexus library file")
            }
            val itemArray = document.optJSONArray("items")
            if (itemArray == null && format.isBlank()) {
                // A JSON object with neither our marker nor an item list is some other
                // file the user picked by mistake; never overwrite it with the library.
                throw IllegalStateException("That file is not a Nexus library file")
            }
            updatedAt = document.optLong(
                "updatedAt",
                document.optLong("exportedAt", 0L)
            )
            version = document.optInt("version", 0)
            array = itemArray ?: JSONArray()
        }

        val items = buildList {
            for (index in 0 until array.length()) {
                jsonToItem(array.optJSONObject(index))
                    ?.takeIf(::isSyncable)
                    ?.let(::add)
            }
        }
        return Parsed(
            updatedAt = updatedAt,
            items = items,
            signature = signature(items),
            authoritative = version >= FORMAT_VERSION
        )
    }

    /**
     * Content signature used to decide whether the file and the local library differ.
     * Device-local fields (pin state, play stats and `file:` artwork paths) are ignored
     * so they never cause a write loop or a false conflict.
     */
    fun signature(items: List<GameItem>): String {
        val canonical = items
            .map(::canonicalItem)
            .sorted()
            .joinToString(RECORD_SEPARATOR)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun parseArrayOrFail(text: String): JSONArray = try {
        JSONArray(text)
    } catch (error: JSONException) {
        throw IllegalStateException("That file is not a Nexus library file", error)
    }

    private fun itemToJson(item: GameItem): JSONObject = JSONObject().apply {
        put("id", item.id)
        put("title", item.title)
        put("type", item.type.name)
        put("isGame", item.isGame)
        item.packageName?.let { put("packageName", it) }
        item.activityName?.let { put("activityName", it) }
        item.launchUrl?.let { put("launchUrl", it) }
        item.gfnCmsId?.let { put("gfnCmsId", it) }
        // Local artwork paths only exist on this device.
        shareableUrl(item.posterUrl)?.let { put("posterUrl", it) }
        shareableUrl(item.nsfwPosterUrl)?.let { put("nsfwPosterUrl", it) }
        shareableUrl(item.heroBannerUrl)?.let { put("heroBannerUrl", it) }
        shareableUrl(item.iconUrl)?.let { put("iconUrl", it) }
        put("genre", item.genre)
        put("publisher", item.publisher)
        put("description", item.description)
        put("isFavorite", item.isFavorite)
        put("favoriteOrder", item.favoriteOrder)
        put("isHidden", item.isHidden)
        put("isCustomized", item.isCustomized)
        put("isDeleted", item.isDeleted)
        put("lastPlayedTimestamp", item.lastPlayedTimestamp)
        put("playCount", item.playCount)
        put("customArtworkSource", item.customArtworkSource.orEmpty())
        put("tags", item.tags)
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
                nsfwPosterUrl = json.optNullableString("nsfwPosterUrl"),
                heroBannerUrl = json.optNullableString("heroBannerUrl"),
                iconUrl = json.optNullableString("iconUrl"),
                genre = json.optString("genre", "Game"),
                publisher = json.optString("publisher", ""),
                description = json.optString("description", ""),
                isFavorite = json.optBoolean("isFavorite", false),
                favoriteOrder = json.optInt("favoriteOrder", 0),
                isHidden = json.optBoolean("isHidden", false),
                isCustomized = json.optBoolean("isCustomized", false),
                isDeleted = json.optBoolean("isDeleted", false),
                lastPlayedTimestamp = json.optLong("lastPlayedTimestamp", 0L),
                playCount = json.optInt("playCount", 0),
                customArtworkSource = json.optString("customArtworkSource")
                    .takeIf { it.isNotBlank() },
                tags = json.optString("tags", ""),
                // Never inherit "pinned on this device"; imported shortcuts launch
                // through their stored intent or the app's own shortcut API.
                isPinned = false
            )
        }.getOrNull()
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    private fun canonicalItem(item: GameItem): String = listOf(
        item.id,
        item.title,
        item.type.name,
        item.isGame.toString(),
        item.packageName.orEmpty(),
        item.activityName.orEmpty(),
        item.launchUrl.orEmpty(),
        item.gfnCmsId.orEmpty(),
        shareableUrl(item.posterUrl).orEmpty(),
        shareableUrl(item.nsfwPosterUrl).orEmpty(),
        shareableUrl(item.heroBannerUrl).orEmpty(),
        shareableUrl(item.iconUrl).orEmpty(),
        item.genre,
        item.publisher,
        item.description,
        item.isFavorite.toString(),
        item.favoriteOrder.toString(),
        item.isHidden.toString(),
        item.isDeleted.toString(),
        item.isCustomized.toString(),
        item.customArtworkSource.orEmpty(),
        item.tags
    ).joinToString(FIELD_SEPARATOR)

    private fun shareableUrl(url: String?): String? =
        url?.takeUnless { it.startsWith("file:") }?.takeIf { it.isNotBlank() }

    private const val RECORD_SEPARATOR = "\u001e"
    private const val FIELD_SEPARATOR = "\u001f"
}
