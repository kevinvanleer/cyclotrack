package com.kvl.cyclotrack

import com.opencsv.CSVReader
import org.junit.Assert
import org.junit.Test
import java.io.File
import java.io.FileReader

class AccumulateAscentDescentTest {
    private val error = 0.1
    private fun getElevationTestData(filename: String): List<Pair<Double, Double>> =
        CSVReader(FileReader(
            File(AccumulateAscentDescentTest::class.java.getResource(
                filename)!!.toURI()))).let { reader ->
            reader.readNext().let { line ->
                val altIdx = line.indexOf("altitude")
                val altAccIdx = line.indexOf("verticalAccuracyMeters")
                reader.map { Pair(it[altIdx].toDouble(), it[altAccIdx].toDouble()) }
            }
        }

    @Test
    fun ride_000337_old100() {
        val elevationData =
            getElevationTestData("/ride-data/cyclotrack_000337_Evening-bike-ride.csv")
        val (ascent, descent) = accumulateAscentDescent(elevationData)
        val expected = 167.64
        Assert.assertEquals(expected, ascent, expected * error)
        Assert.assertEquals(-expected, descent, expected * error)
    }

    @Test
    fun ride_000317_old100() {
        val elevationData =
            getElevationTestData("/ride-data/cyclotrack_000317_Evening-bike-ride.csv")
        val (ascent, descent) = accumulateAscentDescent(elevationData)
        val expected = 167.64
        Assert.assertEquals(expected, ascent, expected * error)
        Assert.assertEquals(-expected, descent, expected * error)
    }

    @Test
    fun ride_000316_industrial_park() {
        val elevationData =
            getElevationTestData("/ride-data/cyclotrack_000316_Industrial-park-2.csv")
        val (ascent, descent) = accumulateAscentDescent(elevationData)
        val expected = 342.9
        Assert.assertEquals(expected, ascent, expected * error)
        Assert.assertEquals(-expected, descent, expected * error)
    }

    @Test
    fun ride_000315_route76() {
        val elevationData =
            getElevationTestData("/ride-data/cyclotrack_000315_Route-76.csv")
        val (ascent, descent) = accumulateAscentDescent(elevationData)
        val expected = 368.0
        Assert.assertEquals(expected, ascent, expected * error)
        Assert.assertEquals(-expected, descent, expected * error)
    }

    @Test
    fun ride_000226_klondikePark() {
        val elevationData =
            getElevationTestData("/ride-data/cyclotrack_000226_Dutzow_to_Klondike_Park.csv")
        val (ascent, descent) = accumulateAscentDescent(elevationData)
        val expected = 6.0
        Assert.assertEquals(expected, ascent, expected * error)
        Assert.assertEquals(-expected, descent, expected * error)
    }

    @Test
    fun ride_000309_cullumBranch() {
        val elevationData =
            getElevationTestData("/ride-data/cyclotrack_000309_Dutzow-to-Cullum-Branch.csv")
        val (ascent, descent) = accumulateAscentDescent(elevationData)
        val expected = 6.0
        Assert.assertEquals(expected, ascent, expected * error)
        Assert.assertEquals(-expected, descent, expected * error)
    }
}