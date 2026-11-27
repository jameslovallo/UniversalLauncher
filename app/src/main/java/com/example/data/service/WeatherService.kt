package com.example.data.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Where the weather widget gets its coordinates from. */
enum class WeatherLocationMode {
    DEVICE_LOCATION,
    ZIP_CODE
}

/**
 * Temperature unit used by the weather widget. Defaults to the device's region settings
 * when no preference has been saved yet.
 */
enum class TemperatureUnit(val label: String, val chipLabel: String) {
    CELSIUS("Celsius", "°C"),
    FAHRENHEIT("Fahrenheit", "°F");

    companion object {
        fun fromStorage(
            value: String?,
            locale: Locale = Locale.getDefault()
        ): TemperatureUnit = when (value) {
            CELSIUS.name -> CELSIUS
            FAHRENHEIT.name -> FAHRENHEIT
            else -> defaultForLocale(locale)
        }

        /**
         * Fahrenheit is the official scale in only a handful of countries, so everything else
         * defaults to Celsius.
         */
        fun defaultForLocale(locale: Locale = Locale.getDefault()): TemperatureUnit {
            val country = locale.country.uppercase(Locale.ROOT)
            return if (country in FAHRENHEIT_COUNTRIES) FAHRENHEIT else CELSIUS
        }
    }
}

data class WeatherInfo(
    val temperatureC: Double,
    val weatherCode: Int,
    val locationLabel: String,
    val isDay: Boolean = true
)

private val FAHRENHEIT_COUNTRIES = setOf(
    "US", "LR", "MM", "BS", "BZ", "KY", "PW", "FM", "MH", "TC", "PR", "GU", "VI", "AS"
)

/** Temperature in the requested scale, converted from the Celsius value returned by the API. */
fun WeatherInfo.temperature(unit: TemperatureUnit): Double =
    if (unit == TemperatureUnit.FAHRENHEIT) temperatureC * 9.0 / 5.0 + 32.0 else temperatureC

/**
 * Compact display value such as "72°F" or "22°C". Pass `showUnit = false` on the home
 * screen and navigation bar, where the chosen scale is already implied, to render "72°".
 */
fun WeatherInfo.temperatureDisplay(
    unit: TemperatureUnit,
    showUnit: Boolean = true
): String {
    val suffix = when {
        !showUnit -> "°"
        unit == TemperatureUnit.FAHRENHEIT -> "°F"
        else -> "°C"
    }
    return "${temperature(unit).roundToInt()}$suffix"
}

/**
 * Current-conditions lookup for the favorites home.
 *
 * Uses the U.S. National Weather Service (`api.weather.gov`) first for real-time station
 * observations and hourly forecast fallback without an API key, and falls back to
 * Open-Meteo for international locations outside NWS coverage or when NWS cannot
 * return a valid observation.
 */
