package com.kvl.cyclotrack

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import dagger.hilt.android.AndroidEntryPoint
import kotlin.reflect.full.declaredMemberProperties

@AndroidEntryPoint
class AllDataFragment : Fragment() {
    private val viewModel: TripInProgressViewModel by navGraphViewModels(R.id.trip_in_progress_graph) {
        defaultViewModelProviderFactory
    }
    //private val viewModel: TripInProgressViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_all_data, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val grid: GridLayout = view.findViewById(R.id.all_data_grid)

        Log.d("UI::AllDataFragment", "onViewCreated")

        viewModel.currentProgress.observe(viewLifecycleOwner, {
            Log.d("UI::AllDataFragment", "Location observer detected change")
            grid.removeAllViews()

            for (prop in TripProgress::class.declaredMemberProperties) {
                val label = TextView(activity)
                val value = TextView(activity)
                if (prop.parameters.size == 1 && prop.name != "location") {
                    label.text = prop.name
                    value.text = prop.call(it).toString()
                    value.textSize = 20f
                    grid.addView(label)
                    grid.addView(value)
                }
            }
            if (it?.location != null) {
                for (prop in Measurements::class.declaredMemberProperties) {
                    val label = TextView(activity)
                    val value = TextView(activity)
                    if (prop.parameters.size == 1) {
                        try {
                            label.text = prop.name
                            value.text = prop.call(it.location).toString()
                            value.textSize = 20f
                            grid.addView(label)
                            grid.addView(value)
                        } catch (e: java.lang.IllegalAccessException) {
                            Log.d("REFLECTION", "Could not access ${prop.name}")
                        }
                    }
                }
            }
        })
        view.findViewById<Button>(R.id.button_exit_all_data).setOnClickListener {
            findNavController().navigate(R.id.action_back_to_dashboard)
        }
    }
}