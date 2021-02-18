package com.kvl.cyclotrack

import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.MediatorLiveData
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import dagger.hilt.android.AndroidEntryPoint
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties

data class AllData(
    var speed: SpeedData? = null,
    var cadence: CadenceData? = null,
    var hrm: HrmData? = null,
    var progress: TripProgress? = null,
)

@AndroidEntryPoint
class AllDataFragment : Fragment() {
    private val viewModel: TripInProgressViewModel by navGraphViewModels(R.id.trip_in_progress_graph) {
        defaultViewModelProviderFactory
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

        val allData = MediatorLiveData<AllData>()
        allData.value = AllData()

        allData.addSource(viewModel.speedSensor) {
            allData.value = allData.value?.copy(speed = it)
        }
        allData.addSource(viewModel.cadenceSensor) {
            allData.value = allData.value?.copy(cadence = it)
        }
        allData.addSource(viewModel.hrmSensor) {
            allData.value = allData.value?.copy(hrm = it)
        }
        allData.addSource(viewModel.currentProgress) {
            allData.value = allData.value?.copy(progress = it)
        }

        allData.observe(viewLifecycleOwner, {
            Log.d("UI::AllDataFragment", "All data observer detected change")
            Log.d("UI::AllDataFragment", "${it.progress?.measurements}")
            grid.removeAllViews()

            for (prop in AllData::class.declaredMemberProperties) {
                try {
                    if (prop.call(it) != null) {
                        addHeadingRowToGrid(prop, grid)
                        for (innerProp in prop.get(it)!!::class.declaredMemberProperties) {
                            try {
                                addRowToGrid(innerProp, prop.call(it), grid)
                            } catch (e: IllegalAccessException) {
                                Log.d("REFLECTION", "Could not access ${innerProp.name}")
                            }
                        }
                        addEmptyRowToGrid(grid)
                    }
                } catch (e: IllegalAccessException) {
                    Log.d("REFLECTION", "Could not access ${prop.name}")
                }
            }
        })
        view.findViewById<Button>(R.id.button_exit_all_data).setOnClickListener {
            findNavController().navigate(R.id.action_back_to_dashboard)
        }
    }

    private fun addEmptyRowToGrid(
        grid: GridLayout,
    ) {
        val label = TextView(activity)
        val value = TextView(activity)
        grid.addView(label)
        grid.addView(value)
    }

    private fun addHeadingRowToGrid(
        innerProp: KProperty1<out Any, *>,
        grid: GridLayout,
    ) {
        val label = TextView(activity)
        val value = TextView(activity)
        label.text = innerProp.name
        label.textSize = 20f
        label.typeface = Typeface.DEFAULT_BOLD
        grid.addView(label)
        grid.addView(value)
    }

    private fun addRowToGrid(
        innerProp: KProperty1<out Any, *>,
        obj: Any?,
        grid: GridLayout,
    ) {
        try {
            if (innerProp.parameters.size == 1 && innerProp.name != "location") {
                val label = TextView(activity)
                val value = TextView(activity)
                label.text = innerProp.name
                value.text = innerProp.call(obj).toString()
                value.textSize = 20f
                grid.addView(label)
                grid.addView(value)
            }
        } catch (e: IllegalAccessException) {
            Log.d("REFLECTION", "Could not access ${innerProp.name}")
        }
    }
}