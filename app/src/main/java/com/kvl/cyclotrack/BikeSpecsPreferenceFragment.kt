package com.kvl.cyclotrack

import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.Toolbar
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.kvl.cyclotrack.databinding.FragmentBikeSpecsPreferenceBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@AndroidEntryPoint
class BikeSpecsPreferenceFragment : Fragment() {
    val logTag = "BikeSpecsPref"

    companion object {
        fun newInstance() = BikeSpecsPreferenceFragment()
    }

    private val viewModel: BikeSpecsPreferenceViewModel by viewModels()
    private lateinit var binding: FragmentBikeSpecsPreferenceBinding
    private lateinit var menuProvider: MenuProvider

    private fun initializePurchaseDate() {
        binding.preferencePreferenceBikeSpecsPurchaseDate.setOnClickListener {
            val purchaseDate =
                (viewModel.getPurchaseDateInstant() ?: Instant.now()).atZone(ZoneId.systemDefault())

            val datePicker = DatePicker(context)
            datePicker.updateDate(
                purchaseDate.year,
                purchaseDate.monthValue - 1,
                purchaseDate.dayOfMonth
            )
            context?.let { thisContext ->
                AlertDialog.Builder(thisContext).apply {
                    setTitle("Purchase date")
                    setView(datePicker)
                    setPositiveButton("OK") { _, _ ->
                        val newDate = ZonedDateTime.of(
                            datePicker.year,
                            datePicker.month + 1,
                            datePicker.dayOfMonth,
                            0,
                            0,
                            0,
                            0,
                            ZoneId.systemDefault()
                        )
                        viewModel.setPurchaseDateInstant(newDate.toInstant())
                        binding.preferencePreferenceBikeSpecsPurchaseDate.setText(
                            newDate.format(
                                DateTimeFormatter.ISO_LOCAL_DATE
                            )
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
        binding = FragmentBikeSpecsPreferenceBinding.inflate(
            inflater,
            container,
            false
        )
        binding.viewmodel = viewModel

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

        view.findViewById<AppCompatButton>(R.id.preference_bike_specs_button_link_ble_devices)
            .apply {
                setOnClickListener { view ->
                    view.findNavController()
                        .navigate(
                            BikeSpecsPreferenceFragmentDirections.actionLinkSensorsToBike(
                                viewModel.currentBikeId!!,
                                when {
                                    viewModel.name.isNullOrEmpty() -> "Bike ${viewModel.currentBikeId}"
                                    else -> viewModel.name!!
                                }
                            )
                        )
                }
            }

        viewModel.bikes.observe(viewLifecycleOwner) { bikes ->
            ArrayAdapter(
                requireContext(),
                R.layout.view_spinner_item,
                bikes.map { bike -> bike.name ?: "Bike ${bike.id}" }
            ).also { adapter ->
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                bikeSelect.setAdapter(adapter)
                viewModel.notifyChange()
                bikeSelect.setText(bikes.find { bike -> bike.id == viewModel.currentBikeId }
                    ?.let { bike ->
                        bike.name ?: "Bike ${bike.id}"
                    }, false)
            }
            bikeSelect.onItemClickListener =
                AdapterView.OnItemClickListener { _, _, position, _ ->
                    viewModel.reset()
                    viewModel.currentBikeId = bikes[position].id!!
                }
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().findViewById<Toolbar>(R.id.preferences_toolbar).title =
            "Settings: Bike specs"
        addMenuProvider()
    }

    override fun onPause() {
        super.onPause()
        requireActivity().removeMenuProvider(menuProvider)
    }

    private fun addMenuProvider() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                this@BikeSpecsPreferenceFragment.menuProvider = this
                menuInflater.inflate(R.menu.menu_settings_bikes, menu)
            }

            override fun onMenuItemSelected(item: MenuItem): Boolean {
                Log.d(logTag, "Options menu clicked")
                return when (item.itemId) {
                    R.id.action_settings_bike_add -> {
                        viewModel.addBike()
                        true
                    }
                    R.id.action_settings_bike_delete -> {
                        lifecycleScope.launch(Dispatchers.IO) {
                            when {
                                viewModel.bikeHasTrips() -> {
                                    Snackbar.make(
                                        this@BikeSpecsPreferenceFragment.requireView(),
                                        getString(R.string.snackbar_message_cannot_delete_bike),
                                        BaseTransientBottomBar.LENGTH_SHORT
                                    ).show()
                                }
                                (viewModel.bikes.value?.size ?: 0) <= 1 -> {
                                    Snackbar.make(
                                        this@BikeSpecsPreferenceFragment.requireView(),
                                        getString(R.string.snackbar_message_cannot_delete_last_bike),
                                        BaseTransientBottomBar.LENGTH_SHORT
                                    ).show()
                                }
                                else -> {
                                    viewModel.deleteCurrentBike()
                                }
                            }
                        }
                        true
                    }
                    else -> {
                        Log.w(logTag, "unimplemented menu option")
                        false
                    }
                }
            }
        })
    }
}