package com.kvl.cyclotrack

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
        Assert.assertEquals(178, bearingToWindAngle(359f, 1))
        Assert.assertEquals(181, bearingToWindAngle(359f, 358))
        Assert.assertEquals(150, bearingToWindAngle(0f, 330))
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