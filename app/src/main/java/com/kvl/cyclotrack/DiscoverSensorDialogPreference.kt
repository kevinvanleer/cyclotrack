package com.kvl.cyclotrack

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.util.Log
import androidx.preference.DialogPreference
import androidx.preference.PreferenceViewHolder
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException


class DiscoverSensorDialogPreference(context: Context, attrs: AttributeSet) :
    DialogPreference(context, attrs, R.attr.dialogPreferenceStyle) {
    private val TAG = "SavedSensorPref"
    private var _linkedDevices = HashSet<ExternalSensor>()

    init {
        isPersistent = true
        dialogLayoutResource = R.layout.view_discovered_sensor_list
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder?) {
        super.onBindViewHolder(holder)
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any? {
        // Default value from attribute. Fallback value is set to 0.
        //return a.getInt(index, 0)
        return HashSet<String>()
    }

    var linkedDevices: HashSet<ExternalSensor>
        get() = _linkedDevices
        set(value) {
            _linkedDevices = value
        }

    fun persist(deviceSet: Set<ExternalSensor>) {
        Log.d(TAG, "Persist $deviceSet")
        _linkedDevices = deviceSet.toHashSet()
        persistStringSet(deviceSet.map {
            Gson().toJson(ExternalSensor(it.address, it.name))
        }.toHashSet())
    }

    fun clear() {
        persistStringSet(HashSet<String>())
    }

    fun reset() {
        Log.d(TAG, "Restoring from persisted value")
        var stringSet: Set<String> = getPersistedStringSet(HashSet<String>())
        _linkedDevices = stringSet.map {
            try {
                Gson().fromJson(it, ExternalSensor::class.java)
            } catch (e: JsonSyntaxException) {
                Log.e(TAG, "Could not parse sensor from JSON", e)
                ExternalSensor("Sensor information is invalid, please remove", "Invalid sensor")
            }
        }.toHashSet()
    }

    override fun onSetInitialValue(
        restorePersistedValue: Boolean,
        defaultValue: Any?,
    ) {
        // Read the value. Use the default value if it is not possible.
        (if (restorePersistedValue) reset() else _linkedDevices = HashSet())
    }
}