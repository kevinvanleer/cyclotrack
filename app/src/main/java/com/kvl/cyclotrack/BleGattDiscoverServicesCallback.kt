package com.kvl.cyclotrack

import android.annotation.SuppressLint
import android.bluetooth.*
import android.util.Log
import java.util.*


fun getGattUuid(uuid: String): UUID {
    val gattUuidSuffix = "0000-1000-8000-00805f9b34fb"
    return UUID.fromString("$uuid-$gattUuidSuffix")
}

const val updateNotificationDescriptorId = "2902"
const val cadenceSpeedGattServiceId = "1816"
const val cscMeasurementCharacteristicId = "2a5b"
const val cscFeatureCharacteristicId = "2a5c"
const val batteryGattServiceId = "180f"
const val batterLevelCharacteristicId = "2a19"
const val heartRateServiceId = "180d"
const val hrmCharacteristicId = "2a37"
const val fitnessMachineServiceId = "1826"
const val fitnessMachineFeatureCharacteristicId = "2acc"
const val indoorBikeDataCharacteristicId = "2ad2"

val characteristicUpdateNotificationDescriptorUuid =
    getGattUuid(updateNotificationDescriptorId)
val batteryServiceUuid = getGattUuid(batteryGattServiceId)
val batteryLevelCharUuid = getGattUuid(batterLevelCharacteristicId)

val heartRateServiceUuid = getGattUuid(heartRateServiceId)
val hrmCharacteristicUuid = getGattUuid(hrmCharacteristicId)

val cadenceSpeedServiceUuid = getGattUuid(cadenceSpeedGattServiceId)
val cscMeasurementCharacteristicUuid = getGattUuid(cscMeasurementCharacteristicId)
val cscFeatureCharacteristicUuid = getGattUuid(cscFeatureCharacteristicId)

fun readCharacteristic(gatt: BluetoothGatt, serviceUuid: UUID, characteristicUuid: UUID) {
    val logTag = "readCharacteristic"
    val characteristic = gatt
        .getService(serviceUuid)?.getCharacteristic(characteristicUuid)
    Log.v(logTag, "read characteristic ${characteristic?.uuid}")
    try {
        characteristic?.let { gatt.readCharacteristic(it) }
    } catch (e: SecurityException) {
        Log.w(logTag, "Bluetooth permissions have not been granted", e)
    }
}

fun readBatteryLevel(gatt: BluetoothGatt) {
    val logTag = "readBatteryLevel"
    val batteryLevelChar = gatt
        .getService(batteryServiceUuid)?.getCharacteristic(batteryLevelCharUuid)
    Log.v(logTag, "read battery level characteristic ${batteryLevelChar?.uuid}")
    try {
        batteryLevelChar?.let { gatt.readCharacteristic(it) }
    } catch (e: SecurityException) {
        Log.w(logTag, "Bluetooth permissions have not been granted", e)
    }
}

fun enableNotifications(
    gatt: BluetoothGatt,
    characteristic: BluetoothGattCharacteristic,
    enable: Boolean = true,
) {
    val descriptor =
        characteristic.getDescriptor(characteristicUpdateNotificationDescriptorUuid)
    descriptor?.value =
        if (enable) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else byteArrayOf(
            0x00,
            0x00
        )
    try {
        gatt.writeDescriptor(descriptor)
    } catch (e: SecurityException) {
        Log.w("BleUtils:enableNotifications", "Bluetooth permissions have not been granted", e)
    }
}

fun enableNotifications(
    gatt: BluetoothGatt,
    serviceUuid: UUID,
    characteristicUuid: UUID,
    enable: Boolean = true
) {
    gatt
        .getService(serviceUuid)?.getCharacteristic(characteristicUuid)?.let {
            enableNotifications(gatt, it, enable)
        }
}

fun BluetoothGatt.hasCharacteristic(serviceUuid: UUID, charUuid: UUID): Boolean {
    val thisService = services.find { it.uuid == serviceUuid }
    return null != thisService?.characteristics?.find { it.uuid == charUuid }
}

