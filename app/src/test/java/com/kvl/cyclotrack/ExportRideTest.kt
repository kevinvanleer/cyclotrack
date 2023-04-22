package com.kvl.cyclotrack

import org.junit.Assert
import org.junit.Test

class ExportRideTest {
    @Test
    fun getDataCsv_noData() {
        val testArray = ArrayList<Measurements>()
        val result = getDataCsv(measurements = testArray.toTypedArray())
        Assert.assertEquals(1, result.size)
        Assert.assertArrayEquals(
            arrayOf("accuracy,altitude,bearing,bearingAccuracyDegrees,elapsedRealtimeNanos,elapsedRealtimeUncertaintyNanos,id,latitude,longitude,speed,speedAccuracyMetersPerSecond,time,tripId,verticalAccuracyMeters,"),
            result
        )
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
            id = 1
        )

        val testArray = ArrayList<Measurements>()

        testArray.add(testMeasurements)
        var result = getDataCsv(measurements = testArray.toTypedArray())
        Assert.assertEquals(2, result.size)
        Assert.assertArrayEquals(
            arrayOf(
                "accuracy,altitude,bearing,bearingAccuracyDegrees,elapsedRealtimeNanos,elapsedRealtimeUncertaintyNanos,id,latitude,longitude,speed,speedAccuracyMetersPerSecond,time,tripId,verticalAccuracyMeters,",
                "1.0,100.0,0.0,0.1,123456,1.0,1,123.123,321.321,11.11,0.01,98765432,1,0.01,"
            ),
            result
        )

        testArray.clear()
        for (i in 0..9) {
            testArray.add(testMeasurements.copy(id = i.toLong()))
        }
        result = getDataCsv(measurements = testArray.toTypedArray())
        Assert.assertEquals(11, result.size)
        Assert.assertArrayEquals(
            arrayOf(
                "accuracy,altitude,bearing,bearingAccuracyDegrees,elapsedRealtimeNanos,elapsedRealtimeUncertaintyNanos,id,latitude,longitude,speed,speedAccuracyMetersPerSecond,time,tripId,verticalAccuracyMeters,",
                "1.0,100.0,0.0,0.1,123456,1.0,0,123.123,321.321,11.11,0.01,98765432,1,0.01,",
                "1.0,100.0,0.0,0.1,123456,1.0,1,123.123,321.321,11.11,0.01,98765432,1,0.01,",
                "1.0,100.0,0.0,0.1,123456,1.0,2,123.123,321.321,11.11,0.01,98765432,1,0.01,",
                "1.0,100.0,0.0,0.1,123456,1.0,3,123.123,321.321,11.11,0.01,98765432,1,0.01,",
                "1.0,100.0,0.0,0.1,123456,1.0,4,123.123,321.321,11.11,0.01,98765432,1,0.01,",
                "1.0,100.0,0.0,0.1,123456,1.0,5,123.123,321.321,11.11,0.01,98765432,1,0.01,",
                "1.0,100.0,0.0,0.1,123456,1.0,6,123.123,321.321,11.11,0.01,98765432,1,0.01,",
                "1.0,100.0,0.0,0.1,123456,1.0,7,123.123,321.321,11.11,0.01,98765432,1,0.01,",
                "1.0,100.0,0.0,0.1,123456,1.0,8,123.123,321.321,11.11,0.01,98765432,1,0.01,",
                "1.0,100.0,0.0,0.1,123456,1.0,9,123.123,321.321,11.11,0.01,98765432,1,0.01,",
            ),
            result
        )
    }
}
