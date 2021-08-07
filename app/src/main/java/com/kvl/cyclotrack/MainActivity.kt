package com.kvl.cyclotrack

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.kvl.cyclotrack.events.BluetoothActionEvent
import com.kvl.cyclotrack.events.GoogleFitAccessGranted
import dagger.hilt.android.AndroidEntryPoint
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val logTag = "LOGIN"
    lateinit var googleFitApiService: GoogleFitApiService
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent.getStringExtra("destinationView")) {
            TripInProgressFragment.toString() -> {

            }
            else -> setContentView(R.layout.activity_main)
        }
        setSupportActionBar(findViewById(R.id.toolbar))
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        googleFitApiService = GoogleFitApiService(this)
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }

    @Subscribe
    fun onBluetoothActionEvent(event: BluetoothActionEvent) {
        Log.d(logTag, "Show enable bluetooth dialog")
        startActivity(Intent(event.action))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        //menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(logTag, "onActivityResult: ${resultCode}")
        when (resultCode) {
            Activity.RESULT_OK -> {
                Log.i(logTag, "Permission request granted.")
                when (requestCode) {
                    1 -> EventBus.getDefault().post(GoogleFitAccessGranted())
                    else -> Log.d(logTag, "Result was not from Google Fit")
                }
            }
            Activity.RESULT_CANCELED -> Log.w(logTag,
                "Permission request was cancelled ${resultCode}")

            else -> Log.w(logTag, "Google permission request failed ${resultCode}")
        }
    }
}