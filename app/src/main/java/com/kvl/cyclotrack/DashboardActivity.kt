package com.kvl.cyclotrack

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import com.kvl.cyclotrack.events.BluetoothActionEvent
import dagger.hilt.android.AndroidEntryPoint
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

@AndroidEntryPoint
class DashboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(this.javaClass.simpleName, "onCreate")
        setContentView(R.layout.activity_dashboard)
        findNavController(R.id.nav_host_fragment_dashboard).setGraph(
            R.navigation.dashboard_nav_graph,
            intent.extras
        )
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