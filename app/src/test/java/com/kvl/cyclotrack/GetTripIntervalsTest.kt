package com.kvl.cyclotrack

import org.junit.Assert
import org.junit.Test


class GetTripIntervalsTest {

    private val zeroedMeasurements =
        Measurements(
            accuracy = 0f,
            altitude = 0.0,
            latitude = 0.0,
            longitude = 0.0,
            speed = 0f,
            time = 0,
            bearing = 0f,
            tripId = 1L,
            elapsedRealtimeNanos = 0L
        )
    private val standardTestMeasurements = arrayListOf(
        zeroedMeasurements.copy(time = 123),
        zeroedMeasurements.copy(time = 234),
        zeroedMeasurements.copy(time = 345),
        zeroedMeasurements.copy(time = 456),
    )

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
    fun getTripIntervals_noData() {
        val testTimeStates = ArrayList<TimeState>()
        val testMeasurements = ArrayList<Measurements>()
        val intervals =
            getTripIntervals(testTimeStates.toTypedArray(), testMeasurements.toTypedArray())
        Assert.assertArrayEquals(ArrayList<LongRange>().toTypedArray(), intervals)
    }

    @Test
    fun getTripIntervals_noTimeStates() {
        val testTimeStates = ArrayList<TimeState>()
        val testMeasurements = standardTestMeasurements
        val intervals =
            getTripIntervals(testTimeStates.toTypedArray(), testMeasurements.toTypedArray())
        Assert.assertArrayEquals(arrayOf((123L..456L)), intervals)
    }

