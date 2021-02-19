package com.kvl.cyclotrack

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.SharedPreferences
import android.util.Log
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class DiscoverSensorViewModel @ViewModelInject constructor(private val sharedPreferences: SharedPreferences) :
    ViewModel() {
    val TAG = "DiscoverSensorViewModel"
    val bleDevices = MutableLiveData<Array<ExternalSensor>>()
    val selectedDevices = MutableLiveData<Set<ExternalSensor>>()
    private val bluetoothLeScanner: BluetoothLeScanner? =
        BluetoothAdapter.getDefaultAdapter().bluetoothLeScanner

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
            when (result.device.type) {
                BluetoothDevice.DEVICE_TYPE_LE, BluetoothDevice.DEVICE_TYPE_DUAL -> {
                    Log.d(TAG,
                        "Found device ${result.device.name}, ${result.device.type}: ${result.device}")
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
        }
    }

    fun startScan() {
        if (BuildConfig.BUILD_TYPE == "dev") {
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

        if (BleService.isBluetoothEnabled()) bluetoothLeScanner?.startScan(scanDevicesCallback)
    }

    fun stopScan() = bluetoothLeScanner?.stopScan(scanDevicesCallback)
    override fun onCleared() {
        stopScan()
    }

    fun initializeSelectedDevices(linkedDevices: HashSet<ExternalSensor>) {
        selectedDevices.value = linkedDevices.toSet()
    }
}
