package com.kvl.cyclotrack.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import com.kvl.cyclotrack.FeatureFlags
import com.kvl.cyclotrack.R
import com.kvl.cyclotrack.userCircumferenceToMeters

fun getSafeZoneTopMarginPreference(context: Context): Int =
    getPreferences(context).getInt(
        context.getString(R.string.preferences_dashboard_safe_zone_top_margin), 0
    )

fun getSafeZoneBottomMarginPreference(context: Context): Int =
    getPreferences(context).getInt(
        context.getString(R.string.preferences_dashboard_safe_zone_bottom_margin), 0
    )

fun getBrightnessPreference(context: Context): Float {
    return if (getPreferences(context)
            .getBoolean(
                context.getString(R.string.preferences_dashboard_brightness_toggle_key),
                true
            )
    ) {
        getPreferences(context)
            .getInt(context.getString(R.string.preferences_dashboard_brightness_key), 50) / 100f
    } else -1f
}

fun userMassToKilograms(context: Context, input: String?): Float? {
    return try {
        (input?.toFloat()
            ?: Float.NEGATIVE_INFINITY) * (when (PreferenceManager.getDefaultSharedPreferences(
            context
        )
            .getString("display_units", "1")) {
            "1" -> POUNDS_TO_KG
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

fun getUserCircumferenceOrNull(context: Context): Float? =
    userCircumferenceToMeters(
        getPreferences(context).getString(
            context.getString(R.string.preference_key_wheel_circumference),
            ""
        )
    )

fun getBikeMassOrNull(prefs: SharedPreferences, key: String): String? {
    val stored = prefs.getString(key, "")
    Log.d("PreferenceUtilities", "Bike mass preference: $stored")
    return stored
}

fun metersToUserCircumference(context: Context, meters: Float): String {
    return metersToUserCircumference(meters, getPreferences(context))
}

fun metersToUserCircumference(meters: Float, prefs: SharedPreferences): String {
    val storedCircumference = prefs.getString("wheel_circumference", "2037")
    return com.kvl.cyclotrack.metersToUserCircumference(meters, storedCircumference)
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

fun isStravaSynced(context: Context) = getPreferences(context).getString(
    context.getString(R.string.preference_key_strava_refresh_token),
    null
).isNullOrBlank().not()
