package com.kvl.cyclotrack

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.navArgs
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.RoundCap
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TripDetailsFragment : Fragment() {
    /*private val viewModel: TripDetailsViewModel by navGraphViewModels(R.id.trip_in_progress_graph) {
        defaultViewModelProviderFactory
    }*/
    private val viewModel: TripDetailsViewModel by viewModels()
    private val args: TripDetailsFragmentArgs by navArgs()
    private lateinit var map: GoogleMap
    private var mapView: MapView? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.trip_details_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        mapView = view.findViewById(R.id.trip_details_map_view)
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync {
            Log.d("TRIP_DETAILS_FRAGMENT", "GOT MAP")
            map = it
            map.setMapStyle(MapStyleOptions.loadRawResourceStyle(context, R.raw.summary_map_style))
        }
        val distanceHeadingView: HeadingView = view.findViewById(R.id.trip_details_distance)
        val durationHeadingView: HeadingView = view.findViewById(R.id.trip_details_time)
        val speedHeadingView: HeadingView = view.findViewById(R.id.trip_details_speed)

        val tripId = args.tripId
        Log.d("TRIP_DETAILS", tripId.toString())
        viewModel.tripId = tripId
        viewModel.tripOverview().observe(viewLifecycleOwner, Observer { overview ->
            if (overview != null) {
                val userDistance = overview.distance?.times(0.000621371) ?: 0.0
                val userTime = overview.duration?.div(3600) ?: 0.0
                val averageSpeed = userDistance / userTime
                distanceHeadingView.value =
                    String.format("%.2f mi", userDistance)
                durationHeadingView.value = formatDuration(overview.duration ?: 0.0)
                speedHeadingView.value = String.format("%.1f mph", averageSpeed)


            } else {
                Log.d("TRIP_DETAILS_FRAG", "overview is null")
            }
        })
        viewModel.measurements().observe(viewLifecycleOwner, { measurements ->
            Log.d("TRIP_DETAILS_FRAGMENT",
                "Recorded ${measurements.size} measurements for trip ${tripId}")
            val mapData = plotPath(measurements)
            if (mapData.bounds != null) {
                Log.d("TRIP_DETAILS_FRAGMENT", "Plotting path")
                mapData.path.startCap(RoundCap())
                mapData.path.endCap(RoundCap())
                mapData.path.width(5f)
                mapData.path.color(0xff007700.toInt())
                map.addPolyline(mapData.path)
                map.moveCamera(CameraUpdateFactory.newLatLngBounds(mapData.bounds, 1000, 1000, 100))
            }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (mapView != null) mapView?.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        if (mapView != null) mapView?.onResume()
    }

    override fun onStart() {
        super.onStart()
        if (mapView != null) mapView?.onStart()
    }

    override fun onPause() {
        super.onPause()
        if (mapView != null) mapView?.onPause()
    }

    override fun onStop() {
        super.onStop()
        if (mapView != null) mapView?.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mapView != null) mapView?.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (mapView != null) mapView?.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (mapView != null) mapView?.onLowMemory()
    }
}