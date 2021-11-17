package com.kvl.cyclotrack

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.kvl.cyclotrack.databinding.FragmentBikeSpecsPreferenceBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class BikeSpecsPreferenceFragment : Fragment() {
    companion object {
        fun newInstance() = BikeSpecsPreferenceFragment()
    }

    private val viewModel: BikeSpecsPreferenceViewModel by viewModels()
    private lateinit var binding: FragmentBikeSpecsPreferenceBinding

    private fun initializePurchaseDate() {
        binding.preferencePreferenceBikeSpecsPurchaseDate.setOnClickListener {
            val purchaseDate = viewModel.purchaseDate?.let {
                try {
                    viewModel.purchaseDate?.let {
                        SimpleDateFormat(
                            dateFormatPattenDob,
                            Locale.US
                        ).parse(it)
                    }
                } catch (e: ParseException) {
                    Log.e(tag, "Could not parse preference data", e)
                    null
                }
            }

            val default = GregorianCalendar.getInstance()
            val c = GregorianCalendar()
            c.time = purchaseDate ?: default.time
            val year = c.get(GregorianCalendar.YEAR)
            val month = c.get(GregorianCalendar.MONTH)
            val day = c.get(GregorianCalendar.DAY_OF_MONTH)

            val datePicker = DatePicker(context)
            datePicker.updateDate(year, month, day)
            context?.let { thisContext ->
                AlertDialog.Builder(thisContext).apply {
                    setTitle("Date of birth")
                    setView(datePicker)
                    setPositiveButton("OK") { _, _ ->
                        val newDate = GregorianCalendar.getInstance()
                        newDate.set(datePicker.year, datePicker.month, datePicker.dayOfMonth)
                        viewModel.purchaseDate = newDate.timeInMillis.toString()
                        binding.preferencePreferenceBikeSpecsPurchaseDate.setText(viewModel.purchaseDate)
                    }
                }.create().show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        /*viewModel =
            BikeSpecsPreferenceViewModel(
                PreferenceManager.getDefaultSharedPreferences(
                    requireContext()
                )
            )*/
        binding = FragmentBikeSpecsPreferenceBinding.inflate(
            inflater,
            container,
            false
        )
        binding.viewmodel = viewModel

        activity?.title = "Settings: Bike specs"

        initializePurchaseDate()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val circumferencePref =
            view.findViewById<EditText>(R.id.preference_edit_wheel_circumference)
        circumferencePref.inputType =
            EditorInfo.TYPE_CLASS_NUMBER or EditorInfo.TYPE_NUMBER_FLAG_DECIMAL
        circumferencePref.isSingleLine = true
        val bikeSelect =
            view.findViewById<AutoCompleteTextView>(R.id.preference_bike_specs_spinner_bike_select)

        viewModel.bikes.observe(viewLifecycleOwner) { bikes ->
            ArrayAdapter(
                requireContext(),
                R.layout.view_spinner_item,
                bikes.map { bike -> bike.name ?: "Bike ${bike.id}" }
            ).also { adapter ->
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                bikeSelect.setAdapter(adapter)
                bikeSelect.setText(bikes.find { bike -> bike.id == viewModel.currentBikeId }
                    ?.let { bike ->
                        bike.name ?: "Bike ${bike.id}"
                    }, false)
            }
            bikeSelect.onItemClickListener =
                AdapterView.OnItemClickListener { _, _, position, _ ->
                    viewModel.currentBikeId = bikes[position].id!!
                }
        }
    }

    override fun onResume() {
        super.onResume()
        viewLifecycleOwner.lifecycleScope.launch {
        }
    }
}