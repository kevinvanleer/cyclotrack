package com.kvl.cyclotrack

import android.bluetooth.BluetoothDevice

/*
data class ExternalSensorFeatures() {
    HRM(2.0.pow(0).toInt()),
    SPEED(2.0.pow(1).toInt()),
    CADENCE(2.0.pow(2).toInt()),
    POWER(2.0.pow(3).toInt())
}
*/

data class ExternalSensor(
    val address: String,
    val name: String? = null,
    val features: Int? = null,
) {
    constructor(device: BluetoothDevice) : this(address = device.address, name = device.name)
}