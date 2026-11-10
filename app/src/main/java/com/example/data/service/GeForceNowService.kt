package com.example.data.service

import com.example.data.model.GameItem
import com.example.data.model.GameType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

object GeForceNowService {
    private const val GFN_BASE_PLAY_URL = "https://play.geforcenow.com"
    // GFNDB mirrors NVIDIA's public GeForce NOW catalog. All 6,000+ titles are served
    // as a static JSON index with NVIDIA image URLs that contain the official CMS id.
    private const val GFNDB_INDEX_URL = "https://gfndb.info/data/index.json"
    private const val GFNDB_GAME_BASE_URL = "https://gfndb.info/game"

    // The catalog index only ships square icons. Each game's detail page carries
    // the official NVIDIA portrait box-art URL (GAME_BOX_ART_01_<hash>.jpg), so we
    // resolve that for search results and cache it.
    private const val MAX_SEARCH_RESULTS = 40
    private const val MAX_CONCURRENT_ART_FETCHES = 4
    private val BOX_ART_REGEX = Regex(
        """https://img\.nvidiagrid\.net/apps/[0-9a-fA-F-]+/ZZ/GAME_BOX_ART_01_[0-9a-fA-F-]+\.(?:jpg|png)"""
    )

    // UTM parameters are required by NVIDIA's GFN Deep Linking SDK.
    private const val UTM_SOURCE = "universal_launcher"
    private const val UTM_CAMPAIGN = "gfn_catalog"

    private val httpClient = OkHttpClient.Builder().build()

    // Cached catalog so repeated searches don't re-download the full index.
    private var cachedCatalog: List<GfnCatalogEntry>? = null
    private var cacheTimestamp: Long = 0L
    private const val CACHE_TTL_MS: Long = 24 * 60 * 60 * 1000L // 24 hours

    // slug -> resolved box-art URL (per-process cache).
    private val boxArtCache = ConcurrentHashMap<String, String>()

    /**
     * Canonical GeForce NOW catalog entry.
     *
     * [gfnCmsId] is the official NVIDIA CMS identifier extracted from the icon URL.
     * [launchUrl] is always derived from [gfnCmsId] via [createGfnLaunchUrl] so the
     * deep link follows NVIDIA's documented format instead of being fabricated.
     */
    data class GfnCatalogEntry(
        val title: String,
        val slug: String,
        val gfnCmsId: String,
        val iconUrl: String,
        val developer: String?,
        val stores: List<String>,
        val tier: String?,
        val playType: String?,
        val gamePass: Boolean
    ) {
        fun toGameItem(posterOverride: String? = null): GameItem = GameItem(
            id = "gfn_$gfnCmsId",
            title = title,
            type = GameType.GEFORCE_NOW,
            gfnCmsId = gfnCmsId,
            launchUrl = createGfnLaunchUrl(gfnCmsId),
            posterUrl = posterOverride ?: iconUrl,
            heroBannerUrl = null,
            genre = buildGenre(),
            publisher = developer?.ifBlank { "Unknown" } ?: "Unknown",
            description = buildDescription(),
            tags = buildTags(),
            customArtworkSource = "GFN"
        )

        private fun buildGenre(): String {
            val parts = mutableListOf<String>()
            playType?.lowercase()?.let { parts.add(it) }
            stores.firstOrNull()?.lowercase()?.let { parts.add(it) }
            return if (parts.isEmpty()) "GeForce NOW Title" else parts.joinToString(", ")
        }

        private fun buildDescription(): String {
            val storeStr = stores.joinToString(", ")
            val playTypeStr = playType?.lowercase() ?: "unknown"
            val tierStr = if (tier != null) " ($tier tier)" else ""
            return "Stream $title on GeForce NOW via $storeStr ($playTypeStr$tierStr)."
        }

        private fun buildTags(): String {
            return listOfNotNull(
                "GeForce NOW",
                "Cloud",
                playType,
                tier,
                if (gamePass) "Game Pass" else null
            ).joinToString(", ")
        }
    }

    /** Curated GeForce NOW library populated from the live catalog on first use. */
    val DEFAULT_GFN_LIBRARY: List<GameItem>
        get() = cachedCatalog?.map { it.toGameItem() } ?: emptyList()

    /**
     * Build a GeForce NOW deep link following NVIDIA's documented format:
     * https://play.geforcenow.com/games?game-id=<id>&utm_source=...&utm_campaign=...
     *
     * @param gameId the GeForce NOW CMS / game identifier
     */
    fun createGfnLaunchUrl(gameId: String?): String {
        val id = gameId?.trim()
        return if (id.isNullOrBlank()) {
            GFN_BASE_PLAY_URL
        } else {
            "$GFN_BASE_PLAY_URL/games?game-id=$id&utm_source=$UTM_SOURCE&utm_campaign=$UTM_CAMPAIGN"
        }
    }

    /**
     * Fetch and cache the full GeForce NOW catalog from GFNDB's index.
     */
    private suspend fun fetchCatalog(): List<GfnCatalogEntry> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (cachedCatalog != null && now - cacheTimestamp < CACHE_TTL_MS) {
            return@withContext cachedCatalog!!
        }

