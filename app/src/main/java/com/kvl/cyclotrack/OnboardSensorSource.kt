package com.kvl.cyclotrack

import android.content.Context
import android.content.Context.SENSOR_SERVICE
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import com.kvl.cyclotrack.events.OnboardSensorEvent
import com.kvl.cyclotrack.util.SystemUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import org.greenrobot.eventbus.EventBus
import javax.inject.Inject
import kotlin.math.abs

class OnboardSensorSource @Inject constructor(@ApplicationContext context: Context) :
    SensorEventListener {
    private val sensorManager: SensorManager =
        context.getSystemService(SENSOR_SERVICE) as SensorManager

    // events are delivered, posted, and (with EventBus's default POSTING mode)
    // consumed on this thread, keeping the sensor pipeline off the main thread
    private var handlerThread: HandlerThread? = null

    fun start() {
        if (handlerThread != null) return
        Log.d("SENSOR", "Starting onboard sensor source")
        val thread = HandlerThread("OnboardSensorSource").apply { start() }
        handlerThread = thread
        val handler = Handler(thread.looper)
        // TYPE_ACCELEROMETER intentionally not registered; arrives with slope-correction
        listOf(Sensor.TYPE_GRAVITY, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_PRESSURE)
            .forEach { type ->
                sensorManager.getDefaultSensor(type)?.let { sensor ->
                    sensorManager.registerListener(
                        this,
                        sensor,
                        SensorManager.SENSOR_DELAY_NORMAL,
                        handler
                    )
                }
            }
    }

    fun stop() {
        Log.d("SENSOR", "Stopping onboard sensor source")
        sensorManager.unregisterListener(this)
        handlerThread?.quitSafely()
        handlerThread = null
    }

    // values are copied and the wall-clock timestamp fixed here because the framework
    // recycles SensorEvent objects
    override fun onSensorChanged(event: SensorEvent) {
        // event.timestamp is usually elapsedRealtimeNanos, but some HALs use other clock
        // bases; fall back to receipt time when the derived wall clock is implausible
        val now = SystemUtils.currentTimeMillis()
        val derived = now - (SystemClock.elapsedRealtimeNanos() - event.timestamp) / 1_000_000
        val timestamp = if (abs(now - derived) <= SENSOR_CLOCK_SANITY_MS) derived else now
        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY -> OnboardSensorEvent.Gravity(
                event.values[0], event.values[1], event.values[2], timestamp
            )

            Sensor.TYPE_GYROSCOPE -> OnboardSensorEvent.Gyroscope(
                event.values[0], event.values[1], event.values[2], timestamp
            )

            Sensor.TYPE_ACCELEROMETER -> OnboardSensorEvent.Accelerometer(
                event.values[0], event.values[1], event.values[2], timestamp
            )

            Sensor.TYPE_PRESSURE -> OnboardSensorEvent.Pressure(event.values[0], timestamp)
            else -> null
        }?.let { EventBus.getDefault().post(it) }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.v("SENSOR", "${sensor?.name} accuracy changed: $accuracy")
    }

    companion object {
        const val SENSOR_CLOCK_SANITY_MS = 10_000L
    }
}
