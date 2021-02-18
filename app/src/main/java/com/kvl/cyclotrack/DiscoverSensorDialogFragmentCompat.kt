package com.kvl.cyclotrack

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
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
    val TAG = "BLE_DEVICE_PREF"
    private lateinit var discoveredRecyclerView: RecyclerView
    private lateinit var savedRecyclerView: RecyclerView
    private lateinit var noSavedDevicesMessage: TextView
    private fun discoveredSensorPref() = preference as DiscoverSensorDialogPreference
    private lateinit var theView: View

    private val viewModel: DiscoverSensorViewModel by viewModels()

    companion object {
        fun getInstance(key: String) = DiscoverSensorDialogFragmentCompat().apply {
            arguments = Bundle(1).apply { putString(ARG_KEY, key) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        liveDevices.removeObserver(deviceListObserver)
        viewModel.stopScan()
    }

    override fun getView() = theView

    private fun onDiscoveredItemSelected(checked: Boolean, position: Int, device: ExternalSensor) {
        when (checked) {
            true -> {
                Log.d(TAG, "Adding device")
                viewModel.addToSelectedDevices(device)
                if (viewModel.selectedDevices.value?.let { it.size > 3 } == true) {
                    Log.d(TAG, "User tried to add a fourth device")
                    Toast.makeText(context,
                        "Too many linked devices. Please remove a linked device to link this one.",
                        Toast.LENGTH_SHORT).show()
                    viewModel.removeFromSelectedDevices(device)
                }
                //discoveredRecyclerView.adapter?.notifyItemRemoved(position)
                //savedRecyclerView.adapter?.notifyDataSetChanged()
                //savedRecyclerView.adapter?.notifyItemInserted(discoveredSensorPref().pairedDevices.size - 1)
            }
            false -> {
                Log.d(TAG, "Removing device from discovered list")
                //val removedIndex = discoveredSensorPref().pairedDevices.indexOf(device)
                //discoveredSensorPref().removeDevice(device)
                viewModel.removeFromSelectedDevices(device)
                //savedRecyclerView.adapter?.notifyItemRemoved(removedIndex)
                //savedRecyclerView.adapter?.notifyItemRangeChanged(removedIndex,
                //    discoveredSensorPref().pairedDevices.size - removedIndex)
            }
        }
        showHideSaved()
    }

    private fun onLinkedItemSelected(checked: Boolean, position: Int, device: ExternalSensor) {
        when (checked) {
            false -> {
                Log.d(TAG, "Removing device from linked list")
                //discoveredSensorPref().removeDevice(device)
                viewModel.removeFromSelectedDevices(device)
                //savedRecyclerView.adapter?.notifyItemRemoved(position)
                //savedRecyclerView.adapter?.notifyItemRangeChanged(position,
                //    discoveredSensorPref().pairedDevices.size - position)
                //discoveredRecyclerView.adapter?.notifyItemChanged(bleDevices.indexOf(device))
            }
        }
        showHideSaved()
    }

    private fun showHideSaved() {
        when (viewModel.selectedDevices.value?.isNullOrEmpty()) {
            true -> {
                savedRecyclerView.visibility = View.GONE
                noSavedDevicesMessage.visibility = View.VISIBLE
            }
            false -> {
                savedRecyclerView.visibility = View.VISIBLE
                noSavedDevicesMessage.visibility = View.GONE
            }
        }
    }

    override fun onBindDialogView(view: View?) {
        super.onBindDialogView(view)

        Log.d(TAG, "Binding view")
        discoveredRecyclerView = view?.findViewById(R.id.discovered_sensor_recycler_view)!!
        savedRecyclerView = view.findViewById(R.id.saved_sensor_recycler_view)!!
        noSavedDevicesMessage = view.findViewById(R.id.saved_sensor_empty_recycler_message)

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
        viewModel.startScan()
        showHideSaved()
    }

    private val deviceListObserver: Observer<Pair<Array<ExternalSensor>, Array<ExternalSensor>>> =
        Observer { pair ->
            Log.d(TAG, "Observing device update")
            val discoveredDevices = pair.first
            val selectedDevices = pair.second

            discoveredRecyclerView.let { it ->
                it.apply {
                    //setHasFixedSize(true)
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
                    //setHasFixedSize(true)
                    layoutManager = LinearLayoutManager(activity)
                    adapter =
                        SavedBleDeviceAdapter(selectedDevices, ::onLinkedItemSelected)
                }
            }
        }

    override fun onDialogClosed(positiveResult: Boolean) {
        Log.d(TAG, "Closing discover/save device dialog")
        if (positiveResult) {
            Log.d(TAG, "save preference ${viewModel.selectedDevices.value}")
            viewModel.selectedDevices.value?.toSet()?.let { discoveredSensorPref().persist(it) }
                ?: discoveredSensorPref().clear()
        } else {
            discoveredSensorPref().reset()
        }
        liveDevices.removeObserver(deviceListObserver)
        viewModel.stopScan()
    }
}
