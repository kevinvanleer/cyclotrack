package com.kvl.cyclotrack

import android.bluetooth.BluetoothDevice
import androidx.room.*
import kotlin.math.pow

data class ExternalSensorFeatures(
    val HRM: Int = (2.0.pow(0).toInt()),
    val SPEED: Int = (2.0.pow(1).toInt()),
    val CADENCE: Int = (2.0.pow(2).toInt()),
    val POWER: Int = (2.0.pow(3).toInt()),
    val FITNESS_MACHINE: Int = (2.0.pow(4).toInt())
)

@Entity(
    foreignKeys = [ForeignKey(
        entity = Bike::class,
        parentColumns = arrayOf("id"),
        childColumns = arrayOf("bikeId"),
        onDelete = ForeignKey.SET_DEFAULT
    )],
    indices = [Index(value = ["bikeId"])]
)
data class ExternalSensor(
    val address: String,
    val name: String? = null,
    val features: Int? = null,
    val bikeId: Long? = null,
    @PrimaryKey(autoGenerate = true) val id: Long? = null,
) {
    constructor(device: BluetoothDevice) : this(
        address = device.address,
        name = device.name,
    )

    @Ignore
    var batteryLevel: Int? = null
}