fun BluetoothGatt.printGattTable() {
    val logTag = "printGattTable"
    if (services.isEmpty()) {
        Log.v(
            logTag,
            "No service and characteristic available, call discoverServices() first?"
        )
        return
    }
    var characteristicsTable = ""
    services.forEach { service ->
        val characteristicsTableRow = service.characteristics.joinToString(
            separator = "\n|--",
            prefix = "|--"
        ) { "${it.uuid} ${it.descriptors?.getOrNull(0)?.uuid}" }
        characteristicsTable += "\nService ${service.uuid}\nCharacteristics:\n$characteristicsTableRow"
    }
    Log.v(logTag, characteristicsTable)
}

fun getGattDiscoverServicesCallback(
    onGetFeatures: (gatt: BluetoothGatt, value: Int) -> Unit,
    onReadBatteryLevel: (gatt: BluetoothGatt, value: Byte) -> Unit
) =
    object : BluetoothGattCallback() {
        val logTag = "gattDiscoverServicesCallback"
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int,
        ) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(logTag, "Connected to GATT server ${gatt.device.address}.")
                    try {
                        Log.i(
                            logTag,
                            "Attempting to start service discovery: ${gatt.discoverServices()}"
                        )
                    } catch (e: SecurityException) {
                        Log.w(logTag, "Bluetooth permissions have not been granted", e)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(logTag, "Disconnected ${gatt.device.address} from GATT server.")
                }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int,
        ) {
            Log.d(logTag, "Write descriptor finished")
            super.onDescriptorWrite(gatt, descriptor, status)
            if (gatt != null && descriptor != null) {
                try {
                    gatt.setCharacteristicNotification(descriptor.characteristic, true)
                } catch (e: SecurityException) {
                    Log.w(logTag, "Bluetooth permissions have not been granted", e)
                }
                //TODO: Trigger next stage in pipeline here that would allow setup of
                // other notifications or reads like battery level below
                // Be careful you can cause an infinite loop!
                readBatteryLevel(gatt)
            }
        }

        // New services discovered
        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            with(gatt) {
                Log.d(logTag, "Discovered services status: $status")
                printGattTable()

                val bitmap =
                    hasCharacteristic(
                        heartRateServiceUuid,
                        hrmCharacteristicUuid
                    ).toInt() * ExternalSensorFeatures().HRM +
                            hasCharacteristic(
                                getGattUuid(fitnessMachineServiceId),
                                getGattUuid(indoorBikeDataCharacteristicId)
                            ).toInt() * ExternalSensorFeatures().FITNESS_MACHINE


                when (hasCharacteristic(
                    cadenceSpeedServiceUuid,
                    cscMeasurementCharacteristicUuid
                )) {
                    true ->
                        readCharacteristic(
                            this, cadenceSpeedServiceUuid,
                            cscFeatureCharacteristicUuid
                        )
                    else -> {
                        onGetFeatures(this, bitmap)
                    }
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            Log.v(logTag, "onCharacteristicChanged")
            enableNotifications(gatt, characteristic, false)
            when (characteristic.uuid) {
                cscMeasurementCharacteristicUuid -> {
                    val speedId = 0x01
                    val cadenceId = 0x02
                    val sensorType =
                        characteristic.getIntValue(
                            BluetoothGattCharacteristic.FORMAT_UINT8,
                            0
                        )
                    when {
                        (sensorType and speedId > 0) -> onGetFeatures(
                            gatt,
                            ExternalSensorFeatures().SPEED
                        )
                        (sensorType and cadenceId > 0) -> onGetFeatures(
                            gatt,
                            ExternalSensorFeatures().CADENCE
                        )
                    }
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            Log.v(logTag, "onCharacteristicRead")
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    when (characteristic.uuid) {
                        batteryLevelCharUuid -> onReadBatteryLevel(gatt, characteristic.value[0])
                        cscFeatureCharacteristicUuid -> {
                            val speedId = 0x01
                            val cadenceId = 0x02
                            val sensorType =
                                characteristic.getIntValue(
                                    BluetoothGattCharacteristic.FORMAT_UINT8,
                                    0
                                )
                            when {
                                (sensorType and speedId > 0) -> onGetFeatures(
                                    gatt,
                                    ExternalSensorFeatures().SPEED
                                )
                                (sensorType and cadenceId > 0) -> onGetFeatures(
                                    gatt,
                                    ExternalSensorFeatures().CADENCE
                                )
                            }
                        }
                    }
                }
            }
        }
    }

fun Boolean.toInt() = if (this) 1 else 0
