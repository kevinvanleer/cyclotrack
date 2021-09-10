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

fun getAutoCircumferenceOrNull(prefs: SharedPreferences): Float? {
    val storedCircumference = prefs.getFloat("auto_circumference", 0f)
    Log.d("TRIP_UTILS_PREF", "Auto circumference preference: ${storedCircumference}")
    return storedCircumference.takeIf { it > 0 }
}

fun getUserCircumferenceOrNull(prefs: SharedPreferences): Float? {
    val storedCircumference = prefs.getString("wheel_circumference", "")
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

fun useGoogleFitRestingHeartRate(context: Context) = getPreferences(context).getBoolean(
    context.getString(R.string.preferences_key_advanced_enable_google_fit_resting_heart_rate),
    FeatureFlags.betaBuild
)

fun useGoogleFitBiometrics(context: Context) = getPreferences(context).getBoolean(
    context.getString(R.string.preference_key_biometrics_use_google_fit_biometrics),
    true
)

fun shouldSyncGoogleFitBiometrics(context: Context) = getPreferences(context).getBoolean(
    context.getString(R.string.preferences_key_advanced_enable_google_fit_sync_biometrics),
    FeatureFlags.betaBuild
)

fun shouldCollectOnboardSensors(context: Context) = getPreferences(context).getBoolean(
    context.getString(R.string.preferences_key_advanced_onboard_sensors),
    FeatureFlags.betaBuild
)