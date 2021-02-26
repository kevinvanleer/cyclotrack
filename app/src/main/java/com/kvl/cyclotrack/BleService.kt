package com.kvl.cyclotrack

import android.app.Activity
import android.app.Application
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Handler
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.util.*
import javax.inject.Inject

data class HrmData(var batteryLevel: Byte?, var bpm: Short?)
data class SpeedData(
    val batteryLevel: Byte?,
    val revolutionCount: Int?,
    val lastEvent: Int?,
    val rpm: Float?,
    val timestamp: Long? = null,
)

data class CadenceData(
    val batteryLevel: Byte?,
    val revolutionCount: Int?,
    val lastEvent: Int?,
    val rpm: Float?,
    val timestamp: Long? = null,
)

class BleService @Inject constructor(context: Application, sharedPreferences: SharedPreferences) {
    private val addresses = object {
        var hrm: String? = null
        var speed: String? = null
        var cadence: String? = null
    }
    private val myMacs =
        sharedPreferences.getStringSet(context.resources.getString(R.string.sharedPrefKey_paired_ble_devices),
            HashSet())?.map {
            try {
                Gson().fromJson(it, ExternalSensor::class.java)
            } catch (e: JsonSyntaxException) {
                Log.e(TAG, "Could not parse sensor from JSON", e)
                ExternalSensor("REMOVE_INVALID_SENSOR")
            }
        }?.filter { it.address != "REMOVE_INVALID_SENSOR" }?.toTypedArray()

    private var gatts = ArrayList<BluetoothGatt>()

    var hrmSensor = MutableLiveData(HrmData(null, null))
    var cadenceSensor = MutableLiveData(CadenceData(null, null, null, null))
    var speedSensor = MutableLiveData(SpeedData(null, null, null, null))

    private val TAG = "BLE_SERVICE"
    private val context = context
    private val bluetoothLeScanner = BluetoothAdapter.getDefaultAdapter().bluetoothLeScanner
    private var mScanning = false
    private val handler = Handler()

    // Stops scanning after 10 seconds.
    private val SCAN_PERIOD: Long = 10000
    private val STATE_DISCONNECTED = 0
    private val STATE_CONNECTING = 1
    private val STATE_CONNECTED = 2
    val ACTION_GATT_CONNECTED = "com.example.bluetooth.le.ACTION_GATT_CONNECTED"
    val ACTION_GATT_DISCONNECTED = "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED"
    val ACTION_GATT_SERVICES_DISCOVERED =
        "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED"
    val ACTION_DATA_AVAILABLE = "com.example.bluetooth.le.ACTION_DATA_AVAILABLE"
    val EXTRA_DATA = "com.example.bluetooth.le.EXTRA_DATA"

    val updateNotificationDescriptorId = "2902"
    val cadenceSpeedGattServiceId = "1816"
    val cscMeasurementCharacteristicId = "2a5b"
    val cscFeatureCharacteristicId = "2a5c"
    val batteryGattServiceId = "180f"
    val batterLevelCharacteristicId = "2a19"
    val heartRateServiceId = "180d"
    val hrmCharacteristicId = "2a37"

    val characteristicUpdateNotificationDescriptorUuid = getGattUuid(updateNotificationDescriptorId)
    val batteryServiceUuid = getGattUuid(batteryGattServiceId)
    val batteryLevelCharUuid = getGattUuid(batterLevelCharacteristicId)

    val heartRateServiceUuid = getGattUuid(heartRateServiceId)
    val hrmCharacteristicUuid = getGattUuid(hrmCharacteristicId)

    val cadenceSpeedServiceUuid = getGattUuid(cadenceSpeedGattServiceId)
    val cscMeasurementCharacteristicUuid = getGattUuid(cscMeasurementCharacteristicId)

