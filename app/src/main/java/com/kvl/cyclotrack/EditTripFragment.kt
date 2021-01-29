package com.kvl.cyclotrack

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
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
        return inflater.inflate(R.layout.edit_trip_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tripName: EditText = view.findViewById(R.id.edit_trip_name)
        val tripNotes: EditText = view.findViewById(R.id.edit_trip_notes)
        val tripDate: TextView = view.findViewById(R.id.edit_trip_date)

        tripName.setText(args.tripName)
        tripNotes.setText(args.tripNotes)
        tripDate.text = args.tripDate

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
                viewModel.changeDetails(s.toString(), tripNotes.text.toString())
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
                viewModel.changeDetails(tripName.text.toString(), s.toString())
            }
        })
    }
}