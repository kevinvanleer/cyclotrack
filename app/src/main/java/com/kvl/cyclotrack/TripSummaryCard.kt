package com.kvl.cyclotrack

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.format.DateUtils
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.res.ResourcesCompat
import com.google.android.gms.maps.CameraUpdateFactory.newLatLngBounds
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.kvl.cyclotrack.util.SystemUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TripSummaryCard : CardView {
    private lateinit var defaultBackgroundColor: ColorStateList
    private lateinit var map: GoogleMap
    private var dateView: TextView
    private var titleView: TextView
    private var startTimeView: TextView
    private var durationView: TextView
    private var mapView: MapView
    private var path: PolylineOptions? = null

    var tripId: Long = 0L
    var showSelectionIndicator = false

    private val logTag = "TripSummaryCard"

    init {
        Log.d(logTag, "initialize")
        View.inflate(context, R.layout.view_trip_summary_card, this)
        titleView = findViewById(R.id.trip_summary_title)
        startTimeView = findViewById(R.id.trip_summary_start_time)
        dateView = findViewById(R.id.trip_summary_date)
        durationView = findViewById((R.id.trip_summary_duration))
        mapView = findViewById(R.id.trip_summary_map)
        mapView.onCreate(null)
    }

    constructor(context: Context) : super(context) {
        //init(null, 0)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        //init(attrs, 0)
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        //init(attrs, defStyle)
    }

    override fun setSelected(selected: Boolean) {
        when (selected) {
            true -> setCardBackgroundColor(
                ResourcesCompat.getColor(
                    context.resources,
                    R.color.accentColor, null
                )
            )

            else -> if (this::defaultBackgroundColor.isInitialized) setCardBackgroundColor(
                defaultBackgroundColor
            )
        }
        super.setSelected(selected)
    }

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
        startTime = "at " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(value))
    }

    fun setDate(value: Long) {
        date =
            if (DateUtils.isToday(value)) "earlier today" else DateUtils.getRelativeTimeSpanString(
                value,
                SystemUtils.currentTimeMillis(),
                DateUtils.DAY_IN_MILLIS
            ).toString()
    }

    fun setDuration(value: Double) {
        duration = "for ${formatDuration(value)}"
    }

    fun setTripDetails(_duration: Double, _distance: Double) {
        duration =
            "${
                String.format(
                    "%.2f",
                    getUserDistance(context, _distance)
                )
            }${getUserDistanceUnitShort(context)}  in  ${formatDuration(_duration)}"
    }

    fun setTripInProgress(_duration: Double, _distance: Double) {
        title = "Ride in progress"
        date = "Tap to continue"
        startTime = String.format(
            "%.2f %s",
            getUserDistance(context, _distance), getUserDistanceUnitShort(context)
        )
        duration = formatDuration(_duration)
    }

    fun drawPath(polyline: PolylineOptions, latLngBounds: LatLngBounds) {
        Log.d("TRIP_SUMMARY_CARD", "DRAW PATH")
        path = polyline
        if (this::map.isInitialized) {
            map.addPolyline(polyline)
            map.moveCamera(newLatLngBounds(latLngBounds, 1000, 1000, 100))
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        Log.d(logTag, "onFinishInflate")

        defaultBackgroundColor = cardBackgroundColor

        val card = this
        mapView.setOnClickListener {
            Log.d(logTag, "MapView perform click")
            card.performClick()
        }

        mapView.getMapAsync {
            Log.d("TRIP_SUMMARY_CARD", "GOT MAP")
            map = it
            map.setOnMapClickListener {
                Log.d(logTag, "Map perform click")
                card.performClick()
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
