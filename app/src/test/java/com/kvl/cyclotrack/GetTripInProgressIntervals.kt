package com.kvl.cyclotrack

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class GetTripInProgressIntervals {
    @Test
    fun getTripInProgressIntervals_noData() {
        val testTimeStates = ArrayList<TimeState>()
        val intervals =
            getTripInProgressIntervals(testTimeStates.toTypedArray())
        Assert.assertArrayEquals(ArrayList<LongRange>().toTypedArray(), intervals)
    }

    @Test
    fun getTripInProgressIntervals_oneInterval_typical() {
        val testTimeStates = ArrayList<TimeState>()
        testTimeStates.add(TimeState(0, TimeStateEnum.START, 1234))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, 2345))
        testTimeStates.add(TimeState(0, TimeStateEnum.STOP, 3456))
        val intervals =
            getTripInProgressIntervals(testTimeStates.toTypedArray())
        Assert.assertArrayEquals(arrayOf((1234L until 2345L)), intervals)
    }

    @Test
    fun getTripInProgressIntervals_oneInterval_missingStop() {
        val testTimeStates = ArrayList<TimeState>()
        testTimeStates.add(TimeState(0, TimeStateEnum.START, 1234))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, 2345))
        val intervals =
            getTripInProgressIntervals(testTimeStates.toTypedArray())
        Assert.assertArrayEquals(arrayOf((1234L until 2345L)), intervals)
    }

    @Test
    fun getTripInProgressIntervals_oneInterval_missingPause() {
        val testTimeStates = ArrayList<TimeState>()
        testTimeStates.add(TimeState(0, TimeStateEnum.START, 1234))
        testTimeStates.add(TimeState(0, TimeStateEnum.STOP, 2345))
        val intervals =
            getTripInProgressIntervals(testTimeStates.toTypedArray())
        Assert.assertArrayEquals(arrayOf((1234L until 2345L)), intervals)
    }

    @Test
    fun getTripInProgressIntervals_oneInterval_open() {
        val testTimeStates = ArrayList<TimeState>()
        testTimeStates.add(TimeState(0, TimeStateEnum.START, 1234))
        val intervals =
            getTripInProgressIntervals(
                testTimeStates.toTypedArray(),
                4567L
            )
        Assert.assertArrayEquals(arrayOf((1234L..4567L)), intervals)
    }

    @Test
    fun getTripInProgressIntervals_multiInterval_typical() {
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
        val intervals =
            getTripInProgressIntervals(testTimeStates.toTypedArray())
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
    fun getTripInProgressIntervals_multiInterval_startResume() {
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
        val intervals =
            getTripInProgressIntervals(testTimeStates.toTypedArray())
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
    fun getTripInProgressIntervals_multiInterval_missingStop() {
        val testTimeStates = ArrayList<TimeState>()
        testTimeStates.add(TimeState(0, TimeStateEnum.START, 1234))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, 2345))
        testTimeStates.add(TimeState(0, TimeStateEnum.RESUME, 3456))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, 4567))
        testTimeStates.add(TimeState(0, TimeStateEnum.RESUME, 5678))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, 6789))
        testTimeStates.add(TimeState(0, TimeStateEnum.RESUME, 7890))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, 8901))
        val intervals =
            getTripInProgressIntervals(testTimeStates.toTypedArray())
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
    fun getTripInProgressIntervals_multiInterval_missingPause() {
        val testTimeStates = ArrayList<TimeState>()
        testTimeStates.add(TimeState(0, TimeStateEnum.START, 1234))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, 2345))
        testTimeStates.add(TimeState(0, TimeStateEnum.RESUME, 3456))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, 4567))
        testTimeStates.add(TimeState(0, TimeStateEnum.RESUME, 5678))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, 6789))
        testTimeStates.add(TimeState(0, TimeStateEnum.RESUME, 7890))
        testTimeStates.add(TimeState(0, TimeStateEnum.STOP, 9012))
        val intervals =
            getTripInProgressIntervals(testTimeStates.toTypedArray())
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
    fun getTripInProgressIntervals_multiInterval_open() {
        val testTimeStates = ArrayList<TimeState>()
        testTimeStates.add(TimeState(0, TimeStateEnum.START, 1234))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, 2345))
        testTimeStates.add(TimeState(0, TimeStateEnum.RESUME, 3456))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, 4567))
        testTimeStates.add(TimeState(0, TimeStateEnum.RESUME, 5678))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, 6789))
        testTimeStates.add(TimeState(0, TimeStateEnum.RESUME, 7890))
        val intervals =
            getTripInProgressIntervals(
                testTimeStates.toTypedArray(),
                8901L
            )
        Assert.assertArrayEquals(
            arrayOf(
                (1234L until 2345L),
                (3456L until 4567L),
                (5678L until 6789L),
                (7890L..8901L)
            ), intervals
        )
    }
}