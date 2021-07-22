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
    private val multiSelectModeCallback: (Boolean) -> Unit,
    private val savedInstanceState: Bundle?,
) :
    RecyclerView.Adapter<TripSummariesAdapter.TripSummaryViewHolder>() {

    private val logTag = "TRIP_SUMMARIES_ADAPTER"
    var multiSelectMode = false
        set(value) {
            field = value
            val changedTrips = selectedTrips.toTypedArray()
            selectedTrips.clear()
            multiSelectModeCallback(value)
            if (!value) {
                changedTrips.forEach { tripId ->
                    notifyItemChanged(trips.indexOf(trips.find { it.id == tripId }))
                }
            }
        }
    var selectedTrips = ArrayList<Long>()

    class TripSummaryViewHolder(val tripSummaryView: TripSummaryCard) :
        RecyclerView.ViewHolder(tripSummaryView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TripSummaryViewHolder {
        val tripSummaryView = LayoutInflater.from(parent.context)
            .inflate(R.layout.trip_summary_card, parent, false) as TripSummaryCard
        return TripSummaryViewHolder(tripSummaryView)
    }

    override fun onBindViewHolder(holder: TripSummaryViewHolder, position: Int) {
        val tripId = trips[position].id ?: 0L
        val tripInProgress = trips[position].inProgress

        buildView(holder, position)

        holder.tripSummaryView.setOnClickListener { view ->
            if (multiSelectMode) {
                view.isSelected = !view.isSelected
                if (view.isSelected) {
                    selectedTrips.add(tripId)
                } else {
                    selectedTrips.remove(tripId)
                    if (selectedTrips.isEmpty()) {
                        multiSelectMode = false
                    }
                }
            } else {
                try {
                    if (tripInProgress) {
                        view.findNavController().navigate(R.id.action_start_trip,
                            Bundle().apply {
                                Log.d(logTag, "Start dashboard with trip ${tripId}")
                                putLong("tripId", tripId)
                            })
                    } else {
                        view.findNavController()
                            .navigate(TripSummariesFragmentDirections.actionViewTripDetails(tripId))
                    }
                } catch (e: IllegalArgumentException) {
                    Log.e("TRIP_SUMMARIES_ADAPTER", e.message, e)
                }
            }
        }

        if (FeatureFlags.betaBuild) {
            holder.tripSummaryView.setOnLongClickListener { view ->
                multiSelectMode = true
                multiSelectModeCallback(true)
                selectedTrips.add(tripId)
                view.isSelected = true
                true
            }
        }
    }

    private fun buildView(
        holder: TripSummaryViewHolder,
        position: Int,
    ) {
        when (trips[position].inProgress) {
            true -> buildInProgressView(holder, position)
            else -> buildFinishedView(holder, position)
        }
    }

    private fun buildInProgressView(
        holder: TripSummaryViewHolder,
        position: Int,
    ) {
        val tripId = trips[position].id ?: 0L

        Log.d(logTag, "Building in progress view for ${tripId}:${position}")
        holder.tripSummaryView.tripId = tripId
        holder.tripSummaryView.setTripInProgress(trips[position].duration ?: 0.0,
            trips[position].distance ?: 0.0)
        holder.tripSummaryView.onResumeMap()
        holder.tripSummaryView.clearMap()
        holder.tripSummaryView.showSelectionIndicator = multiSelectMode
        holder.tripSummaryView.isSelected =
            multiSelectMode && selectedTrips.contains(tripId)

        viewLifecycleOwner.lifecycleScope.launch {
            Log.d(logTag, "Updating view for ${tripId}:${position}")
            val measurements = viewModel.getTripMeasurements(tripId)
            val timeStates = viewModel.getTripTimeStates(tripId)
            Log.d(logTag, "Retrieved data for ${tripId}:${position}")
            val mapData = plotPath(measurements, timeStates)
            Log.d(logTag, "Plotted path for ${tripId}:${position}")
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
            }
            Log.d(logTag, "Updated view for ${tripId}:${position}")
        }
    }

    private fun buildFinishedView(
        holder: TripSummaryViewHolder,
        position: Int,
    ) {
        val tripId = trips[position].id ?: 0L

        Log.d(logTag, "Building view for ${tripId}:${position}")
        holder.tripSummaryView.tripId = tripId
        holder.tripSummaryView.title = trips[position].name ?: "Unnamed trip"
        holder.tripSummaryView.setStartTime(trips[position].timestamp)
        holder.tripSummaryView.setDate(trips[position].timestamp)
        holder.tripSummaryView.setTripDetails(trips[position].duration ?: 0.0,
            trips[position].distance ?: 0.0)
        holder.tripSummaryView.onResumeMap()
        holder.tripSummaryView.clearMap()
        holder.tripSummaryView.showSelectionIndicator = multiSelectMode
        holder.tripSummaryView.isSelected =
            multiSelectMode && selectedTrips.contains(tripId)

        viewLifecycleOwner.lifecycleScope.launch {
            Log.d(logTag, "Updating view for ${tripId}:${position}")
            val measurements = viewModel.getTripMeasurements(tripId)
            val timeStates = viewModel.getTripTimeStates(tripId)
            Log.d(logTag, "Retrieved data for ${tripId}:${position}")
            val mapData = plotPath(measurements, timeStates)
            Log.d(logTag, "Plotted path for ${tripId}:${position}")
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
            }
            Log.d(logTag, "Updated view for ${tripId}:${position}")
        }
    }

    override fun getItemCount() = trips.size
}