package com.kvl.cyclotrack

import com.kvl.cyclotrack.util.kelvinToCelsius
import com.kvl.cyclotrack.util.kelvinToFahrenheit
import org.junit.Assert
import org.junit.Test

class UnitConversionTest {
    @Test
    fun kelvinToCelsius() {
        Assert.assertEquals(0.0, kelvinToCelsius(273.15), 0.01)
        Assert.assertEquals(22.14, kelvinToCelsius(295.29), 0.01)
    }

    @Test
    fun kelvinToFahrenheit() {
        Assert.assertEquals(71.85, kelvinToFahrenheit(295.29), 0.01)
        Assert.assertEquals(32.0, kelvinToFahrenheit(273.15), 0.01)
    }

    @Test
    fun getUserTemperature_US() {
        Assert.assertEquals(32, getUserTemperature("1", 273.15))
        Assert.assertEquals(72, getUserTemperature("1", 295.29))
    }

    @Test
    fun getUserTemperature_metric() {
        Assert.assertEquals(0, getUserTemperature("2", 273.15))
        Assert.assertEquals(22, getUserTemperature("2", 295.29))
    }

    @Test
    fun bearingToWindAngle() {
        Assert.assertEquals(180, bearingToWindAngle(90f, 90))
        Assert.assertEquals(180, bearingToWindAngle(0f, 0))
        Assert.assertEquals(180, bearingToWindAngle(130f, 130))
        Assert.assertEquals(0, bearingToWindAngle(0f, 180))
        Assert.assertEquals(0, bearingToWindAngle(180f, 0))
        Assert.assertEquals(178, bearingToWindAngle(1f, 359))
        Assert.assertEquals(182, bearingToWindAngle(359f, 1))
        Assert.assertEquals(179, bearingToWindAngle(359f, 358))
        Assert.assertEquals(150, bearingToWindAngle(0f, 330))
    }

    private fun wind(speed: Double, direction: Int) = Weather(
        timestamp = 0, sunrise = 0, sunset = 0, temperature = 280.0, feelsLike = 280.0,
        pressure = 1013, humidity = 50, dewPoint = null, uvIndex = null, clouds = 0,
        visibility = 10000, windSpeed = speed, windDirection = direction, tripId = 1,
    )

    @Test
    fun getAverageWind() {
        listOf(0, 45, 90, 135, 180, 225, 270, 315).forEach { direction ->
            val average = arrayOf(wind(4.0, direction)).getAverageWind()!!
            Assert.assertEquals(4.0, average.first, 1e-9)
            Assert.assertEquals(direction.toDouble(), average.second, 1e-9)
        }
        arrayOf(wind(4.0, 350), wind(4.0, 10)).getAverageWind()!!.let {
            Assert.assertTrue(it.second < 1e-9 || it.second > 360 - 1e-9)
        }
        Assert.assertEquals(
            270.0,
            arrayOf(wind(2.0, 225), wind(2.0, 315)).getAverageWind()!!.second,
            1e-9
        )
    }

    @Test
    fun degreesToCardinal() {
        listOf(
            "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
        ).forEachIndexed { idx, cardinal ->
            Assert.assertEquals(cardinal, degreesToCardinal(idx * 22.5f))
            Assert.assertEquals(cardinal, degreesToCardinal((idx * 22.5f - 11f + 360f) % 360f))
            Assert.assertEquals(cardinal, degreesToCardinal(idx * 22.5f + 11f))
        }
        Assert.assertEquals("N", degreesToCardinal(360f))
    }

    @Test
    fun bearingToIconRotation() {
        Assert.assertEquals(0, bearingToIconRotation(0))
        Assert.assertEquals(180, bearingToIconRotation(180))
        Assert.assertEquals(330, bearingToIconRotation(330))
        Assert.assertEquals(270, bearingToIconRotation(0, 270))
        Assert.assertEquals(180, bearingToIconRotation(270, 270))
        Assert.assertEquals(90, bearingToIconRotation(180, 270))
    }

    @Test
    fun windArrowTest() {
        Assert.assertEquals(
            178,
            bearingToIconRotation(bearingToWindAngle(1f, 359), 0)
        )
        Assert.assertEquals(
            182,
            bearingToIconRotation(bearingToWindAngle(359f, 1), 0)
        )
        Assert.assertEquals(
            90,
            bearingToIconRotation(bearingToWindAngle(0f, 0), 270)
        )
        Assert.assertEquals(
            180,
            bearingToIconRotation(bearingToWindAngle(90f, 180), 270)
        )
        Assert.assertEquals(
            150,
            bearingToIconRotation(bearingToWindAngle(0f, 330), 0)
        )
        Assert.assertEquals(
            60,
            bearingToIconRotation(bearingToWindAngle(0f, 330), 270)
        )
        Assert.assertEquals(
            80,
            bearingToIconRotation(bearingToWindAngle(0f, 260), 0)
        )
        Assert.assertEquals(
            35,
            bearingToIconRotation(bearingToWindAngle(45f, 260), 0)
        )
        Assert.assertEquals(
            190,
            bearingToIconRotation(bearingToWindAngle(250f, 260), 0)
        )
        Assert.assertEquals(
            120,
            bearingToIconRotation(bearingToWindAngle(320f, 260), 0)
        )
        Assert.assertEquals(
            320,
            bearingToIconRotation(bearingToWindAngle(120f, 260), 0)
        )
    }
}
