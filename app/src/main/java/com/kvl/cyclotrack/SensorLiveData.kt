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

class SensorLiveData(context: Context) : LiveData<SensorModel>() {
    private var sensorManager: SensorManager =
        context.getSystemService(SENSOR_SERVICE) as SensorManager
    private var accelerometer: Sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var gyroscope: Sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accListener = AccelerometerEventListener()
    private val gyroListener = GyroscopeEventListener()

    inner class AccelerometerEventListener : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            Log.v("SENSOR",
                "${event.sensor.name}: ${event.values[0]},${event.values[1]},${event.values[2]}")

            var newAccelerometerAverage: FloatArray =
                value?.accelerometerAverage?.copyOf() ?: event.values.copyOf()

            val alpha = 0.05f
            for (i in 0..2) {
                newAccelerometerAverage[i] =
                    alpha * event.values[i] + (1 - alpha) * newAccelerometerAverage[i]
            }
            value = SensorModel(gyroscope = value?.gyroscope,
                gyroscopeAverage = value?.gyroscopeAverage,
                tilt = value?.tilt,
                accelerometer = event,
                accelerometerAverage = newAccelerometerAverage)
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
        sensorManager.registerListener(accListener,
            accelerometer,
            SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(gyroListener, gyroscope, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onInactive() {
        super.onInactive()
        sensorManager.unregisterListener(accListener)
        sensorManager.unregisterListener(gyroListener)
    }
}

@Keep
data class SensorModel(
    val accelerometer: SensorEvent?,
    val accelerometerAverage: FloatArray?,
    val gyroscopeAverage: FloatArray?,
    val gyroscope: SensorEvent?,
    val tilt: FloatArray?,
)