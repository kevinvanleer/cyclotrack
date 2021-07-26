package com.kvl.cyclotrack

import org.junit.Assert
import org.junit.Test

class StatisticsTest {
    @Test
    fun accumulateAscentDescent_ascent() {

        var testData = listOf(0.0, 10.0, 10.1)
        //Assert.assertEquals(Pair(10.1, 0.0), accumulateAscentDescent(testData, 10.0))
        testData = listOf(0.0, 10.0, 10.1, 10.0, 0.0)
        Assert.assertEquals(Pair(10.1, -10.1), accumulateAscentDescent(testData, 10.0))
        testData = listOf(0.0, 10.0, 10.1, 10.0, -1.0)
        Assert.assertEquals(Pair(10.1, -11.1), accumulateAscentDescent(testData, 10.0))
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
            0.0)
        Assert.assertEquals(Pair(11.0, -11.0), accumulateAscentDescent(testData, 10.0))
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
            0.0)
        Assert.assertEquals(Pair(11.0, -11.0), accumulateAscentDescent(testData, 10.0))
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
            0.0)
        Assert.assertEquals(Pair(11.0, -11.0), accumulateAscentDescent(testData, 10.0))
    }
}