package com.kvl.cyclotrack

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlin.reflect.KFunction3

class DiscoveredBleDeviceAdapter(
    private val discoveredBleDevices: Array<ExternalSensor>,
    private val selectedBleDevices: Array<ExternalSensor>,
    private val onDiscoveredItemSelected: KFunction3<Boolean, Int, ExternalSensor, Unit>,
) :
    RecyclerView.Adapter<DiscoveredBleDeviceAdapter.DiscoveredBleDeviceViewHolder>() {
    class DiscoveredBleDeviceViewHolder(val bleDeviceView: DiscoveredBleDevice) :
        RecyclerView.ViewHolder(bleDeviceView)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): DiscoveredBleDeviceViewHolder {
        return DiscoveredBleDeviceViewHolder(LayoutInflater.from(parent.context)
            .inflate(R.layout.discovered_sensor_item,
                parent,
                false) as DiscoveredBleDevice)
    }

    override fun onBindViewHolder(holder: DiscoveredBleDeviceViewHolder, position: Int) {
        holder.bleDeviceView.deviceName = discoveredBleDevices[position].name ?: "UNKNOWN"
        holder.bleDeviceView.deviceDetails = discoveredBleDevices[position].address
        holder.bleDeviceView.isChecked =
            (selectedBleDevices.find { it.address == discoveredBleDevices[position].address } != null)
        holder.bleDeviceView.setOnCheckedChangedListener { _, checked ->
            onDiscoveredItemSelected(checked, position, discoveredBleDevices[position])
        }
    }

    override fun getItemCount() = discoveredBleDevices.size
}
