package com.kvl.cyclotrack

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.Menu
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dagger.hilt.android.AndroidEntryPoint

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

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(applicationContext)
            .registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    Log.d(logTag, "Show enable bluetooth dialog")
                    startActivity(intent)
                }
            }, IntentFilter(BluetoothAdapter.ACTION_REQUEST_ENABLE))
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
            Activity.RESULT_OK -> when (requestCode) {
                1 -> LocalBroadcastManager.getInstance(this)
                    .sendBroadcast(Intent(getString(R.string.intent_action_google_fit_access_granted)))
                else -> Log.d(logTag, "Result was not from Google Fit")
            }
            else -> Log.d(logTag, "Permission not granted")
        }
    }
}