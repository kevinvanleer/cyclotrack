package com.kvl.cyclotrack

import android.app.Application
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.util.Log
import androidx.lifecycle.MutableLiveData
import java.util.*
import javax.inject.Inject

data class HrmData(var batteryLevel: Int, var bpm: Int)
data class SpeedData(val batteryLevel: Int, val speed: Int)
data class CadenceData(val batteryLevel: Int, val cadence: Int)

class BleService @Inject constructor(context: Application) {

    var hrmSensor = MutableLiveData(HrmData(0, 0))
    var cadenceSensor = MutableLiveData(CadenceData(0, 0))
    var speedSensor = MutableLiveData(SpeedData(0, 0))

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
    val batteryServiceUuid = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    val batteryLevelCharUuid = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    val heartRateServiceUuid = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    val hrmCharacteristicUuid = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")

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

    // Various callback methods defined by the BLE API.
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int,
        ) {
            val intentAction: String
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "Connected to GATT server.")
                    Log.i(TAG, "Attempting to start service discovery: " +
                            gatt.discoverServices())
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "Disconnected from GATT server.")
                }
            }
        }

        private fun readBatteryLevel(gatt: BluetoothGatt) {
            val batteryLevelChar = gatt
                .getService(batteryServiceUuid)?.getCharacteristic(batteryLevelCharUuid)
            //if (batteryLevelChar?.isReadable() == true) {
            Log.d(TAG, "read characteristic $batteryLevelChar")
            gatt.readCharacteristic(batteryLevelChar)
            //}
        }

        private fun prepareHeartRateMeasurement(gatt: BluetoothGatt) {
            val CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID =
                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

            val enable = true
            val heartRateMeasurementChar = gatt
                .getService(heartRateServiceUuid)?.getCharacteristic(hrmCharacteristicUuid)
            Log.d(TAG, "write enable notification descriptor for hrm $heartRateMeasurementChar")
            val descriptor =
                heartRateMeasurementChar?.getDescriptor(
                    CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID)
            descriptor?.value =
                if (enable) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else byteArrayOf(0x00,
                    0x00)
            gatt.writeDescriptor(descriptor)
        }

        private fun readHeartRateMeasurement(gatt: BluetoothGatt) {
            val enable = true
            val heartRateMeasurementChar = gatt
                .getService(heartRateServiceUuid)?.getCharacteristic(hrmCharacteristicUuid)
            //if (batteryLevelChar?.isReadable == true) {
            Log.d(TAG, "enable characteristic notifications $heartRateMeasurementChar")
            gatt.setCharacteristicNotification(heartRateMeasurementChar, enable)
            //TODO: BLE requests cannot be made concurrently
            readBatteryLevel(gatt)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int,
        ) {
            Log.d(TAG, "Write descriptor finished")
            super.onDescriptorWrite(gatt, descriptor, status)
            if (gatt != null) readHeartRateMeasurement(gatt)
        }

        // New services discovered
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            with(gatt) {
                Log.d(TAG, "Discovered services: $status")
                printGattTable()
            }

            prepareHeartRateMeasurement(gatt)
            /*
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED)
                else -> Log.w(TAG, "onServicesDiscovered received: $status")
            }*/
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
                    broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic)
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            Log.d(TAG, "onCharacteristicChanged")
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic)
        }
    }

    // Device scan callback.
    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            //leDeviceListAdapter!!.addDevice(result.device)
            //leDeviceListAdapter.notifyDataSetChanged()
            Log.d("BLE_SERVICE",
                "Found device ${result.device.name}, ${result.device.type}: ${result.device.toString()}")
            if (result.device.address == "DF:AB:78:D7:C5:E9") {
                result.device.connectGatt(context, true, gattCallback)
                bluetoothLeScanner.stopScan(this)
            }
        }
    }


    private fun broadcastUpdate(action: String, characteristic: BluetoothGattCharacteristic) {
        //val intent = Intent(action)

        // This is special handling for the Heart Rate Measurement profile. Data
        // parsing is carried out as per profile specifications.
        Log.d(TAG, "broadcast update for ${characteristic.uuid.toString()}")

        when (characteristic.uuid) {
            hrmCharacteristicUuid -> {
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
                hrmSensor.postValue(HrmData(hrmSensor.value?.batteryLevel ?: 0, heartRate))
                //intent.putExtra(EXTRA_DATA, (heartRate).toString())
            }
            batteryLevelCharUuid -> {
                Log.d(TAG, "Battery level: ${characteristic.value[0]}")
                hrmSensor.postValue(HrmData(characteristic.value[0].toInt(),
                    hrmSensor.value?.bpm ?: 0))
            }
            else -> {
                // For all other profiles, writes the data formatted in HEX.
                val data: ByteArray? = characteristic.value
                if (data?.isNotEmpty() == true) {
                    val hexString: String = data.joinToString(separator = " ") {
                        String.format("%02X", it)
                    }
                    Log.d(TAG, String.format("Received ${characteristic.uuid}: $hexString"))
                    //intent.putExtra(EXTRA_DATA, "$data\n$hexString")
                }
            }

        }
        //sendBroadcast(intent)
    }

    private fun scanLeDevice() {
        if (!mScanning) { // Stops scanning after a pre-defined scan period.
            handler.postDelayed({
                mScanning = false
                bluetoothLeScanner.stopScan(leScanCallback)
            }, SCAN_PERIOD)
            mScanning = true
            bluetoothLeScanner.startScan(leScanCallback)
        } else {
            mScanning = false
            bluetoothLeScanner.stopScan(leScanCallback)
        }
    }

    //private val leDeviceListAdapter: LeDeviceListAdapter? = null


    fun initialize() {
        /*
        if (PackageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.d("BLE_SERVICE", "BLE not supported on this device")
        }*/

        // Initializes Bluetooth adapter.
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        scanLeDevice()

        /*
        // Ensures Bluetooth is available on the device and it is enabled. If not,
// displays a dialog requesting user permission to enable Bluetooth.
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }
         */
    }
}