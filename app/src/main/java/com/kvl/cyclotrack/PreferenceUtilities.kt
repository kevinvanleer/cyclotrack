package com.kvl.cyclotrack

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager

fun getBrightnessPreference(context: Context): Float {
    return if (getPreferences(context)
            .getBoolean(context.getString(R.string.preferences_dashboard_brightness_toggle_key),
                true)
    ) {
        getPreferences(context)
            .getInt(context.getString(R.string.preferences_dashboard_brightness_key), 50) / 100f
    } else -1f
}

fun getUserCircumferenceOrNull(context: Context): Float? {
    return getUserCircumferenceOrNull(getPreferences(context))
}

fun getUserCircumference(context: Context): Float = getUserCircumferenceOrNull(context) ?: 0f

fun getUserCircumferenceOrNull(prefs: SharedPreferences): Float? {
    val storedCircumference = prefs.getString("wheel_circumference", "2037")
    Log.d("TRIP_UTILS_PREF", "Wheel circumference preference: ${storedCircumference}")
    return userCircumferenceToMeters(storedCircumference)
}

fun metersToUserCircumference(context: Context, meters: Float): String {
    return metersToUserCircumference(meters, getPreferences(context))
}

fun metersToUserCircumference(meters: Float, prefs: SharedPreferences): String {
    val storedCircumference = prefs.getString("wheel_circumference", "2037")
    return metersToUserCircumference(meters, storedCircumference)
}

fun getSystemOfMeasurement(context: Context): String? =
    getPreferences(context)
        .getString("display_units", "1")

fun getPreferences(context: Context): SharedPreferences =
    PreferenceManager.getDefaultSharedPreferences(context)

