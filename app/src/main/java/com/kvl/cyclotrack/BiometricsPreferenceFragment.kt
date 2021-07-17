package com.kvl.cyclotrack

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.DatePicker
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.kvl.cyclotrack.databinding.BiometricsPreferenceFragmentBinding
import dagger.hilt.android.AndroidEntryPoint
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class BiometricsPreferenceFragment : Fragment() {
    companion object {
        fun newInstance() = BiometricsPreferenceFragment()
    }

    private lateinit var viewModel: BiometricsViewModel
    private lateinit var binding: BiometricsPreferenceFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        viewModel =
            BiometricsViewModel(GoogleFitApiService(requireActivity()),
                PreferenceManager.getDefaultSharedPreferences(requireContext()))
        binding = BiometricsPreferenceFragmentBinding.inflate(inflater, container, false)
        binding.viewmodel = viewModel

        binding.preferenceBiometricsDobLayout.setOnClickListener {
            Log.d(tag, "on click layout")
        }
        binding.preferenceBiometricsDob.setOnClickListener {
            val dob = viewModel.dob?.let {
                try {
                    viewModel.dob?.let {
                        SimpleDateFormat(getString(R.string.date_format_patten_dob),
                            Locale.US).parse(it)
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
                            SimpleDateFormat(getString(R.string.date_format_patten_dob)).format(
                                newDate.time)
                        binding.preferenceBiometricsDob.setText(viewModel.dob)
                    }
                }.create().show()
            }
        }

        activity?.title = "Settings: Biometrics"

        return binding.root
    }
}