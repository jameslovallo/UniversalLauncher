package com.example.data.service

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class TemperatureUnitTest {

    private val conditions = WeatherInfo(
        temperatureC = 20.0,
        weatherCode = 0,
        locationLabel = "Test"
    )

    @Test
    fun localeDefaultUsesFahrenheitForFahrenheitCountries() {
        assertEquals(
            TemperatureUnit.FAHRENHEIT,
            TemperatureUnit.defaultForLocale(Locale.US)
        )
        assertEquals(
            TemperatureUnit.FAHRENHEIT,
            TemperatureUnit.defaultForLocale(Locale.forLanguageTag("en-LR"))
        )
    }

    @Test
    fun localeDefaultUsesCelsiusEverywhereElse() {
        assertEquals(
            TemperatureUnit.CELSIUS,
            TemperatureUnit.defaultForLocale(Locale.UK)
        )
        assertEquals(
            TemperatureUnit.CELSIUS,
            TemperatureUnit.defaultForLocale(Locale.GERMANY)
        )
        // A locale without a country cannot be Fahrenheit.
        assertEquals(
            TemperatureUnit.CELSIUS,
            TemperatureUnit.defaultForLocale(Locale.ROOT)
        )
    }

    @Test
    fun storedUnitOverridesTheLocale() {
        assertEquals(
            TemperatureUnit.CELSIUS,
            TemperatureUnit.fromStorage("CELSIUS", Locale.US)
        )
        assertEquals(
            TemperatureUnit.FAHRENHEIT,
            TemperatureUnit.fromStorage("FAHRENHEIT", Locale.UK)
        )
    }

    @Test
    fun temperatureConvertsFromCelsius() {
        assertEquals(20.0, conditions.temperature(TemperatureUnit.CELSIUS), 0.001)
        assertEquals(68.0, conditions.temperature(TemperatureUnit.FAHRENHEIT), 0.001)
    }

    @Test
    fun displayIncludesTheScaleSymbol() {
        assertEquals("20°C", conditions.temperatureDisplay(TemperatureUnit.CELSIUS))
        assertEquals("68°F", conditions.temperatureDisplay(TemperatureUnit.FAHRENHEIT))
    }

    @Test
    fun displayCanHideTheScaleLetter() {
        assertEquals("20°", conditions.temperatureDisplay(TemperatureUnit.CELSIUS, showUnit = false))
        assertEquals("68°", conditions.temperatureDisplay(TemperatureUnit.FAHRENHEIT, showUnit = false))
    }

    @Test
    fun unknownOrLegacyAutoStoredValueFallsBackToLocaleDefault() {
        assertEquals(TemperatureUnit.FAHRENHEIT, TemperatureUnit.fromStorage(null, Locale.US))
        assertEquals(TemperatureUnit.CELSIUS, TemperatureUnit.fromStorage(null, Locale.UK))
        assertEquals(TemperatureUnit.FAHRENHEIT, TemperatureUnit.fromStorage("AUTO", Locale.US))
        assertEquals(TemperatureUnit.CELSIUS, TemperatureUnit.fromStorage("kelvin", Locale.UK))
        assertEquals(TemperatureUnit.CELSIUS, TemperatureUnit.fromStorage("CELSIUS", Locale.US))
    }
}
