package com.kvl.cyclotrack

import android.app.Application
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.coroutineScope
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.logEvent
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.kvl.cyclotrack.events.BluetoothActionEvent
import com.kvl.cyclotrack.events.ConnectedBikeEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import javax.inject.Inject

data class HrmData(val batteryLevel: Byte?, val bpm: Short?)

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

@AndroidEntryPoint
class BleService @Inject constructor(
    private val context: Application,
    private val externalSensorRepository: ExternalSensorRepository,
    private val bikeRepository: BikeRepository,
) : LifecycleService() {
    private var connectedBike: Bike? = null
    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val addresses = object {
        var hrm: String? = null
        var speed: String? = null
        var cadence: String? = null
    }
    private val logTag = "BleService"
    private var scanCallbacks = ArrayList<ScanCallback>()
    private var gatts = ArrayList<BluetoothGatt>()

    private var hrmSensor = HrmData(null, null)
    private var cadenceSensor = CadenceData(null, null, null, null)
    private var speedSensor = SpeedData(null, null, null, null)

    private fun getFitnessMachineFeatures(gatt: BluetoothGatt) {
        val featureChar = gatt
            .getService(getGattUuid(fitnessMachineServiceId))
            ?.getCharacteristic(getGattUuid(fitnessMachineFeatureCharacteristicId))
        Log.v(logTag, "read supported fitness machine features: ${featureChar?.uuid}")
        try {
            featureChar?.let { gatt.readCharacteristic(it) }
        } catch (e: SecurityException) {
            Log.w(logTag, "Bluetooth permissions have not been granted", e)
        }
    }

    private val receiveBluetoothStateChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    when (intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR
                    )) {
                        BluetoothAdapter.STATE_ON -> {
                            Log.d(logTag, "Detected Bluetooth ON")
                            initialize()
                        }
                        BluetoothAdapter.STATE_OFF -> {
                            Log.d(logTag, "Detected Bluetooth OFF")
                            disconnect()
                        }
                    }
                }
            }
        }
    }

    init {
        context.registerReceiver(
            receiveBluetoothStateChanges,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        )
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
                    if (gatts.any { it.device.address == gatt.device.address }) {
                        gatt.connect()
                    }
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
            }
        }

        // New services discovered
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            with(gatt) {
                Log.d(logTag, "Discovered services: $status")
                printGattTable()
                when {
                    hasCharacteristic(
                        heartRateServiceUuid,
                        hrmCharacteristicUuid
                    ) -> enableNotifications(
                        gatt,
                        gatt.getService(heartRateServiceUuid)
                            .getCharacteristic(hrmCharacteristicUuid)
                    )
                    hasCharacteristic(
                        cadenceSpeedServiceUuid,
                        cscMeasurementCharacteristicUuid
                    ) -> enableNotifications(
                        gatt,
                        gatt.getService(
                            cadenceSpeedServiceUuid
                        )
                            .getCharacteristic(cscMeasurementCharacteristicUuid)
                    )
                    hasCharacteristic(
                        getGattUuid(fitnessMachineServiceId),
                        getGattUuid(indoorBikeDataCharacteristicId)
                    ) ->
                        enableNotifications(
                            gatt,
                            gatt.getService(
                                getGattUuid(fitnessMachineServiceId)
                            )
                                .getCharacteristic(getGattUuid(indoorBikeDataCharacteristicId))
                        )
                    else
                    -> Log.d(logTag, "No supported characteristics")
                }
            }
        }

        // Result of a characteristic read operation
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            Log.v(logTag, "onCharacteristicRead")
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
            Log.v(logTag, "onCharacteristicChanged")
            broadcastUpdate(gatt, characteristic)
        }
    }

    // Device scan callback.
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
                    gatts.add(result.device.connectGatt(context, true, genericGattCallback))
                    bluetoothManager.adapter?.bluetoothLeScanner?.stopScan(this)
                    scanCallbacks.remove(this)
                }
            } catch (e: SecurityException) {
                Log.w(logTag, "Bluetooth permissions have not been granted.", e)
            }
        }
    }

    private fun broadcastUpdate(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        Log.d(logTag, "broadcast update for ${characteristic.uuid} on ${gatt.device.address}")

        when (characteristic.uuid) {
            hrmCharacteristicUuid -> {
                if (addresses.hrm == null) {
                    addresses.hrm = gatt.device.address
                    readBatteryLevel(gatt)
                }
                val flag = characteristic.properties
                val format = when (flag and 0x01) {
                    0x01 -> {
                        Log.d(logTag, "Heart rate format UINT16.")
                        BluetoothGattCharacteristic.FORMAT_UINT16
                    }
                    else -> {
                        Log.d(logTag, "Heart rate format UINT8.")
                        BluetoothGattCharacteristic.FORMAT_UINT8
                    }
                }
                val heartRate = characteristic.getIntValue(format, 1)
                Log.d(logTag, String.format("Received heart rate: %d", heartRate))
                HrmData(
                    hrmSensor.batteryLevel,
                    heartRate.toShort()
                ).let {
                    hrmSensor = it
                    EventBus.getDefault().post(it)
                }
            }
            batteryLevelCharUuid -> {
                val batteryLevel = characteristic.value[0]
                Log.d(logTag, "Battery level: $batteryLevel")
                when (gatt.device.address) {
                    addresses.hrm -> {
                        hrmSensor.copy(batteryLevel = batteryLevel).let {
                            hrmSensor = it
                            EventBus.getDefault().post(it)
                        }
                    }
                    addresses.speed -> {
                        speedSensor.copy(batteryLevel = batteryLevel).let {
                            speedSensor = it
                            EventBus.getDefault().post(it)
                        }
                    }
                    addresses.cadence -> {
                        cadenceSensor.copy(batteryLevel = batteryLevel).let {
                            cadenceSensor = it
                            EventBus.getDefault().post(it)
                        }
                    }
                    else -> Log.d(
                        logTag,
                        "No sensor associated with device ${gatt.device.address} with battery level $batteryLevel"
                    )
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
                        if (revolutionCount != speedSensor.revolutionCount ||
                            SystemUtils.currentTimeMillis() - (speedSensor.timestamp
                                ?: 0) > timeout
                        ) {
                            val rpm = getRpm(
                                revolutionCount,
                                (speedSensor.revolutionCount ?: revolutionCount),
                                lastEvent,
                                (speedSensor.lastEvent ?: lastEvent)
                            )
                            Log.d(
                                logTag,
                                "Speed sensor: $revolutionCount :: $lastEvent :: $rpm"
                            )
                            if (connectedBike == null && rpm > 0) {
                                connectBike(gatt)
                            }
                            SpeedData(
                                speedSensor.batteryLevel,
                                revolutionCount,
                                lastEvent, rpm, SystemUtils.currentTimeMillis()
                            ).let {
                                speedSensor = it
                                EventBus.getDefault().post(it)
                            }
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
                        Log.d(
                            logTag,
                            "Cadence sensor changed: $revolutionCount :: $lastEvent"
                        )
                        if (revolutionCount != cadenceSensor.revolutionCount ||
                            SystemUtils.currentTimeMillis() - (cadenceSensor.timestamp
                                ?: 0) > timeout
                        ) {
                            val rpm = getRpm(
                                revolutionCount,
                                (cadenceSensor.revolutionCount ?: revolutionCount),
                                lastEvent,
                                (cadenceSensor.lastEvent ?: lastEvent)
                            )
                            Log.d(
                                logTag,
                                "Cadence sensor update: $revolutionCount :: $lastEvent :: $rpm"
                            )
                            if (connectedBike == null && rpm > 0) {
                                connectBike(gatt)
                            }
                            CadenceData(
                                cadenceSensor.batteryLevel,
                                revolutionCount,
                                lastEvent, rpm, SystemUtils.currentTimeMillis()
                            ).let {
                                cadenceSensor = it
                                EventBus.getDefault().post(it)
                            }
                        }
                    }
                    else -> {
                        Log.d(logTag, "Unknown CSC sensor type")
                        val data: ByteArray? = characteristic.value
                        if (data?.isNotEmpty() == true) {
                            val hexString: String = data.joinToString(separator = " ") {
                                String.format("%02X", it)
                            }
                            Log.d(
                                logTag,
                                String.format("Received ${characteristic.uuid}: $hexString")
                            )
                        }
                    }
                }
            }
            getGattUuid(indoorBikeDataCharacteristicId) -> {
                val data: ByteArray? = characteristic.value
                if (data?.isNotEmpty() == true) {
                    val hexString: String = data.joinToString(separator = " ") {
                        String.format("%02X", it)
                    }
                    Log.d(
                        logTag,
                        String.format("Indoor bike data: ${characteristic.uuid}: $hexString")
                    )
                }
                getFitnessMachineFeatures(gatt)
            }
            getGattUuid(fitnessMachineFeatureCharacteristicId) -> {
                val data: ByteArray? = characteristic.value
                if (data?.isNotEmpty() == true) {
                    val supportedFeatures =
                        characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 0)
                    val targetFeatures =
                        characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 4)
                    val hexString = "${Integer.toBinaryString(supportedFeatures)} ${
                        Integer.toBinaryString(targetFeatures)
                    }"
                    Log.d(
                        logTag,
                        String.format("Supported fitness features: ${characteristic.uuid}: $hexString")
                    )
                }
                if (data?.isNotEmpty() == true) {
                    val hexString: String = data.joinToString(separator = " ") {
                        String.format("%02X", it)
                    }
                    Log.d(logTag, String.format("Received ${characteristic.uuid}: $hexString"))
                }
            }
            else -> {
                // For all other profiles, writes the data formatted in HEX.
                val data: ByteArray? = characteristic.value
                if (data?.isNotEmpty() == true) {
                    val hexString: String = data.joinToString(separator = " ") {
                        String.format("%02X", it)
                    }
                    Log.d(logTag, String.format("Received ${characteristic.uuid}: $hexString"))
                }
            }
        }
    }

    private fun connectBike(gatt: BluetoothGatt) {
        lifecycle.coroutineScope.launch {
            externalSensorRepository.get(gatt.device.address)?.bikeId?.let { bikeId ->
                bikeRepository.get(bikeId)?.let { bike ->
                    connectedBike = bike
                    EventBus.getDefault()
                        .post(ConnectedBikeEvent(bike = bike))
                }
            }
        }
    }

    companion object {
        private const val logTag = "BleServiceCompanion"
        fun isBluetoothSupported(context: Context): Boolean {
            return (context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
        }

        fun isBluetoothEnabled(context: Context): Boolean {
            Log.d(logTag, "isBluetoothEnabled")
            return (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.isEnabled.also {
                Log.d(
                    logTag,
                    "bluetoothEnabled=${it}"
                )
            } ?: false.also {
                Log.d(logTag, "Bluetooth not supported")
            }
        }

        fun enableBluetooth(context: Context) {
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).let { bluetoothManager ->
                bluetoothManager.adapter?.let { bluetoothAdapter ->
                    if (!bluetoothAdapter.isEnabled) {
                        Log.d(logTag, "Requesting to enable Bluetooth")
                        EventBus.getDefault()
                            .post(BluetoothActionEvent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    }
                } ?: Log.d(logTag, "Cannot enable bluetooth, not supported")
            }
        }
    }

    fun initialize() {
        Log.d(logTag, "initializing bluetooth service")
        if (!isBluetoothSupported(context)) {
            Log.d(logTag, "BLE not supported on this device")
            return
        }

        enableBluetooth(context)

        lifecycle.coroutineScope.launch {
            externalSensorRepository.all().takeIf { it.isNotEmpty() }?.let { myMacs ->
                bluetoothManager.adapter?.let { bluetoothAdapter ->
                    myMacs.forEach { sensor ->
                        try {
                            connectToSensor(bluetoothAdapter, sensor.address)
                        } catch (e: IllegalArgumentException) {
                            Log.e(logTag, "Invalid sensor address", e)
                            FirebaseCrashlytics.getInstance().recordException(e)
                            externalSensorRepository.removeSensor(sensor)
                        }
                    }
                } ?: Log.d(logTag, "Cannot connect to sensors. Bluetooth not supported")
            }
        }
    }

    private fun connectToSensor(
        bluetoothAdapter: BluetoothAdapter,
        sensorAddress: String
    ) {
        try {
            val device = bluetoothAdapter.getRemoteDevice(sensorAddress)
            if (device.type == BluetoothDevice.DEVICE_TYPE_UNKNOWN) {
                Log.d(
                    logTag,
                    "Scanning for uncached device: ${device.address}"
                )
                bluetoothAdapter.bluetoothLeScanner?.let { bluetoothLeScanner ->
                    val callback = scanForDevice(device.address)
                    scanCallbacks.add(callback)
                    bluetoothLeScanner.startScan(callback)
                } ?: Log.w(logTag, "BLE scanner not available")
            } else {
                Log.i(
                    logTag,
                    "Connecting to ${device.name}, ${device.type}: ${device.address}"
                )
                //TODO: Store BLE service for each device and use corresponding callback
                gatts.add(device.connectGatt(context, true, genericGattCallback))
            }
        } catch (e: SecurityException) {
            Log.w(logTag, "BLE permissions have not been granted", e)
        }
    }

    fun stopAllScans() {
        try {
            Log.d(logTag, "Stop all scans")
            scanCallbacks.forEach {
                Log.d(logTag, "Stopping device scan")
                bluetoothManager.adapter?.bluetoothLeScanner?.stopScan(it)
            }
            scanCallbacks.clear()
        } catch (e: SecurityException) {
            Log.w(logTag, "Bluetooth permissions have not been granted", e)
        }
    }

    fun disconnect() {
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

        addresses.hrm = null
        addresses.cadence = null
        addresses.speed = null

        hrmSensor = HrmData(null, null)
        cadenceSensor = CadenceData(null, null, null, null)
        speedSensor = SpeedData(null, null, null, null)
    }

    private fun shouldConnect(gatt: BluetoothGatt) =
        when (bluetoothManager.getConnectionState(
            gatt.device,
            BluetoothProfile.GATT
        )) {
            BluetoothProfile.STATE_CONNECTING, BluetoothProfile.STATE_CONNECTED -> false
            else -> true
        }

    fun restore() {
        Log.d(logTag, "called restore")
        gatts.filter { shouldConnect(it) }.forEach { gatt ->
            bluetoothManager.adapter?.let { bluetoothAdapter ->
                FirebaseAnalytics.getInstance(context).logEvent("RestoreSensor") {}
                connectToSensor(bluetoothAdapter, gatt.device.address)
            }
        }
    }
}