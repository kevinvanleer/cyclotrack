package com.kvl.cyclotrack

import SensorGroup
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.MediatorLiveData
import androidx.navigation.findNavController
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LinkedSensorsFragment : Fragment() {
    val logTag = "LinkedSensorsFragment"

    lateinit var heading: TextView
    lateinit var linearLayout: LinearLayout

    companion object {
        fun newInstance() = LinkedSensorsFragment()
    }

    private val viewModel: LinkedSensorsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_linked_sensors, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        heading = view.findViewById(R.id.fragmentLinkedSensors_textView_heading)
        linearLayout = view.findViewById(R.id.fragmentLinkedSensors_linearLayout)

        viewModel.connectLinkedSensors()

        MediatorLiveData<Triple<Array<Bike>, Array<ExternalSensor>, Array<ExternalSensor>?>>().apply {
            var lastBike: Array<Bike>? = null
            var lastSensor: Array<ExternalSensor>? = null
            var lastState: Array<ExternalSensor>? = null

            fun update(
                bikes: Array<Bike>?,
                sensors: Array<ExternalSensor>?,
                sensorStates: Array<ExternalSensor>?
            ) {
                if (bikes != null && sensors != null)
                    value = Triple(bikes, sensors, sensorStates)
            }

            addSource(viewModel.bikes) {
                lastBike = it
                update(lastBike, lastSensor, lastState)
            }
            addSource(viewModel.sensors) {
                lastSensor = it
                update(lastBike, lastSensor, lastState)
            }
            addSource(viewModel.deviceStates) {
                lastState = it
                update(lastBike, lastSensor, lastState)
            }
        }.observe(viewLifecycleOwner) { triple ->
            val bikes = triple.first
            val sensors = triple.second
            val states = triple.third
            linearLayout.removeAllViews()

            val sensorStates = sensors.map { sensor ->
                states?.find { it.address == sensor.address }?.batteryLevel?.let { batteryLevel ->
                    sensor.batteryLevel = batteryLevel
                }
                sensor
            }
            Log.d(logTag, "$sensors,$states,$bikes")

            bikes.sortedBy { id }.map { Pair(it.id, it.name) }.toMutableList()
                .apply { add(0, Pair(null, "Body")) }
                .forEach { (bikeId, bikeName) ->
                    sensorStates.filter { sensor -> sensor.bikeId == bikeId }
                        .let { bikeSensors ->
                            SensorGroup(requireContext()).apply {
                                initialize(
                                    bikeName ?: "Bike ${bikeId}",
                                    bikeId,
                                    bikeSensors.toTypedArray()
                                )
                                linearLayout.addView(this)

                                setOnClickListener {
                                    view.findNavController()
                                        .navigate(
                                            when (bikeId) {
                                                null ->
                                                    LinkedSensorsFragmentDirections.actionLinkBodySensors()
                                                else ->
                                                    LinkedSensorsFragmentDirections.actionLinkBikeSensors(
                                                        bikeId, bikeName ?: "Bike ${bikeId}"
                                                    )
                                            }
                                        )
                                }
                            }
                            Space(context).apply {
                                minimumHeight = 64
                                linearLayout.addView(this)
                            }
                        }
                }
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().findViewById<Toolbar>(R.id.preferences_toolbar).title =
            "Settings: Linked sensors"
    }
}
