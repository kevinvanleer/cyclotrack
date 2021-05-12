package com.kvl.cyclotrack

import org.junit.Assert
import org.junit.Test

class GetTripIntervalsTest {
    @Test
    fun getTripIntervals_noData() {
        val testTimeStates = ArrayList<TimeState>()
        val testMeasurements = ArrayList<CriticalMeasurements>()
        val intervals =
            getTripIntervals(testTimeStates.toTypedArray(), testMeasurements.toTypedArray())
        Assert.assertArrayEquals(ArrayList<LongRange>().toTypedArray(), intervals)
    }

    @Test
    fun getTripIntervals_noTimeStates() {
        val testTimeStates = ArrayList<TimeState>()
        val testMeasurements = ArrayList<CriticalMeasurements>()
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 123, 0, 0f, 0, 0))
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 234, 0, 0f, 0, 0))
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 345, 0, 0f, 0, 0))
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 456, 0, 0f, 0, 0))
        val intervals =
            getTripIntervals(testTimeStates.toTypedArray(), testMeasurements.toTypedArray())
        Assert.assertArrayEquals(arrayOf(LongRange(123, 456)), intervals)
    }

    @Test
    fun getTripIntervals_oneInterval_typical() {
        val testTimeStates = ArrayList<TimeState>()
        testTimeStates.add(TimeState(0, TimeStateEnum.START, 1234))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, 2345))
        testTimeStates.add(TimeState(0, TimeStateEnum.STOP, 3456))
        val testMeasurements = ArrayList<CriticalMeasurements>()
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 123, 0, 0f, 0, 0))
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 234, 0, 0f, 0, 0))
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 345, 0, 0f, 0, 0))
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 456, 0, 0f, 0, 0))
        val intervals =
            getTripIntervals(testTimeStates.toTypedArray(), testMeasurements.toTypedArray())
        Assert.assertArrayEquals(arrayOf(LongRange(1234, 2345)), intervals)
    }

    @Test
    fun getTripIntervals_oneInterval_missingStop() {
        val testTimeStates = ArrayList<TimeState>()
        testTimeStates.add(TimeState(0, TimeStateEnum.START, 1234))
        testTimeStates.add(TimeState(0, TimeStateEnum.PAUSE, 2345))
        val testMeasurements = ArrayList<CriticalMeasurements>()
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 123, 0, 0f, 0, 0))
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 234, 0, 0f, 0, 0))
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 345, 0, 0f, 0, 0))
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 456, 0, 0f, 0, 0))
        val intervals =
            getTripIntervals(testTimeStates.toTypedArray(), testMeasurements.toTypedArray())
        Assert.assertArrayEquals(arrayOf(LongRange(1234, 2345)), intervals)
    }

    @Test
    fun getTripIntervals_oneInterval_missingPause() {
        val testTimeStates = ArrayList<TimeState>()
        testTimeStates.add(TimeState(0, TimeStateEnum.START, 1234))
        testTimeStates.add(TimeState(0, TimeStateEnum.STOP, 2345))
        val testMeasurements = ArrayList<CriticalMeasurements>()
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 123, 0, 0f, 0, 0))
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 234, 0, 0f, 0, 0))
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 345, 0, 0f, 0, 0))
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 456, 0, 0f, 0, 0))
        val intervals =
            getTripIntervals(testTimeStates.toTypedArray(), testMeasurements.toTypedArray())
        Assert.assertArrayEquals(arrayOf(LongRange(1234, 2345)), intervals)
    }

    @Test
    fun getTripIntervals_oneInterval_open_resolved() {
        val testTimeStates = ArrayList<TimeState>()
        testTimeStates.add(TimeState(0, TimeStateEnum.START, 1234))
        val testMeasurements = ArrayList<CriticalMeasurements>()
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 123, 0, 0f, 0, 0))
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 234, 0, 0f, 0, 0))
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 345, 0, 0f, 0, 0))
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 4567, 0, 0f, 0, 0))
        val intervals =
            getTripIntervals(testTimeStates.toTypedArray(), testMeasurements.toTypedArray())
        Assert.assertArrayEquals(arrayOf(LongRange(1234, 4567)), intervals)
    }


    @Test
    fun getTripIntervals_oneInterval_open_unresolved() {
        val testTimeStates = ArrayList<TimeState>()
        testTimeStates.add(TimeState(0, TimeStateEnum.START, 1234))
        val testMeasurements = ArrayList<CriticalMeasurements>()
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 123, 0, 0f, 0, 0))
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 234, 0, 0f, 0, 0))
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 345, 0, 0f, 0, 0))
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 456, 0, 0f, 0, 0))
        val intervals =
            getTripIntervals(testTimeStates.toTypedArray(), testMeasurements.toTypedArray())
        Assert.assertArrayEquals(arrayOf(LongRange(123, 456)), intervals)
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
        val testMeasurements = ArrayList<CriticalMeasurements>()
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 123, 0, 0f, 0, 0))
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 234, 0, 0f, 0, 0))
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 345, 0, 0f, 0, 0))
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 456, 0, 0f, 0, 0))
        val intervals =
            getTripIntervals(testTimeStates.toTypedArray(), testMeasurements.toTypedArray())
        Assert.assertArrayEquals(arrayOf(LongRange(1234, 2345),
            LongRange(3456, 4567),
            LongRange(5678, 6789),
            LongRange(7890, 8901)), intervals)
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
        val testMeasurements = ArrayList<CriticalMeasurements>()
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 123, 0, 0f, 0, 0))
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 234, 0, 0f, 0, 0))
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 345, 0, 0f, 0, 0))
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 456, 0, 0f, 0, 0))
        val intervals =
            getTripIntervals(testTimeStates.toTypedArray(), testMeasurements.toTypedArray())
        Assert.assertArrayEquals(arrayOf(LongRange(1234, 2345),
            LongRange(3456, 4567),
            LongRange(5678, 6789),
            LongRange(7890, 8901)), intervals)
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
        val testMeasurements = ArrayList<CriticalMeasurements>()
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 123, 0, 0f, 0, 0))
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 234, 0, 0f, 0, 0))
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 345, 0, 0f, 0, 0))
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 456, 0, 0f, 0, 0))
        val intervals =
            getTripIntervals(testTimeStates.toTypedArray(), testMeasurements.toTypedArray())
        Assert.assertArrayEquals(arrayOf(LongRange(1234, 2345),
            LongRange(3456, 4567),
            LongRange(5678, 6789),
            LongRange(7890, 8901)), intervals)
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
        val testMeasurements = ArrayList<CriticalMeasurements>()
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 123, 0, 0f, 0, 0))
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 234, 0, 0f, 0, 0))
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 345, 0, 0f, 0, 0))
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 456, 0, 0f, 0, 0))
        val intervals =
            getTripIntervals(testTimeStates.toTypedArray(), testMeasurements.toTypedArray())
        Assert.assertArrayEquals(arrayOf(LongRange(1234, 2345),
            LongRange(3456, 4567),
            LongRange(5678, 6789),
            LongRange(7890, 9012)), intervals)
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
        val testMeasurements = ArrayList<CriticalMeasurements>()
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 123, 0, 0f, 0, 0))
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 234, 0, 0f, 0, 0))
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 345, 0, 0f, 0, 0))
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 8901, 0, 0f, 0, 0))
        val intervals =
            getTripIntervals(testTimeStates.toTypedArray(), testMeasurements.toTypedArray())
        Assert.assertArrayEquals(arrayOf(LongRange(1234, 2345),
            LongRange(3456, 4567),
            LongRange(5678, 6789),
            LongRange(7890, 8901)), intervals)
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
        val testMeasurements = ArrayList<CriticalMeasurements>()
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 123, 0, 0f, 0, 0))
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 234, 0, 0f, 0, 0))
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 345, 0, 0f, 0, 0))
        testMeasurements.add(CriticalMeasurements(0.0, 0.0, 0.0, 0f, 4567, 0, 0f, 0, 0))
        val intervals =
            getTripIntervals(testTimeStates.toTypedArray(), testMeasurements.toTypedArray())
        Assert.assertArrayEquals(arrayOf(LongRange(1234, 2345),
            LongRange(3456, 4567),
            LongRange(5678, 6789)), intervals)
    }
}