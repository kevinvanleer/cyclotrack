package com.kvl.cyclotrack

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LinkedSensorsViewModel @Inject constructor(
    private val bleSensors: ExternalSensorRepository,
    bikeRepository: BikeRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {
    private val logTag = "LinkedSensorsViewModel"
    val deviceStates = MutableLiveData<Array<ExternalSensor>>(arrayOf())
    private var gatts = ArrayList<BluetoothGatt>()
    private var scanCallbacks = ArrayList<ScanCallback>()

    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private val bluetoothLeScanner: BluetoothLeScanner? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter.bluetoothLeScanner

    val sensors = bleSensors.observeAll()
    val bikes = bikeRepository.observeAll()

    private fun closeGatt(gatt: BluetoothGatt) {
        try {
            Log.d(logTag, "Closing GATT for ${gatt.device.address}")
            gatt.close()
            gatts.remove(gatt)
        } catch (e: SecurityException) {
            Log.w(logTag, "Bluetooth permissions have not been granted", e)
        }
    }

    private fun closeGatt(gattAddress: String?) {
        gatts.find { it.device.address == gattAddress }?.let { closeGatt(it) }
    }

    private fun connectGatt(device: BluetoothDevice) {
        try {
            if (!gatts.any { it.device.address == device.address })
                gatts.add(
                    device.connectGatt(
                        appContext,
                        true,
                        getGattDiscoverServicesCallback(
                            ::updateFeatures,
                            ::updateBatteryLevel
                        )
                    )
                )
        } catch (e: SecurityException) {
            Log.w(logTag, "Bluetooth permissions have not been granted", e)
        }
    }

    private fun updateFeatures(gatt: BluetoothGatt, features: Int) {
        Log.d(logTag, "features: $features")
        sensors.value?.find { it.address == gatt.device.address }?.let { foundSensor ->
            foundSensor.copy(features = features).let { newSensor ->
                Log.d(logTag, "$newSensor")
                viewModelScope.launch {
                    bleSensors.get(newSensor.address)?.copy(features = features)
                        ?.let { bleSensors.update(it) }
                }
            }
        }
        if (gatt.hasCharacteristic(
                getGattUuid(batteryGattServiceId),
                getGattUuid(batterLevelCharacteristicId)
            )
        ) {
            readBatteryLevel(gatt)
        } else {
            closeGatt(gatt)
        }
    }

    private fun updateBatteryLevel(gatt: BluetoothGatt, batteryLevel: Byte) {
        Log.d(logTag, "battery level: $batteryLevel")
        deviceStates.value?.toMutableList()?.let { list ->
            list.removeIf { it.address == gatt.device.address }
            ExternalSensor(address = gatt.device.address).let { newSensor ->
                newSensor.batteryLevel = batteryLevel.toInt()
                list.add(newSensor)
            }
            deviceStates.postValue(list.toTypedArray())
        }
        closeGatt(gatt)
    }

    private fun scanForDevice(mac: String): ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            try {
                super.onScanResult(callbackType, result)
                Log.d(
                    logTag,
                    "Found device ${result.device.name}, ${result.device.type}: ${result.device}"
                )
                if (result.device.address == mac) {
                    Log.d(
                        logTag,
                        "Connecting to ${result.device.name}, ${result.device.type}: ${result.device}"
                    )
                    connectGatt(result.device)
                    bluetoothLeScanner?.stopScan(this)
                    scanCallbacks.remove(this)
                }
            } catch (e: SecurityException) {
                Log.w(logTag, "Bluetooth permissions have not been granted.", e)
            }
        }
    }

    fun connectLinkedSensors() {
        Log.d(logTag, "initializing bluetooth service")
        if (!BleService.isBluetoothSupported(appContext)) {
            Log.d(logTag, "BLE not supported on this device")
            return
        }

        BleService.enableBluetooth(appContext)

        viewModelScope.launch {
            bleSensors.all().let { linkedSensors ->
                disconnectUnlinkedSensors(linkedSensors)
                connectSensors(linkedSensors)
            }
        }
    }

    private fun connectSensors(linkedSensors: Array<ExternalSensor>) {
        bluetoothManager.adapter?.let { bluetoothAdapter ->
            linkedSensors.forEach {
                try {
                    val device = bluetoothAdapter.getRemoteDevice(it.address)
                    if (device.type == BluetoothDevice.DEVICE_TYPE_UNKNOWN) {
                        Log.d(
                            logTag,
                            "Scanning for uncached device: ${device.address}"
                        )
                        val callback = scanForDevice(device.address)
                        scanCallbacks.add(callback)
                        bluetoothLeScanner?.startScan(callback)
                    } else {
                        Log.d(
                            logTag,
                            "Connecting to ${device.name}, ${device.type}: ${device.address}"
                        )
                        connectGatt(device)
                    }
                } catch (e: SecurityException) {
                    Log.w(logTag, "BLE permissions have not been granted", e)
                } catch (e: IllegalArgumentException) {
                    Log.e(logTag, "Invalid bluetooth address", e)
                    FirebaseCrashlytics.getInstance().recordException(e)
                    viewModelScope.launch(Dispatchers.IO) {
                        bleSensors.removeSensor(it)
                    }
                }
            }
        }
    }

    private fun disconnectUnlinkedSensors(linkedSensors: Array<ExternalSensor>) {
        gatts.map { it.device.address }.forEach { address ->
            if (!linkedSensors.any { address == it.address }) closeGatt(address)
        }
    }

    private fun stopAllScans() {
        try {
            Log.d(logTag, "Stop all scans")
            scanCallbacks.forEach {
                Log.d(logTag, "Stopping device scan")
                bluetoothLeScanner?.stopScan(it)
            }
            scanCallbacks.clear()

            (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).let { bluetoothManager ->
                gatts.filter {
                    bluetoothManager.getConnectionState(
                        it.device,
                        BluetoothProfile.GATT
                    ) != BluetoothProfile.STATE_CONNECTED
                }
                    .forEach { gatt ->
                        closeGatt(gatt)
                    }
            }
        } catch (e: SecurityException) {
            Log.w(logTag, "Bluetooth permissions have not been granted", e)
        }
    }

    private fun disconnect() {
        Log.d(logTag, "disconnect")
        stopAllScans()
        gatts.forEach { gatt ->
            Log.d(logTag, "Disconnecting ${gatt.device.address}")
            try {
                gatt.close()
            } catch (e: SecurityException) {
                Log.w(logTag, "Bluetooth permissions have not been granted.", e)
            }
        }
        gatts.clear()
    }

    override fun onCleared() {
        Log.d(logTag, "view model cleared")
        disconnect()
    }
}
