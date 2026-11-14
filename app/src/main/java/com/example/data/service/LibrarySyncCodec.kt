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
 * - `isPinned`, `isFavorite`, `favoriteOrder`, play counts and last-played times
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
        val posterHashes: Map<String, PosterHashes> = emptyMap(),
        val appCustomizations: List<GameItem> = emptyList(),
        val appPosterHashes: Map<String, PosterHashes> = emptyMap()
    )

    const val FORMAT_NAME = "nexus_library"
    const val FORMAT_VERSION = 2

    fun isSyncable(item: GameItem): Boolean = when (item.type) {
        GameType.GEFORCE_NOW, GameType.MOONLIGHT, GameType.CUSTOM_CLOUD -> true
        GameType.SHORTCUT -> item.isGame
        else -> false
    }

    /**
     * Installed apps stay tied to each device's installed packages, so they never create
     * ghost tiles on other devices. However, user customizations and assigned posters on
     * installed apps/games do travel in `appCustomizations` and apply wherever the same
     * package is installed.
     */
    fun isSyncableAppCustomization(item: GameItem): Boolean =
        item.type == GameType.INSTALLED_APP &&
            !item.isDeleted &&
            (
                item.isCustomized ||
                    !item.posterUrl.isNullOrBlank() ||
                    !item.nsfwPosterUrl.isNullOrBlank() ||
                    item.overlayLogo ||
                    !item.logoUrl.isNullOrBlank() ||
                    item.overlayNsfwLogo ||
                    !item.nsfwLogoUrl.isNullOrBlank() ||
                    item.isHidden
                )

    /** Shareable rows in a stable order. */
    fun syncable(rows: List<GameItem>): List<GameItem> =
        rows.filter(::isSyncable).sortedBy { it.id }

    fun syncableAppCustomizations(rows: List<GameItem>): List<GameItem> =
        rows.filter(::isSyncableAppCustomization).sortedBy { it.id }

    fun buildDocument(
        items: List<GameItem>,
        updatedAt: Long,
        localHashForUrl: (String) -> String? = ::localFileSha256,
        appCustomizations: List<GameItem> = emptyList(),
        appPosterHashOverrides: Map<String, PosterHashes> = emptyMap()
    ): JSONObject {
        val payload = JSONArray()
        items.sortedBy { it.id }.forEach { payload.put(itemToJson(it, emptyMap(), localHashForUrl)) }
        return JSONObject()
            .put("format", FORMAT_NAME)
            .put("version", FORMAT_VERSION)
            .put("updatedAt", updatedAt)
            // Kept for compatibility with the old export format.
            .put("exportedAt", updatedAt)
            .put("localPosters", true)
            .put("items", payload)
            .apply {
                if (appCustomizations.isNotEmpty()) {
                    val appArray = JSONArray()
                    appCustomizations.sortedBy { it.id }.forEach { app ->
                        appArray.put(itemToJson(app, appPosterHashOverrides, localHashForUrl))
                    }
                    put("appCustomizations", appArray)
                }
            }
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
        val appArray: JSONArray?
        if (trimmed.startsWith("[")) {
            // Bare array written by older builds; its age and deletions are unknown.
            array = parseArrayOrFail(trimmed)
            updatedAt = 0L
            version = 0
            tracksLocalPosters = false
            appArray = null
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
            appArray = document.optJSONArray("appCustomizations")
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
        val appPosterHashes = mutableMapOf<String, PosterHashes>()
        val appCustomizations = buildList {
            if (appArray != null) {
                for (index in 0 until appArray.length()) {
                    val obj = appArray.optJSONObject(index)
                    val app = jsonToItem(obj)?.takeIf { it.type == GameType.INSTALLED_APP } ?: continue
                    add(app)
                    if (obj != null) {
                        val pHash = obj.optNullableString("posterHash")
                        val nHash = obj.optNullableString("nsfwPosterHash")
                        if (pHash != null || nHash != null) {
                            appPosterHashes[app.id] = PosterHashes(pHash, nHash)
                        }
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
            posterHashes = posterHashes,
            appCustomizations = appCustomizations,
            appPosterHashes = appPosterHashes
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

    fun librarySignature(
        items: List<GameItem>,
        appCustomizations: List<GameItem>,
        itemHashOverrides: Map<String, PosterHashes> = emptyMap(),
        appHashOverrides: Map<String, PosterHashes> = emptyMap(),
        localHashForUrl: (String) -> String? = ::localFileSha256
    ): String {
        val itemsPart = items
            .map { canonicalItem(it, itemHashOverrides[it.id], localHashForUrl) }
            .sorted()
        val appsPart = appCustomizations
            .map { canonicalItem(it, appHashOverrides[it.id], localHashForUrl) }
            .sorted()
        val combined = (itemsPart + listOf(SECTION_SEPARATOR) + appsPart).joinToString(RECORD_SEPARATOR)
        return sha256Hex(combined.toByteArray(Charsets.UTF_8))
    }

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
        hashOverrides: Map<String, PosterHashes>,
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
        val override = hashOverrides[item.id]
        val remotePoster = shareableUrl(item.posterUrl)
        if (remotePoster != null) {
            put("posterUrl", remotePoster)
        } else {
            (item.posterUrl?.let(localHashForUrl) ?: override?.posterHash)?.let { put("posterHash", it) }
        }
        val remoteNsfwPoster = shareableUrl(item.nsfwPosterUrl)
        if (remoteNsfwPoster != null) {
            put("nsfwPosterUrl", remoteNsfwPoster)
        } else {
            (item.nsfwPosterUrl?.let(localHashForUrl) ?: override?.nsfwPosterHash)?.let {
                put("nsfwPosterHash", it)
            }
        }
        shareableUrl(item.logoUrl)?.let { put("logoUrl", it) }
        if (item.overlayLogo) put("overlayLogo", true)
        shareableUrl(item.nsfwLogoUrl)?.let { put("nsfwLogoUrl", it) }
        if (item.overlayNsfwLogo) put("overlayNsfwLogo", true)
        shareableUrl(item.heroBannerUrl)?.let { put("heroBannerUrl", it) }
        shareableUrl(item.iconUrl)?.let { put("iconUrl", it) }
        put("genre", item.genre)
        put("publisher", item.publisher)
        put("description", item.description)
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
                logoUrl = json.optNullableString("logoUrl"),
                overlayLogo = json.optBoolean("overlayLogo", false),
                nsfwLogoUrl = json.optNullableString("nsfwLogoUrl"),
                overlayNsfwLogo = json.optBoolean("overlayNsfwLogo", false),
                heroBannerUrl = json.optNullableString("heroBannerUrl"),
                iconUrl = json.optNullableString("iconUrl"),
                genre = json.optString("genre", "Game"),
                publisher = json.optString("publisher", ""),
                description = json.optString("description", ""),
                // Favorites and their display order are strictly per-device.
                isFavorite = false,
                favoriteOrder = 0,
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
            shareableUrl(item.logoUrl).orEmpty(),
            item.overlayLogo.toString(),
            shareableUrl(item.nsfwLogoUrl).orEmpty(),
            item.overlayNsfwLogo.toString(),
            shareableUrl(item.heroBannerUrl).orEmpty(),
            shareableUrl(item.iconUrl).orEmpty(),
            item.genre,
            item.publisher,
            item.description,
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
    private const val SECTION_SEPARATOR = "\u001d"
}