    @Test
    fun getTripIntervals_oneInterval_typical() {
        val testTimeStates = ArrayList<TimeState>()
        testTimeStates.add(TimeState(0, TimeStateEnum.START, 1234))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, 2345))
        testTimeStates.add(TimeState(0, TimeStateEnum.STOP, 3456))
        val testMeasurements = standardTestMeasurements
        val intervals =
            getTripIntervals(testTimeStates.toTypedArray(), testMeasurements.toTypedArray())
        Assert.assertArrayEquals(arrayOf((1234L until 2345L)), intervals)
    }

    @Test
    fun getTripIntervals_oneInterval_missingStop() {
        val testTimeStates = ArrayList<TimeState>()
        testTimeStates.add(TimeState(0, TimeStateEnum.START, 1234))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, 2345))
        val testMeasurements = standardTestMeasurements
        val intervals =
            getTripIntervals(testTimeStates.toTypedArray(), testMeasurements.toTypedArray())
        Assert.assertArrayEquals(arrayOf((1234L until 2345L)), intervals)
    }

    @Test
    fun getTripIntervals_oneInterval_missingPause() {
        val testTimeStates = ArrayList<TimeState>()
        testTimeStates.add(TimeState(0, TimeStateEnum.START, 1234))
        testTimeStates.add(TimeState(0, TimeStateEnum.STOP, 2345))
        val testMeasurements = standardTestMeasurements
        val intervals =
            getTripIntervals(testTimeStates.toTypedArray(), testMeasurements.toTypedArray())
        Assert.assertArrayEquals(arrayOf((1234L until 2345L)), intervals)
    }

    @Test
    fun getTripIntervals_oneInterval_open_resolved() {
        val testTimeStates = ArrayList<TimeState>()
        testTimeStates.add(TimeState(0, TimeStateEnum.START, 1234))
        val testMeasurements = ArrayList<Measurements>()
        testMeasurements.add(zeroedMeasurements.copy(time = 123))
        testMeasurements.add(zeroedMeasurements.copy(time = 234))
        testMeasurements.add(zeroedMeasurements.copy(time = 345))
        testMeasurements.add(zeroedMeasurements.copy(time = 4567))
        val intervals =
            getTripIntervals(testTimeStates.toTypedArray(), testMeasurements.toTypedArray())
        Assert.assertArrayEquals(arrayOf((1234L..4567L)), intervals)
    }


    @Test
    fun getTripIntervals_oneInterval_open_unresolved() {
        val testTimeStates = ArrayList<TimeState>()
        testTimeStates.add(TimeState(0, TimeStateEnum.START, 1234))
        val testMeasurements = standardTestMeasurements
        val intervals =
            getTripIntervals(testTimeStates.toTypedArray(), testMeasurements.toTypedArray())
        Assert.assertArrayEquals(arrayOf((123L..456L)), intervals)
    }

    @Test
    fun getTripIntervals_multiInterval_typical() {
        val testTimeStates = ArrayList<TimeState>()
        testTimeStates.add(TimeState(0, TimeStateEnum.START, 1234))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, 2345))
        testTimeStates.add(TimeState(0, TimeStateEnum.RESUME, 3456))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, 4567))
        testTimeStates.add(TimeState(0, TimeStateEnum.RESUME, 5678))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, 6789))
        testTimeStates.add(TimeState(0, TimeStateEnum.RESUME, 7890))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, 8901))
        testTimeStates.add(TimeState(0, TimeStateEnum.STOP, 9012))
        val testMeasurements = standardTestMeasurements
        val intervals =
            getTripIntervals(testTimeStates.toTypedArray(), testMeasurements.toTypedArray())
        Assert.assertArrayEquals(
            arrayOf(
                (1234L until 2345L),
                (3456L until 4567L),
                (5678L until 6789L),
                (7890L until 8901L)
            ), intervals
        )
    }

    @Test
    fun getTripIntervals_multiInterval_startResume() {
        val testTimeStates = ArrayList<TimeState>()
        testTimeStates.add(TimeState(0, TimeStateEnum.START, 1234))
        testTimeStates.add(TimeState(0, TimeStateEnum.RESUME, 2300))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, 2345))
        testTimeStates.add(TimeState(0, TimeStateEnum.RESUME, 3456))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, 4567))
        testTimeStates.add(TimeState(0, TimeStateEnum.RESUME, 5678))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, 6789))
        testTimeStates.add(TimeState(0, TimeStateEnum.RESUME, 7890))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, 8901))
        testTimeStates.add(TimeState(0, TimeStateEnum.STOP, 9012))
        val testMeasurements = standardTestMeasurements
        val intervals =
            getTripIntervals(testTimeStates.toTypedArray(), testMeasurements.toTypedArray())
        Assert.assertArrayEquals(
            arrayOf(
                (1234L until 2345L),
                (3456L until 4567L),
                (5678L until 6789L),
                (7890L until 8901L)
            ), intervals
        )
    }

    @Test
    fun getTripIntervals_multiInterval_missingStop() {
        val testTimeStates = ArrayList<TimeState>()
        testTimeStates.add(TimeState(0, TimeStateEnum.START, 1234))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, 2345))
        testTimeStates.add(TimeState(0, TimeStateEnum.RESUME, 3456))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, 4567))
        testTimeStates.add(TimeState(0, TimeStateEnum.RESUME, 5678))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, 6789))
        testTimeStates.add(TimeState(0, TimeStateEnum.RESUME, 7890))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, 8901))
        val testMeasurements = standardTestMeasurements
        val intervals =
            getTripIntervals(testTimeStates.toTypedArray(), testMeasurements.toTypedArray())
        Assert.assertArrayEquals(
            arrayOf(
                (1234L until 2345L),
                (3456L until 4567L),
                (5678L until 6789L),
                (7890L until 8901L)
            ), intervals
        )
    }

    @Test
    fun getTripIntervals_multiInterval_missingPause() {
        val testTimeStates = ArrayList<TimeState>()
        testTimeStates.add(TimeState(0, TimeStateEnum.START, 1234))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, 2345))
        testTimeStates.add(TimeState(0, TimeStateEnum.RESUME, 3456))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, 4567))
        testTimeStates.add(TimeState(0, TimeStateEnum.RESUME, 5678))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, 6789))
        testTimeStates.add(TimeState(0, TimeStateEnum.RESUME, 7890))
        testTimeStates.add(TimeState(0, TimeStateEnum.STOP, 9012))
        val testMeasurements = standardTestMeasurements
        val intervals =
            getTripIntervals(testTimeStates.toTypedArray(), testMeasurements.toTypedArray())
        Assert.assertArrayEquals(
            arrayOf(
                (1234L until 2345L),
                (3456L until 4567L),
                (5678L until 6789L),
                (7890L until 9012L)
            ), intervals
        )
    }

    @Test
    fun getTripIntervals_multiInterval_open_resolved() {
        val testTimeStates = ArrayList<TimeState>()
        testTimeStates.add(TimeState(0, TimeStateEnum.START, 1234))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, 2345))
        testTimeStates.add(TimeState(0, TimeStateEnum.RESUME, 3456))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, 4567))
        testTimeStates.add(TimeState(0, TimeStateEnum.RESUME, 5678))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, 6789))
        testTimeStates.add(TimeState(0, TimeStateEnum.RESUME, 7890))
        val testMeasurements = ArrayList<Measurements>()
        testMeasurements.add(zeroedMeasurements.copy(time = 123))
        testMeasurements.add(zeroedMeasurements.copy(time = 234))
        testMeasurements.add(zeroedMeasurements.copy(time = 345))
        testMeasurements.add(zeroedMeasurements.copy(time = 8901))
        val intervals =
            getTripIntervals(testTimeStates.toTypedArray(), testMeasurements.toTypedArray())
        Assert.assertArrayEquals(
            arrayOf(
                (1234L until 2345L),
                (3456L until 4567L),
                (5678L until 6789L),
                (7890L..8901L)
            ), intervals
        )
    }

    @Test
    fun getTripIntervals_multiInterval_open_unresolved() {
        val testTimeStates = ArrayList<TimeState>()
        testTimeStates.add(TimeState(0, TimeStateEnum.START, 1234))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, 2345))
        testTimeStates.add(TimeState(0, TimeStateEnum.RESUME, 3456))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, 4567))
        testTimeStates.add(TimeState(0, TimeStateEnum.RESUME, 5678))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, 6789))
        testTimeStates.add(TimeState(0, TimeStateEnum.RESUME, 7890))
        val testMeasurements = ArrayList<Measurements>()
        testMeasurements.add(zeroedMeasurements.copy(time = 123))
        testMeasurements.add(zeroedMeasurements.copy(time = 234))
        testMeasurements.add(zeroedMeasurements.copy(time = 345))
        testMeasurements.add(zeroedMeasurements.copy(time = 4567))
        val intervals =
            getTripIntervals(testTimeStates.toTypedArray(), testMeasurements.toTypedArray())
        Assert.assertArrayEquals(
            arrayOf(
                (1234L until 2345L),
                (3456L until 4567L),
                (5678L until 6789L)
            ), intervals
        )
    }

    @Test
    fun getTripIntervals_hammer_noTimeStates() {
        val testTimeStates = ArrayList<TimeState>()
        val testMeasurements = getMeasurementsArray(50000)
        val intervals =
            getTripIntervals(testTimeStates.toTypedArray(), testMeasurements)
        Assert.assertArrayEquals(
            arrayOf(
                testMeasurements.first().time..
                        testMeasurements.last().time
            ), intervals
        )
    }

    @Test
    fun getTripIntervals_hammer_oneInterval_typical() {
        val testTimeStates = ArrayList<TimeState>()
        val testMeasurements = getMeasurementsArray(50000)
        testTimeStates.add(TimeState(0, TimeStateEnum.START, testMeasurements.first().time - 10))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, testMeasurements[49842].time + 5))
        testTimeStates.add(TimeState(0, TimeStateEnum.STOP, testMeasurements[49995].time + 15))
        val intervals =
            getTripIntervals(testTimeStates.toTypedArray(), testMeasurements)
        Assert.assertArrayEquals(
            arrayOf(
                testMeasurements.first().time - 10 until
                        testMeasurements[49842].time + 5
            ), intervals
        )
    }

    @Test
    fun getTripIntervals_extra_resume_trip_238() {
        val testTimeStates = ArrayList<TimeState>()
        testTimeStates.add(TimeState(238, TimeStateEnum.START, 1620418633789))
        testTimeStates.add(TimeState(238, TimeStateEnum.RESUME, 1620419639741))
        testTimeStates.add(TimeState(238, TimeStateEnum.PAUSE, 1620425399299))
        testTimeStates.add(TimeState(238, TimeStateEnum.STOP, 1620425405837))
        val intervals =
            getTripIntervals(testTimeStates.toTypedArray())
        Assert.assertEquals(1, intervals.size)
        Assert.assertEquals(1620418633789 until 1620425399299, intervals.first())
    }

    @Test
    fun getTripIntervals_hammer_forceOverlap() {
        val testTimeStates = ArrayList<TimeState>()
        val testMeasurements = getMeasurementsArray(50000)
        testTimeStates.add(TimeState(0, TimeStateEnum.START, testMeasurements.first().time))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, testMeasurements[1000].time))
        testTimeStates.add(TimeState(0, TimeStateEnum.RESUME, testMeasurements[1000].time))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, testMeasurements[2000].time))
        testTimeStates.add(TimeState(0, TimeStateEnum.RESUME, testMeasurements[2000].time))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, testMeasurements.last().time))
        testTimeStates.add(TimeState(0, TimeStateEnum.STOP, testMeasurements.last().time))
        val intervals =
            getTripIntervals(testTimeStates.toTypedArray(), testMeasurements)
        Assert.assertEquals(true, intervals[0].contains(testMeasurements.first().time))
        Assert.assertEquals(true, intervals[1].contains(testMeasurements[1000].time))
        Assert.assertEquals(true, intervals[2].contains(testMeasurements[2000].time))
        //Maybe these should be expected false
        Assert.assertEquals(false, intervals[0].contains(testMeasurements[1000].time))
        Assert.assertEquals(false, intervals[1].contains(testMeasurements[2000].time))
        Assert.assertEquals(false, intervals.last().contains(testMeasurements.last().time))
    }
}