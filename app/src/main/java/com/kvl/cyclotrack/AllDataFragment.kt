package com.kvl.cyclotrack

import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.declaredMemberProperties

class AllDataFragment : Fragment() {
    private val viewModel: LiveDataViewModel by navGraphViewModels(R.id.nav_graph)

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

            for (prop in LocationModel::class.declaredMemberProperties) {
                Log.d("UI", prop.toString())
                val label = TextView(activity)
                val value = TextView(activity)
                if (prop.parameters.size == 1 && prop.name != "location") {
                    label.text = prop.name
                    label.width
                    value.text = prop.call(it).toString()
                    value.textSize = 20f
                    grid.addView(label)
                    grid.addView(value)
                }
            }
            if (it.location != null) {
                for (prop in Location::class.declaredMemberFunctions) {
                    val label = TextView(activity)
                    val value = TextView(activity)
                    if (prop.parameters.size == 1 && prop.returnType.toString() != "kotlin.Unit" && prop.name != "toString" && prop.name != "getExtras" && prop.name != "describeContents") {
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