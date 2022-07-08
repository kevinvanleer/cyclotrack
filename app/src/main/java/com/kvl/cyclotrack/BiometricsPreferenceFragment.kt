package com.kvl.cyclotrack

import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.DatePicker
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.kvl.cyclotrack.databinding.FragmentBiometricsPreferenceBinding
import com.kvl.cyclotrack.util.dateFormatPattenDob
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class BiometricsPreferenceFragment : Fragment() {
    private val logTag = "BiometricsPreferences"

    companion object {
        fun newInstance() = BiometricsPreferenceFragment()
    }

    private lateinit var viewModel: BiometricsViewModel
    private lateinit var binding: FragmentBiometricsPreferenceBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        viewModel =
            BiometricsViewModel(
                GoogleFitApiService(requireActivity()),
                PreferenceManager.getDefaultSharedPreferences(requireContext())
            )
        binding = FragmentBiometricsPreferenceBinding.inflate(inflater, container, false)
        binding.viewmodel = viewModel

        binding.preferenceBiometricsDobLayout.setOnClickListener {
            Log.d(tag, "on click layout")
        }
        binding.preferenceBiometricsDob.setOnClickListener {
            val dob = viewModel.dob?.let {
                try {
                    viewModel.dob?.let {
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
            default.set(1990, 6, 22)
            val c = GregorianCalendar()
            c.time = dob ?: default.time
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
                        viewModel.dob =
                            SimpleDateFormat(dateFormatPattenDob).format(
                                newDate.time
                            )
                        binding.preferenceBiometricsDob.setText(viewModel.dob)
                    }
                }.create().show()
            }
        }

        activity?.title = ""

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        requireActivity().findViewById<FloatingActionButton>(R.id.fab).apply {
            visibility = GONE
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.updateGoogleFitBiometrics()
        }
    }

    override fun onStop() {
        super.onStop()
        requireActivity().findViewById<FloatingActionButton>(R.id.fab).apply {
            visibility = VISIBLE
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(logTag, "onViewCreated")
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
                inflater.inflate(R.menu.menu_profile, menu)
                Log.d(logTag, "Options menu created")
            }

            override fun onMenuItemSelected(item: MenuItem): Boolean {
                Log.d("TRIP_SUMMARIES", "Options menu clicked")
                return when (item.itemId) {
                    R.id.action_settings -> {
                        Log.d("TRIP_SUMMARIES", "Options menu clicked settings")
                        findNavController().let {
                            Log.d("TRIP_SUMMARIES", it.toString())
                            it.navigate(R.id.action_go_to_settings)
                            true
                        }
                    }
                    else -> {
                        Log.w(logTag, "unimplemented menu item selected")
                        false
                    }
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }
}