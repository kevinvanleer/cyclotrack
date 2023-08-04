package com.kvl.cyclotrack

import org.junit.Assert
import org.junit.Test

class SplitArrayLastTwoTest {
    private fun zeroSplit(timestamp: Long) = Split(tripId = 1, timestamp = timestamp)

    @Test
    fun lastTwo_empty() {
        val (secondLast, last) = emptyArray<Split>().lastTwo(1)
        Assert.assertEquals(zeroSplit(secondLast.timestamp), secondLast)
        Assert.assertEquals(zeroSplit(last.timestamp), last)
    }

    @Test
    fun lastTwo_one() {
        val testLastSplit = Split(
            tripId = 1,
            distance = 1.0,
            duration = 1.0,
            totalDistance = 1.0,
            totalDuration = 1.0,
            timestamp = 1,
            id = 1
        )
        val (secondLast, last) = arrayOf(

            testLastSplit
        ).lastTwo(1)
        Assert.assertEquals(zeroSplit(secondLast.timestamp), secondLast)
        Assert.assertEquals(testLastSplit, last)
    }

    @Test
    fun lastTwo_two() {
        val testSecondLastSplit = Split(
            tripId = 1,
            distance = 1.0,
            duration = 1.0,
            totalDistance = 1.0,
            totalDuration = 1.0,
            timestamp = 1,
            id = 1
        )
        val testLastSplit = Split(
            tripId = 1,
            distance = 2.0,
            duration = 2.0,
            totalDistance = 2.0,
            totalDuration = 2.0,
            timestamp = 2,
            id = 2
        )
        val (secondLast, last) = arrayOf(
            testSecondLastSplit,
            testLastSplit
        ).lastTwo(1)
        Assert.assertEquals(testSecondLastSplit, secondLast)
        Assert.assertEquals(testLastSplit, last)
    }
}