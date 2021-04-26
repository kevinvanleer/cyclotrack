package com.kvl.cyclotrack

import org.junit.Assert
import org.junit.Test

class ExportRideTest {
    @Test
    fun getRideMeasurmentsCsv_noData() {
        val testArray = ArrayList<Measurements>()
        val result = getRideMeasurementsCsv(measurements = testArray.toTypedArray())
        Assert.assertEquals(1, result.size)
        Assert.assertArrayEquals(
            arrayOf("accuracy,altitude,bearing,bearingAccuracyDegrees,cadenceLastEvent,cadenceRevolutions,cadenceRpm,elapsedRealtimeNanos,elapsedRealtimeUncertaintyNanos,heartRate,id,latitude,longitude,speed,speedAccuracyMetersPerSecond,speedLastEvent,speedRevolutions,speedRpm,time,tripId,verticalAccuracyMeters,"),
            result)
    }

    @Test
    fun getRideMeasurmentsCsv_basic() {

        val testMeasurements = Measurements(
            tripId = 1,
            accuracy = 1.0f,
            altitude = 100.0,
            bearing = 0.0f,
            elapsedRealtimeNanos = 123456,
            latitude = 123.123,
            longitude = 321.321,
            speed = 11.11f,
            time = 98765432,
            bearingAccuracyDegrees = 0.1f,
            elapsedRealtimeUncertaintyNanos = 1.0,
            speedAccuracyMetersPerSecond = 0.01f,
            verticalAccuracyMeters = 0.01f,
            heartRate = 62,
            cadenceRevolutions = 12345,
            cadenceLastEvent = 2352,
            cadenceRpm = 70f,
            speedRevolutions = 5432,
            speedLastEvent = 4564,
            speedRpm = 15.0f,
            id = 1
        )

        val testArray = ArrayList<Measurements>()

        testArray.add(testMeasurements)
        var result = getRideMeasurementsCsv(measurements = testArray.toTypedArray())
        Assert.assertEquals(2, result.size)
        Assert.assertArrayEquals(arrayOf("accuracy,altitude,bearing,bearingAccuracyDegrees,cadenceLastEvent,cadenceRevolutions,cadenceRpm,elapsedRealtimeNanos,elapsedRealtimeUncertaintyNanos,heartRate,id,latitude,longitude,speed,speedAccuracyMetersPerSecond,speedLastEvent,speedRevolutions,speedRpm,time,tripId,verticalAccuracyMeters,",
            "1.0,100.0,0.0,0.1,2352,12345,70.0,123456,1.0,62,1,123.123,321.321,11.11,0.01,4564,5432,15.0,98765432,1,0.01,"),
            result)

        testArray.clear()
        for (i in 0..9) {
            testArray.add(testMeasurements.copy(id = i.toLong()))
        }
        result = getRideMeasurementsCsv(measurements = testArray.toTypedArray())
        Assert.assertEquals(11, result.size)
        Assert.assertArrayEquals(arrayOf(
            "accuracy,altitude,bearing,bearingAccuracyDegrees,cadenceLastEvent,cadenceRevolutions,cadenceRpm,elapsedRealtimeNanos,elapsedRealtimeUncertaintyNanos,heartRate,id,latitude,longitude,speed,speedAccuracyMetersPerSecond,speedLastEvent,speedRevolutions,speedRpm,time,tripId,verticalAccuracyMeters,",
            "1.0,100.0,0.0,0.1,2352,12345,70.0,123456,1.0,62,0,123.123,321.321,11.11,0.01,4564,5432,15.0,98765432,1,0.01,",
            "1.0,100.0,0.0,0.1,2352,12345,70.0,123456,1.0,62,1,123.123,321.321,11.11,0.01,4564,5432,15.0,98765432,1,0.01,",
            "1.0,100.0,0.0,0.1,2352,12345,70.0,123456,1.0,62,2,123.123,321.321,11.11,0.01,4564,5432,15.0,98765432,1,0.01,",
            "1.0,100.0,0.0,0.1,2352,12345,70.0,123456,1.0,62,3,123.123,321.321,11.11,0.01,4564,5432,15.0,98765432,1,0.01,",
            "1.0,100.0,0.0,0.1,2352,12345,70.0,123456,1.0,62,4,123.123,321.321,11.11,0.01,4564,5432,15.0,98765432,1,0.01,",
            "1.0,100.0,0.0,0.1,2352,12345,70.0,123456,1.0,62,5,123.123,321.321,11.11,0.01,4564,5432,15.0,98765432,1,0.01,",
            "1.0,100.0,0.0,0.1,2352,12345,70.0,123456,1.0,62,6,123.123,321.321,11.11,0.01,4564,5432,15.0,98765432,1,0.01,",
            "1.0,100.0,0.0,0.1,2352,12345,70.0,123456,1.0,62,7,123.123,321.321,11.11,0.01,4564,5432,15.0,98765432,1,0.01,",
            "1.0,100.0,0.0,0.1,2352,12345,70.0,123456,1.0,62,8,123.123,321.321,11.11,0.01,4564,5432,15.0,98765432,1,0.01,",
            "1.0,100.0,0.0,0.1,2352,12345,70.0,123456,1.0,62,9,123.123,321.321,11.11,0.01,4564,5432,15.0,98765432,1,0.01,",
        ),
            result)
    }

