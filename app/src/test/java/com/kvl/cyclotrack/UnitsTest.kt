package com.kvl.cyclotrack

import com.kvl.cyclotrack.util.Foot
import com.kvl.cyclotrack.util.Hour
import com.kvl.cyclotrack.util.Inch
import com.kvl.cyclotrack.util.Kilo
import com.kvl.cyclotrack.util.KilometersPerHour
import com.kvl.cyclotrack.util.Meter
import com.kvl.cyclotrack.util.MetersPerSecond
import com.kvl.cyclotrack.util.Mile
import com.kvl.cyclotrack.util.MilesPerHour
import com.kvl.cyclotrack.util.Minute
import com.kvl.cyclotrack.util.Quantity
import com.kvl.cyclotrack.util.Second
import com.kvl.cyclotrack.util.Yard
import org.junit.Assert
import org.junit.Test

class UnitsTest {
    @Test
    fun convertFeetToInches() {
        val testQuantity = Quantity(3.0, Foot)
        Assert.assertEquals(36.0, testQuantity.convertTo(Inch).value, 1e-6)
    }

    @Test
    fun convertFeetToMiles() {
        val testQuantity = Quantity(5280.0, Foot)
        Assert.assertEquals(1.0, testQuantity.convertTo(Mile).value, 0.0)
    }

    @Test
    fun convertFeetToMeters() {
        val testQuantity = Quantity(3.0, Foot)
        Assert.assertEquals(0.9144, testQuantity.convertTo(Meter).value, 1e-6)
    }

    @Test
    fun convertYardToMeters() {
        val testQuantity = Quantity(1.0, Yard)
        Assert.assertEquals(0.9144, testQuantity.convertTo(Meter).value, 1e-6)
    }

    @Test
    fun convertMileToMeters() {
        val testQuantity = Quantity(1.0, Mile)
        Assert.assertEquals(1609.344, testQuantity.convertTo(Meter).value, 1e-6)
    }

    @Test
    fun convertMetersToMiles() {
        val testQuantity = Quantity(1609.344, Meter)
        Assert.assertEquals(1.0, testQuantity.convertTo(Mile).value, 1e-6)
    }

    @Test
    fun metersToKilometers() {
        Assert.assertEquals(2.0, Quantity(2000.0, Meter).convertTo(Kilo(Meter)).value, 1e-6)
    }

    @Test
    fun kilometersToMeters() {
        Assert.assertEquals(1000.0, Quantity(1.0, Kilo(Meter)).convertTo(Meter).value, 1e-6)
    }

    @Test
    fun convertSecondsToMinutes() {
        Assert.assertEquals(1.0, Quantity(60.0, Second).convertTo(Minute).value, 1e-6)
    }

    @Test
    fun convertSecondsToHours() {
        Assert.assertEquals(1.0, Quantity(3600.0, Second).convertTo(Hour).value, 1e-6)
    }

    @Test
    fun convertHoursToSeconds() {
        Assert.assertEquals(3600.0, Quantity(1.0, Hour).convertTo(Second).value, 1e-6)
    }

    @Test
    fun mphToMetersPerSecond() {
        Assert.assertEquals(
            8.9408,
            Quantity(20.0, MilesPerHour).convertTo(MetersPerSecond).value,
            1e-6
        )
    }

    @Test
    fun metersPerSecondToMph() {
        Assert.assertEquals(
            20.0,
            Quantity(8.9408, MetersPerSecond).convertTo(MilesPerHour).value,
            1e-6
        )
    }

    @Test
    fun kphToMetersPerSecond() {
        Assert.assertEquals(
            5.5555556,
            Quantity(20.0, KilometersPerHour).convertTo(MetersPerSecond).value,
            1e-6
        )
    }

    @Test
    fun metersPerSecondToKph() {
        Assert.assertEquals(
            20.0,
            Quantity(5.5555556, MetersPerSecond).convertTo(KilometersPerHour).value,
            1e-6
        )
    }
}