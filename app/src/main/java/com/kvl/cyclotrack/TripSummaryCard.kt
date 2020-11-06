package com.kvl.cyclotrack

import android.content.Context
import android.os.Bundle
import android.text.format.DateUtils
import android.util.AttributeSet
import android.util.Log
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.google.android.gms.maps.CameraUpdateFactory.newLatLngBounds
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.PolylineOptions
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

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

    private fun formatDuration(value: Double): String {
        var formattedString = ""
        if (value < 1.0) {
            formattedString += "zero seconds"
        } else if (value < 60) {
            formattedString += "${value.roundToInt()} sec"
        } else if (value < 3600) {
            val minutes = value / 60
            val minutePart = minutes.toLong()
            val seconds = (minutes - minutePart) * 60
            val secondPart = seconds.toLong()
            formattedString += "${minutePart}m ${secondPart}s"
        } else {
            val hours = value / 3600
            val hourPart = hours.toLong()
            val minutes = (hours - hourPart) * 60
            val minutePart = minutes.toLong()
            val seconds = (minutes - minutePart) * 60
            val secondPart = seconds.roundToInt()
            formattedString += "${hourPart}h ${minutePart}m ${secondPart}s"
        }
        return formattedString
    }

    fun setDuration(value: Double) {
        duration = "for ${formatDuration(value)}"
    }

    fun setTripDetails(_duration: Double, _distance: Double) {
        duration =
            "${String.format("%.2f", _distance * 0.000621371)}mi  in  ${formatDuration(_duration)}"
    }

    fun drawPath(polyline: PolylineOptions, latLngBound: LatLngBounds) {
        Log.d("TRIP_SUMMARY_CARD", "DRAW PATH")
        path = polyline
        bounds = latLngBound
        map.addPolyline(polyline)
        map.moveCamera(newLatLngBounds(bounds, 5))
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        titleView = findViewById(R.id.trip_summary_title)
        startTimeView = findViewById(R.id.trip_summary_start_time)
        dateView = findViewById(R.id.trip_summary_date)
        durationView = findViewById((R.id.trip_summary_duration))
        mapView = findViewById(R.id.trip_summary_map)
        mapView.onCreate(null)
        mapView.getMapAsync {
            Log.d("TRIP_SUMMARY_CARD", "GOT MAP")
            map = it
            map.setMapStyle(MapStyleOptions.loadRawResourceStyle(context, R.raw.summary_map_style))

        }
    }

    /*override fun onTouchEvent(event: MotionEvent?): Boolean {
        super.onTouchEvent(event)
        Log.d("TRIP_SUMMARY_CARD", "${tripId} DON'T TOUCH ME!")
        return performClick()
    }

    override fun performClick(): Boolean {
        Log.d("TRIP_SUMMARY_CARD", "${tripId} DON'T CLICK ME!")

        return true
    }*/

    fun onCreateMap(savedInstanceState: Bundle?) {
        mapView.onCreate(savedInstanceState)
    }

    fun onStartMap() {
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
}