    @Test
    fun getDataCsv_noData() {
        val testArray = ArrayList<Measurements>()
        val result = getDataCsv(measurements = testArray.toTypedArray())
        Assert.assertEquals(1, result.size)
        Assert.assertArrayEquals(
            arrayOf("accuracy,altitude,bearing,bearingAccuracyDegrees,cadenceLastEvent,cadenceRevolutions,cadenceRpm,elapsedRealtimeNanos,elapsedRealtimeUncertaintyNanos,heartRate,id,latitude,longitude,speed,speedAccuracyMetersPerSecond,speedLastEvent,speedRevolutions,speedRpm,time,tripId,verticalAccuracyMeters,"),
            result)
    }

    @Test
    fun getDataCsv_basic() {

        val testMeasurements = Measurements(
            tripId = 1,
            accuracy = 1.0f,
            altitude = 100.0,
            bearing = 0.0f,
            elapsedRealtimeNanos = 123456,
            latitude = 123.123,
            longitude = 321.321,
            speed = 11.11f,
            time = 98765432,
            bearingAccuracyDegrees = 0.1f,
            elapsedRealtimeUncertaintyNanos = 1.0,
            speedAccuracyMetersPerSecond = 0.01f,
            verticalAccuracyMeters = 0.01f,
            heartRate = 62,
            cadenceRevolutions = 12345,
            cadenceLastEvent = 2352,
            cadenceRpm = 70f,
            speedRevolutions = 5432,
            speedLastEvent = 4564,
            speedRpm = 15.0f,
            id = 1
        )

        val testArray = ArrayList<Measurements>()
        /*
        for (i in 0..1) {
            testArray.add(testMeasurements.copy(id = i.toLong()))
        }*/

        testArray.add(testMeasurements)
        var result = getDataCsv(measurements = testArray.toTypedArray())
        Assert.assertEquals(2, result.size)
        Assert.assertArrayEquals(arrayOf("accuracy,altitude,bearing,bearingAccuracyDegrees,cadenceLastEvent,cadenceRevolutions,cadenceRpm,elapsedRealtimeNanos,elapsedRealtimeUncertaintyNanos,heartRate,id,latitude,longitude,speed,speedAccuracyMetersPerSecond,speedLastEvent,speedRevolutions,speedRpm,time,tripId,verticalAccuracyMeters,",
            "1.0,100.0,0.0,0.1,2352,12345,70.0,123456,1.0,62,1,123.123,321.321,11.11,0.01,4564,5432,15.0,98765432,1,0.01,"),
            result)

        testArray.clear()
        for (i in 0..9) {
            testArray.add(testMeasurements.copy(id = i.toLong()))
        }
        result = getDataCsv(measurements = testArray.toTypedArray())
        Assert.assertEquals(11, result.size)
        Assert.assertArrayEquals(arrayOf(
            "accuracy,altitude,bearing,bearingAccuracyDegrees,cadenceLastEvent,cadenceRevolutions,cadenceRpm,elapsedRealtimeNanos,elapsedRealtimeUncertaintyNanos,heartRate,id,latitude,longitude,speed,speedAccuracyMetersPerSecond,speedLastEvent,speedRevolutions,speedRpm,time,tripId,verticalAccuracyMeters,",
            "1.0,100.0,0.0,0.1,2352,12345,70.0,123456,1.0,62,0,123.123,321.321,11.11,0.01,4564,5432,15.0,98765432,1,0.01,",
            "1.0,100.0,0.0,0.1,2352,12345,70.0,123456,1.0,62,1,123.123,321.321,11.11,0.01,4564,5432,15.0,98765432,1,0.01,",
            "1.0,100.0,0.0,0.1,2352,12345,70.0,123456,1.0,62,2,123.123,321.321,11.11,0.01,4564,5432,15.0,98765432,1,0.01,",
            "1.0,100.0,0.0,0.1,2352,12345,70.0,123456,1.0,62,3,123.123,321.321,11.11,0.01,4564,5432,15.0,98765432,1,0.01,",
            "1.0,100.0,0.0,0.1,2352,12345,70.0,123456,1.0,62,4,123.123,321.321,11.11,0.01,4564,5432,15.0,98765432,1,0.01,",
            "1.0,100.0,0.0,0.1,2352,12345,70.0,123456,1.0,62,5,123.123,321.321,11.11,0.01,4564,5432,15.0,98765432,1,0.01,",
            "1.0,100.0,0.0,0.1,2352,12345,70.0,123456,1.0,62,6,123.123,321.321,11.11,0.01,4564,5432,15.0,98765432,1,0.01,",
            "1.0,100.0,0.0,0.1,2352,12345,70.0,123456,1.0,62,7,123.123,321.321,11.11,0.01,4564,5432,15.0,98765432,1,0.01,",
            "1.0,100.0,0.0,0.1,2352,12345,70.0,123456,1.0,62,8,123.123,321.321,11.11,0.01,4564,5432,15.0,98765432,1,0.01,",
            "1.0,100.0,0.0,0.1,2352,12345,70.0,123456,1.0,62,9,123.123,321.321,11.11,0.01,4564,5432,15.0,98765432,1,0.01,",
        ),
            result)
    }
}