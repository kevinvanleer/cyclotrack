package com.kvl.cyclotrack

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TripSummariesFragment : Fragment() {
    private val viewModel: TripSummariesViewModel by navGraphViewModels(R.id.cyclotrack_nav_graph) {
        defaultViewModelProviderFactory
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.trip_summaries_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewManager = LinearLayoutManager(activity)

        viewModel.realTrips.observe(viewLifecycleOwner, { trips ->
            Log.d("TRIP_SUMMARIES",
                "There were ${trips.size.toString()} trips returned from the database")
            val viewAdapter =
                TripSummariesAdapter(trips, viewModel, viewLifecycleOwner, savedInstanceState)
            view.findViewById<RecyclerView>(R.id.trip_summary_card_list).apply {
                setHasFixedSize(true)
                layoutManager = viewManager
                adapter = viewAdapter
            }
        })

        view.findViewById<FloatingActionButton>(R.id.fab).setOnClickListener {
            findNavController().navigate(R.id.action_start_trip)
        }
    }
}