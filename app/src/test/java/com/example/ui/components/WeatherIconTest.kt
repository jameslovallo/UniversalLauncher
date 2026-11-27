package com.example.ui.components

import com.example.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WeatherIconTest {

    @Test
    fun `weatherIconRes maps WMO codes to Google Weather Filled drawables with day and night variants`() {
        assertEquals(R.drawable.ic_weather_clear_day, weatherIconRes(0, isDay = true))
        assertEquals(R.drawable.ic_weather_clear_night, weatherIconRes(0, isDay = false))

        assertEquals(R.drawable.ic_weather_mostly_clear_day, weatherIconRes(1, isDay = true))
        assertEquals(R.drawable.ic_weather_mostly_clear_night, weatherIconRes(1, isDay = false))

        assertEquals(R.drawable.ic_weather_partly_cloudy_day, weatherIconRes(2, isDay = true))
        assertEquals(R.drawable.ic_weather_partly_cloudy_night, weatherIconRes(2, isDay = false))

        assertEquals(R.drawable.ic_weather_cloudy, weatherIconRes(3, isDay = true))
        assertEquals(R.drawable.ic_weather_fog, weatherIconRes(45, isDay = true))
        assertEquals(R.drawable.ic_weather_drizzle, weatherIconRes(51, isDay = true))
        assertEquals(R.drawable.ic_weather_rain, weatherIconRes(61, isDay = true))
        assertEquals(R.drawable.ic_weather_heavy_rain, weatherIconRes(65, isDay = true))
        assertEquals(R.drawable.ic_weather_sleet_mixed, weatherIconRes(66, isDay = true))
        assertEquals(R.drawable.ic_weather_flurries, weatherIconRes(71, isDay = true))
        assertEquals(R.drawable.ic_weather_snow, weatherIconRes(73, isDay = true))
        assertEquals(R.drawable.ic_weather_heavy_snow, weatherIconRes(75, isDay = true))

        assertEquals(R.drawable.ic_weather_scattered_showers_day, weatherIconRes(80, isDay = true))
        assertEquals(R.drawable.ic_weather_scattered_showers_night, weatherIconRes(80, isDay = false))

        assertEquals(R.drawable.ic_weather_scattered_snow_day, weatherIconRes(85, isDay = true))
        assertEquals(R.drawable.ic_weather_scattered_snow_night, weatherIconRes(85, isDay = false))

        assertEquals(R.drawable.ic_weather_thunderstorm_day, weatherIconRes(95, isDay = true))
        assertEquals(R.drawable.ic_weather_thunderstorm_night, weatherIconRes(95, isDay = false))
        assertEquals(R.drawable.ic_weather_strong_thunderstorm, weatherIconRes(99, isDay = true))

        assertNotEquals(weatherIconRes(0, isDay = true), weatherIconRes(0, isDay = false))
    }
}