    private fun BluetoothGatt.printGattTable() {
        if (services.isEmpty()) {
            Log.v(TAG,
                "No service and characteristic available, call discoverServices() first?")
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
        Log.v(TAG, characteristicsTable)
    }

    private fun BluetoothGatt.hasCharacteristic(serviceUuid: UUID, charUuid: UUID): Boolean {
        val thisService = services.find { it.uuid == serviceUuid }
        return null != thisService?.characteristics?.find { it.uuid == charUuid }
    }

    private fun readBatteryLevel(gatt: BluetoothGatt) {
        val batteryLevelChar = gatt
            .getService(batteryServiceUuid)?.getCharacteristic(batteryLevelCharUuid)
        Log.d(TAG, "read battery level characteristic ${batteryLevelChar?.uuid}")
        batteryLevelChar?.let { gatt.readCharacteristic(it) }
    }

    private fun enableNotifications(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        enable: Boolean = true,
    ) {
        val descriptor =
            characteristic.getDescriptor(characteristicUpdateNotificationDescriptorUuid)
        descriptor?.value =
            if (enable) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else byteArrayOf(0x00,
                0x00)
        gatt.writeDescriptor(descriptor)
    }

    // Various callback methods defined by the BLE API.
    private val genericGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int,
        ) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "Connected to GATT server.")
                    Log.i(TAG, "Attempting to start service discovery: " +
                            gatt.discoverServices())
                    gatts.add(gatt)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "Disconnected from GATT server.")
                }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int,
        ) {
            Log.d(TAG, "Write descriptor finished")
            super.onDescriptorWrite(gatt, descriptor, status)
            if (gatt != null && descriptor != null) {
                gatt.setCharacteristicNotification(descriptor.characteristic, true)
                //TODO: Trigger next stage in pipeline here that would allow setup of
                // other notifications or reads like battery level below
                // Be careful you can cause an infinite loop!
            }
        }

        // New services discovered
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            with(gatt) {
                Log.d(TAG, "Discovered services: $status")
                printGattTable()
                when {
                    hasCharacteristic(heartRateServiceUuid,
                        hrmCharacteristicUuid) -> enableNotifications(gatt,
                        gatt.getService(heartRateServiceUuid)
                            .getCharacteristic(hrmCharacteristicUuid))
                    hasCharacteristic(cadenceSpeedServiceUuid,
                        cscMeasurementCharacteristicUuid) -> enableNotifications(gatt,
                        gatt.getService(
                            cadenceSpeedServiceUuid)
                            .getCharacteristic(cscMeasurementCharacteristicUuid))
                    else -> Log.d(TAG, "No supported characteristics")
                }
            }
        }

        // Result of a characteristic read operation
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            Log.d(TAG, "onCharacteristicRead")
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    broadcastUpdate(gatt, characteristic)
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            Log.d(TAG, "onCharacteristicChanged")
            broadcastUpdate(gatt, characteristic)
        }
    }

    // Device scan callback.
    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            Log.d(TAG,
                "Found device ${result.device.name}, ${result.device.type}: ${result.device}")
            /*
            if (myMacs?.contains(result.device.address) == true) {
                Log.d(TAG,
                    "Connecting to ${result.device.name}, ${result.device.type}: ${result.device}")
                result.device.connectGatt(context, true, gattCallback)
            }
             */
        }
    }

    private fun broadcastUpdate(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        Log.d(TAG, "broadcast update for ${characteristic.uuid}")

        when (characteristic.uuid) {
            hrmCharacteristicUuid -> {
                if (addresses.hrm == null) {
                    addresses.hrm = gatt.device.address
                    readBatteryLevel(gatt)
                }
                val flag = characteristic.properties
                val format = when (flag and 0x01) {
                    0x01 -> {
                        Log.d(TAG, "Heart rate format UINT16.")
                        BluetoothGattCharacteristic.FORMAT_UINT16
                    }
                    else -> {
                        Log.d(TAG, "Heart rate format UINT8.")
                        BluetoothGattCharacteristic.FORMAT_UINT8
                    }
                }
                val heartRate = characteristic.getIntValue(format, 1)
                Log.d(TAG, String.format("Received heart rate: %d", heartRate))
                hrmSensor.postValue(HrmData(hrmSensor.value?.batteryLevel,
                    heartRate.toShort()))
            }
            batteryLevelCharUuid -> {
                val batteryLevel = characteristic.value[0]
                Log.d(TAG, "Battery level: $batteryLevel")
                when (gatt.device.address) {
                    addresses.hrm -> {
                        hrmSensor.postValue(hrmSensor.value?.copy(batteryLevel = batteryLevel))
                    }
                    addresses.speed -> {
                        speedSensor.postValue(speedSensor.value?.copy(batteryLevel = batteryLevel))
                    }
                    addresses.cadence -> {
                        cadenceSensor.postValue(cadenceSensor.value?.copy(batteryLevel = batteryLevel))
                    }
                    else -> Log.d(TAG,
                        "No sensor associated with device ${gatt.device.address} with battery level $batteryLevel")
                }
            }
            cscMeasurementCharacteristicUuid -> {
                val timeout = 2000
                val speedId = 0x01
                val cadenceId = 0x02
                val sensorType =
                    characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                when {
                    (sensorType and speedId > 0) -> {
                        if (addresses.speed == null) {
                            addresses.speed = gatt.device.address
                            readBatteryLevel(gatt)
                        }
                        //NOTE: I'm surprised this is allowed since a UINT32 should not be written to an INT32
                        //however in all practicality the value required to induce this bug will never be reached.
                        //Additionally the spec states that this value does not rollover.
                        val revolutionCount =
                            characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 1)
                        val lastEvent =
                            characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 5)
                        if (revolutionCount != speedSensor.value?.revolutionCount ||
                            System.currentTimeMillis() - (speedSensor.value?.timestamp
                                ?: 0) > timeout
                        ) {
                            val rpm = getRpm(revolutionCount,
                                (speedSensor.value?.revolutionCount ?: revolutionCount),
                                lastEvent,
                                (speedSensor.value?.lastEvent ?: lastEvent))
                            Log.d(TAG, "Speed sensor: ${revolutionCount} :: ${lastEvent} :: ${rpm}")
                            speedSensor.postValue(SpeedData(speedSensor.value?.batteryLevel,
                                revolutionCount,
                                lastEvent, rpm, System.currentTimeMillis()))
                        }
                    }
                    (sensorType and cadenceId > 0) -> {
                        if (addresses.cadence == null) {
                            addresses.cadence = gatt.device.address
                            readBatteryLevel(gatt)
                        }
                        val revolutionCount =
                            characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 1)
                        val lastEvent =
                            characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 3)
                        Log.d(TAG,
                            "Cadence sensor changed: ${revolutionCount} :: ${lastEvent}")
                        if (revolutionCount != cadenceSensor.value?.revolutionCount ||
                            System.currentTimeMillis() - (cadenceSensor.value?.timestamp
                                ?: 0) > timeout
                        ) {
                            val rpm = getRpm(revolutionCount,
                                (cadenceSensor.value?.revolutionCount ?: revolutionCount),
                                lastEvent,
                                (cadenceSensor.value?.lastEvent ?: lastEvent))
                            Log.d(TAG,
                                "Cadence sensor update: ${revolutionCount} :: ${lastEvent} :: ${rpm}")
                            cadenceSensor.postValue(CadenceData(cadenceSensor.value?.batteryLevel,
                                revolutionCount,
                                lastEvent, rpm, System.currentTimeMillis()))
                        }
                    }
                    else -> {
                        Log.d(TAG, "Unknown CSC sensor type")
                        val data: ByteArray? = characteristic.value
                        if (data?.isNotEmpty() == true) {
                            val hexString: String = data.joinToString(separator = " ") {
                                String.format("%02X", it)
                            }
                            Log.d(TAG, String.format("Received ${characteristic.uuid}: $hexString"))
                        }
                    }
                }
            }
            else -> {
                // For all other profiles, writes the data formatted in HEX.
                val data: ByteArray? = characteristic.value
                if (data?.isNotEmpty() == true) {
                    val hexString: String = data.joinToString(separator = " ") {
                        String.format("%02X", it)
                    }
                    Log.d(TAG, String.format("Received ${characteristic.uuid}: $hexString"))
                }
            }
        }
    }

    companion object {
        fun isBluetoothSupported(context: Context): Boolean {
            return (context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
        }

        fun isBluetoothEnabled(): Boolean {
            return BluetoothAdapter.getDefaultAdapter().isEnabled
        }

        fun enableBluetooth() {
            BluetoothAdapter.getDefaultAdapter().enable()
        }

        fun enableBluetooth(context: Context, activity: Activity) {
            val bluetoothManager =
                context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val bluetoothAdapter = bluetoothManager.adapter
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                activity.startActivity(enableBtIntent)
            }
        }
    }

    fun initialize(activity: Activity) {
        if (!isBluetoothSupported(context)) {
            Log.d("BLE_SERVICE", "BLE not supported on this device")
            return
        }

        if (myMacs.isNullOrEmpty()) {
            Log.d("BLE_SERVICE", "No BLE devices have been selected by the user")
            return
        }

        enableBluetooth(context, activity)

        //HACK: This is required to successfully connect after reboot
        // Initializes Bluetooth adapter.
        bluetoothLeScanner.startScan(leScanCallback)
        bluetoothLeScanner.stopScan(leScanCallback)
        //END HACK

        myMacs.forEach {
            val device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(it.address)
            Log.d(TAG,
                "Connecting to ${device.name}, ${device.type}: ${device.address}")
            //TODO: Store BLE service for each device and use corresponding callback
            device.connectGatt(context, true, genericGattCallback)
        }
    }

    fun disconnect() {
        gatts.forEach { gatt ->
            Log.d(TAG, "Disconnecting ${gatt.device.address}")
            gatt.disconnect()
        }
    }
}