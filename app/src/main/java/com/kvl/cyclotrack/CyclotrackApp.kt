package com.kvl.cyclotrack

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CyclotrackApp : Application(), Configuration.Provider {
    companion object {
        lateinit var instance: CyclotrackApp private set
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val exportCompleteChannel =
                NotificationChannel(getString(R.string.notification_export_trip_id),
                    getString(R.string.notification_export_trip_name),
                    NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = getString(R.string.notification_channel_description_export_trip)
                }
            val exportInProgressChannel =
                NotificationChannel(getString(R.string.notification_export_trip_in_progress_id),
                    getString(R.string.notification_export_trip_in_progress_name),
                    NotificationManager.IMPORTANCE_LOW).apply {
                    description = getString(R.string.notification_channel_description_export_trip)
                }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(exportCompleteChannel)
            notificationManager.createNotificationChannel(exportInProgressChannel)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(false)
    }

    @Inject
    lateinit var workFactory: HiltWorkerFactory
    override fun getWorkManagerConfiguration() =
        Configuration.Builder().setWorkerFactory(workFactory).build()
}