class WeatherService internal constructor(
    private val context: Context,
    private val client: OkHttpClient
) {
    constructor(context: Context) : this(context, defaultClient())

    private data class NwsPointMetadata(
        val observationStationsUrl: String?,
        val forecastHourlyUrl: String?
    )

    private val nwsPointCache = ConcurrentHashMap<String, NwsPointMetadata>()

    suspend fun current(mode: WeatherLocationMode, zipCode: String): Result<WeatherInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                val point = when (mode) {
                    WeatherLocationMode.DEVICE_LOCATION -> deviceLocation()
                    WeatherLocationMode.ZIP_CODE -> geocode(zipCode)
                }
                fetchCurrent(point.first, point.second, point.third)
            }
        }

    private fun deviceLocation(): Triple<Double, Double, String> {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            throw IllegalStateException("Location permission is required")
        }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        val fix = providers
            .mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }
            ?: throw IllegalStateException("No recent location fix")
        return Triple(fix.latitude, fix.longitude, "Current location")
    }

    private fun geocode(zipCode: String): Triple<Double, Double, String> {
        val zip = zipCode.trim()
        if (zip.isBlank()) throw IllegalStateException("Enter a ZIP code first")
        val url = "https://geocoding-api.open-meteo.com/v1/search" +
            "?name=${java.net.URLEncoder.encode(zip, "UTF-8")}" +
            "&count=1&language=en&format=json"
        val json = getJson(url)
        val results = json.optJSONArray("results") ?: throw IllegalStateException("ZIP code not found")
        if (results.length() == 0) throw IllegalStateException("ZIP code not found")
        val first = results.getJSONObject(0)
        val label = buildString {
            append(first.optString("name"))
            val admin = first.optString("admin1")
            if (admin.isNotBlank() && admin != first.optString("name")) {
                append(", ").append(admin)
            }
        }.ifBlank { zip }
        return Triple(first.getDouble("latitude"), first.getDouble("longitude"), label)
    }

    internal fun fetchCurrent(latitude: Double, longitude: Double, label: String): WeatherInfo {
        val nwsResult = runCatching { fetchNwsCurrent(latitude, longitude, label) }.getOrNull()
        if (nwsResult != null) {
            return nwsResult
        }
        return fetchOpenMeteoCurrent(latitude, longitude, label)
    }

    private fun fetchNwsCurrent(latitude: Double, longitude: Double, label: String): WeatherInfo? {
        val lat4 = String.format(Locale.US, "%.4f", latitude)
        val lon4 = String.format(Locale.US, "%.4f", longitude)
        val cacheKey = "$lat4,$lon4"
        val pointMeta = nwsPointCache[cacheKey] ?: run {
            val pointsJson = getJsonOrNull(
                url = "https://api.weather.gov/points/$lat4,$lon4",
                isNws = true
            ) ?: return null
            val props = pointsJson.optJSONObject("properties") ?: return null
            val meta = NwsPointMetadata(
                observationStationsUrl = props.optString("observationStations").takeIf {
                    it.isNotBlank() && it != "null"
                },
                forecastHourlyUrl = props.optString("forecastHourly").takeIf {
                    it.isNotBlank() && it != "null"
                }
            )
            if (nwsPointCache.size >= MAX_POINT_CACHE_ENTRIES) {
                nwsPointCache.clear()
            }
            nwsPointCache[cacheKey] = meta
            meta
        }

        var tempC: Double? = null
        var obsDesc: String? = null
        var obsIcon: String? = null

        val stationsUrl = pointMeta.observationStationsUrl
        if (!stationsUrl.isNullOrBlank()) {
            val stationsJson = getJsonOrNull(stationsUrl, isNws = true)
            val features = stationsJson?.optJSONArray("features")
            if (features != null) {
                val limit = minOf(MAX_NWS_STATIONS_TO_PROBE, features.length())
                for (i in 0 until limit) {
                    val feature = features.optJSONObject(i) ?: continue
                    val stationId = feature.optJSONObject("properties")
                        ?.optString("stationIdentifier")
                        ?.takeIf { it.isNotBlank() && it != "null" }
                    val stationUrl = feature.optString("id")
                        .takeIf { it.startsWith("http") }
                        ?: stationId?.let { "https://api.weather.gov/stations/$it" }
                        ?: continue

                    val obsJson = getJsonOrNull("$stationUrl/observations/latest", isNws = true)
                        ?: continue
                    val obsProps = obsJson.optJSONObject("properties") ?: continue

                    if (tempC == null) {
                        tempC = parseNwsTemperatureC(obsProps.optJSONObject("temperature"))
                    }
                    if (obsDesc == null) {
                        obsDesc = obsProps.optString("textDescription")
                            .takeIf { it.isNotBlank() && it != "null" }
                    }
                    if (obsIcon == null) {
                        obsIcon = obsProps.optString("icon")
                            .takeIf { it.isNotBlank() && it != "null" }
                    }

                    if (tempC != null && (obsIcon != null || obsDesc != null)) {
                        break
                    }
                }
            }
        }

        var hourlyIsDay: Boolean? = null
        var hourlyIcon: String? = null
        var hourlyDesc: String? = null

        val needsHourlyFallback = tempC == null ||
            (obsIcon == null && obsDesc == null) ||
            parseNwsIsDayOrNull(obsIcon) == null

        if (needsHourlyFallback && !pointMeta.forecastHourlyUrl.isNullOrBlank()) {
            val hourlyJson = getJsonOrNull(pointMeta.forecastHourlyUrl, isNws = true)
            val periods = hourlyJson?.optJSONObject("properties")?.optJSONArray("periods")
            val firstPeriod = if (periods != null && periods.length() > 0) {
                periods.optJSONObject(0)
            } else {
                null
            }
            if (firstPeriod != null) {
                if (tempC == null && !firstPeriod.isNull("temperature")) {
                    val value = firstPeriod.optDouble("temperature", Double.NaN)
                    if (value.isFinite()) {
                        val unit = firstPeriod.optString("temperatureUnit")
                        tempC = if (unit.equals("F", ignoreCase = true)) {
                            (value - 32.0) * 5.0 / 9.0
                        } else {
                            value
                        }
                    }
                }
                if (firstPeriod.has("isDaytime") && !firstPeriod.isNull("isDaytime")) {
                    hourlyIsDay = firstPeriod.optBoolean("isDaytime")
                }
                hourlyIcon = firstPeriod.optString("icon")
                    .takeIf { it.isNotBlank() && it != "null" }
                hourlyDesc = firstPeriod.optString("shortForecast")
                    .takeIf { it.isNotBlank() && it != "null" }
            }
        }

        val resolvedTempC = tempC ?: return null
        val isDay = parseNwsIsDayOrNull(obsIcon)
            ?: hourlyIsDay
            ?: parseNwsIsDayOrNull(hourlyIcon)
            ?: true
        val weatherCode = mapNwsToWmoCode(
            iconUrl = obsIcon ?: hourlyIcon,
            textDescription = obsDesc ?: hourlyDesc
        )

        return WeatherInfo(
            temperatureC = resolvedTempC,
            weatherCode = weatherCode,
            locationLabel = label,
            isDay = isDay
        )
    }

    private fun fetchOpenMeteoCurrent(latitude: Double, longitude: Double, label: String): WeatherInfo {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$latitude&longitude=$longitude" +
            "&current=temperature_2m,weather_code,is_day&timezone=auto"
        val current = getJson(url).getJSONObject("current")
        return WeatherInfo(
            temperatureC = current.getDouble("temperature_2m"),
            weatherCode = current.optInt("weather_code", 0),
            locationLabel = label,
            isDay = current.optInt("is_day", 1) == 1
        )
    }

    private fun getJson(url: String, isNws: Boolean = false): JSONObject {
        val request = buildRequest(url, isNws)
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Weather request failed (${response.code})")
            }
            return JSONObject(response.body?.string().orEmpty())
        }
    }

    private fun getJsonOrNull(url: String, isNws: Boolean = false): JSONObject? =
        runCatching {
            val request = buildRequest(url, isNws)
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) null else JSONObject(body)
            }
        }.getOrNull()

    private fun buildRequest(url: String, isNws: Boolean): Request {
        val builder = Request.Builder().url(url).get()
        if (isNws) {
            builder
                .header("User-Agent", NWS_USER_AGENT)
                .header("Accept", "application/geo+json")
        }
        return builder.build()
    }

    companion object {
        private const val MAX_NWS_STATIONS_TO_PROBE = 4
        private const val MAX_POINT_CACHE_ENTRIES = 16
        private val NWS_USER_AGENT =
            "NexusLauncher/${BuildConfig.VERSION_NAME} (Android launcher weather widget)"

        private fun defaultClient(): OkHttpClient = HttpClients.shared.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        internal fun parseNwsTemperatureC(tempObj: JSONObject?): Double? {
            if (tempObj == null || tempObj.isNull("value")) return null
            val raw = tempObj.optDouble("value", Double.NaN)
            if (!raw.isFinite()) return null
            val unitCode = tempObj.optString("unitCode")
            return if (unitCode.endsWith("degF", ignoreCase = true)) {
                (raw - 32.0) * 5.0 / 9.0
            } else {
                raw
            }
        }

        internal fun parseNwsIsDayOrNull(iconUrl: String?): Boolean? {
            if (iconUrl.isNullOrBlank()) return null
            val lower = iconUrl.lowercase(Locale.ROOT)
            return when {
                "/night/" in lower -> false
                "/day/" in lower -> true
                else -> null
            }
        }

        internal fun mapNwsToWmoCode(iconUrl: String?, textDescription: String?): Int {
            val desc = textDescription?.lowercase(Locale.ROOT).orEmpty()
            val hasHeavy = "heavy" in desc
            val hasHailOrSevere = "hail" in desc || "severe" in desc || "strong" in desc || "tornado" in desc
            val hasFlurries = "flurr" in desc
            val hasDrizzle = "drizzle" in desc

            val tokens = extractNwsIconTokens(iconUrl)
            var bestCode: Int? = null
            var bestPriority = -1

            for (rawToken in tokens) {
                val base = rawToken.removePrefix("wind_").let { token ->
                    if (token.startsWith("n") && token.length > 1 && token.substring(1) in SKY_TOKENS) {
                        token.substring(1)
                    } else {
                        token
                    }
                }
                val (code, priority) = when (base) {
                    "tornado", "hurricane", "tropical_storm" -> 99 to 100
                    "tsra", "tsra_sct", "tsra_hi" -> (if (hasHailOrSevere) 99 else 95) to 95
                    "blizzard" -> 75 to 90
                    "snow" -> when {
                        hasHeavy -> 75
                        hasFlurries -> 71
                        else -> 73
                    } to 80
                    "fzra", "rain_fzra", "sleet", "snow_sleet", "rain_snow", "snow_fzra" -> 66 to 75
                    "rain_showers", "rain_showers_hi" -> (if (hasHeavy) 82 else 80) to 70
                    "rain" -> when {
                        hasHeavy -> 65
                        hasDrizzle -> 51
                        else -> 61
                    } to 65
                    "drizzle" -> 51 to 60
                    "fog", "haze", "smoke", "dust" -> 45 to 50
                    "ovc" -> 3 to 40
                    "bkn" -> 2 to 30
                    "sct" -> 2 to 25
                    "few" -> 1 to 20
                    "skc" -> 0 to 10
                    else -> continue
                }
                if (priority > bestPriority) {
                    bestCode = code
                    bestPriority = priority
                }
            }

            if (bestCode != null) return bestCode
            return mapNwsDescriptionToWmoCode(desc)
        }

        private val SKY_TOKENS = setOf("skc", "few", "sct", "bkn", "ovc", "fog")

        private fun extractNwsIconTokens(iconUrl: String?): List<String> {
            if (iconUrl.isNullOrBlank()) return emptyList()
            val lower = iconUrl.lowercase(Locale.ROOT).substringBefore('?')
            val marker = when {
                "/day/" in lower -> "/day/"
                "/night/" in lower -> "/night/"
                else -> return emptyList()
            }
            val tail = lower.substringAfter(marker)
            return tail.split('/')
                .map { it.substringBefore(',').trim() }
                .filter { it.isNotEmpty() }
        }

        private fun mapNwsDescriptionToWmoCode(desc: String): Int = when {
            "thunder" in desc || "t-storm" in desc ->
                if ("hail" in desc || "severe" in desc) 99 else 95
            "tornado" in desc || "hurricane" in desc -> 99
            "blizzard" in desc || "heavy snow" in desc -> 75
            "flurr" in desc || "light snow" in desc -> 71
            "snow" in desc -> 73
            "freezing" in desc || "sleet" in desc || "wintry" in desc || "ice" in desc -> 66
            "heavy rain" in desc -> 65
            "shower" in desc -> 80
            "drizzle" in desc -> 51
            "rain" in desc -> 61
            "fog" in desc || "mist" in desc || "haze" in desc || "smoke" in desc -> 45
            "overcast" in desc || ("cloudy" in desc && "partly" !in desc && "mostly" !in desc) -> 3
            "mostly cloudy" in desc || "partly cloudy" in desc || "partly sunny" in desc || "scattered" in desc -> 2
            "mostly clear" in desc || "mostly sunny" in desc || "few clouds" in desc -> 1
            "clear" in desc || "sunny" in desc || "fair" in desc -> 0
            else -> 3
        }
    }
}
