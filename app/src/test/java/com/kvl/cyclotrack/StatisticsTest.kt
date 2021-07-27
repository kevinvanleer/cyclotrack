package com.kvl.cyclotrack

import org.junit.Assert
import org.junit.Test

class StatisticsTest {
    @Test
    fun isRangeGreaterThan_true() {
        Assert.assertEquals(true, isRangeGreaterThan(Pair(5.0, 2.0), Pair(0.0, 1.0)))
        Assert.assertEquals(true, isRangeGreaterThan(Pair(5.0, 2.0), Pair(0.0, 2.9)))
        Assert.assertEquals(true, isRangeGreaterThan(Pair(5.0, 2.0), Pair(0.0, 2.9)))
        Assert.assertEquals(true, isRangeGreaterThan(Pair(5.0, 0.0), Pair(0.0, 0.0)))
    }

    @Test
    fun isRangeGreaterThan_false() {
        Assert.assertEquals(false, isRangeGreaterThan(Pair(5.0, 2.0), Pair(0.0, 3.0)))
        Assert.assertEquals(false, isRangeGreaterThan(Pair(5.0, 2.0), Pair(0.0, 3.1)))
        Assert.assertEquals(false, isRangeGreaterThan(Pair(0.0, 3.0), Pair(5.0, 2.0)))
        Assert.assertEquals(false, isRangeGreaterThan(Pair(0.0, 3.0), Pair(5.0, 1.0)))
    }

    @Test
    fun isRangeLessThan_true() {
        Assert.assertEquals(true, isRangeLessThan(Pair(2.0, 1.0), Pair(10.0, 1.0)))
        Assert.assertEquals(true, isRangeLessThan(Pair(2.0, 1.0), Pair(4.0, 0.9)))
        Assert.assertEquals(true, isRangeLessThan(Pair(2.0, 0.0), Pair(4.0, 0.0)))
    }

    @Test
    fun isRangeLessThan_false() {
        Assert.assertEquals(false, isRangeLessThan(Pair(2.0, 1.0), Pair(4.0, 1.0)))
        Assert.assertEquals(false, isRangeLessThan(Pair(2.0, 1.0), Pair(4.0, 2.0)))
        Assert.assertEquals(false, isRangeLessThan(Pair(2.0, 0.0), Pair(4.0, 10.0)))
    }

    @Test
    fun accumulateAscentDescent_happyPath() {
        var testData = listOf(0.0, 10.0, 10.1).map { Pair(it, 5.0) }
        Assert.assertEquals(Pair(10.1, 0.0), accumulateAscentDescent(testData))
        testData = listOf(0.0, 10.0, 10.1, 10.0, 0.0).map { Pair(it, 5.0) }
        Assert.assertEquals(Pair(10.1, -10.1), accumulateAscentDescent(testData))
        testData = listOf(0.0, 10.0, 10.1, 10.0, -1.0, 10.1, 10.2, 10.0, 0.0).map { Pair(it, 5.0) }
        Assert.assertEquals(Pair(21.2, -21.2), accumulateAscentDescent(testData))
        testData = listOf(0.0, 10.0, 10.1, 10.0, -1.0, 10.0, 10.1, 10.0, 0.0).map { Pair(it, 5.0) }
        Assert.assertEquals(Pair(21.1, -11.1), accumulateAscentDescent(testData))
        testData = listOf(0.0, 10.0, 10.1, 10.0, -1.0).map { Pair(it, 5.0) }
        Assert.assertEquals(Pair(10.1, -11.1), accumulateAscentDescent(testData))
        testData = listOf(0.0,
            0.0,
            0.0,
            1.0,
            0.0,
            2.0,
            0.0,
            3.0,
            0.0,
            4.0,
            0.0,
            5.0,
            0.0,
            6.0,
            0.0,
            7.0,
            8.0,
            0.0,
            9.0,
            11.0,
            9.0,
            1.0,
            0.0).map { Pair(it, 5.0) }
        Assert.assertEquals(Pair(11.0, -11.0), accumulateAscentDescent(testData))
        testData = listOf(0.0,
            0.0,
            0.0,
            1.0,
            0.5,
            2.0,
            1.0,
            3.0,
            2.0,
            4.0,
            3.0,
            5.0,
            4.0,
            6.0,
            5.0,
            7.0,
            6.0,
            8.0,
            9.0,
            11.0,
            9.0,
            8.0,
            9.0,
            7.0,
            8.0,
            6.0,
            7.0,
            0.0).map { Pair(it, 5.0) }
        Assert.assertEquals(Pair(11.0, -11.0), accumulateAscentDescent(testData))
        testData = listOf(0.0,
            0.0,
            0.0,
            1.0,
            0.5,
            2.0,
            1.0,
            3.0,
            2.0,
            4.0,
            3.0,
            5.0,
            4.0,
            6.0,
            5.0,
            7.0,
            6.0,
            8.0,
            9.0,
            11.0,
            9.0,
            8.0,
            9.0,
            7.0,
            8.0,
            6.0,
            7.0,
            0.0).map { Pair(it, 5.0) }
        Assert.assertEquals(Pair(11.0, -11.0), accumulateAscentDescent(testData))
    }

    @Test
    fun accumulateAscentDescent_varyingAccuracy() {
        var testData = listOf(
            Pair(0.0, 5.0),
            Pair(10.0, 5.0),
            Pair(22.0, 100.0),
            Pair(10.1, 5.0),
            Pair(22.0, 100.0),
            Pair(10.0, 5.0),
            Pair(0.0, 5.0),
            Pair(0.0, 5.0),
        )
        Assert.assertEquals(Pair(10.1, -10.1), accumulateAscentDescent(testData))
    }
}