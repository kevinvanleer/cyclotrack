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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.Observer
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.logEvent
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DiscoverSensorFragment : Fragment() {
    private lateinit var liveDevices: MediatorLiveData<Pair<Array<ExternalSensor>, Array<ExternalSensor>>>
    val logTag = "SensorDiscoveryFragment"
    private lateinit var discoveredRecyclerView: RecyclerView
    private lateinit var savedRecyclerView: RecyclerView
    private lateinit var noSavedDevicesMessage: TextView
    private lateinit var discoverSensorsIndicator: ProgressBar
    private lateinit var discoverSensorMessage: TextView
    private lateinit var enableBluetoothButton: Button

    companion object {
        fun newInstance() = EditTripFragment()
    }

    private val args: DiscoverSensorFragmentArgs by navArgs()
    private val viewModel: DiscoverSensorViewModel by viewModels()

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
        Log.d(logTag, "initializeLocationServices")
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

    private fun onDiscoveredItemSelected(checked: Boolean, position: Int, device: ExternalSensor) {
        Log.d(logTag, "onDiscoveredItemSelected; position: ${position}")
        when (checked) {
            true -> {
                if (viewModel.linkedSensors(viewModel.bikeId).value?.let { it.size >= 3 } == true) {
                    Log.d(logTag, "User tried to add a fourth device")
                    Toast.makeText(
                        context,
                        "Too many linked devices. Please remove a linked device to link this one.",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Log.d(logTag, "Adding device")
                    viewModel.linkDevice(device)
                    FirebaseAnalytics.getInstance(requireContext()).logEvent("AddedBleSensor") {
                        param("sensorName", device.name ?: "N/A")
                        param("sensorFeatures", (device.features ?: -1).toLong())
                    }
                }
            }
            false -> {
                Log.d(logTag, "Removing device from discovered list")
                viewModel.unlinkDevice(device)
            }
        }
    }

    private fun onLinkedItemSelected(checked: Boolean, position: Int, device: ExternalSensor) {
        Log.d(logTag, "onLinkedItemSelected; position: ${position}")
        if (!checked) {
            Log.d(logTag, "Removing device from linked list")
            viewModel.unlinkDevice(device)
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
                            !selectedDevices.any { it.address == discovered.address }
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
                addSource(viewModel.linkedSensors(viewModel.bikeId)) {
                    if (it != null) {
                        lastSelected = it
                        update()
                    }
                }
            }
        liveDevices.observeForever(deviceListObserver)
    }

    private fun disableBluetoothScan() {
        try {
            viewModel.stopScan()
        } catch (e: SecurityException) {
            Log.w(logTag, "Cannot stop BLE scan without permission", e)
        } finally {
            discoverSensorsIndicator.visibility = View.GONE
            discoverSensorMessage.visibility = View.GONE
            discoveredRecyclerView.visibility = View.GONE
            enableBluetoothButton.visibility = View.VISIBLE
        }
    }

    private fun enableBluetoothScan() {
        Log.d(logTag, "enableBluetoothScan")
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
                    when (intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR
                    )) {
                        BluetoothAdapter.STATE_ON -> {
                            Log.d(logTag, "Detected Bluetooth ON")
                            initializeBluetoothScan()
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
    private fun initializeBluetoothScanS() =
        when (PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_CONNECT
            ) -> startBluetoothScan()
            else -> {
                Log.w(logTag, "No bluetooth scan permission. Requesting!")
                disableBluetoothScan()
                requestBluetoothPermissions.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    )
                )
            }
        }

    private fun initializeBluetoothScanOld() =
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) -> startBluetoothScan()
            else -> {
                Log.w(logTag, "No bluetooth scan permission. Requesting!")
                disableBluetoothScan()
                initializeLocationService()
            }
        }

    private fun initializeBluetoothScan() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            initializeBluetoothScanS()
        } else {
            initializeBluetoothScanOld()
        }

    private fun startBluetoothScan() {
        Log.d(logTag, "startBluetoothScan")
        if (BleService.isBluetoothEnabled(requireContext())) {
            enableBluetoothScan()
        } else {
            disableBluetoothScan()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private val requestBluetoothPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.entries.forEach { entry ->
            if (entry.key == Manifest.permission.BLUETOOTH_SCAN) {
                Log.d(logTag, "Bluetooth scan permissions granted")
                startBluetoothScan()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        if (args.bikeId > 0) {
            viewModel.bikeId = args.bikeId
            viewModel.bikeName = args.bikeName
        }
        Log.d(logTag, "bikeId=${viewModel.bikeId}")
        return inflater.inflate(R.layout.fragment_sensor_discovery, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceBundle: Bundle?) {
        super.onViewCreated(view, savedInstanceBundle)

        Log.d(logTag, "Binding view")
        view.findViewById<TextView>(R.id.textview_bike_name).apply {
            text = viewModel.bikeName
        }
        view.findViewById<TextView>(R.id.textview_sensor_linking_instructions).apply {
            text = when (viewModel.bikeName.lowercase()) {
                "body" ->
                    getString(R.string.body_sensor_linking_instructions)
                else ->
                    getString(R.string.bike_sensor_linking_instructions)

            }
        }
        discoveredRecyclerView = view.findViewById(R.id.discovered_sensor_recycler_view)!!
        savedRecyclerView = view.findViewById(R.id.saved_sensor_recycler_view)!!
        noSavedDevicesMessage = view.findViewById(R.id.saved_sensor_empty_recycler_message)
        discoverSensorsIndicator =
            view.findViewById(R.id.discover_sensors_scanning_indicator)
        discoverSensorMessage = view.findViewById(R.id.scanning_message)
        enableBluetoothButton =
            view.findViewById(R.id.button_discover_sensors_enable_bluetooth)
        enableBluetoothButton.setOnClickListener {
            when (BleService.isBluetoothEnabled(requireContext())) {
                true -> initializeBluetoothScan()
                else -> BleService.enableBluetooth(requireContext())
            }
        }
        requireContext().registerReceiver(
            receiveBluetoothStateChanges,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        )
        when (BleService.isBluetoothEnabled(requireContext())) {
            true -> initializeBluetoothScan()
            else -> disableBluetoothScan()
        }
        observeDeviceChanges()
    }

    override fun onResume() {
        super.onResume()
        requireActivity().findViewById<Toolbar>(R.id.preferences_toolbar).title =
            "Settings: Link sensors"
    }
}
