package com.kvl.cyclotrack

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.model.RoundCap

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
            val mapData = plotPath(measurements)
            if (mapData.bounds != null) {
                mapData.path.startCap(RoundCap())
                mapData.path.endCap(RoundCap())
                mapData.path.width(5f)
                mapData.path.color(0xff007700.toInt())
                holder.tripSummaryView.drawPath(mapData.path, mapData.bounds)
                holder.tripSummaryView.setTripDetails(trips[position].duration ?: 0.0,
                    trips[position].distance ?: 0.0)
            }
        })
        holder.tripSummaryView.setOnClickListener { view ->
            view.findNavController()
                .navigate(TripSummariesFragmentDirections.actionViewTripDetails(tripId))
        }
    }

    override fun getItemCount() = trips.size
}