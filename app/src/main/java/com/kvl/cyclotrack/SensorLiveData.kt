package com.kvl.cyclotrack

import android.content.Context
import android.content.Context.SENSOR_SERVICE
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.annotation.Keep
import androidx.lifecycle.LiveData
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class SensorLiveData @Inject constructor(@ApplicationContext context: Context) :
    LiveData<SensorModel>() {
    private var sensorManager: SensorManager =
        context.getSystemService(SENSOR_SERVICE) as SensorManager
    private val accListener = AccelerometerEventListener()
    private val gravityListener = GravityEventListener()
    private val gyroListener = GyroscopeEventListener()

    inner class AccelerometerEventListener : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            Log.v(
                "SENSOR",
                "${event.sensor.name}: ${event.values[0]},${event.values[1]},${event.values[2]}"
            )

            var newAccelerometerAverage: FloatArray =
                value?.accelerometerAverage?.copyOf() ?: event.values.copyOf()

            val alpha = 0.05f
            for (i in 0..2) {
                newAccelerometerAverage[i] =
                    alpha * event.values[i] + (1 - alpha) * newAccelerometerAverage[i]
            }
            value = SensorModel(
                gravity = value?.gravity,
                gyroscope = value?.gyroscope,
                gyroscopeAverage = value?.gyroscopeAverage,
                tilt = value?.tilt,
                accelerometer = event,
                accelerometerAverage = newAccelerometerAverage)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            Log.v("SENSOR", "${sensor?.name} accuracy changed: $accuracy")
        }
    }

    inner class GravityEventListener : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            Log.v("SENSOR",
                "${event.sensor.name}: ${event.values[0]},${event.values[1]},${event.values[2]}")

            value = value?.copy(gravity = event) ?: SensorModel(gravity = event,
                gyroscope = null,
                accelerometer = null,
                gyroscopeAverage = null,
                accelerometerAverage = null,
                tilt = null)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            Log.v("SENSOR", "${sensor?.name} accuracy changed: $accuracy")
        }
    }

    inner class GyroscopeEventListener : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            Log.v("SENSOR",
                "${event.sensor.name}: ${event.values[0]},${event.values[1]},${event.values[2]}")

            var newGyroscopeAverage: FloatArray =
                value?.gyroscopeAverage?.copyOf() ?: event.values.copyOf()
            var newTiltArray: FloatArray = value?.tilt?.copyOf() ?: floatArrayOf(0f, 0f, 0f)

            val alpha = 0.53f
            for (i in 0..2) {
                newGyroscopeAverage[i] =
                    alpha * event.values[i] + (1 - alpha) * newGyroscopeAverage[i]
                newTiltArray[i] += event.values[i]
            }

            value = SensorModel(accelerometer = value?.accelerometer,
                accelerometerAverage = value?.accelerometerAverage,
                gravity = value?.gravity,
                gyroscope = event,
                gyroscopeAverage = newGyroscopeAverage,
                tilt = newTiltArray)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            Log.v("SENSOR", "${sensor?.name} accuracy changed: $accuracy")
        }
    }

    override fun onActive() {
        Log.d("UI", "Activating sensor live data")
        /*sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { accSensor ->
            sensorManager.registerListener(gravityListener, accSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }*/
        sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)?.let { gravity ->
            sensorManager.registerListener(
                gravityListener,
                gravity,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { gyroscope ->
            sensorManager.registerListener(
                gyroListener,
                gyroscope,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    override fun onInactive() {
        super.onInactive()
        sensorManager.unregisterListener(gravityListener)
        sensorManager.unregisterListener(gyroListener)
    }
}

@Keep
data class SensorModel(
    val accelerometer: SensorEvent?,
    val accelerometerAverage: FloatArray?,
    val gravity: SensorEvent?,
    val gyroscopeAverage: FloatArray?,
    val gyroscope: SensorEvent?,
    val tilt: FloatArray?,
)