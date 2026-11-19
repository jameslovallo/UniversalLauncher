package com.example.data.service

import com.example.data.model.GameItem
import com.example.data.model.GameType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object AmazonLunaService {
    const val LUNA_BASE_URL = "https://luna.amazon.com"
    const val LUNA_PACKAGE_NAME = "com.amazon.spiderpork"

    internal const val TEMPO_GET_PAGE_URL =
        "https://proxy-prod.tempo.digital.a2z.com/getPage"
    private const val TEMPO_CLIENT_REACT_VERSION = "1.0.37488.0"
    private const val TEMPO_MARKETPLACE_ID = "ATVPDKIKX0DER"
    private const val TEMPO_SEARCH_PAGE_SIZE = 60

    private const val PRIME_WP_JSON_URL =
        "https://clouddosage.com/wp-json/wp/v2/gamelists?slug=free-games-on-amazon-luna-with-amazon-prime"
    private const val PRIME_FALLBACK_WP_JSON_URL =
        "https://clouddosage.com/wp-json/wp/v2/posts?slug=prime-gaming-luna"
    private const val PRIME_PAGE_URL =
        "https://clouddosage.com/gamelists/free-games-on-amazon-luna-with-amazon-prime/"
    private const val PRIME_FALLBACK_PAGE_URL =
        "https://clouddosage.com/prime-gaming-luna/"

    private const val CATALOG_WP_JSON_URL =
        "https://clouddosage.com/wp-json/wp/v2/gamelists?slug=amazon-luna-games"
    private const val CATALOG_PAGE_URL =
        "https://clouddosage.com/gamelists/amazon-luna-games/"

    private const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    private const val CACHE_TTL_MS: Long = 24 * 60 * 60 * 1000L
    private const val MAX_CONCURRENT_POSTER_FETCHES = 4
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val httpClient = HttpClients.shared.newBuilder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    private val posterSemaphore = Semaphore(MAX_CONCURRENT_POSTER_FETCHES)
    private val posterCache = ConcurrentHashMap<String, String>()

    @Volatile
    private var cachedPrimeEntries: List<LunaCatalogEntry>? = null

    @Volatile
    private var primeCacheTimestamp: Long = 0L

    @Volatile
    private var cachedFullCatalog: List<LunaCatalogEntry>? = null

    @Volatile
    private var fullCatalogTimestamp: Long = 0L

    data class LunaCatalogEntry(
        val id: String,
        val title: String,
        val slug: String,
        val webUrl: String = LUNA_BASE_URL,
        val imageUrl: String? = null,
        val heroBannerUrl: String? = null,
        val publisher: String? = null,
        val genres: List<String> = emptyList(),
        val releaseYear: String? = null,
        val includedWithPrime: Boolean = false
    ) {
        fun toGameItem(posterOverride: String? = null): GameItem {
            val cachedPoster = posterCache[title.lowercase(Locale.ROOT)]
            val resolvedPoster = posterOverride ?: imageUrl ?: cachedPoster
            val artworkSource = when {
                posterOverride != null -> "STEAMGRIDDB"
                !imageUrl.isNullOrBlank() -> "LUNA"
                cachedPoster != null -> "STEAMGRIDDB"
                else -> "LUNA"
            }
            val resolvedGenre = when {
                genres.isNotEmpty() -> genres.joinToString(", ")
                includedWithPrime -> "Included with Prime"
                else -> "Amazon Luna"
            }
            val resolvedPublisher = publisher?.takeIf { it.isNotBlank() } ?: "Amazon Luna"
            val yearSuffix = releaseYear?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
            return GameItem(
                id = "luna_$id",
                title = title,
                type = GameType.AMAZON_LUNA,
                isGame = true,
                launchUrl = createLunaLaunchIntentUri(webUrl),
                posterUrl = resolvedPoster,
                heroBannerUrl = heroBannerUrl,
                genre = resolvedGenre,
                publisher = resolvedPublisher,
                description = if (includedWithPrime) {
                    "Play $title$yearSuffix on Amazon Luna (included with Amazon Prime)."
                } else {
                    "Stream $title$yearSuffix on Amazon Luna."
                },
                tags = buildList {
                    add("Amazon Luna")
                    add("Cloud")
                    if (includedWithPrime) add("Amazon Prime")
                    genres.forEach { if (it.isNotBlank()) add(it) }
                }.distinct().joinToString(", "),
                customArtworkSource = artworkSource
            )
        }
    }

    /**
     * Builds an Android `intent:` URI targeting the native Amazon Luna app (`com.amazon.spiderpork`),
     * with [S.browser_fallback_url] pointing to the Luna web URL.
     */
    fun createLunaLaunchIntentUri(targetUrl: String? = null): String {
        val raw = targetUrl?.trim()?.takeIf {
            it.startsWith("https://", ignoreCase = true) ||
                it.startsWith("http://", ignoreCase = true)
        } ?: LUNA_BASE_URL
        val parsed = runCatching { URI(raw) }.getOrNull()
        val scheme = parsed?.scheme?.takeIf { it.isNotBlank() } ?: "https"
        val host = parsed?.host?.takeIf { it.isNotBlank() } ?: "luna.amazon.com"
        val rawPath = parsed?.rawPath.orEmpty()
        val rawQuery = parsed?.rawQuery?.takeIf { it.isNotBlank() }?.let { "?$it" }.orEmpty()
        val pathPart = if (rawPath.isBlank()) "/" else rawPath
        val cleanFallback = if (pathPart == "/" && rawQuery.isBlank()) {
            "$scheme://$host"
        } else {
            "$scheme://$host$pathPart$rawQuery"
        }
        return "intent://$host$pathPart$rawQuery#Intent;scheme=$scheme;package=$LUNA_PACKAGE_NAME;S.browser_fallback_url=$cleanFallback;end;"
    }

    /**
     * Extracts the fallback HTTP(S) URL from an Amazon Luna `intent:` URI or direct URL.
     */
    fun extractWebUrl(launchUriOrUrl: String?): String {
        val trimmed = launchUriOrUrl?.trim().orEmpty()
        if (trimmed.isEmpty()) return LUNA_BASE_URL
        if (trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true)
        ) {
            return trimmed
        }
        if (trimmed.startsWith("intent://", ignoreCase = true)) {
            val fallbackMarker = "S.browser_fallback_url="
            val idx = trimmed.indexOf(fallbackMarker)
            if (idx >= 0) {
                val valueStart = idx + fallbackMarker.length
                val endIdx = trimmed.indexOf(';', valueStart).let { if (it >= 0) it else trimmed.length }
                val fallback = trimmed.substring(valueStart, endIdx)
                if (fallback.startsWith("http://", ignoreCase = true) ||
                    fallback.startsWith("https://", ignoreCase = true)
                ) {
                    return fallback
                }
            }
            val fragmentIdx = trimmed.indexOf("#Intent")
            val authorityAndPath = trimmed.substring(
                "intent://".length,
                if (fragmentIdx >= 0) fragmentIdx else trimmed.length
            )
            if (authorityAndPath.isNotBlank()) {
                return "https://$authorityAndPath"
            }
        }
        return LUNA_BASE_URL
    }

    /** Returns all Amazon Luna games currently available for free with Amazon Prime. */
    suspend fun getPrimeIncludedGames(): Result<List<GameItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val entries = fetchPrimeCatalogEntries()
            entries.map { it.toGameItem() }
        }
    }

    /**
     * Searches the live Amazon Luna catalog via Luna's Tempo search API and merges any
     * local substring matches from the cached Prime-included and browse catalogs.
     * Returns all Prime-included games when [query] is blank.
     */
    suspend fun searchCatalog(query: String?): Result<List<GameItem>> = withContext(Dispatchers.IO) {
        val trimmed = query?.trim().orEmpty()
        if (trimmed.isBlank()) {
            return@withContext getPrimeIncludedGames()
        }
        runCatching {
            val tempoSearchResult = runCatching { fetchTempoSearchEntries(trimmed) }
            val primeEntriesResult = runCatching { fetchPrimeCatalogEntries() }
            val fullCatalogResult = runCatching { fetchFullCatalogEntries() }

            if (tempoSearchResult.isFailure &&
                primeEntriesResult.isFailure &&
                fullCatalogResult.isFailure
            ) {
                throw IOException(
                    "Could not load Amazon Luna catalog",
                    tempoSearchResult.exceptionOrNull() ?: primeEntriesResult.exceptionOrNull()
                )
            }

            val primeEntries = primeEntriesResult.getOrDefault(emptyList())
            val fullCatalog = fullCatalogResult.getOrDefault(emptyList())
            val primeIds = primeEntries.map { it.id }.toSet()
            val primeTitles = primeEntries.map { it.title.lowercase(Locale.ROOT) }.toSet()

            val searchEntries = tempoSearchResult.getOrDefault(emptyList()).map { entry ->
                val isPrime = entry.id in primeIds ||
                    entry.title.lowercase(Locale.ROOT) in primeTitles
                if (isPrime) entry.copy(includedWithPrime = true) else entry
            }

            val lower = trimmed.lowercase(Locale.ROOT)
            val localMatches = mergeCatalogEntries(primeEntries = primeEntries, fullEntries = fullCatalog)
                .filter { entry ->
                    entry.title.lowercase(Locale.ROOT).contains(lower) ||
                        entry.slug.lowercase(Locale.ROOT).contains(lower)
                }

            val combined = mergeOrderedCatalogEntries(
                primaryEntries = searchEntries,
                secondaryEntries = localMatches
            )
            combined.map { it.toGameItem() }
        }
    }

    /**
     * Resolves and caches a portrait poster URL from SteamGridDB for a Luna title.
     * Used lazily by Discover grid cards when a fallback source lacks artwork.
     */
    suspend fun resolvePosterForTitle(title: String, allowFallbackSearch: Boolean = false): String? =
        withContext(Dispatchers.IO) {
            val normalizedKey = title.trim().lowercase(Locale.ROOT)
            if (normalizedKey.isEmpty()) return@withContext null
            posterCache[normalizedKey]?.let { return@withContext it }

            posterSemaphore.withPermit {
                posterCache[normalizedKey]?.let { return@withPermit it }
                val exactPoster = SteamGridDbService.findPosterForGame(title)
                val resolved = exactPoster ?: if (allowFallbackSearch) {
                    SteamGridDbService.searchArtwork(title).firstOrNull()?.thumbnailUrl
                } else {
                    null
                }
                if (!resolved.isNullOrBlank()) {
                    posterCache[normalizedKey] = resolved
                }
                resolved
            }
        }

    private fun fetchPrimeCatalogEntries(): List<LunaCatalogEntry> {
        val now = System.currentTimeMillis()
        cachedPrimeEntries?.let { cached ->
            if (now - primeCacheTimestamp < CACHE_TTL_MS && cached.isNotEmpty()) {
                return cached
            }
        }

        val tempoEntries = runCatching {
            val body = executeTempoPost(
                buildTempoBrowseRequestBody(query = "included_with_prime", sort = "TITLE_A_TO_Z")
            ) ?: return@runCatching emptyList()
            parseTempoGetPageResponse(body, includedWithPrime = true)
        }.getOrDefault(emptyList())

        val entries = tempoEntries.ifEmpty {
            val wpEntries = fetchFirstNonEmpty(
                listOf(PRIME_WP_JSON_URL, PRIME_FALLBACK_WP_JSON_URL)
            ) { url ->
                val body = executeGet(url, accept = "application/json")
                    ?: return@fetchFirstNonEmpty emptyList()
                parseWpGamelistJson(body, includedWithPrime = true)
            }
            wpEntries.ifEmpty {
                fetchFirstNonEmpty(listOf(PRIME_PAGE_URL, PRIME_FALLBACK_PAGE_URL)) { url ->
                    val html = executeGet(url, accept = "text/html")
                        ?: return@fetchFirstNonEmpty emptyList()
                    parseGamelistHtml(html, includedWithPrime = true, isFullDocument = true)
                }
            }
        }

        if (entries.isEmpty()) {
            throw IOException("Unable to load Prime-included Amazon Luna games right now.")
        }
        cachedPrimeEntries = entries
        primeCacheTimestamp = now
        return entries
    }

    private fun fetchFullCatalogEntries(): List<LunaCatalogEntry> {
        val now = System.currentTimeMillis()
        cachedFullCatalog?.let { cached ->
            if (now - fullCatalogTimestamp < CACHE_TTL_MS && cached.isNotEmpty()) {
                return cached
            }
        }

        val tempoEntries = runCatching {
            val body = executeTempoPost(
                buildTempoBrowseRequestBody(query = "all_games", sort = "TITLE_A_TO_Z")
            ) ?: return@runCatching emptyList()
            parseTempoGetPageResponse(body, includedWithPrime = false)
        }.getOrDefault(emptyList())

        val entries = tempoEntries.ifEmpty {
            val wpEntries = executeGet(CATALOG_WP_JSON_URL, accept = "application/json")
                ?.let { parseWpGamelistJson(it, includedWithPrime = false) }
                .orEmpty()
            wpEntries.ifEmpty {
                executeGet(CATALOG_PAGE_URL, accept = "text/html")
                    ?.let { parseGamelistHtml(it, includedWithPrime = false, isFullDocument = true) }
                    .orEmpty()
            }
        }
        if (entries.isNotEmpty()) {
            cachedFullCatalog = entries
            fullCatalogTimestamp = now
        }
        return entries
    }

    private fun fetchTempoSearchEntries(query: String): List<LunaCatalogEntry> {
        val body = executeTempoPost(
            buildTempoSearchRequestBody(
                query = query,
                pageSize = TEMPO_SEARCH_PAGE_SIZE,
                startIndex = 0
            )
        ) ?: throw IOException("Amazon Luna search request failed")
        return parseTempoGetPageResponse(body, includedWithPrime = false)
    }

    private fun buildTempoBrowseRequestBody(
        query: String,
        sort: String = "TITLE_A_TO_Z"
    ): String = buildTempoBasePayload().apply {
        put(
            "pageContext",
            JSONObject().apply {
                put("pageType", "multistate_browse_results")
                put("pageId", "default")
            }
        )
        put(
            "searchContext",
            JSONObject().apply {
                put("query", query)
                put("sort", sort)
                put("filtersList", JSONArray())
            }
        )
    }.toString()

    private fun buildTempoSearchRequestBody(
        query: String,
        pageSize: Int = TEMPO_SEARCH_PAGE_SIZE,
        startIndex: Int = 0
    ): String = buildTempoBasePayload().apply {
        put(
            "pageContext",
            JSONObject().apply {
                put("pageType", "multistate_search_results")
                put("pageId", "default")
            }
        )
        put(
            "searchContext",
            JSONObject().apply {
                put("query", query)
                put("sort", "RELEVANCE")
                put(
                    "pagination",
                    JSONObject().apply {
                        put("pageSize", pageSize)
                        put("startIndex", startIndex)
                    }
                )
            }
        )
    }.toString()

    private fun buildTempoBasePayload(): JSONObject = JSONObject().apply {
        put("featureScheme", "WEB_V1")
        put(
            "clientContext",
            JSONObject().apply {
                put(
                    "browserMetadata",
                    JSONObject().apply {
                        put("browserClientRole", "BROWSER")
                        put("browserType", "Chrome")
                        put("browserVersion", "131.0.0.0")
                        put("deviceModel", "unknown")
                        put("deviceType", "unknown")
                        put("osName", "Windows")
                        put("osVersion", "10")
                    }
                )
                put("refMarker", JSONObject.NULL)
                put("referrer", JSONObject.NULL)
            }
        )
        put(
            "inputContext",
            JSONObject().apply {
                put("gamepadTypes", JSONArray())
            }
        )
        put("dynamicFeatures", JSONArray())
    }

    private fun executeTempoPost(jsonPayload: String): String? {
        val request = Request.Builder()
            .url(TEMPO_GET_PAGE_URL)
            .post(jsonPayload.toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("Accept", "application/json")
            .addHeader("Accept-Language", "en-US")
            .addHeader("Content-Type", "application/json")
            .addHeader("Origin", LUNA_BASE_URL)
            .addHeader("Referer", "$LUNA_BASE_URL/")
            .addHeader("User-Agent", DESKTOP_USER_AGENT)
            .addHeader("x-amz-locale", "en_US")
            .addHeader("x-amz-marketplace-id", TEMPO_MARKETPLACE_ID)
            .addHeader("x-amz-platform", "web")
            .addHeader("x-amz-device-type", "browser")
            .addHeader("x-amz-client-version", "-")
            .addHeader("x-amz-react-version", TEMPO_CLIENT_REACT_VERSION)
            .addHeader("x-amz-country-of-residence", "US")
            .addHeader("x-amzn-RequestId", UUID.randomUUID().toString())
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        } catch (_: IOException) {
            null
        }
    }

    internal fun parseTempoGetPageResponse(
        jsonBody: String,
        includedWithPrime: Boolean
    ): List<LunaCatalogEntry> {
        val trimmed = jsonBody.trim()
        if (trimmed.isEmpty()) return emptyList()
        val root = runCatching { JSONObject(trimmed) }.getOrNull() ?: return emptyList()
        val mainContent = root
            .optJSONObject("pageMemberGroups")
            ?.optJSONObject("mainContent")
            ?: return emptyList()
        val topWidgets = mainContent.optJSONArray("widgets") ?: return emptyList()

        val cardWidgets = mutableListOf<JSONObject>()
        collectCardWidgets(topWidgets, cardWidgets, depth = 0)

        val entries = LinkedHashMap<String, LunaCatalogEntry>()
        for (widget in cardWidgets) {
            val rawPresentation = widget.optString("presentationData")
            if (rawPresentation.isBlank()) continue
            val presentation = runCatching { JSONObject(rawPresentation) }.getOrNull() ?: continue

            val rawTitle = presentation.optString("title")
            val title = rawTitle.replace(WHITESPACE_REGEX, " ").trim()
                .takeIf { it.length in 2..200 } ?: continue

            val targetPath = widget.optJSONArray("actions")
                ?.optJSONObject(0)
                ?.optString("target")
                ?.trim()
                .orEmpty()

            val imagePortrait = presentation.optString("imagePortrait")
                .trim()
                .takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
            val imageLandscape = presentation.optString("imageLandscape")
                .trim()
                .takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
            val gameId = presentation.optString("gameId").trim()

            // Skip non-game promotional/header widgets that happen to carry a title.
            val isGameCard = targetPath.contains("/game/") ||
                gameId.isNotEmpty() ||
                imagePortrait != null
            if (!isGameCard) continue

            val webUrl = when {
                targetPath.startsWith("https://", ignoreCase = true) ||
                    targetPath.startsWith("http://", ignoreCase = true) -> targetPath
                targetPath.startsWith("/") -> "$LUNA_BASE_URL$targetPath"
                else -> LUNA_BASE_URL
            }

            val pathSegments = targetPath
                .substringBefore('?')
                .trim('/')
                .split('/')
                .filter { it.isNotBlank() }

            val slug = when {
                pathSegments.size >= 2 && pathSegments[0].equals("game", ignoreCase = true) ->
                    pathSegments[1]
                else -> slugify(title)
            }
            val entryId = when {
                pathSegments.size >= 3 && pathSegments[0].equals("game", ignoreCase = true) ->
                    pathSegments.last()
                else -> slug
            }

            val hoverDetails = presentation.optJSONObject("hoverDetails")
            val publisher = hoverDetails?.optString("publishersText")?.trim()?.takeIf { it.isNotBlank() }
            val releaseYear = presentation.optString("releaseYear").trim().takeIf { it.isNotBlank() }
            val genreTags = buildList {
                val genresArray = presentation.optJSONArray("genreTags")
                if (genresArray != null) {
                    for (i in 0 until genresArray.length()) {
                        val tag = genresArray.optString(i).trim()
                        if (tag.isNotBlank()) add(tag)
                    }
                }
            }

            val entry = LunaCatalogEntry(
                id = entryId,
                title = title,
                slug = slug,
                webUrl = webUrl,
                imageUrl = imagePortrait,
                heroBannerUrl = imageLandscape,
                publisher = publisher,
                genres = genreTags,
                releaseYear = releaseYear,
                includedWithPrime = includedWithPrime
            )

            val dedupeKey = title.lowercase(Locale.ROOT)
            val existing = entries[dedupeKey]
            entries[dedupeKey] = if (existing == null) {
                entry
            } else {
                mergeEntryPair(existing, entry)
            }
        }

        return entries.values.toList()
    }

    private fun collectCardWidgets(
        widgets: JSONArray,
        out: MutableList<JSONObject>,
        depth: Int
    ) {
        if (depth > 5) return
        for (i in 0 until widgets.length()) {
            val widget = widgets.optJSONObject(i) ?: continue
            if (widget.optString("presentationData").isNotBlank()) {
                out += widget
            }
            val nested = widget.optJSONArray("widgets")
            if (nested != null && nested.length() > 0) {
                collectCardWidgets(nested, out, depth + 1)
            }
        }
    }

    private inline fun fetchFirstNonEmpty(
        urls: List<String>,
        parser: (String) -> List<LunaCatalogEntry>
    ): List<LunaCatalogEntry> {
        for (url in urls) {
            val parsed = runCatching { parser(url) }.getOrDefault(emptyList())
            if (parsed.isNotEmpty()) return parsed
        }
        return emptyList()
    }

    private fun executeGet(url: String, accept: String): String? {
        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", accept)
            .addHeader("User-Agent", DESKTOP_USER_AGENT)
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        } catch (_: IOException) {
            null
        }
    }

    internal fun mergeCatalogEntries(
        primeEntries: List<LunaCatalogEntry>,
        fullEntries: List<LunaCatalogEntry>
    ): List<LunaCatalogEntry> {
        val byKey = LinkedHashMap<String, LunaCatalogEntry>()
        for (entry in primeEntries) {
            val key = entry.title.lowercase(Locale.ROOT)
            val flagged = entry.copy(includedWithPrime = true)
            val existing = byKey[key]
            byKey[key] = if (existing == null) flagged else mergeEntryPair(existing, flagged)
        }
        for (entry in fullEntries) {
            val key = entry.title.lowercase(Locale.ROOT)
            val existing = byKey[key]
            byKey[key] = if (existing == null) entry else mergeEntryPair(existing, entry)
        }
        return byKey.values.toList()
    }

    internal fun mergeOrderedCatalogEntries(
        primaryEntries: List<LunaCatalogEntry>,
        secondaryEntries: List<LunaCatalogEntry>
    ): List<LunaCatalogEntry> {
        val byKey = LinkedHashMap<String, LunaCatalogEntry>()
        for (entry in primaryEntries) {
            val key = entry.title.lowercase(Locale.ROOT)
            val existing = byKey[key]
            byKey[key] = if (existing == null) entry else mergeEntryPair(existing, entry)
        }
        for (entry in secondaryEntries) {
            val key = entry.title.lowercase(Locale.ROOT)
            val existing = byKey[key]
            byKey[key] = if (existing == null) entry else mergeEntryPair(existing, entry)
        }
        return byKey.values.toList()
    }

    private fun mergeEntryPair(
        first: LunaCatalogEntry,
        second: LunaCatalogEntry
    ): LunaCatalogEntry = first.copy(
        webUrl = if (first.webUrl == LUNA_BASE_URL && second.webUrl != LUNA_BASE_URL) {
            second.webUrl
        } else {
            first.webUrl
        },
        imageUrl = first.imageUrl?.takeIf { it.isNotBlank() } ?: second.imageUrl,
        heroBannerUrl = first.heroBannerUrl?.takeIf { it.isNotBlank() } ?: second.heroBannerUrl,
        publisher = first.publisher?.takeIf { it.isNotBlank() } ?: second.publisher,
        genres = first.genres.ifEmpty { second.genres },
        releaseYear = first.releaseYear?.takeIf { it.isNotBlank() } ?: second.releaseYear,
        includedWithPrime = first.includedWithPrime || second.includedWithPrime
    )

    internal fun parseWpGamelistJson(
        jsonBody: String,
        includedWithPrime: Boolean
    ): List<LunaCatalogEntry> {
        val trimmed = jsonBody.trim()
        if (!trimmed.startsWith("[")) return emptyList()
        val array = runCatching { JSONArray(trimmed) }.getOrNull() ?: return emptyList()
        if (array.length() == 0) return emptyList()
        val post = array.optJSONObject(0) ?: return emptyList()
        val renderedHtml = post.optJSONObject("content")?.optString("rendered").orEmpty()
        if (renderedHtml.isBlank()) return emptyList()
        return parseGamelistHtml(
            html = renderedHtml,
            includedWithPrime = includedWithPrime,
            isFullDocument = false
        )
    }

    internal fun parseGamelistHtml(
        html: String,
        includedWithPrime: Boolean,
        isFullDocument: Boolean = false
    ): List<LunaCatalogEntry> {
        val jsonLdEntries = parseJsonLdEntries(html, includedWithPrime)
        val contentSlice = if (isFullDocument) extractMainHtmlContent(html) else html
        val entries = LinkedHashMap<String, LunaCatalogEntry>()

        fun addEntry(rawTitle: String, slugHint: String? = null, webUrl: String? = null, imageUrl: String? = null) {
            val title = cleanTitle(rawTitle) ?: return
            val key = title.lowercase(Locale.ROOT)
            val slug = slugHint?.takeIf { it.isNotBlank() } ?: slugify(title)
            val resolvedWebUrl = webUrl?.takeIf {
                it.startsWith("https://luna.amazon.", ignoreCase = true) ||
                    it.startsWith("http://luna.amazon.", ignoreCase = true)
            } ?: LUNA_BASE_URL
            val existing = entries[key]
            if (existing == null) {
                entries[key] = LunaCatalogEntry(
                    id = slug,
                    title = title,
                    slug = slug,
                    webUrl = resolvedWebUrl,
                    imageUrl = imageUrl?.takeIf { it.startsWith("http") },
                    includedWithPrime = includedWithPrime
                )
            } else {
                entries[key] = existing.copy(
                    webUrl = if (existing.webUrl == LUNA_BASE_URL && resolvedWebUrl != LUNA_BASE_URL) {
                        resolvedWebUrl
                    } else {
                        existing.webUrl
                    },
                    imageUrl = existing.imageUrl ?: imageUrl?.takeIf { it.startsWith("http") }
                )
            }
        }

        // 1. Structured game links inside CloudDosage gamelists: /games/<slug>/
        GAME_LINK_REGEX.findAll(contentSlice).forEach { match ->
            val slug = match.groupValues[1]
            val innerHtml = match.groupValues[2]
            val imageUrl = IMG_SRC_REGEX.find(innerHtml)?.groupValues?.getOrNull(1)
            val text = stripHtml(innerHtml)
            if (text.isNotBlank()) {
                addEntry(rawTitle = text, slugHint = slug, imageUrl = imageUrl)
            }
        }

        // 2. Direct Luna links if present
        LUNA_LINK_REGEX.findAll(contentSlice).forEach { match ->
            val href = decodeHtmlEntities(match.groupValues[1])
            val innerHtml = match.groupValues[2]
            val text = stripHtml(innerHtml)
            if (text.isNotBlank()) {
                addEntry(rawTitle = text, webUrl = href)
            }
        }

        // 3. Table rows (`<tr>...</tr>`) for list/table formatted WordPress blocks
        if (entries.isEmpty()) {
            TABLE_ROW_REGEX.findAll(contentSlice).forEach { rowMatch ->
                val rowHtml = rowMatch.groupValues[1]
                if (rowHtml.contains("<th", ignoreCase = true)) return@forEach
                val cells = TABLE_CELL_REGEX.findAll(rowHtml).toList()
                val firstCell = cells.firstOrNull()?.groupValues?.getOrNull(1) ?: return@forEach
                val cellText = stripHtml(firstCell)
                val lunaHref = FIRST_LUNA_HREF_REGEX.find(rowHtml)?.groupValues?.getOrNull(1)
                val imgSrc = IMG_SRC_REGEX.find(rowHtml)?.groupValues?.getOrNull(1)
                addEntry(rawTitle = cellText, webUrl = lunaHref, imageUrl = imgSrc)
            }
        }

        // 4. List items (`<li>...</li>`) inside article content
        if (entries.isEmpty()) {
            LIST_ITEM_REGEX.findAll(contentSlice).forEach { liMatch ->
                val liHtml = liMatch.groupValues[1]
                val lunaHref = FIRST_LUNA_HREF_REGEX.find(liHtml)?.groupValues?.getOrNull(1)
                val imgSrc = IMG_SRC_REGEX.find(liHtml)?.groupValues?.getOrNull(1)
                val titleText = stripHtml(liHtml)
                    .substringBefore(" – ")
                    .substringBefore(" - ")
                addEntry(rawTitle = titleText, webUrl = lunaHref, imageUrl = imgSrc)
            }
        }

        // Merge any JSON-LD entries discovered in the document.
        for (ld in jsonLdEntries) {
            addEntry(ld.title, ld.slug, ld.webUrl, ld.imageUrl)
        }

        return entries.values.toList()
    }

    private fun parseJsonLdEntries(html: String, includedWithPrime: Boolean): List<LunaCatalogEntry> {
        val result = mutableListOf<LunaCatalogEntry>()
        JSON_LD_REGEX.findAll(html).forEach { match ->
            val rawJson = match.groupValues[1].trim()
            val root = runCatching { JSONObject(rawJson) }.getOrNull() ?: return@forEach
            val graph = root.optJSONArray("@graph")
            val candidates = buildList {
                add(root)
                if (graph != null) {
                    for (i in 0 until graph.length()) {
                        graph.optJSONObject(i)?.let(::add)
                    }
                }
            }
            for (obj in candidates) {
                val itemList = obj.optJSONArray("itemListElement") ?: continue
                for (i in 0 until itemList.length()) {
                    val elem = itemList.optJSONObject(i) ?: continue
                    val itemObj = elem.optJSONObject("item") ?: elem
                    val name = cleanTitle(itemObj.optString("name")) ?: continue
                    val slug = slugify(name)
                    result += LunaCatalogEntry(
                        id = slug,
                        title = name,
                        slug = slug,
                        includedWithPrime = includedWithPrime
                    )
                }
            }
        }
        return result
    }

    private fun extractMainHtmlContent(html: String): String {
        val entryMatch = ENTRY_CONTENT_REGEX.find(html)
        if (entryMatch != null) return entryMatch.groupValues[1]
        val mainMatch = MAIN_TAG_REGEX.find(html)
        if (mainMatch != null) return mainMatch.groupValues[1]
        val articleMatch = ARTICLE_TAG_REGEX.find(html)
        if (articleMatch != null) return articleMatch.groupValues[1]
        return html
    }

    private fun cleanTitle(raw: String): String? {
        val normalized = decodeHtmlEntities(raw)
            .replace(WHITESPACE_REGEX, " ")
            .trim()
            .trim('•', '-', '|', ',')
            .trim()
        if (normalized.length < 2 || normalized.length > 100) return null
        val lower = normalized.lowercase(Locale.ROOT)
        if (lower in IGNORED_TITLES || IGNORED_PREFIXES.any { lower.startsWith(it) }) {
            return null
        }
        if (lower.startsWith("http://") || lower.startsWith("https://")) return null
        return normalized
    }

    private fun stripHtml(html: String): String =
        decodeHtmlEntities(HTML_TAG_REGEX.replace(html, " "))
            .replace(WHITESPACE_REGEX, " ")
            .trim()

    private fun decodeHtmlEntities(text: String): String {
        var result = text
            .replace("&amp;", "&")
            .replace("&#038;", "&")
            .replace("&#38;", "&")
            .replace("&quot;", "\"")
            .replace("&#34;", "\"")
            .replace("&#39;", "'")
            .replace("&#039;", "'")
            .replace("&apos;", "'")
            .replace("&#8217;", "'")
            .replace("&#8216;", "'")
            .replace("&rsquo;", "'")
            .replace("&lsquo;", "'")
            .replace("&#8220;", "\"")
            .replace("&#8221;", "\"")
            .replace("&rdquo;", "\"")
            .replace("&ldquo;", "\"")
            .replace("&#8211;", "-")
            .replace("&#8212;", "-")
            .replace("&ndash;", "-")
            .replace("&mdash;", "-")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
        result = NUMERIC_HEX_ENTITY_REGEX.replace(result) { match ->
            match.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: match.value
        }
        result = NUMERIC_DEC_ENTITY_REGEX.replace(result) { match ->
            match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: match.value
        }
        return result
    }

    private fun slugify(title: String): String =
        title.lowercase(Locale.ROOT)
            .replace(NON_ALNUM_REGEX, "_")
            .trim('_')
            .ifBlank { "game" }

    private val GAME_LINK_REGEX = Regex(
        """<a\b[^>]*href=["']https?://[^"']*/games/([a-zA-Z0-9_-]+)/?["'][^>]*>(.*?)</a>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val LUNA_LINK_REGEX = Regex(
        """<a\b[^>]*href=["'](https?://luna\.amazon\.[^"']+)["'][^>]*>(.*?)</a>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val FIRST_LUNA_HREF_REGEX = Regex(
        """href=["'](https?://luna\.amazon\.[^"']+)["']""",
        RegexOption.IGNORE_CASE
    )
    private val IMG_SRC_REGEX = Regex(
        """(?:data-src|src)=["'](https?://[^"']+\.(?:jpg|jpeg|png|webp)[^"']*)["']""",
        RegexOption.IGNORE_CASE
    )
    private val TABLE_ROW_REGEX = Regex(
        """<tr\b[^>]*>(.*?)</tr>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val TABLE_CELL_REGEX = Regex(
        """<td\b[^>]*>(.*?)</td>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val LIST_ITEM_REGEX = Regex(
        """<li\b[^>]*>(.*?)</li>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val JSON_LD_REGEX = Regex(
        """<script\b[^>]*type=["']application/ld\+json["'][^>]*>(.*?)</script>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val ENTRY_CONTENT_REGEX = Regex(
        """<div\b[^>]*class=["'][^"']*entry-content[^"']*["'][^>]*>(.*?)</div>\s*(?:<footer|</article|<!--)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val MAIN_TAG_REGEX = Regex(
        """<main\b[^>]*>(.*?)</main>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val ARTICLE_TAG_REGEX = Regex(
        """<article\b[^>]*>(.*?)</article>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val HTML_TAG_REGEX = Regex("""<[^>]+>""")
    private val WHITESPACE_REGEX = Regex("""\s+""")
    private val NON_ALNUM_REGEX = Regex("""[^a-z0-9]+""")
    private val NUMERIC_HEX_ENTITY_REGEX = Regex("""&#x([0-9a-fA-F]+);""")
    private val NUMERIC_DEC_ENTITY_REGEX = Regex("""&#(\d+);""")

    private val IGNORED_TITLES = setOf(
        "amazon luna",
        "prime gaming",
        "luna+",
        "luna standard",
        "read more",
        "view game",
        "learn more",
        "see all",
        "home",
        "games",
        "game lists",
        "menu",
        "search",
        "privacy policy",
        "terms of use",
        "subscribe",
        "play on luna",
        "play now"
    )
    private val IGNORED_PREFIXES = listOf(
        "all games",
        "free games",
        "click here",
        "subscribe ",
        "updated ",
        "check out "
    )
}
