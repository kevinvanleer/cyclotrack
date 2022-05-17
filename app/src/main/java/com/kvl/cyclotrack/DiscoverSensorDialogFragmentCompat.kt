package com.kvl.cyclotrack

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.Observer
import androidx.preference.PreferenceDialogFragmentCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DiscoverSensorDialogFragmentCompat : PreferenceDialogFragmentCompat() {
    private lateinit var liveDevices: MediatorLiveData<Pair<Array<ExternalSensor>, Array<ExternalSensor>>>
    val logTag = "BLE_DEVICE_PREF"
    private lateinit var discoveredRecyclerView: RecyclerView
    private lateinit var savedRecyclerView: RecyclerView
    private lateinit var noSavedDevicesMessage: TextView
    private lateinit var discoverSensorsIndicator: ProgressBar
    private lateinit var discoverSensorMessage: TextView
    private lateinit var enableBluetoothButton: Button
    private fun discoveredSensorPref() = preference as DiscoverSensorDialogPreference
    private lateinit var theView: View

    private val viewModel: DiscoverSensorViewModel by viewModels()

    companion object {
        fun getInstance(key: String) = DiscoverSensorDialogFragmentCompat().apply {
            arguments = Bundle(1).apply { putString(ARG_KEY, key) }
        }
    }

    private val requestLocationPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        for (entry in permissions.entries) {
            Log.d("LOCATION_PERMISSIONS", "${entry.key} = ${entry.value}")
            if (entry.key == Manifest.permission.ACCESS_FINE_LOCATION) {
                if (entry.value
                ) {
                    try {
                        startBluetoothScan()
                    } catch (e: IllegalArgumentException) {
                        Log.d(logTag, "CANNOT HANDLE MULTIPLE TOUCHES")
                    }
                } else {
                    disableBluetoothScan()
                }
            }
        }
    }

    private fun initializeLocationService() {
        when {
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                requireContext().let {
                    AlertDialog.Builder(it).apply {
                        setPositiveButton(
                            "PROCEED"
                        ) { _, _ ->
                            requestLocationPermissions.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                )
                            )
                        }
                        setNegativeButton(
                            "DENY"
                        ) { _, _ ->
                            Log.d("TRIP_SUMMARIES", "CLICKED DENY")
                            disableBluetoothScan()
                        }
                        setTitle("Grant Location Access")
                        setMessage("This app collects location data to enable the in-ride dashboard, and post-ride maps and graphs even when the app is closed or not in use.\n\nCyclotrack needs access to GPS location data and background location data to calculate speed and distance traveled during your rides. Data is only collected and recorded while rides are in progress. Data collected is stored on your device for your personal use. Your data is not sent to any third parties, including the developer (unless you enable the Google Fit integration).\n\nLocation permissions are required to scan for BLE devices. Some BLE devices can be used to approximate a devices location. However Cyclotrack does not use BLE devices in this way.\n\nPlease select PROCEED and then grant Cyclotrack access to location data.")
                    }.create()
                }.show()
            }
            else -> {
                requestLocationPermissions.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            requireContext().unregisterReceiver(receiveBluetoothStateChanges)
        } catch (e: Exception) {
            Log.i(logTag, "Failed to unregister broadcast receiver", e)
        }
        try {
            liveDevices.removeObserver(deviceListObserver)
        } catch (e: UninitializedPropertyAccessException) {
            Log.i(logTag, "liveDevices was not initialized", e)
        }
        viewModel.stopScan()
    }

    override fun getView() = theView

    private fun onDiscoveredItemSelected(checked: Boolean, position: Int, device: ExternalSensor) {
        Log.d(logTag, "onDiscoveredItemSelected; position: ${position}")
        when (checked) {
            true -> {
                Log.d(logTag, "Adding device")
                viewModel.addToSelectedDevices(device)
                if (viewModel.selectedDevices.value?.let { it.size > 3 } == true) {
                    Log.d(logTag, "User tried to add a fourth device")
                    Toast.makeText(context,
                        "Too many linked devices. Please remove a linked device to link this one.",
                        Toast.LENGTH_SHORT).show()
                    viewModel.removeFromSelectedDevices(device)
                }
            }
            false -> {
                Log.d(logTag, "Removing device from discovered list")
                viewModel.removeFromSelectedDevices(device)
            }
        }
    }

    private fun onLinkedItemSelected(checked: Boolean, position: Int, device: ExternalSensor) {
        Log.d(logTag, "onLinkedItemSelected; position: ${position}")
        if (!checked) {
            Log.d(logTag, "Removing device from linked list")
            viewModel.removeFromSelectedDevices(device)
        }
    }

    private val deviceListObserver: Observer<Pair<Array<ExternalSensor>, Array<ExternalSensor>>> =
        Observer { pair ->
            Log.d(logTag, "Observing device update")
            val discoveredDevices = pair.first
            val selectedDevices = pair.second

            discoveredRecyclerView.let { it ->
                it.apply {
                    layoutManager = LinearLayoutManager(activity)
                    adapter =
                        DiscoveredBleDeviceAdapter(discoveredDevices.filter { discovered ->
                            !selectedDevices.contains(discovered)
                        }
                            .toTypedArray(),
                            selectedDevices,
                            ::onDiscoveredItemSelected)
                }
            }

            savedRecyclerView.let {
                it.apply {
                    layoutManager = LinearLayoutManager(activity)
                    adapter =
                        SavedBleDeviceAdapter(selectedDevices, ::onLinkedItemSelected)
                }
            }
        }

    private fun observeDeviceChanges() {
        Log.d(logTag, "observeDeviceChanges")
        liveDevices =
            MediatorLiveData<Pair<Array<ExternalSensor>, Array<ExternalSensor>>>().apply {
                var lastDiscovered: Array<ExternalSensor> = arrayOf()
                var lastSelected: Array<ExternalSensor> = arrayOf()
                fun update() {
                    this.value = Pair(lastDiscovered, lastSelected)
                }
                addSource(viewModel.bleDevices) {
                    if (it != null) {
                        lastDiscovered = it
                        update()
                    }
                }
                addSource(viewModel.selectedDevices) {
                    if (it != null) {
                        lastSelected = it.toTypedArray()
                        update()
                    }
                }
            }
        liveDevices.observeForever(deviceListObserver)
        viewModel.initializeSelectedDevices(discoveredSensorPref().linkedDevices)
    }

    private fun disableBluetoothScan() {
        viewModel.stopScan()
        discoverSensorsIndicator.visibility = View.GONE
        discoverSensorMessage.visibility = View.GONE
        discoveredRecyclerView.visibility = View.GONE
        enableBluetoothButton.visibility = View.VISIBLE
    }

    private fun enableBluetoothScan() {
        try {
            viewModel.startScan()
            discoverSensorsIndicator.visibility = View.VISIBLE
            discoverSensorMessage.visibility = View.VISIBLE
            discoveredRecyclerView.visibility = View.VISIBLE
            enableBluetoothButton.visibility = View.GONE
        } catch (e: SecurityException) {
            Log.w(logTag, "Cannot scan BLE without permission")
            discoverSensorsIndicator.visibility = View.GONE
            discoverSensorMessage.visibility = View.GONE
            discoveredRecyclerView.visibility = View.GONE
            enableBluetoothButton.visibility = View.VISIBLE
        }
    }

    private val receiveBluetoothStateChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR)) {
                        BluetoothAdapter.STATE_ON -> {
                            Log.d(logTag, "Detected Bluetooth ON")
                            enableBluetoothScan()
                        }
                        BluetoothAdapter.STATE_OFF -> {
                            Log.d(logTag, "Detected Bluetooth OFF")
                            disableBluetoothScan()
                        }
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun initializeBluetoothScan() {
        when (PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_SCAN
            ) -> startBluetoothScan()
            else -> {
                Log.w(logTag, "No bluetooth scan permission. Requesting!")
                requestBluetoothPermissions.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN))
            }
        }
    }

    private fun startBluetoothScan() {
        if (BleService.isBluetoothEnabled(requireContext())) {
            enableBluetoothScan()
        } else {
            disableBluetoothScan()
        }
        requireContext().registerReceiver(
            receiveBluetoothStateChanges,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        )
        observeDeviceChanges()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private val requestBluetoothPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.entries.forEach { entry ->
            if (entry.key == Manifest.permission.BLUETOOTH_SCAN) {
                Log.d(logTag, "Bluetooth permissions granted")
                //initializeBluetoothScan()
                startBluetoothScan()
            }
        }
    }

    private fun prepareBluetoothScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            initializeBluetoothScan()
        } else {
            when (PackageManager.PERMISSION_GRANTED) {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) -> startBluetoothScan()
                else -> initializeLocationService()
            }
        }
    }

    private fun manageBluetooth(view: View) {
        discoverSensorsIndicator =
            view.findViewById(R.id.discover_sensors_scanning_indicator)
        discoverSensorMessage = view.findViewById(R.id.scanning_message)
        enableBluetoothButton =
            view.findViewById(R.id.button_discover_sensors_enable_bluetooth)
        enableBluetoothButton.setOnClickListener {
            BleService.enableBluetooth(requireContext())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            initializeBluetoothScan()
        } else {
            when (PackageManager.PERMISSION_GRANTED) {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) -> startBluetoothScan()
                else -> disableBluetoothScan()
            }
        }
    }

    private fun showHideSavedDevices() {
        when (viewModel.selectedDevices.value?.isNullOrEmpty()) {
            true -> {
                savedRecyclerView.visibility = View.GONE
                noSavedDevicesMessage.visibility = View.VISIBLE
            }
            else -> {
                savedRecyclerView.visibility = View.VISIBLE
                noSavedDevicesMessage.visibility = View.GONE
            }
        }
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)

        Log.d(logTag, "Binding view")
        discoveredRecyclerView = view?.findViewById(R.id.discovered_sensor_recycler_view)!!
        savedRecyclerView = view.findViewById(R.id.saved_sensor_recycler_view)!!
        noSavedDevicesMessage = view.findViewById(R.id.saved_sensor_empty_recycler_message)

        manageBluetooth(view)
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        Log.d(logTag, "Closing discover/save device dialog")
        if (positiveResult) {
            Log.d(logTag, "save preference ${viewModel.selectedDevices.value}")
            viewModel.selectedDevices.value?.toSet()?.let { discoveredSensorPref().persist(it) }
                ?: discoveredSensorPref().clear()
        } else {
            discoveredSensorPref().reset()
        }
    }
}
