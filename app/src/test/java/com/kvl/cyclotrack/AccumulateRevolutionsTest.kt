package com.kvl.cyclotrack

import com.kvl.cyclotrack.data.CadenceSpeedMeasurement
import com.kvl.cyclotrack.data.SensorType
import org.junit.Assert
import org.junit.Test

class AccumulateRevolutionsTest {
    private fun measurement(timestamp: Long, revolutions: Int, lastEvent: Int) =
        CadenceSpeedMeasurement(
            tripId = 1,
            timestamp = timestamp,
            revolutions = revolutions,
            lastEvent = lastEvent,
            rpm = 90f,
            sensorType = SensorType.SPEED
        )

    @Test
    fun accumulateRevolutions_simple() {
        val measurements = arrayOf(
            measurement(0, 100, 1000),
            measurement(1000, 150, 2000),
            measurement(2000, 210, 3000),
        )
        Assert.assertEquals(110L, accumulateRevolutions(measurements))
    }

    @Test
    fun accumulateRevolutions_rollover() {
        val measurements = arrayOf(
            measurement(0, 65530, 1000),
            measurement(1000, 4, 2000),
        )
        Assert.assertEquals(10L, accumulateRevolutions(measurements))
    }

    @Test
    fun accumulateRevolutions_resetMaskedByLastEventRollover() {
        // Sensor reset during a pause: revolutions restart near zero but
        // lastEvent (16-bit, 64s wrap) has climbed past the old value, so
        // validateSpeed passes the pair. The negative delta must be skipped,
        // not accumulated.
        val measurements = arrayOf(
            measurement(0, 14000, 2119),
            measurement(1000, 14278, 2200),
            measurement(245000, 21, 12980),
            measurement(246000, 50, 13990),
        )
        Assert.assertEquals(278L + 29L, accumulateRevolutions(measurements))
    }
}
