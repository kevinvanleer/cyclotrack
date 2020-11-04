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
import androidx.navigation.navGraphViewModels
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TripDetailsFragment : Fragment() {
    /*private val viewModel: TripDetailsViewModel by navGraphViewModels(R.id.trip_in_progress_graph) {
        defaultViewModelProviderFactory
    }*/
    private val viewModel: TripDetailsViewModel by viewModels()
    private val args: TripDetailsFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.trip_details_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val distanceHeadingView:HeadingView = view.findViewById(R.id.trip_details_distance)
        val durationHeadingView:HeadingView = view.findViewById(R.id.trip_details_time)

        val tripId = args.tripId
        Log.d("TRIP_DETAILS", tripId.toString())
        viewModel.tripId = tripId
        viewModel.tripOverview().observe(viewLifecycleOwner, Observer {overview ->
            if(overview != null) {
                distanceHeadingView.value =
                    String.format("%.2f mi", overview.distance?.times(0.000621371) ?: 0.0)
                durationHeadingView.value = formatDuration(overview?.duration ?: 0.0)
            } else {
                Log.d("TRIP_DETAILS_FRAG", "overview is null")
            }
        })
    }
}