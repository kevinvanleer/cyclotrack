package com.kvl.cyclotrack

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import com.kvl.cyclotrack.events.BluetoothActionEvent
import com.kvl.cyclotrack.events.GoogleFitAccessGranted
import com.kvl.cyclotrack.events.StravaAuthorizationRequest
import dagger.hilt.android.AndroidEntryPoint
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

@AndroidEntryPoint
class PreferencesActivity : AppCompatActivity() {
    private val logTag = "PreferencesActivity"

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onRequestStravaAuthorization(event: StravaAuthorizationRequest) {
        activityResultLauncher.launch(event.intent)
    }

    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.d(logTag, "received activity result")
            if (result.resultCode == Activity.RESULT_OK) {
                Log.d(logTag, "user authorized access to strava")
                Log.d(logTag, result.data.toString())
            }
        }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("PreferencesActivity", "onCreate")
        setContentView(R.layout.activity_preferences)
        findNavController(R.id.nav_host_fragment_preferences).setGraph(
            R.navigation.preferences_nav_graph,
            intent.extras
        )
        setSupportActionBar(findViewById(R.id.preferences_toolbar))

    }

    @Suppress("DEPRECATION")
    @SuppressWarnings("deprecation")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        //Required for Google Sign-in
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