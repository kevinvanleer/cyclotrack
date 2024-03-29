package com.kvl.cyclotrack

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.kvl.cyclotrack.events.BluetoothActionEvent
import com.kvl.cyclotrack.events.GoogleFitAccessGranted
import dagger.hilt.android.AndroidEntryPoint
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

@AndroidEntryPoint
class PreferencesActivity : AppCompatActivity() {
    val logTag: String = this.javaClass.simpleName
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(logTag, "onCreate")
        setContentView(R.layout.activity_preferences)
        findNavController(R.id.nav_host_fragment_preferences).setGraph(
            R.navigation.preferences_nav_graph,
            intent.extras
        )
        Log.d(logTag, "$intent")
        intent.data?.let {
            Log.d(logTag, "${intent.data!!.query}")
            Log.d(logTag, "${intent.data!!.getQueryParameter("code")}")
            if (it.getQueryParameter("scope")?.contains("activity:write") == true) {
                //start worker with code
                WorkManager.getInstance(applicationContext).enqueue(
                    OneTimeWorkRequestBuilder<StravaTokenExchangeWorker>()
                        .setInputData(workDataOf("authCode" to it.getQueryParameter("code")))
                        .build()
                )
            } else {
                AlertDialog.Builder(applicationContext).apply {
                    setTitle("Cannot upload to Strava")
                    setMessage("Please select \"Sync with Strava\" again and check the box next to \"Upload activities from Cyclotrack to Strava.\"")
                }.create().show()
            }
        }

        setSupportActionBar(findViewById(R.id.preferences_toolbar))
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        //Required for Google Sign-in
        Log.d(logTag, "onActivityResult")
        Log.d(logTag, "$data")
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(this.javaClass.simpleName, "onActivityResult: ${resultCode}")
        when (resultCode) {
            Activity.RESULT_OK -> {
                Log.i(this.javaClass.simpleName, "Permission request granted.")
                when (requestCode) {
                    1 -> EventBus.getDefault().post(GoogleFitAccessGranted())
                    else -> Log.d(this.javaClass.simpleName, "Result was not from Google Fit")
                }
            }
            Activity.RESULT_CANCELED -> Log.w(
                this.javaClass.simpleName,
                "Permission request was cancelled ${resultCode}"
            )

            else -> Log.w(
                this.javaClass.simpleName,
                "Google permission request failed ${resultCode}"
            )
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(this.javaClass.simpleName, "onStart")
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        Log.d(this.javaClass.simpleName, "onStop")
        EventBus.getDefault().unregister(this)
    }

    @Subscribe
    fun onBluetoothActionEvent(event: BluetoothActionEvent) {
        Log.d(this.javaClass.simpleName, "Show enable bluetooth dialog")
        startActivity(Intent(event.action))
    }
}
