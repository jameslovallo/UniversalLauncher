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
    fun automaticUnitUsesFahrenheitForFahrenheitCountries() {
        assertEquals(
            TemperatureUnit.FAHRENHEIT,
            resolveTemperatureUnit(TemperatureUnit.AUTO, Locale.US)
        )
        assertEquals(
            TemperatureUnit.FAHRENHEIT,
            resolveTemperatureUnit(TemperatureUnit.AUTO, Locale.forLanguageTag("en-LR"))
        )
    }

    @Test
    fun automaticUnitUsesCelsiusEverywhereElse() {
        assertEquals(
            TemperatureUnit.CELSIUS,
            resolveTemperatureUnit(TemperatureUnit.AUTO, Locale.UK)
        )
        assertEquals(
            TemperatureUnit.CELSIUS,
            resolveTemperatureUnit(TemperatureUnit.AUTO, Locale.GERMANY)
        )
        // A locale without a country cannot be Fahrenheit.
        assertEquals(
            TemperatureUnit.CELSIUS,
            resolveTemperatureUnit(TemperatureUnit.AUTO, Locale.ROOT)
        )
    }

    @Test
    fun explicitUnitOverridesTheLocale() {
        assertEquals(
            TemperatureUnit.CELSIUS,
            resolveTemperatureUnit(TemperatureUnit.CELSIUS, Locale.US)
        )
        assertEquals(
            TemperatureUnit.FAHRENHEIT,
            resolveTemperatureUnit(TemperatureUnit.FAHRENHEIT, Locale.UK)
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
    fun unknownStoredValueFallsBackToAutomatic() {
        assertEquals(TemperatureUnit.AUTO, TemperatureUnit.fromStorage(null))
        assertEquals(TemperatureUnit.AUTO, TemperatureUnit.fromStorage("kelvin"))
        assertEquals(TemperatureUnit.CELSIUS, TemperatureUnit.fromStorage("CELSIUS"))
    }
}
