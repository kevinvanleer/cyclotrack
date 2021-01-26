package com.kvl.cyclotrack

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.model.RoundCap
import kotlinx.coroutines.launch

class TripSummariesAdapter(
    private val trips: Array<Trip>,
    private val viewModel: TripSummariesViewModel,
    private val viewLifecycleOwner: LifecycleOwner,
    private val context: Context,
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
        holder.tripSummaryView.clearMap()

        zipLiveData(viewModel.getTripMeasurements(tripId),
            viewModel.getTripTimeStates(tripId)).observe(viewLifecycleOwner,
            { pair ->
                val measurements = pair.first
                val timeStates = pair.second
                Log.d("TRIP_SUMMARIES_ADAPTER",
                    "Recorded ${measurements.size} measurements for trip ${tripId}")
                viewLifecycleOwner.lifecycleScope.launch {
                    val mapData = plotPath(measurements, timeStates)
                    if (mapData.bounds != null) {
                        mapData.paths.forEach { path ->
                            path.startCap(RoundCap())
                            path.endCap(RoundCap())
                            path.width(5f)
                            path.color(ResourcesCompat.getColor(context.resources,
                                R.color.colorAccent,
                                null))
                            holder.tripSummaryView.drawPath(path, mapData.bounds)
                        }
                        holder.tripSummaryView.setTripDetails(trips[position].duration ?: 0.0,
                            trips[position].distance ?: 0.0)
                    }
                }
            })
        holder.tripSummaryView.setOnClickListener { view ->
            try {
                view.findNavController()
                    .navigate(TripSummariesFragmentDirections.actionViewTripDetails(tripId))
            } catch (e: IllegalArgumentException) {
                Log.e("TRIP_SUMMARIES_ADAPTER", e.message, e)
            }
        }
    }

    override fun getItemCount() = trips.size
}