        val request = Request.Builder()
            .url(GFNDB_INDEX_URL)
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", "UniversalLauncher/1.0")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected GFNDB response code: ${response.code}")
            }
            val body = response.body?.string()
                ?: throw IOException("Empty GFNDB response body")
            val jsonArray = JSONArray(body)
            val entries = mutableListOf<GfnCatalogEntry>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val title = obj.optString("title", "")
                val slug = obj.optString("slug", "")
                val iconUrlRaw: String? = obj.optString("icon", "")
                val iconUrl = iconUrlRaw ?: ""

                // Extract the NVIDIA CMS id from the icon URL path:
                // https://img.nvidiagrid.net/apps/<UUID>/ZZ/GAME_ICON_01_<...>.png
                val cmsId = iconUrl.substringAfter("/apps/").substringBefore("/ZZ/")

                val developerRaw: String? = obj.optString("developer")
                val developer = developerRaw?.takeIf { it.isNotBlank() }
                val storesArray = obj.optJSONArray("stores")
                val stores = mutableListOf<String>()
                if (storesArray != null) {
                    for (j in 0 until storesArray.length()) {
                        stores.add(storesArray.getString(j))
                    }
                }
                val tierRaw: String? = obj.optString("tier")
                val tier = tierRaw?.takeIf { it.isNotBlank() }
                val playTypeRaw: String? = obj.optString("playType")
                val playType = playTypeRaw?.takeIf { it.isNotBlank() }
                val gamePass = obj.optBoolean("gp", false)

                if (title.isNotBlank() && cmsId.isNotBlank()) {
                    entries.add(
                        GfnCatalogEntry(
                            title = title,
                            slug = slug,
                            gfnCmsId = cmsId,
                            iconUrl = iconUrl,
                            developer = developer,
                            stores = stores,
                            tier = tier,
                            playType = playType,
                            gamePass = gamePass
                        )
                    )
                }
            }
            cachedCatalog = entries
            cacheTimestamp = now
            return@use entries
        }
    }

    /**
     * Search titles from the live GeForce NOW catalog.
     *
     * The GFNDB index only contains square icons, so the real portrait box art is
     * resolved from each matched title's detail page (bounded + cached).
     *
     * Resolution order:
     * 1. Live catalog match (title / developer / store / slug) — accurate art + deep link.
     * 2. Not found — returns an empty list rather than fabricating artwork or deep links.
     */
    suspend fun searchOrSyncCatalog(query: String? = null): List<GameItem> = withContext(Dispatchers.IO) {
        val catalog = fetchCatalog()

        val matches = if (query.isNullOrBlank()) {
            catalog
        } else {
            val lowercaseQuery = query.lowercase().trim()
            catalog.filter { entry ->
                entry.title.lowercase().contains(lowercaseQuery) ||
                        entry.slug.lowercase().contains(lowercaseQuery) ||
                        entry.developer?.lowercase()?.contains(lowercaseQuery) == true ||
                        entry.stores.any { it.lowercase().contains(lowercaseQuery) }
            }
        }

        if (matches.isEmpty()) {
            // The title is not in the GeForce NOW catalog. Return empty so the caller can
            // surface an honest "not found" state instead of showing fabricated art/links.
            return@withContext emptyList()
        }

        // Keep the payload bounded: resolving art is one small request per title.
        val limited = matches.take(MAX_SEARCH_RESULTS)
        val semaphore = Semaphore(MAX_CONCURRENT_ART_FETCHES)
        coroutineScope {
            limited.map { entry ->
                async {
                    semaphore.withPermit {
                        entry.toGameItem(posterOverride = fetchBoxArtUrl(entry.slug))
                    }
                }
            }.awaitAll()
        }
    }

    /**
     * Resolve the official box art for a GeForce NOW game already stored in the library.
     * Used to backfill entries that were saved with the square catalog icon.
     */
    suspend fun resolveBoxArtForCmsId(cmsId: String?): String? = withContext(Dispatchers.IO) {
        val id = cmsId?.trim()
        if (id.isNullOrBlank()) return@withContext null
        try {
            val catalog = fetchCatalog()
            val entry = catalog.firstOrNull { it.gfnCmsId == id } ?: return@withContext null
            fetchBoxArtUrl(entry.slug)
        } catch (e: IOException) {
            null
        }
    }

    /**
     * Read the GFNDB game detail page and extract NVIDIA's portrait box-art URL.
     * Cached per slug; returns null when unavailable so callers fall back to the icon.
     */
    private suspend fun fetchBoxArtUrl(slug: String): String? {
        if (slug.isBlank()) return null
        boxArtCache[slug]?.let { return it }
        return try {
            val request = Request.Builder()
                .url("$GFNDB_GAME_BASE_URL/$slug/")
                .addHeader("Accept", "text/html")
                .addHeader("User-Agent", "UniversalLauncher/1.0")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val html = response.body?.string() ?: return@use null
                val boxArt = BOX_ART_REGEX.find(html)?.value
                if (boxArt != null) boxArtCache[slug] = boxArt
                boxArt
            }
        } catch (e: IOException) {
            null
        }
    }
}
