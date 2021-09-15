package com.kvl.cyclotrack

import com.opencsv.CSVReader
import org.junit.Assert
import org.junit.Test
import java.io.File
import java.io.FileReader

class GetAverageCadenceTest {
    private fun getMeasurementsData(filename: String): List<CriticalMeasurements> =
        CSVReader(
            FileReader(
                File(
                    AccumulateAscentDescentTest::class.java.getResource(
                        filename
                    )!!.toURI()
                )
            )
        ).let { reader ->
            reader.readNext().let { line ->
                val cadenceRevsIdx = line.indexOf("cadenceRevolutions")
                val cadenceLastEventIdx = line.indexOf("cadenceLastEvent")
                val timeIdx = line.indexOf("time")
                reader.map {
                    CriticalMeasurements(
                        cadenceLastEvent = it[cadenceLastEventIdx].toInt(),
                        cadenceRevolutions = it[cadenceRevsIdx].toInt(),
                        time = it[timeIdx].toLong(),
                        accuracy = 0f,
                        altitude = 0.0,
                        latitude = 0.0,
                        longitude = 0.0,
                        speed = 0f
                    )
                }
            }
        }

    @Test
    fun ride_000337_old100() {
        val measurements =
            getMeasurementsData("/ride-data/cyclotrack_000337_Evening-bike-ride.csv")
                .filter { it.cadenceRevolutions != null }
                .sortedBy { it.time }

        var last: CriticalMeasurements? = null
        var count = 0
        measurements.forEach {
            if (last != null) if (!didDeviceFail(it, last!!)) ++count
            last = it
        }

        Assert.assertEquals(measurements.size - 1, count)

        Assert.assertEquals(
            76.5f,
            getAverageCadenceTheEasyWay(measurements)!!, 1e-1f
        )
        Assert.assertEquals(
            78f,
            getAverageCadenceTheHardWay(measurements), 1e-1f
        )
    }

    @Test
    fun ride_000309_cullumBranch() {
        val measurements =
            getMeasurementsData("/ride-data/cyclotrack_000309_Dutzow-to-Cullum-Branch.csv")
                .filter { it.cadenceRevolutions != null }
                .sortedBy { it.time }

        var last: CriticalMeasurements? = null
        var count = 0
        measurements.forEach {
            if (last != null) if (!didDeviceFail(it, last!!)) ++count
            last = it
        }

        Assert.assertEquals(measurements.size - 144, count)

        Assert.assertEquals(
            20.9f, getAverageCadenceTheEasyWay(measurements)!!, 1e-1f
        )
        Assert.assertEquals(
            78.8f,
            getAverageCadenceTheHardWay(measurements), 1e-1f
        )
    }

    @Test
    fun ride_000309_cullumBranch_getAverageCadence() {
        val measurements =
            getMeasurementsData("/ride-data/cyclotrack_000309_Dutzow-to-Cullum-Branch.csv")
        val cleanedMeasurements = measurements
            .filter { it.cadenceRevolutions != null }
            .sortedBy { it.time }
        Assert.assertEquals(
            getAverageCadenceTheHardWay(cleanedMeasurements),
            getAverageCadence(measurements.toTypedArray())
        )
    }

    @Test
    fun ride_000337_old100_getAverageCadence() {
        val measurements =
            getMeasurementsData("/ride-data/cyclotrack_000337_Evening-bike-ride.csv")
        val cleanedMeasurements = measurements
            .filter { it.cadenceRevolutions != null }
            .sortedBy { it.time }
        Assert.assertEquals(
            getAverageCadenceTheEasyWay(cleanedMeasurements),
            getAverageCadence(measurements.toTypedArray())
        )
    }
}