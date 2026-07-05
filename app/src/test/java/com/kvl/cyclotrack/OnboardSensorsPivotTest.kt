package com.kvl.cyclotrack

import com.kvl.cyclotrack.data.OnboardSensorType
import com.kvl.cyclotrack.data.OnboardSensorsPivot
import com.kvl.cyclotrack.data.reconstructSnapshots
import org.junit.Assert
import org.junit.Test

class OnboardSensorsPivotTest {
    private fun row(
        id: Long,
        gravity: Triple<Float, Float, Float>? = null,
        gyro: Triple<Float, Float, Float>? = null,
        accel: Triple<Float, Float, Float>? = null,
        pressure: Float? = null,
    ) = OnboardSensors(
        tripId = 1,
        timestamp = id * 100,
        gravityX = gravity?.first, gravityY = gravity?.second, gravityZ = gravity?.third,
        gyroscopeX = gyro?.first, gyroscopeY = gyro?.second, gyroscopeZ = gyro?.third,
        accelerometerX = accel?.first, accelerometerY = accel?.second, accelerometerZ = accel?.third,
        pressure = pressure,
        id = id,
    )

    @Test
    fun `emits one measurement per changed sensor and drops repeats`() {
        val g1 = Triple(0.1f, 9.8f, 0.2f)
        val g2 = Triple(0.2f, 9.7f, 0.3f)
        val out = OnboardSensorsPivot().pivot(
            listOf(
                row(1, gravity = g1, pressure = 1013.1f),
                row(2, gravity = g1, pressure = 1013.1f),
                row(3, gravity = g2, pressure = 1013.1f),
                row(4, gravity = g2, pressure = 1013.2f),
            )
        )
        Assert.assertEquals(
            listOf(
                OnboardSensorType.GRAVITY to 0.1f,
                OnboardSensorType.PRESSURE to 1013.1f,
                OnboardSensorType.GRAVITY to 0.2f,
                OnboardSensorType.PRESSURE to 1013.2f,
            ),
            out.map { it.sensorType to it.v0 },
        )
        Assert.assertEquals(9.8f, out[0].v1)
        Assert.assertNull(out[1].v1)
    }

    @Test
    fun `dedupe state carries across chunks and tracks lastId`() {
        val g = Triple(0.1f, 9.8f, 0.2f)
        val pivot = OnboardSensorsPivot()
        val first = pivot.pivot(listOf(row(10, gravity = g)))
        val second = pivot.pivot(listOf(row(11, gravity = g), row(12, pressure = 1000f)))
        Assert.assertEquals(1, first.size)
        Assert.assertEquals(listOf(OnboardSensorType.PRESSURE), second.map { it.sensorType })
        Assert.assertEquals(12, pivot.lastId)
    }

    @Test
    fun `null sensors emit nothing`() {
        Assert.assertTrue(OnboardSensorsPivot().pivot(listOf(row(1))).isEmpty())
    }

    @Test
    fun `reconstruct carries values forward like legacy snapshots`() {
        val rows = listOf(
            row(1, gravity = Triple(0.1f, 9.8f, 0.2f), pressure = 1013.1f),
            row(2, gravity = Triple(0.2f, 9.7f, 0.3f), pressure = 1013.1f),
            row(3, gravity = Triple(0.2f, 9.7f, 0.3f), pressure = 1013.2f),
        )
        val snapshots = reconstructSnapshots(OnboardSensorsPivot().pivot(rows))
        val final = snapshots.last()
        Assert.assertEquals(0.2f, final.gravityX)
        Assert.assertEquals(9.7f, final.gravityY)
        Assert.assertEquals(1013.2f, final.pressure)
        Assert.assertTrue(snapshots.all { it.pressure != null || it.gravityX != null })
        Assert.assertEquals(
            rows.last().timestamp,
            snapshots.last().timestamp,
        )
    }
}
