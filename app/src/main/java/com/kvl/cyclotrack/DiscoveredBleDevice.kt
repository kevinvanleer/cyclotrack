package com.kvl.cyclotrack

import android.content.Context
import android.util.AttributeSet
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout

class DiscoveredBleDevice(context: Context, attrs: AttributeSet) :
    ConstraintLayout(context, attrs) {
    private lateinit var checkbox: CheckBox
    private lateinit var deviceDetailsView: TextView

    var deviceName: String
        get() = checkbox.text.toString()
        set(value) {
            checkbox.text = value
        }
    var deviceDetails: String
        get() = deviceDetailsView.text.toString()
        set(value) {
            deviceDetailsView.text = value
        }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        checkbox.isEnabled = enabled
        deviceDetailsView.isEnabled = enabled
    }

    var isChecked: Boolean
        get() = checkbox.isChecked
        set(value) {
            checkbox.isChecked = value
        }

    fun setOnCheckedChangedListener(listener: CompoundButton.OnCheckedChangeListener) {
        checkbox.setOnCheckedChangeListener(listener)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        checkbox = findViewById(R.id.checkbox_select_discovered_sensor)
        deviceDetailsView = findViewById(R.id.textview_discovered_sensor_details)
    }

}
