package com.kvl.cyclotrack

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.kvl.cyclotrack.events.BatteryEvent
import com.kvl.cyclotrack.util.SystemUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import org.greenrobot.eventbus.EventBus
import javax.inject.Inject

class BatterySource @Inject constructor(@param:ApplicationContext private val context: Context) {
    private var lastTemp: Float? = null
    private var lastLevel: Float? = null
    private var lastStatus: Int? = null
    private var started = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            val now = SystemUtils.currentTimeMillis()
            intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                ?.takeIf { it != Int.MIN_VALUE }?.div(10f)
                ?.takeIf { it != lastTemp }?.let {
                    lastTemp = it
                    EventBus.getDefault().post(BatteryEvent.Temperature(it, now))
                }
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) {
                (level * 100f / scale).takeIf { it != lastLevel }?.let {
                    lastLevel = it
                    EventBus.getDefault().post(BatteryEvent.Level(it, now))
                }
            }
        }
    }

    private val thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
        if (status != lastStatus) {
            lastStatus = status
            EventBus.getDefault()
                .post(BatteryEvent.ThermalStatus(status, SystemUtils.currentTimeMillis()))
        }
    }

    fun start() {
        if (started) return
        started = true
        context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .addThermalStatusListener(thermalListener)
        }
    }

    fun stop() {
        if (!started) return
        started = false
        context.unregisterReceiver(batteryReceiver)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .removeThermalStatusListener(thermalListener)
        }
        lastTemp = null; lastLevel = null; lastStatus = null
    }
}
