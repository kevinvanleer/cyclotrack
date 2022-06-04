package com.kvl.cyclotrack

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat

class DeleteExportBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("DELETE_EXPORT_RCVR", "Received broadcast")
        Log.d("DELETE_EXPORT_RCVR", "${intent?.data?.toString()}")
        context?.contentResolver!!.delete(intent?.data!!, null, null)
        with(NotificationManagerCompat.from(context)) {
            cancel(intent.getLongExtra("TRIP_ID", 0).toInt())
        }
    }
}
