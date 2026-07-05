package com.kvl.cyclotrack

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.edit
import androidx.hilt.work.HiltWorkerFactory
import androidx.preference.PreferenceManager
import androidx.work.Configuration
import com.google.firebase.analytics.FirebaseAnalytics
import com.kvl.cyclotrack.util.TelemetryEvent
import com.kvl.cyclotrack.util.emitTelemetry
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CyclotrackApp : Application(), Configuration.Provider {
    companion object {
        lateinit var instance: CyclotrackApp private set
        private const val PREF_LAST_VERSION_CODE = "last_installed_version_code"
    }

    private fun createNotificationChannel() {
        val exportCompleteChannel =
            NotificationChannel(
                getString(R.string.notification_export_trip_id),
                getString(R.string.notification_export_trip_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notification_channel_description_export_trip)
            }
        val exportInProgressChannel =
            NotificationChannel(
                getString(R.string.notification_export_trip_in_progress_id),
                getString(R.string.notification_export_trip_in_progress_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description_export_trip)
            }
        // Register the channel with the system
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(exportCompleteChannel)
        notificationManager.createNotificationChannel(exportInProgressChannel)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        /*PreferenceManager.getDefaultSharedPreferences(this).edit {
            putBoolean(getString(R.string.preference_key_analytics_opt_in_presented),
                false)
            commit()
        }*/
        FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(
            PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(getString(R.string.preferences_key_enable_analytics), false)
        )
        emitUpdateInstalled()
    }

    private fun emitUpdateInstalled() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val last = prefs.getLong(PREF_LAST_VERSION_CODE, -1)
        val current = BuildConfig.VERSION_CODE.toLong()
        if (last != current) {
            if (last >= 0) emitTelemetry(TelemetryEvent.UpdateInstalled(last, current))
            prefs.edit { putLong(PREF_LAST_VERSION_CODE, current) }
        }
    }

    @Inject
    lateinit var workFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workFactory).build()
}
