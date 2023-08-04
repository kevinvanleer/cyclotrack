package com.kvl.cyclotrack

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.greaterThan
import org.junit.Assert
import org.junit.Test

class GetTripLegsTest {
    private fun makeTestMeasurements(i: Long) = Measurements(
        id = i,
        tripId = 1,
        time = i * 1000L + 1688394599L,
        accuracy = 1f,
        bearing = 0f,
        altitude = 0.0,
        latitude = 0.0,
        longitude = i * 0.00001 / 1.113195,
        speed = 1f,
        elapsedRealtimeNanos = i * 1000L,
    )

    private fun getMeasurementsArray(length: Long): Array<Measurements> {
        val testMeasurements = ArrayList<Measurements>()
        for (i in 0 until length) {
            testMeasurements.add(
                makeTestMeasurements(i)
            )
        }
        return testMeasurements.toTypedArray()
    }

    @Test
    fun getTripLegs_noTimeStates() {
        val testMeasurements = getMeasurementsArray(50000)
        val intervals =
            arrayOf(LongRange(testMeasurements.first().time, testMeasurements.last().time))
        val legs = getTripLegs(testMeasurements, intervals)
        assertThat("legs size", legs.size, greaterThan(0))
        Assert.assertEquals(legs.size, 1)
        Assert.assertEquals(legs.first().first().time, testMeasurements.first().time)
        Assert.assertEquals(legs.first().last().time, testMeasurements.last().time)
    }

    @Test
    fun getTripLegs_oneInterval_typical() {
        val testMeasurements = getMeasurementsArray(50000)
        val intervals =
            arrayOf(LongRange(testMeasurements.first().time - 10, testMeasurements[49842].time))
        val legs = getTripLegs(testMeasurements, intervals)
        assertThat("legs size", legs.size, greaterThan(0))
        Assert.assertEquals(1, legs.size)
        Assert.assertEquals(testMeasurements.first().time, legs.first().first().time)
        Assert.assertEquals(testMeasurements[49842].time, legs.first().last().time)
    }

    @Test
    fun getTripLegs_force_overlap() {
        val testMeasurements = getMeasurementsArray(50000)
        val intervals =
            arrayOf(
                (testMeasurements.first().time until testMeasurements[1000].time),
                (testMeasurements[1000].time until testMeasurements[2000].time),
                (testMeasurements[2000].time until testMeasurements.last().time + 1)
            )
        val legs = getTripLegs(testMeasurements, intervals)
        assertThat("legs size", legs.size, greaterThan(0))
        Assert.assertEquals(3, legs.size)
        Assert.assertEquals(testMeasurements.size, legs.sumOf { it.size })
    }
}