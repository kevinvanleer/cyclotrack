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

fun userMassToKilograms(context: Context, input: String?): Float? {
    return try {
        (input?.toFloat()
            ?: Float.NEGATIVE_INFINITY) * (when (PreferenceManager.getDefaultSharedPreferences(
            context
        )
            .getString("display_units", "1")) {
            "1" -> 1 / POUNDS_TO_KG
            else -> 1.0
        }).toFloat()
    } catch (e: NumberFormatException) {
        Log.e("TRIP_UTILS_PREF", "userCircumferenceToMeters: Couldn't parse wheel circumference")
        null
    }
}

fun getBikeMassOrNull(context: Context): Float? {
    return userMassToKilograms(
        context,
        getBikeMassOrNull(
            getPreferences(context),
            context.getString(R.string.preference_key_bike_mass)
        )
    )
}

fun getPairedBleSensors(context: Context): Set<String>? =
    PreferenceManager.getDefaultSharedPreferences(context).getStringSet(
        context.resources.getString(R.string.preferences_paired_ble_devices_key),
        HashSet()
    )

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

fun getBikeMassOrNull(prefs: SharedPreferences, key: String): String? {
    val stored = prefs.getString(key, "")
    Log.d("PreferenceUtilities", "Bike mass preference: ${stored}")
    return stored
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

fun useVo2maxCalorieEstimate(context: Context) = getPreferences(context).getBoolean(
    context.getString(R.string.preferences_key_advanced_use_vo2max_calorie_estimate), false
)