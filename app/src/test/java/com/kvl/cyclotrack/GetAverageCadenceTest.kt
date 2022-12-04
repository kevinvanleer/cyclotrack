package com.kvl.cyclotrack

import com.kvl.cyclotrack.data.CadenceSpeedMeasurement
import com.kvl.cyclotrack.data.SensorType
import com.opencsv.CSVReader
import org.junit.Assert
import org.junit.Test
import java.io.File
import java.io.FileReader

class GetAverageCadenceTest {
    private fun getMeasurementsData(filename: String): List<CadenceSpeedMeasurement> =
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
                val cadenceRpmIdx = line.indexOf("cadenceRpm")
                val cadenceLastEventIdx = line.indexOf("cadenceLastEvent")
                val timeIdx = line.indexOf("time")
                reader.map {
                    CadenceSpeedMeasurement(
                        lastEvent = it[cadenceLastEventIdx].toInt(),
                        revolutions = it[cadenceRevsIdx].toInt(),
                        timestamp = it[timeIdx].toLong(),
                        sensorType = SensorType.CADENCE,
                        rpm = it[cadenceRpmIdx].toFloat(),
                        tripId = 0
                    )
                }
            }
        }

    @Test
    fun ride_000337_old100() {
        val measurements =
            getMeasurementsData("/ride-data/cyclotrack_000337_Evening-bike-ride.csv")
                .filter { it.revolutions != null }
                .sortedBy { it.timestamp }

        var last: CadenceSpeedMeasurement? = null
        var count = 0
        measurements.forEach {
            if (last != null) if (!didDeviceFail(it, last!!)) ++count
            last = it
        }

        Assert.assertEquals(measurements.size - 1, count)

        Assert.assertEquals(
            76.5f,
            getAverageCadenceTheEasyWay(measurements), 1e-1f
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
                .filter { it.revolutions != null }
                .sortedBy { it.timestamp }

        var last: CadenceSpeedMeasurement? = null
        var count = 0
        measurements.forEach {
            if (last != null) if (!didDeviceFail(it, last!!)) ++count
            last = it
        }

        Assert.assertEquals(measurements.size - 144, count)

        Assert.assertEquals(
            20.9f, getAverageCadenceTheEasyWay(measurements), 1e-1f
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
            .filter { it.revolutions != null }
            .sortedBy { it.timestamp }
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
            .filter { it.revolutions != null }
            .sortedBy { it.timestamp }
        Assert.assertEquals(
            getAverageCadenceTheEasyWay(cleanedMeasurements),
            getAverageCadence(measurements.toTypedArray())
        )
    }
}
