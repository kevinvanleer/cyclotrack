package com.kvl.cyclotrack

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class EditTripFragment : Fragment() {
    private val viewModel: EditTripViewModel by viewModels()
    private val args: EditTripFragmentArgs by navArgs()

    companion object {
        fun newInstance() = EditTripFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        viewModel.setTrip(args.tripId)
        return inflater.inflate(R.layout.fragment_edit_trip, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d(
            "backStack", "${
                findNavController().backQueue.map { it.destination.label }
            }"
        )
       
        val tripName: EditText = view.findViewById(R.id.edit_trip_name)
        val tripNotes: EditText = view.findViewById(R.id.edit_trip_notes)
        val tripWheelCirc: EditText = view.findViewById(R.id.edit_trip_wheel_circumference)
        val tripDate: TextView = view.findViewById(R.id.edit_trip_date)
        val tripBikeSelect: AutoCompleteTextView =
            view.findViewById(R.id.edit_trip_spinner_bike_select)

        tripName.setText(args.tripName)
        tripNotes.setText(args.tripNotes)
        tripDate.text = args.tripDate
        viewModel.tripInfo.userWheelCircumference?.let {
            tripWheelCirc.setText(metersToUserCircumference(requireContext(), it))
        }
        viewModel.observeBikes().observe(viewLifecycleOwner) { bikes ->
            Log.d("asdf", bikes.size.toString())
            ArrayAdapter(
                requireContext(),
                R.layout.view_spinner_item,
                bikes.map { bike -> bike.name ?: "Bike ${bike.id}" }
            ).also { adapter ->
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                tripBikeSelect.setAdapter(adapter)
                tripBikeSelect.setText(bikes.find { bike -> bike.id == viewModel.tripInfo.bikeId }
                    ?.let { bike ->
                        bike.name ?: "Bike ${bike.id}"
                    }, false)
            }
            tripBikeSelect.onItemClickListener =
                AdapterView.OnItemClickListener { p0, p1, position, p3 ->
                    Log.d("asdf", position.toString())
                    viewModel.updateTripBikeId(bikes[position].id!!)
                }
        }


        tripName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int,
            ) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable?) {
                viewModel.updateTripName(s.toString())
            }
        })

        tripNotes.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int,
            ) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable?) {
                viewModel.updateTripNotes(s.toString())
            }
        })

        tripWheelCirc.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int,
            ) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable?) {
                viewModel.updateTripCircumference(s.toString())
            }
        })
    }
}