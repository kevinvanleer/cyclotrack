package com.kvl.cyclotrack.preferences

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import com.kvl.cyclotrack.R

class DashboardSafeZonePreferenceActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard_safe_zone_preference)
        findNavController(R.id.nav_host_fragment_dashboard_safe_zone_preference).setGraph(
            R.navigation.dashboard_safe_zone_nav_graph
        )
    }

    override fun onBackPressed() {}

}
