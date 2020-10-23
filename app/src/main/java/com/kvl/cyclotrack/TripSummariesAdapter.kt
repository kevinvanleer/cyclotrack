package com.kvl.cyclotrack

import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.RoundCap
import kotlin.math.max
import kotlin.math.min

class TripSummariesAdapter(
    private val trips: Array<Trip>,
    private val viewModel: TripSummariesViewModel,
    private val viewLifecycleOwner: LifecycleOwner,
    private val savedInstanceState: Bundle?,
) :
    RecyclerView.Adapter<TripSummariesAdapter.TripSummaryViewHolder>() {
    class TripSummaryViewHolder(val tripSummaryView: TripSummaryCard) :
        RecyclerView.ViewHolder(tripSummaryView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TripSummaryViewHolder {
        val tripSummaryView = LayoutInflater.from(parent.context)
            .inflate(R.layout.trip_summary_card, parent, false) as TripSummaryCard
        return TripSummaryViewHolder(tripSummaryView)
    }

    override fun onBindViewHolder(holder: TripSummaryViewHolder, position: Int) {
        val tripId = trips[position].id ?: 0L
        holder.tripSummaryView.tripId = tripId
        holder.tripSummaryView.title = trips[position].name ?: "Unnamed trip"
        holder.tripSummaryView.setStartTime(trips[position].timestamp)
        holder.tripSummaryView.setDate(trips[position].timestamp)
        holder.tripSummaryView.setTripDetails(trips[position].duration ?: 0.0,
            trips[position].distance ?: 0.0)
        holder.tripSummaryView.onResumeMap()
        viewModel.getTripMeasurements(tripId).observe(viewLifecycleOwner, { measurements ->
            Log.d("TRIP_SUMMARIES_ADAPTER",
                "Recorded ${measurements.size} measurements for trip ${tripId}")
            val path = PolylineOptions()
            var northeastLat = -1000.0
            var northeastLng = -1000.0
            var southwestLat = 1000.0
            var southwestLng = 1000.0

            var totalDistance = 0.0
            var lastLat = 0.0
            var lastLng = 0.0

            var maxSpeedAccuracy = 0f
            var accSpeedAccuracy = 0f
            var sampleCount = 0

            measurements.forEach {
                if (it.accuracy < 5) {
                    if (lastLat != 0.0 && lastLng != 0.0) {
                        var distanceArray = floatArrayOf(0f)
                        Location.distanceBetween(lastLat,
                            lastLng,
                            it.latitude,
                            it.longitude,
                            distanceArray)
                        totalDistance += distanceArray[0]
                    }
                    lastLat = it.latitude
                    lastLng = it.longitude
                    path.add(LatLng(it.latitude, it.longitude))
                    northeastLat = max(northeastLat, it.latitude)
                    northeastLng = max(northeastLng, it.longitude)
                    southwestLat = min(southwestLat, it.latitude)
                    southwestLng = min(southwestLng, it.longitude)
                    maxSpeedAccuracy = max(maxSpeedAccuracy, it.speedAccuracyMetersPerSecond)
                    accSpeedAccuracy += it.speedAccuracyMetersPerSecond
                    sampleCount++
                }
            }
            Log.d("TRIP_SUMMARIES_ADAPTER",
                "distance=${totalDistance}, maxSpeedAcc=${maxSpeedAccuracy}, avgSpeedAcc=${accSpeedAccuracy / sampleCount}")
            path.startCap(RoundCap())
            path.endCap(RoundCap())
            path.width(5f)
            path.color(0xff007700.toInt())
            holder.tripSummaryView.drawPath(path,
                LatLngBounds(LatLng(southwestLat, southwestLng),
                    LatLng(northeastLat, northeastLng)))
            holder.tripSummaryView.setTripDetails(trips[position].duration ?: 0.0,
                totalDistance)
        })
    }

    override fun getItemCount() = trips.size
}