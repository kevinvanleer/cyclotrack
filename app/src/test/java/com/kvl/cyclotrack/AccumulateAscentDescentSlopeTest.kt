package com.kvl.cyclotrack

import com.opencsv.CSVReader
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers.*
import org.junit.Assert
import org.junit.Test
import java.io.File
import java.io.FileReader

class AccumulateAscentDescentSlopeTest {
    private val error = 0.0
    private val slopeThreshold = 0.03
    private fun getElevationTestData(filename: String): List<DerivedMeasurements> {
        var previous: DerivedMeasurements? = null
        return CSVReader(FileReader(
            File(AccumulateAscentDescentSlopeTest::
            class.java.getResource(
                filename)!!.toURI()))).let { reader ->
            reader.readNext().let { header ->
                val latIdx = header.indexOf("latitude")
                val lngIdx = header.indexOf("longitude")
                val accIdx = header.indexOf("accuracy")
                val altIdx = header.indexOf("altitude")
                val altAccIdx = header.indexOf("verticalAccuracyMeters")
                val timeIdx = header.indexOf("time")
                //reader.filterIndexed { index, _ -> index % 10 == 0 }.map {
                reader.map {
                    getDerivedMeasurements(
                        CriticalMeasurements(latitude = it[latIdx].toDouble(),
                            longitude = it[lngIdx].toDouble(),
                            speed = 0f,
                            accuracy = it[accIdx].toFloat(),
                            altitude = it[altIdx].toDouble(),
                            verticalAccuracyMeters = it[altAccIdx].toFloat(),
                            time = it[timeIdx].toLong()
                        ),
                        previous
                    ).also { derived -> previous = derived }
                }
            }
        }
    }

    @Test
    fun ride_000337_old100() {
        val elevationData =
            getElevationTestData("/ride-data/cyclotrack_000337_Evening-bike-ride.csv")
        val (ascent, descent) = accumulateAscentDescent_slope(elevationData, slopeThreshold)
        val expected = 167.64
        Assert.assertEquals(expected, ascent, expected * error)
        Assert.assertEquals(-expected, descent, expected * error)
    }

    @Test
    fun ride_000317_old100() {
        val elevationData =
            getElevationTestData("/ride-data/cyclotrack_000317_Evening-bike-ride.csv")
        val (ascent, descent) = accumulateAscentDescent_slope(elevationData, slopeThreshold)
        val expected = 167.64
        Assert.assertEquals(expected, ascent, expected * error)
        Assert.assertEquals(-expected, descent, expected * error)
    }

    @Test
    fun ride_000316_industrial_park() {
        val elevationData =
            getElevationTestData("/ride-data/cyclotrack_000316_Industrial-park-2.csv")
        val (ascent, descent) = accumulateAscentDescent_slope(elevationData, slopeThreshold)
        val expected = 342.9
        Assert.assertEquals(expected, ascent, expected * error)
        Assert.assertEquals(-expected, descent, expected * error)
    }

    @Test
    fun ride_000315_route76() {
        val elevationData =
            getElevationTestData("/ride-data/cyclotrack_000315_Route-76.csv")
        val (ascent, descent) = accumulateAscentDescent_slope(elevationData, slopeThreshold)
        val expected = 368.0
        Assert.assertEquals(expected, ascent, expected * error)
        Assert.assertEquals(-expected, descent, expected * error)
    }

    @Test
    fun ride_000226_klondikePark() {
        val elevationData =
            getElevationTestData("/ride-data/cyclotrack_000226_Dutzow_to_Klondike_Park.csv")
        val (ascent, descent) = accumulateAscentDescent_slope(elevationData, slopeThreshold)
        val expected = 10.0
        MatcherAssert.assertThat(ascent, lessThan(expected))
        MatcherAssert.assertThat(ascent, greaterThanOrEqualTo(0.0))
        MatcherAssert.assertThat(descent, greaterThan(-expected))
        MatcherAssert.assertThat(descent, lessThanOrEqualTo(0.0))
    }

    @Test
    fun ride_000226_cullumBranch() {
        val elevationData =
            getElevationTestData("/ride-data/cyclotrack_000309_Dutzow-to-Cullum-Branch.csv")
        val (ascent, descent) = accumulateAscentDescent_slope(elevationData, slopeThreshold)
        val expected = 10.0
        MatcherAssert.assertThat(ascent, lessThan(expected))
        MatcherAssert.assertThat(ascent, greaterThanOrEqualTo(0.0))
        MatcherAssert.assertThat(descent, greaterThan(-expected))
        MatcherAssert.assertThat(descent, lessThanOrEqualTo(0.0))
    }
}