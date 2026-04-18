package com.kvl.cyclotrack

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.os.Build
import android.util.Log
import com.kvl.cyclotrack.util.getIntValue
import java.util.UUID


fun getGattUuid(id: String): UUID {
    val gattUuidSuffix = "0000-1000-8000-00805f9b34fb"
    return UUID.fromString("$id-$gattUuidSuffix")
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
const val powerServiceId = "1818"
const val powerControlPointCharacteristicId = "2a66"
const val powerFeatureCharacteristicId = "2a65"
const val powerMeasurementCharacteristicId = "2a63"
const val powerVectorCharacteristicId = "2a64"

val characteristicUpdateNotificationDescriptorUuid =
    getGattUuid(updateNotificationDescriptorId)
val batteryServiceUuid = getGattUuid(batteryGattServiceId)
val batteryLevelCharUuid = getGattUuid(batterLevelCharacteristicId)

val heartRateServiceUuid = getGattUuid(heartRateServiceId)
val hrmCharacteristicUuid = getGattUuid(hrmCharacteristicId)

val cadenceSpeedServiceUuid = getGattUuid(cadenceSpeedGattServiceId)
val cscMeasurementCharacteristicUuid = getGattUuid(cscMeasurementCharacteristicId)
val cscFeatureCharacteristicUuid = getGattUuid(cscFeatureCharacteristicId)

val powerServiceUuid = getGattUuid(powerServiceId)
val powerFeatureCharacteristicUuid = getGattUuid(powerFeatureCharacteristicId)
val powerMeasurementCharacteristicUuid = getGattUuid(powerMeasurementCharacteristicId)
fun readCharacteristic(gatt: BluetoothGatt, serviceUuid: UUID, characteristicUuid: UUID) {
    val logTag = "BluetoothGatt"
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
    val logTag = "BluetoothGatt"
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
    val descriptorValue =
        if (enable) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else byteArrayOf(
            0x00,
            0x00
        )
    try {
        if (Build.VERSION.SDK_INT >= 33) {
            gatt.writeDescriptor(descriptor, descriptorValue)
        } else {
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor.apply { value = descriptorValue })
        }
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


fun handlePowerFeaturesMessage(
    characteristicValue: ByteArray,
    characteristic: BluetoothGattCharacteristic,
) {
    val logTag = "BluetoothGatt"
    Log.d(logTag, "Received power features")
    val data: ByteArray = characteristicValue
    if (data.isNotEmpty()) {
        val hexString: String = data.joinToString(separator = " ") {
            String.format("%02X", it)
        }
        Log.d(logTag, String.format("Power features: ${characteristic.uuid}: $hexString"))

        val cyclingPowerFeatureBits: Map<IntRange, String> = mapOf(
            0..0 to "Pedal Power Balance Supported",
            1..1 to "Accumulated Torque Supported",
            2..2 to "Wheel Revolution Data Supported",
            3..3 to "Crank Revolution Data Supported",
            4..4 to "Extreme Magnitudes Supported",
            5..5 to "Extreme Angles Supported",
            6..6 to "Top and Bottom Dead Spot Angles Supported",
            7..7 to "Accumulated Energy Supported",
            8..8 to "Offset Compensation Indicator Supported",
            9..9 to "Offset Compensation Supported",
            10..10 to "Cycling Power Measurement Characteristic Content Masking Supported",
            11..11 to "Multiple Sensor Locations Supported",
            12..12 to "Crank Length Adjustment Supported",
            13..13 to "Chain Length Adjustment Supported",
            14..14 to "Chain Weight Adjustment Supported",
            15..15 to "Span Length Adjustment Supported",
            16..16 to "Sensor Measurement Context (0=Force based, 1=Torque based)",
            17..17 to "Instantaneous Measurement Direction Supported",
            18..18 to "Factory Calibration Date Supported",
            19..19 to "Enhanced Offset Compensation Procedure Supported",
            20..21 to "Distributed System Support (00=Unspecified, 01=Not distributed, 10=Distributed, 11=RFU)",
            22..31 to "Reserved for Future Use"
        )

        val featureValue: UInt = data.foldIndexed(0u) { i, acc, byte ->
            acc or ((byte.toUInt() and 0xFFu) shl (i * 8))
        }

        cyclingPowerFeatureBits.forEach { (range, label) ->
            val mask = ((1u shl (range.last - range.first + 1)) - 1u)
            val bits = (featureValue shr range.first) and mask
            if (bits != 0u) {
                Log.d(logTag, "  bit(s) $range set ($bits): $label")
            }
        }
    }
}

fun BluetoothGatt.hasCharacteristic(serviceUuid: UUID, charUuid: UUID): Boolean {
    val thisService = services.find { it.uuid == serviceUuid }
    return null != thisService?.characteristics?.find { it.uuid == charUuid }
}

fun BluetoothGatt.printGattTable() {
    val logTag = "BluetoothGatt"
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
        val logTag = "BluetoothGatt"
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


                when (true) {
                    (hasCharacteristic(
                        cadenceSpeedServiceUuid,
                        cscMeasurementCharacteristicUuid
                    )) ->
                        readCharacteristic(
                            this, cadenceSpeedServiceUuid,
                            cscFeatureCharacteristicUuid
                        )


                    (hasCharacteristic(
                        powerServiceUuid,
                        powerMeasurementCharacteristicUuid
                    ))
                        ->
                        readCharacteristic(
                            this, powerServiceUuid,
                            powerFeatureCharacteristicUuid
                        )

                    else -> {
                        Log.v(logTag, "default service onGetFeatures")
                        onGetFeatures(this, bitmap)
                    }
                }
            }
        }

        @Suppress("DEPRECATION")
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            Log.v(logTag, "DEPRECATED -- onCharacteristicChanged")
            enableNotifications(gatt, characteristic, false)
            when (characteristic.uuid) {
                cscMeasurementCharacteristicUuid -> {
                    val speedId = 0x01
                    val cadenceId = 0x02
                    val sensorType = characteristic.value[0].toInt()
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

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            Log.v(logTag, "onCharacteristicChanged")
            enableNotifications(gatt, characteristic, false)
            when (characteristic.uuid) {
                cscMeasurementCharacteristicUuid -> {
                    val speedId = 0x01
                    val cadenceId = 0x02
                    val sensorType = value[0].toInt()
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

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            Log.v(logTag, "DEPRECATED -- onCharacteristicRead")
            onCharacteristicRead(gatt, characteristic, characteristic.value, status)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            Log.v(logTag, "onCharacteristicRead")
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    when (characteristic.uuid) {
                        batteryLevelCharUuid -> onReadBatteryLevel(gatt, value[0])
                        cscFeatureCharacteristicUuid -> {
                            val speedId = 0x01
                            val cadenceId = 0x02
                            val sensorType =
                                value.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0) ?: 0
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

                        powerFeatureCharacteristicUuid -> {
                            onGetFeatures(gatt, ExternalSensorFeatures().POWER)
                            handlePowerFeaturesMessage(value, characteristic)
                        }
                    }
                }
            }
        }
    }

fun Boolean.toInt() = if (this) 1 else 0
