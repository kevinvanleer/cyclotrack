package com.kvl.cyclotrack

import android.content.Context
import android.os.Bundle
import android.text.format.DateUtils
import android.util.AttributeSet
import android.util.Log
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.navigation.findNavController
import com.google.android.gms.maps.CameraUpdateFactory.newLatLngBounds
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.PolylineOptions
import java.text.SimpleDateFormat
import java.util.*

class TripSummaryCard(context: Context, attrs: AttributeSet) : CardView(context, attrs) {
    private lateinit var dateView: TextView
    private lateinit var titleView: TextView
    private lateinit var startTimeView: TextView
    private lateinit var durationView: TextView
    private lateinit var mapView: MapView
    private lateinit var map: GoogleMap
    private var path: PolylineOptions? = null
    private var bounds: LatLngBounds? = null

    var tripId: Long = 0L

    var title: String
        get() = titleView.text.toString()
        set(value) {
            titleView.text = value
        }
    var startTime: String
        get() = startTimeView.text.toString()
        set(value) {
            startTimeView.text = value
        }

    var date: String
        get() = dateView.text.toString()
        set(value) {
            dateView.text = value
        }

    var duration: String
        get() = durationView.text.toString()
        set(value) {
            durationView.text = value
        }

    fun setStartTime(value: Long) {
        startTime = "at " + SimpleDateFormat("h:mm a").format(Date(value))
    }

    fun setDate(value: Long) {
        date =
            if (DateUtils.isToday(value)) "earlier today" else DateUtils.getRelativeTimeSpanString(
                value,
                System.currentTimeMillis(),
                DateUtils.DAY_IN_MILLIS).toString()
    }

    fun setDuration(value: Double) {
        duration = "for ${formatDuration(value)}"
    }

    fun setTripDetails(_duration: Double, _distance: Double) {
        duration =
            "${
                String.format("%.2f",
                    getUserDistance(context, _distance))
            }${getUserDistanceUnitShort(context)}  in  ${formatDuration(_duration)}"
    }

    fun drawPath(polyline: PolylineOptions, latLngBound: LatLngBounds) {
        Log.d("TRIP_SUMMARY_CARD", "DRAW PATH")
        path = polyline
        bounds = latLngBound
        map.addPolyline(polyline)
        map.moveCamera(newLatLngBounds(bounds, 1000, 1000, 100))
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        titleView = findViewById(R.id.trip_summary_title)
        startTimeView = findViewById(R.id.trip_summary_start_time)
        dateView = findViewById(R.id.trip_summary_date)
        durationView = findViewById((R.id.trip_summary_duration))
        mapView = findViewById(R.id.trip_summary_map)
        mapView.onCreate(null)
        mapView.isClickable = false
        mapView.getMapAsync {
            Log.d("TRIP_SUMMARY_CARD", "GOT MAP")
            map = it
            map.setOnMapClickListener {
                mapView.findNavController()
                    .navigate(TripSummariesFragmentDirections.actionViewTripDetails(tripId))
            }
            map.setMapStyle(MapStyleOptions.loadRawResourceStyle(context, R.raw.summary_map_style))
            map.uiSettings.setAllGesturesEnabled(false)
            map.uiSettings.isMapToolbarEnabled = false
        }
    }

    fun onCreateMap(savedInstanceState: Bundle?) {
        mapView.onCreate(savedInstanceState)
    }

    fun onStart() {
        mapView.onStart()
    }

    fun onResumeMap() {
        mapView.onResume()
    }

    fun onPauseMap() {
        mapView.onPause()
    }

    fun onStopMap() {
        mapView.onStop()
    }

    fun onDestroyMap() {
        mapView.onDestroy()
    }

    fun onSaveMapInstanceState(state: Bundle) {
        mapView.onSaveInstanceState(state)
    }

    fun onMapLowMemory() {
        mapView.onLowMemory()
    }

    fun clearMap() {
        if (this::map.isInitialized) map.clear()
    }
}