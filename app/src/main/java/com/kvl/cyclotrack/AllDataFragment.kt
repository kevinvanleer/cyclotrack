package com.kvl.cyclotrack

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import kotlin.reflect.full.declaredMemberProperties

class AllDataFragment : Fragment() {
    private val viewModel: LiveDataViewModel by navGraphViewModels(R.id.nav_graph)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

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

        viewModel.getLocationData().observe(this, Observer {
            Log.d("UI::AllDataFragment", "Location observer detected change")
            grid.removeAllViews()

            for(prop in LocationModel::class.declaredMemberProperties) {
                Log.d("UI", prop.toString())
                val label = TextView(activity)
                val value = TextView(activity)
                if(prop.parameters.size == 1) {
                    label.text = prop.name
                    label.width
                    value.text = prop.call(it).toString()
                    value.textSize = 20f
                    grid.addView(label)
                    grid.addView(value)
                }
            }

        })
        view.findViewById<Button>(R.id.button_exit_all_data).setOnClickListener {
            findNavController().navigate(R.id.action_back_to_dashboard)
        }
    }
}