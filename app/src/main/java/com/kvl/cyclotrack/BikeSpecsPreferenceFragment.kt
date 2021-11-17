package com.kvl.cyclotrack

import android.os.Bundle
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
            val purchaseDate =
                (viewModel.purchaseDate ?: Instant.now()).atZone(ZoneId.systemDefault())

            val datePicker = DatePicker(context)
            datePicker.updateDate(
                purchaseDate.year,
                purchaseDate.monthValue,
                purchaseDate.dayOfMonth
            )
            context?.let { thisContext ->
                AlertDialog.Builder(thisContext).apply {
                    setTitle("Date of birth")
                    setView(datePicker)
                    setPositiveButton("OK") { _, _ ->
                        val newDate = GregorianCalendar.getInstance()
                        newDate.set(datePicker.year, datePicker.month, datePicker.dayOfMonth)
                        viewModel.purchaseDate = newDate.toInstant()
                        binding.preferencePreferenceBikeSpecsPurchaseDate.setText(
                            newDate.toInstant().atZone(
                                ZoneId.systemDefault()
                            ).format(DateTimeFormatter.ISO_LOCAL_DATE)
                        )
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