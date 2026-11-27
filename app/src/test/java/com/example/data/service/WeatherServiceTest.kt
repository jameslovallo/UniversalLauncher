package com.example.data.service

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeatherServiceTest {

    private fun mockClient(handler: (String, okhttp3.Request) -> Pair<Int, String>): OkHttpClient {
        val interceptor = Interceptor { chain ->
            val request = chain.request()
            val (code, body) = handler(request.url.toString(), request)
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code in 200..299) "OK" else "Error")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
        return OkHttpClient.Builder().addInterceptor(interceptor).build()
    }

    @Test
    fun `fetchCurrent uses NWS live station observation for US coordinates and avoids Open-Meteo`() {
        val requestedUrls = mutableListOf<String>()
        var openMeteoCalled = false
        val app = ApplicationProvider.getApplicationContext<Application>()
        val client = mockClient { url, request ->
            requestedUrls.add(url)
            assertTrue(request.header("User-Agent")?.isNotBlank() == true)
            when {
                url == "https://api.weather.gov/points/47.6062,-122.3321" -> 200 to """
                    {
                      "properties": {
                        "observationStations": "https://api.weather.gov/gridpoints/SEW/124,67/stations",
                        "forecastHourly": "https://api.weather.gov/gridpoints/SEW/124,67/forecast/hourly"
                      }
                    }
                """.trimIndent()
                url == "https://api.weather.gov/gridpoints/SEW/124,67/stations" -> 200 to """
                    {
                      "features": [
                        { "id": "https://api.weather.gov/stations/KBFI", "properties": { "stationIdentifier": "KBFI" } }
                      ]
                    }
                """.trimIndent()
                url == "https://api.weather.gov/stations/KBFI/observations/latest" -> 200 to """
                    {
                      "properties": {
                        "temperature": { "unitCode": "wmoUnit:degC", "value": 12.0 },
                        "textDescription": "Clear",
                        "icon": "https://api.weather.gov/icons/land/night/skc?size=medium"
                      }
                    }
                """.trimIndent()
                url.contains("open-meteo.com") -> {
                    openMeteoCalled = true
                    500 to "{}"
                }
                else -> 404 to "{}"
            }
        }

        val service = WeatherService(app, client)
        val info = service.fetchCurrent(47.6062, -122.3321, "Seattle, Washington")

        assertFalse(openMeteoCalled)
        assertEquals(12.0, info.temperatureC, 0.001)
        assertEquals(0, info.weatherCode)
        assertFalse(info.isDay)
        assertEquals("Seattle, Washington", info.locationLabel)
        assertEquals(3, requestedUrls.size)
    }

    @Test
    fun `fetchCurrent probes next NWS station when closest station temperature is null`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val client = mockClient { url, _ ->
            when (url) {
                "https://api.weather.gov/points/40.7128,-74.0060" -> 200 to """
                    {
                      "properties": {
                        "observationStations": "https://api.weather.gov/gridpoints/OKX/33,37/stations",
                        "forecastHourly": "https://api.weather.gov/gridpoints/OKX/33,37/forecast/hourly"
                      }
                    }
                """.trimIndent()
                "https://api.weather.gov/gridpoints/OKX/33,37/stations" -> 200 to """
                    {
                      "features": [
                        { "id": "https://api.weather.gov/stations/KNYC", "properties": { "stationIdentifier": "KNYC" } },
                        { "id": "https://api.weather.gov/stations/KLGA", "properties": { "stationIdentifier": "KLGA" } }
                      ]
                    }
                """.trimIndent()
                "https://api.weather.gov/stations/KNYC/observations/latest" -> 200 to """
                    {
                      "properties": {
                        "temperature": { "unitCode": "wmoUnit:degC", "value": null },
                        "textDescription": "Light Rain and Fog/Mist",
                        "icon": "https://api.weather.gov/icons/land/night/rain?size=medium"
                      }
                    }
                """.trimIndent()
                "https://api.weather.gov/stations/KLGA/observations/latest" -> 200 to """
                    {
                      "properties": {
                        "temperature": { "unitCode": "wmoUnit:degC", "value": 9.5 },
                        "textDescription": "Light Rain",
                        "icon": "https://api.weather.gov/icons/land/night/rain?size=medium"
                      }
                    }
                """.trimIndent()
                else -> 404 to "{}"
            }
        }

        val service = WeatherService(app, client)
        val info = service.fetchCurrent(40.7128, -74.0060, "New York, New York")

        assertEquals(9.5, info.temperatureC, 0.001)
        assertEquals(61, info.weatherCode)
        assertFalse(info.isDay)
    }

    @Test
    fun `fetchCurrent falls back to NWS hourly forecast when stations report null temperature`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val client = mockClient { url, _ ->
            when (url) {
                "https://api.weather.gov/points/25.7617,-80.1918" -> 200 to """
                    {
                      "properties": {
                        "observationStations": "https://api.weather.gov/gridpoints/MFL/110,50/stations",
                        "forecastHourly": "https://api.weather.gov/gridpoints/MFL/110,50/forecast/hourly"
                      }
                    }
                """.trimIndent()
                "https://api.weather.gov/gridpoints/MFL/110,50/stations" -> 200 to """
                    {
                      "features": [
                        { "id": "https://api.weather.gov/stations/KMIA", "properties": { "stationIdentifier": "KMIA" } }
                      ]
                    }
                """.trimIndent()
                "https://api.weather.gov/stations/KMIA/observations/latest" -> 200 to """
                    { "properties": { "temperature": { "value": null }, "textDescription": "", "icon": null } }
                """.trimIndent()
                "https://api.weather.gov/gridpoints/MFL/110,50/forecast/hourly" -> 200 to """
                    {
                      "properties": {
                        "periods": [
                          {
                            "temperature": 77,
                            "temperatureUnit": "F",
                            "isDaytime": true,
                            "shortForecast": "Partly Cloudy",
                            "icon": "https://api.weather.gov/icons/land/day/sct?size=small"
                          }
                        ]
                      }
                    }
                """.trimIndent()
                else -> 404 to "{}"
            }
        }

        val service = WeatherService(app, client)
        val info = service.fetchCurrent(25.7617, -80.1918, "Miami, Florida")

        assertEquals(25.0, info.temperatureC, 0.001)
        assertEquals(2, info.weatherCode)
        assertTrue(info.isDay)
    }

    @Test
    fun `fetchCurrent falls back to Open-Meteo for international locations outside NWS coverage`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val client = mockClient { url, _ ->
            when {
                url.startsWith("https://api.weather.gov/points/") ->
                    404 to """{ "status": 404, "detail": "Unable to provide data for requested point" }"""
                url.startsWith("https://api.open-meteo.com/v1/forecast") -> 200 to """
                    {
                      "current": {
                        "temperature_2m": 8.4,
                        "weather_code": 61,
                        "is_day": 0
                      }
                    }
                """.trimIndent()
                else -> 404 to "{}"
            }
        }

        val service = WeatherService(app, client)
        val info = service.fetchCurrent(51.5074, -0.1278, "London, England")

        assertEquals(8.4, info.temperatureC, 0.001)
        assertEquals(61, info.weatherCode)
        assertFalse(info.isDay)
        assertEquals("London, England", info.locationLabel)
    }

    @Test
    fun `mapNwsToWmoCode maps NWS icon paths and text descriptions accurately`() {
        assertEquals(0, WeatherService.mapNwsToWmoCode("https://api.weather.gov/icons/land/day/skc?size=medium", "Sunny"))
        assertEquals(1, WeatherService.mapNwsToWmoCode("https://api.weather.gov/icons/land/night/few?size=medium", "Mostly Clear"))
        assertEquals(2, WeatherService.mapNwsToWmoCode("https://api.weather.gov/icons/land/day/sct?size=medium", "Partly Cloudy"))
        assertEquals(3, WeatherService.mapNwsToWmoCode("https://api.weather.gov/icons/land/day/ovc?size=medium", "Overcast"))
        assertEquals(45, WeatherService.mapNwsToWmoCode("https://api.weather.gov/icons/land/day/fog?size=medium", "Fog"))
        assertEquals(65, WeatherService.mapNwsToWmoCode("https://api.weather.gov/icons/land/day/rain?size=medium", "Heavy Rain"))
        assertEquals(80, WeatherService.mapNwsToWmoCode("https://api.weather.gov/icons/land/day/rain_showers,40?size=small", "Rain Showers"))
        assertEquals(95, WeatherService.mapNwsToWmoCode("https://api.weather.gov/icons/land/day/sct/tsra,60?size=medium", "Thunderstorms"))
        assertEquals(75, WeatherService.mapNwsToWmoCode(null, "Heavy Snow"))
        assertEquals(71, WeatherService.mapNwsToWmoCode(null, "Snow Flurries"))
        assertEquals(66, WeatherService.mapNwsToWmoCode(null, "Freezing Rain"))
    }
}
