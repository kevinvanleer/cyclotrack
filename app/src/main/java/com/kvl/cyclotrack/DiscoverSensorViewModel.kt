package com.kvl.cyclotrack

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class DiscoverSensorViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context
) :
    ViewModel() {
    private val logTag = "DiscoverSensorViewModel"
    val bleDevices = MutableLiveData<Array<ExternalSensor>>()
    val selectedDevices = MutableLiveData<Set<ExternalSensor>>()
    private val bluetoothLeScanner: BluetoothLeScanner? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter.bluetoothLeScanner

    fun addToSelectedDevices(device: ExternalSensor) {
        val newSelection = selectedDevices.value?.toMutableSet() ?: ArrayList()
        newSelection.add(device)
        selectedDevices.value = newSelection.toSet()
    }

    fun removeFromSelectedDevices(device: ExternalSensor) {
        val newSelection = selectedDevices.value?.toMutableSet()
        newSelection?.remove(device)
        selectedDevices.value = newSelection?.toSet()
    }

    private val scanDevicesCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            try {
                when (result.device.type) {
                    BluetoothDevice.DEVICE_TYPE_LE, BluetoothDevice.DEVICE_TYPE_DUAL -> {
                        Log.d(
                            logTag,
                            "Found device ${result.device.name}, ${result.device.type}: ${result.device}"
                        )
                        ExternalSensor(result.device).let { sensor ->
                            if (bleDevices.value?.contains(sensor) == false) {
                                bleDevices.value?.toMutableList()?.let { list ->
                                    list.add(0, sensor)
                                    bleDevices.value = list.toTypedArray()
                                }
                            }
                        }
                    }
                }
            } catch (e: SecurityException) {
                Log.w(logTag, "Bluetooth permissions have not been granted", e)
            }
        }
    }

    fun startScan() {
        if (FeatureFlags.devBuild) {
            val testDevices = ArrayList<ExternalSensor>()
            testDevices.add(ExternalSensor("77:07:70:00:00:01", "Thing 1"))
            testDevices.add(ExternalSensor("77:07:70:00:00:02", "Thing 2"))
            testDevices.add(ExternalSensor("77:07:70:00:00:03", "Thing 3"))
            testDevices.add(ExternalSensor("77:07:70:00:00:04", "Thing 4"))
            testDevices.add(ExternalSensor("77:07:70:00:00:05", "Thing 5"))
            testDevices.add(ExternalSensor("77:07:70:00:00:06", "Thing 6"))
            testDevices.add(ExternalSensor("77:07:70:00:00:07", "Thing 7"))
            bleDevices.value = testDevices.toTypedArray()
        } else {
            bleDevices.value = arrayOf()
        }

        try {
            if (BleService.isBluetoothEnabled(appContext)) bluetoothLeScanner?.startScan(
                scanDevicesCallback
            )
        } catch (e: SecurityException) {
            Log.w(logTag, "Bluetooth permissions have not been granted", e)
            throw SecurityException("Bluetooth scan permission has not been granted")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() = bluetoothLeScanner?.stopScan(scanDevicesCallback)
    override fun onCleared() {
        stopScan()
    }

    fun initializeSelectedDevices(linkedDevices: HashSet<ExternalSensor>) {
        selectedDevices.value = linkedDevices.toSet()
    }
}
