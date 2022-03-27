package com.kvl.cyclotrack

import com.kvl.cyclotrack.widgets.getTableFromSplits
import com.kvl.cyclotrack.widgets.getThreeStatFromSplits
import org.junit.Assert
import org.junit.Test

class AnalyticsCardHelpersTests {
    @Test
    fun getThreeStatFromSplits_empty() {
        val resultArray = arrayOf(Pair("RIDES", "0"), Pair("MI", "0"), Pair("HOURS", "0"))
        Assert.assertArrayEquals(
            resultArray,
            getThreeStatFromSplits("1", emptyArray())
        )
    }

    @Test
    fun getThreeStatFromSplits_oneValidEntry() {
        val testSplits = arrayOf(
            Split(
                tripId = 0,
                duration = 120.0,
                totalDuration = 120.0,
                distance = 1600.0,
                totalDistance = 1600.0,
                timestamp = 1648389200000
            )
        )
        val resultArray = arrayOf(Pair("RIDES", "1"), Pair("MI", "1"), Pair("HOURS", "0.0"))
        Assert.assertArrayEquals(
            resultArray,
            getThreeStatFromSplits("1", testSplits)
        )
    }

    @Test
    fun getThreeStatFromSplits_twoValidEntries() {
        val testSplits = arrayOf(
            Split(
                tripId = 0,
                duration = 120.0,
                totalDuration = 120.0,
                distance = 1600.0,
                totalDistance = 1600.0,
                timestamp = 1648389200000
            ),
            Split(
                tripId = 0,
                duration = 120.0,
                totalDuration = 120.0,
                distance = 1600.0,
                totalDistance = 1600.0,
                timestamp = 1648389200000
            )
        )
        val resultArray = arrayOf(Pair("RIDES", "2"), Pair("MI", "2"), Pair("HOURS", "0.1"))
        Assert.assertArrayEquals(
            resultArray,
            getThreeStatFromSplits("1", testSplits)
        )
    }

    @Test
    fun getThreeStatFromSplits_threeValidEntries() {
        val testSplits = arrayOf(
            Split(
                tripId = 0,
                duration = 120.0,
                totalDuration = 120.0,
                distance = 1600.0,
                totalDistance = 1600.0,
                timestamp = 1648389200000
            ),
            Split(
                tripId = 0,
                duration = 120.0,
                totalDuration = 120.0,
                distance = 1600.0,
                totalDistance = 1600.0,
                timestamp = 1648389200000
            ),
            Split(
                tripId = 0,
                duration = 120.0,
                totalDuration = 120.0,
                distance = 1600.0,
                totalDistance = 1600.0,
                timestamp = 1648389200000
            )
        )
        val resultArray = arrayOf(Pair("RIDES", "3"), Pair("MI", "3"), Pair("HOURS", "0.1"))
        Assert.assertArrayEquals(
            resultArray,
            getThreeStatFromSplits("1", testSplits)
        )
    }

    @Test
    fun getTableFromSplits_empty() {
        Assert.assertEquals(
            emptyList<List<List<String>>>(),
            getTableFromSplits("1", emptyArray())
        )
    }

    @Test
    fun getTableFromSplits_oneValidEntry() {
        val testSplits = arrayOf(
            Split(
                tripId = 0,
                duration = 120.0,
                totalDuration = 120.0,
                distance = 1600.0,
                totalDistance = 1600.0,
                timestamp = 1648389200000
            )
        )
        val resultTable = listOf(
            listOf("2022-03-27", "29.8 mph", "2m 0s")
        )
        Assert.assertEquals(resultTable, getTableFromSplits("1", testSplits))
    }

    @Test
    fun getTableFromSplits_twoValidEntries() {
        val testSplits = arrayOf(
            Split(
                tripId = 0,
                duration = 120.0,
                totalDuration = 120.0,
                distance = 1600.0,
                totalDistance = 1600.0,
                timestamp = 1648389200000
            ),
            Split(
                tripId = 0,
                duration = 120.0,
                totalDuration = 120.0,
                distance = 1600.0,
                totalDistance = 1600.0,
                timestamp = 1648389200000
            )
        )
        val resultTable = listOf(
            listOf("2022-03-27", "29.8 mph", "2m 0s"),
            listOf("2022-03-27", "29.8 mph", "2m 0s")
        )
        Assert.assertEquals(resultTable, getTableFromSplits("1", testSplits))
    }

    @Test
    fun getTableFromSplits_threeValidEntries() {
        val testSplits = arrayOf(
            Split(
                tripId = 0,
                duration = 120.0,
                totalDuration = 120.0,
                distance = 1600.0,
                totalDistance = 1600.0,
                timestamp = 1648389200000
            ),
            Split(
                tripId = 0,
                duration = 120.0,
                totalDuration = 120.0,
                distance = 1600.0,
                totalDistance = 1600.0,
                timestamp = 1648389200000
            ),
            Split(
                tripId = 0,
                duration = 120.0,
                totalDuration = 120.0,
                distance = 1600.0,
                totalDistance = 1600.0,
                timestamp = 1648389200000
            )
        )
        val resultTable = listOf(
            listOf("2022-03-27", "29.8 mph", "2m 0s"),
            listOf("2022-03-27", "29.8 mph", "2m 0s"),
            listOf("2022-03-27", "29.8 mph", "2m 0s")
        )
        Assert.assertEquals(resultTable, getTableFromSplits("1", testSplits))
    }

    @Test
    fun getTableFromSplits_fourValidEntries() {
        val testSplits = arrayOf(
            Split(
                tripId = 0,
                duration = 120.0,
                totalDuration = 120.0,
                distance = 1600.0,
                totalDistance = 1600.0,
                timestamp = 1648389200000
            ),
            Split(
                tripId = 0,
                duration = 120.0,
                totalDuration = 120.0,
                distance = 1600.0,
                totalDistance = 1600.0,
                timestamp = 1648389200000
            ),
            Split(
                tripId = 0,
                duration = 120.0,
                totalDuration = 120.0,
                distance = 1600.0,
                totalDistance = 1600.0,
                timestamp = 1648389200000
            ),
            Split(
                tripId = 0,
                duration = 120.0,
                totalDuration = 120.0,
                distance = 1600.0,
                totalDistance = 1600.0,
                timestamp = 1648389200000
            )
        )
        val resultTable = listOf(
            listOf("2022-03-27", "29.8 mph", "2m 0s"),
            listOf("2022-03-27", "29.8 mph", "2m 0s"),
            listOf("2022-03-27", "29.8 mph", "2m 0s"),
        )
        Assert.assertEquals(resultTable, getTableFromSplits("1", testSplits))
    }
}