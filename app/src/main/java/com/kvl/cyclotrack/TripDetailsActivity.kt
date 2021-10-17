package com.kvl.cyclotrack

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TripDetailsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trip_details)
        findNavController(R.id.nav_host_fragment_tripDetails).setGraph(
            R.navigation.trip_details_nav_graph,
            intent.extras
        )
        setSupportActionBar(findViewById(R.id.toolbar_tripDetails))
    }
}