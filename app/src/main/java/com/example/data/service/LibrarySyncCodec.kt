package com.example.data.service

import android.net.Uri
import com.example.data.model.GameItem
import com.example.data.model.GameType
import java.io.File
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
 * - Raw `file:` artwork paths (which are local to a device); instead, existing local
 *   poster files contribute a content SHA-256 hash (`posterHash` / `nsfwPosterHash`) so
 *   chosen-file posters sync through the folder's `posters/` directory.
 */
internal object LibrarySyncCodec {

    data class PosterHashes(
        val posterHash: String? = null,
        val nsfwPosterHash: String? = null
    )

    data class Parsed(
        val updatedAt: Long,
        val items: List<GameItem>,
        val signature: String,
        /**
         * True when the file was written by a build that stores the full library
         * (including tombstones). Older exports only list live games, so adopting them as
         * authoritative would delete local games they never knew about.
         */
        val authoritative: Boolean = false,
        /** True when the file tracks local chosen-file poster hashes (`posterHash` / `nsfwPosterHash`). */
        val tracksLocalPosters: Boolean = false,
        val posterHashes: Map<String, PosterHashes> = emptyMap()
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

    fun buildDocument(
        items: List<GameItem>,
        updatedAt: Long,
        localHashForUrl: (String) -> String? = ::localFileSha256
    ): JSONObject {
        val payload = JSONArray()
        items.sortedBy { it.id }.forEach { payload.put(itemToJson(it, localHashForUrl)) }
        return JSONObject()
            .put("format", FORMAT_NAME)
            .put("version", FORMAT_VERSION)
            .put("updatedAt", updatedAt)
            // Kept for compatibility with the old export format.
            .put("exportedAt", updatedAt)
            .put("localPosters", true)
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
        val tracksLocalPosters: Boolean
        if (trimmed.startsWith("[")) {
            // Bare array written by older builds; its age and deletions are unknown.
            array = parseArrayOrFail(trimmed)
            updatedAt = 0L
            version = 0
            tracksLocalPosters = false
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
            tracksLocalPosters = document.optBoolean("localPosters", false)
            array = itemArray ?: JSONArray()
        }

        val posterHashes = mutableMapOf<String, PosterHashes>()
        val items = buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index)
                val item = jsonToItem(obj)?.takeIf(::isSyncable) ?: continue
                add(item)
                if (obj != null) {
                    val pHash = obj.optNullableString("posterHash")
                    val nHash = obj.optNullableString("nsfwPosterHash")
                    if (pHash != null || nHash != null) {
                        posterHashes[item.id] = PosterHashes(pHash, nHash)
                    }
                }
            }
        }
        return Parsed(
            updatedAt = updatedAt,
            items = items,
            signature = signatureWithOverrides(items, posterHashes),
            authoritative = version >= FORMAT_VERSION,
            tracksLocalPosters = tracksLocalPosters,
            posterHashes = posterHashes
        )
    }

    /**
     * Content signature used to decide whether the file and the local library differ.
     * Device-local fields (pin state, play stats, and raw `file:` path strings) are excluded,
     * while existing local poster files contribute their SHA-256 hash so changes sync cleanly.
     */
    fun signature(
        items: List<GameItem>,
        localHashForUrl: (String) -> String? = ::localFileSha256
    ): String = signatureWithOverrides(items, emptyMap(), localHashForUrl)

    private fun signatureWithOverrides(
        items: List<GameItem>,
        hashOverrides: Map<String, PosterHashes>,
        localHashForUrl: (String) -> String? = ::localFileSha256
    ): String {
        val canonical = items
            .map { item ->
                canonicalItem(item, hashOverrides[item.id], localHashForUrl)
            }
            .sorted()
            .joinToString(RECORD_SEPARATOR)
        return sha256Hex(canonical.toByteArray(Charsets.UTF_8))
    }

    private fun parseArrayOrFail(text: String): JSONArray = try {
        JSONArray(text)
    } catch (error: JSONException) {
        throw IllegalStateException("That file is not a Nexus library file", error)
    }

    private fun itemToJson(
        item: GameItem,
        localHashForUrl: (String) -> String?
    ): JSONObject = JSONObject().apply {
        put("id", item.id)
        put("title", item.title)
        put("type", item.type.name)
        put("isGame", item.isGame)
        item.packageName?.let { put("packageName", it) }
        item.activityName?.let { put("activityName", it) }
        item.launchUrl?.let { put("launchUrl", it) }
        item.gfnCmsId?.let { put("gfnCmsId", it) }
        // Raw local file paths stay off the JSON; existing local artwork contributes a content hash.
        val remotePoster = shareableUrl(item.posterUrl)
        if (remotePoster != null) {
            put("posterUrl", remotePoster)
        } else {
            item.posterUrl?.let(localHashForUrl)?.let { put("posterHash", it) }
        }
        val remoteNsfwPoster = shareableUrl(item.nsfwPosterUrl)
        if (remoteNsfwPoster != null) {
            put("nsfwPosterUrl", remoteNsfwPoster)
        } else {
            item.nsfwPosterUrl?.let(localHashForUrl)?.let { put("nsfwPosterHash", it) }
        }
        shareableUrl(item.nsfwLogoUrl)?.let { put("nsfwLogoUrl", it) }
        if (item.overlayNsfwLogo) put("overlayNsfwLogo", true)
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
                nsfwLogoUrl = json.optNullableString("nsfwLogoUrl"),
                overlayNsfwLogo = json.optBoolean("overlayNsfwLogo", false),
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

    private fun canonicalItem(
        item: GameItem,
        hashOverride: PosterHashes?,
        localHashForUrl: (String) -> String?
    ): String {
        val posterToken = shareableUrl(item.posterUrl)
            ?: hashOverride?.posterHash
            ?: item.posterUrl?.let(localHashForUrl)
            ?: ""
        val nsfwPosterToken = shareableUrl(item.nsfwPosterUrl)
            ?: hashOverride?.nsfwPosterHash
            ?: item.nsfwPosterUrl?.let(localHashForUrl)
            ?: ""

        return listOf(
            item.id,
            item.title,
            item.type.name,
            item.isGame.toString(),
            item.packageName.orEmpty(),
            item.activityName.orEmpty(),
            item.launchUrl.orEmpty(),
            item.gfnCmsId.orEmpty(),
            posterToken,
            nsfwPosterToken,
            shareableUrl(item.nsfwLogoUrl).orEmpty(),
            item.overlayNsfwLogo.toString(),
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
    }

    private fun shareableUrl(url: String?): String? =
        url?.takeUnless { it.startsWith("file:") || it.startsWith("content:") }
            ?.takeIf { it.isNotBlank() }

    internal fun localFileSha256(url: String): String? {
        if (!url.startsWith("file:")) return null
        val path = runCatching { Uri.parse(url).path }.getOrNull() ?: return null
        val file = File(path)
        if (!file.exists() || !file.isFile || file.length() <= 0L) return null
        return runCatching { sha256Hex(file.readBytes()) }.getOrNull()
    }

    internal fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private const val RECORD_SEPARATOR = "\u001e"
    private const val FIELD_SEPARATOR = "\u001f"
}
