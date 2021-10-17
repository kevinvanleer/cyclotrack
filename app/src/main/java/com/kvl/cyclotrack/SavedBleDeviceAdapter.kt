package com.kvl.cyclotrack

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class SavedBleDeviceAdapter(
    private val selectedBleDevices: Array<ExternalSensor>,
    private val onLinkedItemSelected: (Boolean, Int, ExternalSensor) -> Unit,
) :
    RecyclerView.Adapter<SavedBleDeviceAdapter.SavedBleDeviceViewHolder>() {
    class SavedBleDeviceViewHolder(val bleDeviceView: DiscoveredBleDevice) :
        RecyclerView.ViewHolder(bleDeviceView)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): SavedBleDeviceViewHolder {
        return SavedBleDeviceViewHolder(LayoutInflater.from(parent.context)
            .inflate(
                R.layout.view_discovered_sensor_item,
                parent,
                false
            ) as DiscoveredBleDevice)
    }

    override fun onBindViewHolder(holder: SavedBleDeviceViewHolder, position: Int) {
        if (position >= selectedBleDevices.size) {
            holder.bleDeviceView.deviceName = "EMPTY SLOT"
            holder.bleDeviceView.deviceDetails = "Select a device from the list below"
            holder.bleDeviceView.isChecked = false
            holder.bleDeviceView.isEnabled = false
        } else {
            val thisDevice = selectedBleDevices[position]
            holder.bleDeviceView.deviceName = selectedBleDevices[position].name ?: "UNKNOWN"
            holder.bleDeviceView.deviceDetails = thisDevice.address
            holder.bleDeviceView.isChecked = true
            holder.bleDeviceView.setOnCheckedChangedListener { _, checked ->
                onLinkedItemSelected(checked, position, thisDevice)
            }
        }
    }

    override fun getItemCount() = 3
}
