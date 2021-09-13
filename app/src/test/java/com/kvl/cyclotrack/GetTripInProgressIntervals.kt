package com.kvl.cyclotrack

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

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
        Assert.assertArrayEquals(arrayOf(LongRange(1234, 2345)), intervals)
    }

    @Test
    fun getTripInProgressIntervals_oneInterval_missingStop() {
        val testTimeStates = ArrayList<TimeState>()
        testTimeStates.add(TimeState(0, TimeStateEnum.START, 1234))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, 2345))
        val intervals =
            getTripInProgressIntervals(testTimeStates.toTypedArray())
        Assert.assertArrayEquals(arrayOf(LongRange(1234, 2345)), intervals)
    }

    @Test
    fun getTripInProgressIntervals_oneInterval_missingPause() {
        val testTimeStates = ArrayList<TimeState>()
        testTimeStates.add(TimeState(0, TimeStateEnum.START, 1234))
        testTimeStates.add(TimeState(0, TimeStateEnum.STOP, 2345))
        val intervals =
            getTripInProgressIntervals(testTimeStates.toTypedArray())
        Assert.assertArrayEquals(arrayOf(LongRange(1234, 2345)), intervals)
    }

    @Test
    fun getTripInProgressIntervals_oneInterval_open() {
        val testTimeStates = ArrayList<TimeState>()
        testTimeStates.add(TimeState(0, TimeStateEnum.START, 1234))
        val intervals =
            getTripInProgressIntervals(
                testTimeStates.toTypedArray(),
                Clock.fixed(Instant.ofEpochMilli(4567), ZoneId.systemDefault())
            )
        Assert.assertArrayEquals(arrayOf(LongRange(1234, 4567)), intervals)
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
        Assert.assertArrayEquals(arrayOf(LongRange(1234, 2345),
            LongRange(3456, 4567),
            LongRange(5678, 6789),
            LongRange(7890, 8901)), intervals)
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
        Assert.assertArrayEquals(arrayOf(LongRange(1234, 2345),
            LongRange(3456, 4567),
            LongRange(5678, 6789),
            LongRange(7890, 8901)), intervals)
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
        Assert.assertArrayEquals(arrayOf(LongRange(1234, 2345),
            LongRange(3456, 4567),
            LongRange(5678, 6789),
            LongRange(7890, 8901)), intervals)
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
        Assert.assertArrayEquals(arrayOf(LongRange(1234, 2345),
            LongRange(3456, 4567),
            LongRange(5678, 6789),
            LongRange(7890, 9012)), intervals)
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
                Clock.fixed(Instant.ofEpochMilli(8901), ZoneId.systemDefault())
            )
        Assert.assertArrayEquals(arrayOf(LongRange(1234, 2345),
            LongRange(3456, 4567),
            LongRange(5678, 6789),
            LongRange(7890, 8901)), intervals)
    }
}