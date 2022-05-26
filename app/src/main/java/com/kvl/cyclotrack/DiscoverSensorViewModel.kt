package com.kvl.cyclotrack

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiscoverSensorViewModel @Inject constructor(
    private val bleSensors: ExternalSensorRepository,
    bikeRepository: BikeRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {
    var bikeId: Long? = null
    var bikeName: String = "Body"
    val bikes = bikeRepository.observeAll()

    private val logTag = "DiscoverSensorViewModel"
    val bleDevices = MutableLiveData<Array<ExternalSensor>>(arrayOf())

    fun linkedSensors(bikeId: Long? = null) = when (bikeId) {
        null -> bleSensors.bodySensors()
        else -> bleSensors.bikeSensors(bikeId)
    }

    val sensors = bleSensors.observeAll()

    private val bluetoothLeScanner: BluetoothLeScanner? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter.bluetoothLeScanner

    fun linkDevice(device: ExternalSensor) {
        viewModelScope.launch {
            bleSensors.addSensor(device.copy(bikeId = bikeId))
        }
    }

    fun unlinkDevice(device: ExternalSensor) {
        viewModelScope.launch {
            bleSensors.removeSensor(device)
        }
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
                            viewModelScope.launch {
                                if (bleDevices.value?.any { it.address == sensor.address } != true &&
                                    !bleSensors.all().any { it.address == sensor.address }
                                ) {
                                    bleDevices.value?.toMutableList()?.let { list ->
                                        list.add(0, sensor)
                                        bleDevices.value = list.toTypedArray()
                                    }
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

    fun stopScan() = try {
        bluetoothLeScanner?.stopScan(scanDevicesCallback)
    } catch (e: SecurityException) {
        Log.w(logTag, "Bluetooth permissions have not been granted", e)
        throw SecurityException("Bluetooth scan permission has not been granted")
    }

    override fun onCleared() {
        Log.d(logTag, "view model cleared")
        stopScan()
    }
}
