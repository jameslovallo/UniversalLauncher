package com.example.data.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class ArtworkOption(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val fullUrl: String,
    val type: ArtworkType, // POSTER (vertical 2:3 / 600x900), HERO (horizontal 16:9), ICON (square)
    val authorOrSource: String = "SteamGridDB",
    val isNsfw: Boolean = false
)

enum class ArtworkType {
    POSTER,
    HERO_BANNER,
    ICON
}

/**
 * SteamGridDB artwork lookup.
 *
 * The site's own game and asset search run through keyless public endpoints
 * (`/api/public/search/autocomplete` and `/api/public/search/assets`). Those are used
 * automatically when an app is detected, so no user API key is required. A user-supplied
 * key still gets the documented v2 endpoints first and falls back to the public path.
 */
object SteamGridDbService {
    private const val PUBLIC_BASE_URL = "https://www.steamgriddb.com/api/public"
    private const val V2_BASE_URL = "https://www.steamgriddb.com/api/v2"
    private const val SITE_URL = "https://www.steamgriddb.com"
    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    private data class GameMatch(
        val id: Int,
        val name: String,
        val releaseDate: Long
    )

    /**
     * Search for portrait box art. Never returns curated or example artwork: an empty list
     * simply means SteamGridDB has no match for the query.
     *
     * When [includeNsfw] is true the search returns only NSFW grids; when false it returns
     * only SFW grids.
     */
    suspend fun searchArtwork(
        query: String,
        apiKey: String? = null,
        includeNsfw: Boolean = false
    ): List<ArtworkOption> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isBlank()) return@withContext emptyList()

            if (!apiKey.isNullOrBlank()) {
                val keyed = searchArtworkWithApiKey(trimmed, apiKey, includeNsfw)
                if (keyed.isNotEmpty()) return@withContext keyed
            }

            searchArtworkWithPublicApi(trimmed, includeNsfw)
        }

    /**
     * Resolve the best matching poster for an automatically detected game. Matching is
     * intentionally strict (normalised exact title) so unrelated apps are not given art.
     */
    suspend fun findPosterForGame(title: String): String? = withContext(Dispatchers.IO) {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return@withContext null

        val match = findGameWithPublicApi(trimmed, requireExact = true) ?: return@withContext null
        val portraits = fetchPublicAssets(match.id, limit = 16).filter(::isPortraitGrid)
        val asset = portraits.firstOrNull {
            it.optInt("width", 0) == 600 && it.optInt("height", 0) == 900
        } ?: portraits.firstOrNull() ?: return@withContext null
        val url = asset.optString("url").ifBlank { asset.optString("thumb") }
        url.takeIf { it.isNotBlank() }
    }

    /**
     * Resolve the game's official transparent logo from SteamGridDB (preferring `official`
     * style and static PNGs, falling back to any available SFW logo for the game).
     */
    suspend fun findLogoForGame(
        query: String,
        apiKey: String? = null
    ): String? = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext null

        if (!apiKey.isNullOrBlank()) {
            val keyed = findLogoWithApiKey(trimmed, apiKey)
            if (!keyed.isNullOrBlank()) return@withContext keyed
        }

        val match = findGameWithPublicApi(trimmed, requireExact = false) ?: return@withContext null
        val logos = requestPublicAssets(
            gameId = match.id,
            limit = 25,
            includeRestricted = false,
            assetType = "logo",
            order = "score_desc"
        ).filterNot { it.optBoolean("nsfw") }
            .ifEmpty {
                requestPublicAssets(
                    gameId = match.id,
                    limit = 25,
                    includeRestricted = true,
                    assetType = "logo",
                    order = "score_desc"
                ).filterNot { it.optBoolean("nsfw") }
            }
        pickBestLogoUrl(logos)
    }

    private fun findLogoWithApiKey(query: String, apiKey: String): String? {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val searchRequest = authorizedRequest("$V2_BASE_URL/search/autocomplete/$encoded", apiKey)
        val matches = executeJson(searchRequest)?.let(::parseMatches).orEmpty()
        val match = selectMatch(query, matches, requireExact = false) ?: return null

        val url = "$V2_BASE_URL/logos/game/${match.id}"
        val json = executeJson(authorizedRequest(url, apiKey)) ?: return null
        val data = json.optJSONArray("data") ?: return null
        val logos = (0 until data.length())
            .mapNotNull { data.optJSONObject(it) }
            .filterNot { it.optBoolean("nsfw") }
        return pickBestLogoUrl(logos)
    }

    private fun pickBestLogoUrl(logos: List<JSONObject>): String? {
        val sorted = logos.sortedWith(
            compareByDescending<JSONObject> {
                it.optString("style").equals("official", ignoreCase = true)
            }.thenByDescending {
                !it.optBoolean("is_animated", false) &&
                    it.optString("mime").equals("image/png", ignoreCase = true)
            }.thenByDescending {
                it.optInt("upvotes", it.optInt("score", 0))
            }
        )
        val best = sorted.firstOrNull() ?: return null
        val url = best.optString("url").ifBlank { best.optString("thumb") }
        return url.takeIf { it.isNotBlank() }
    }

    private fun searchArtworkWithPublicApi(
        query: String,
        includeNsfw: Boolean
    ): List<ArtworkOption> {
        val match = findGameWithPublicApi(query, requireExact = false) ?: return emptyList()
        val results = if (includeNsfw) {
            requestPublicAssets(match.id, limit = 60, includeRestricted = true)
                .filter(::isPortraitGrid)
                .filter { it.optBoolean("nsfw") }
        } else {
            val safe = requestPublicAssets(match.id, limit = 40, includeRestricted = false)
                .filter(::isPortraitGrid)
                .filterNot { it.optBoolean("nsfw") }
            safe.ifEmpty {
                requestPublicAssets(match.id, limit = 40, includeRestricted = true)
                    .filter(::isPortraitGrid)
                    .filterNot { it.optBoolean("nsfw") }
            }
        }
        return results.portraitFirst().take(30).toArtworkOptions(match.name)
    }

    private fun searchArtworkWithApiKey(
        query: String,
        apiKey: String,
        includeNsfw: Boolean
    ): List<ArtworkOption> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val searchRequest = authorizedRequest("$V2_BASE_URL/search/autocomplete/$encoded", apiKey)
        val matches = executeJson(searchRequest)?.let(::parseMatches).orEmpty()
        val match = selectMatch(query, matches, requireExact = false) ?: return emptyList()

        // The v2 `nsfw` flag is an exclusive filter: `true` returns only adult grids and
        // omitting it returns only safe grids.
        val results = if (includeNsfw) {
            val adult = fetchV2Grids(match.id, apiKey, adultOnly = true)
            if (adult.any { it.has("nsfw") }) {
                adult.filter { it.optBoolean("nsfw") }
            } else {
                adult
            }
        } else {
            fetchV2Grids(match.id, apiKey, adultOnly = false)
                .filterNot { it.optBoolean("nsfw") }
        }
        return results.portraitFirst().take(30).toArtworkOptions(match.name)
    }

    private fun fetchV2Grids(
        gameId: Int,
        apiKey: String,
        adultOnly: Boolean
    ): List<JSONObject> {
        val url = buildString {
            append("$V2_BASE_URL/grids/game/$gameId")
            if (adultOnly) append("?nsfw=true")
        }
        val json = executeJson(authorizedRequest(url, apiKey)) ?: return emptyList()
        val data = json.optJSONArray("data") ?: return emptyList()
        return (0 until data.length())
            .mapNotNull { data.optJSONObject(it) }
            .filter(::isPortraitGrid)
    }

    /** Conventional 600x900 box art first, preserving the surrounding group order. */
    private fun List<JSONObject>.portraitFirst(): List<JSONObject> =
        sortedByDescending { it.optInt("width", 0) == 600 && it.optInt("height", 0) == 900 }

    private fun List<JSONObject>.toArtworkOptions(gameName: String): List<ArtworkOption> =
        mapIndexedNotNull { index, item ->
            val url = item.optString("url").ifBlank { item.optString("thumb") }
            if (url.isBlank()) return@mapIndexedNotNull null
            ArtworkOption(
                id = "sgdb_${item.optInt("id", index)}",
                title = "$gameName Poster #${index + 1}",
                thumbnailUrl = item.optString("thumb").ifBlank { url },
                fullUrl = url,
                type = ArtworkType.POSTER,
                authorOrSource = item.optJSONObject("author")?.optString("name", "SteamGridDB")
                    ?: "SteamGridDB",
                isNsfw = item.optBoolean("nsfw")
            )
        }

    private fun authorizedRequest(url: String, apiKey: String): Request =
        Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("User-Agent", BROWSER_USER_AGENT)
            .addHeader("Accept", "application/json")
            .build()

    private fun findGameWithPublicApi(query: String, requireExact: Boolean): GameMatch? {
        val url = "$PUBLIC_BASE_URL/search/autocomplete?term=${URLEncoder.encode(query, "UTF-8")}"
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", BROWSER_USER_AGENT)
            .addHeader("Accept", "application/json, text/plain, */*")
            .addHeader("Referer", "$SITE_URL/")
            .build()
        val json = executeJson(request) ?: return null
        return selectMatch(query, parseMatches(json), requireExact)
    }

    private fun fetchPublicAssets(
        gameId: Int,
        limit: Int,
        includeNsfw: Boolean = false
    ): List<JSONObject> {
        if (includeNsfw) {
            // The public filter includes restricted assets when nsfw=true; the caller
            // orders the adult grids after the safe ones.
            return requestPublicAssets(gameId, limit, includeRestricted = true)
        }
        val assets = requestPublicAssets(gameId, limit, includeRestricted = false)
        if (assets.isNotEmpty()) return assets
        // A strict NSFW/humor filter can hide an otherwise valid poster; retry inclusive.
        return requestPublicAssets(gameId, limit, includeRestricted = true)
    }

    private fun requestPublicAssets(
        gameId: Int,
        limit: Int,
        includeRestricted: Boolean,
        assetType: String = "grid",
        order: String = "age_desc"
    ): List<JSONObject> {
        val payload = JSONObject().apply {
            put("styles", JSONArray().put("all"))
            put("languages", JSONArray().put("all"))
            put("dimensions", JSONArray().put("all"))
            put("formats", JSONArray().put("all"))
            put("order", order)
            put("game_id", JSONArray().put(gameId))
            put("static", true)
            put("animated", true)
            put("nsfw", includeRestricted)
            put("epilepsy", includeRestricted)
            put("humor", includeRestricted)
            put("untagged", true)
            put("asset_type", assetType)
            put("page", 0)
            put("limit", limit)
            put("user_steam64", JSONObject.NULL)
            put("user_steam64_likes", JSONObject.NULL)
        }

        val request = Request.Builder()
            .url("$PUBLIC_BASE_URL/search/assets")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .addHeader("User-Agent", BROWSER_USER_AGENT)
            .addHeader("Accept", "application/json, text/plain, */*")
            .addHeader("Origin", SITE_URL)
            .addHeader("Referer", "$SITE_URL/game/$gameId")
            .build()

        val json = executeJson(request) ?: return emptyList()
        if (!json.optBoolean("success", false)) return emptyList()
        val assets = json.optJSONObject("data")?.optJSONArray("assets") ?: return emptyList()
        return (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
    }

    private fun parseMatches(json: JSONObject): List<GameMatch> {
        if (!json.optBoolean("success", false)) return emptyList()
        val data = json.optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).mapNotNull { index ->
            val item = data.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optInt("id", 0)
            val name = item.optString("name")
            if (id == 0 || name.isBlank()) return@mapNotNull null
            GameMatch(id, name, item.optLong("release_date", 0L))
        }
    }

    private fun selectMatch(
        query: String,
        matches: List<GameMatch>,
        requireExact: Boolean
    ): GameMatch? {
        if (matches.isEmpty()) return null
        val target = normalizeTitle(query)
        if (target.isEmpty()) return matches.firstOrNull()

        val exact = matches.filter { normalizeTitle(it.name) == target }
        if (exact.isNotEmpty()) {
            // Prefer the newest release when several games share a title (e.g. remakes).
            return exact.maxByOrNull { it.releaseDate }
        }
        if (requireExact) {
            // Some entries carry a publisher prefix, e.g. "Tom Clancy's The Division
            // Resurgence" for the installed title "The Division: Resurgence". Only accept
            // meaningful suffixes so short names cannot match unrelated series entries.
            if (target.length >= 8) {
                val prefixed = matches.filter { normalizeTitle(it.name).endsWith(target) }
                if (prefixed.isNotEmpty()) return prefixed.maxByOrNull { it.releaseDate }
            }
            return null
        }

        val prefix = matches.filter { normalizeTitle(it.name).startsWith(target) }
        if (prefix.isNotEmpty()) return prefix.minByOrNull { normalizeTitle(it.name).length }

        val contains = matches.filter { normalizeTitle(it.name).contains(target) }
        if (contains.isNotEmpty()) return contains.minByOrNull { normalizeTitle(it.name).length }

        return matches.firstOrNull()
    }

    private fun normalizeTitle(value: String): String = buildString {
        for (char in value.lowercase()) {
            if (char.isLetterOrDigit()) append(char)
        }
    }

    private fun isPortraitGrid(asset: JSONObject): Boolean {
        val width = asset.optInt("width", 0)
        val height = asset.optInt("height", 0)
        if (width <= 0 || height <= 0) return false
        val ratio = width.toDouble() / height.toDouble()
        return ratio in 0.55..0.8
    }

    private fun executeJson(request: Request): JSONObject? = try {
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                null
            } else {
                response.body?.string()?.let { body ->
                    runCatching { JSONObject(body) }.getOrNull()
                }
            }
        }
    } catch (_: Exception) {
        null
    }
}
