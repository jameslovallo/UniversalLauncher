package com.example.data.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
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
    val locationLabel: String
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
 * Android has no public system weather API (no weather provider exists in the SDK), so
 * this always falls back to Open-Meteo, which needs no API key. Location comes either
 * from the device's last known fix or from a ZIP/postal code.
 */
class WeatherService(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

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

    private fun fetchCurrent(latitude: Double, longitude: Double, label: String): WeatherInfo {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$latitude&longitude=$longitude" +
            "&current=temperature_2m,weather_code&timezone=auto"
        val current = getJson(url).getJSONObject("current")
        return WeatherInfo(
            temperatureC = current.getDouble("temperature_2m"),
            weatherCode = current.optInt("weather_code", 0),
            locationLabel = label
        )
    }

    private fun getJson(url: String): JSONObject {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Weather request failed (${response.code})")
            }
            return JSONObject(response.body?.string().orEmpty())
        }
    }
}
