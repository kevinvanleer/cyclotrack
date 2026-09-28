package com.kvl.cyclotrack

import com.kvl.cyclotrack.data.CadenceSpeedMeasurement
import com.kvl.cyclotrack.data.CyclingPowerMeasurement
import com.kvl.cyclotrack.data.SensorType
import com.kvl.cyclotrack.data.powerToCadence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RpmSeriesTest {
    // lastEvent counts 1/1024 s, so 1024 ticks is one second.
    private fun measurement(
        timestamp: Long,
        revolutions: Int,
        lastEvent: Int,
        rpm: Float?,
        id: Long? = timestamp,
    ) = CadenceSpeedMeasurement(
        tripId = 1,
        timestamp = timestamp,
        revolutions = revolutions,
        lastEvent = lastEvent,
        rpm = rpm,
        sensorType = SensorType.CADENCE,
        id = id,
    )

    @Test
    fun recordedRpmIsUsedAsIsFromTheFirstSample() {
        val series = listOf(
            measurement(1000, 0, 0, 82f),
            measurement(2000, 0, 0, 84f),
        ).rpmSeries()

        assertEquals(listOf(82f, 84f), series.map { it.rpm })
        assertEquals(listOf(1000L, 2000L), series.map { it.time.toEpochMilli() })
    }

    // The row id is what keys the Health Connect record, so it has to survive the
    // conversion — a sample that loses it cannot be written.
    @Test
    fun samplesCarryTheirSourceRow() {
        val series = listOf(
            measurement(1000, 0, 0, 82f, id = 55),
            measurement(2000, 0, 0, 84f, id = 56),
        ).rpmSeries()

        assertEquals(listOf(55L, 56L), series.map { it.sourceId })
    }

    // The regression that mattered: a power meter that omits crank revolutions
    // leaves them defaulted to zero, so differencing them reports a dead crank for
    // a ride that was actually being pedalled.
    @Test
    fun powerMeterWithoutCrankRevolutionsStillReportsItsRecordedCadence() {
        val power = arrayOf(
            CyclingPowerMeasurement(
                id = 7, tripId = 1, timestamp = 1000, instantaneousPower = 210, rpm = 90f
            ),
            CyclingPowerMeasurement(
                id = 8, tripId = 1, timestamp = 2000, instantaneousPower = 215, rpm = 91f
            ),
        )
        val series = powerToCadence(power).toList().rpmSeries()

        assertEquals(listOf(90f, 91f), series.map { it.rpm })
        assertEquals(listOf(7L, 8L), series.map { it.sourceId })
    }

    @Test
    fun withoutRecordedRpmTheFirstSampleOnlySeedsTheRate() {
        assertTrue(listOf(measurement(1000, 100, 1024, null)).rpmSeries().isEmpty())
    }

    @Test
    fun withoutRecordedRpmOneRevolutionPerSecondIsSixtyRpm() {
        val series = listOf(
            measurement(1000, 100, 1024, null),
            measurement(2000, 101, 2048, null),
        ).rpmSeries()

        assertEquals(1, series.size)
        assertEquals(2000, series.single().time.toEpochMilli())
        assertEquals(60f, series.single().rpm, 0.001f)
    }

    @Test
    fun stoppedCranksReportZeroRatherThanDivideByZero() {
        val series = listOf(
            measurement(1000, 100, 1024, null),
            measurement(2000, 100, 2048, null),
        ).rpmSeries()

        assertEquals(listOf(0f), series.map { it.rpm })
    }
}
