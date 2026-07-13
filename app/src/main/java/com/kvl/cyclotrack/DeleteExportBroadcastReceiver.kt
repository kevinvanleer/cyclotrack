package com.kvl.cyclotrack

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.kvl.cyclotrack.data.ExportRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DeleteExportBroadcastReceiver : BroadcastReceiver() {
    private val logTag = "DELETE_EXPORT_RCVR"

    @Inject
    lateinit var exportRepository: ExportRepository

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d(logTag, "Received broadcast")
        Log.d(logTag, "${intent?.data}")
        val uri = intent?.data
        if (context == null || uri == null) return
        val pendingResult = goAsync()
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch {
            try {
                exportRepository.load().find { it.uri == uri.toString() }?.let { export ->
                    Log.d(logTag, "Deleting $uri")
                    try {
                        context.contentResolver.delete(uri, null, null)
                    } catch (e: Exception) {
                        // MediaStore ownership is lost on reinstall; still clean up our side
                        Log.e(logTag, "Could not delete export file", e)
                        FirebaseCrashlytics.getInstance().recordException(e)
                    }
                    NotificationManagerCompat.from(context).cancel(
                        ExportTripWorker.completeTag(export.fileType),
                        export.tripId.toInt()
                    )
                    exportRepository.delete(arrayOf(export))
                } ?: Log.d(logTag, "$uri not found in table")
            } catch (e: Exception) {
                Log.e(logTag, "Failed to process delete request", e)
                FirebaseCrashlytics.getInstance().recordException(e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
