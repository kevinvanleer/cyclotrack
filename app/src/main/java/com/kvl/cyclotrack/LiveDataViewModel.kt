package com.kvl.cyclotrack

import android.app.Application
import androidx.lifecycle.AndroidViewModel

class LiveDataViewModel(application: Application) : AndroidViewModel(application) {
    private val locationData = LocationLiveData(application)
    private val sensorData = SensorLiveData(application)

    fun getSensorData() = sensorData
    fun getLocationData() = locationData
}