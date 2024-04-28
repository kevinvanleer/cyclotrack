package com.kvl.cyclotrack

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.kvl.cyclotrack.data.ExportRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DeleteExportBroadcastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var exportRepository: ExportRepository

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("DELETE_EXPORT_RCVR", "Received broadcast")
        Log.d("DELETE_EXPORT_RCVR", "${intent?.data?.toString()}")
        val pendingResult = goAsync()
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch {
            try {
                exportRepository.load().let { exports ->
                    exports.find { it.uri == intent?.data?.toString() }?.let {
                        Log.d("DELETE_EXPORT_RCVR", "Deleting ${intent?.data?.toString()}")
                        context?.contentResolver!!.delete(intent?.data!!, null, null)
                        with(NotificationManagerCompat.from(context)) {
                            cancel(intent.getLongExtra("TRIP_ID", 0).toInt())
                        }
                        exportRepository.delete(arrayOf(it))
                    } ?: Log.d(
                        "DELETE_EXPORT_RCVR",
                        "${intent?.data?.toString()} not found in table"
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }

    }
}
