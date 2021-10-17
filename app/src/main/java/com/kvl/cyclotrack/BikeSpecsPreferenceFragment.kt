package com.kvl.cyclotrack

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.kvl.cyclotrack.databinding.FragmentBikeSpecsPreferenceBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BikeSpecsPreferenceFragment : Fragment() {
    companion object {
        fun newInstance() = BikeSpecsPreferenceFragment()
    }

    private lateinit var viewModel: BikeSpecsPreferenceViewModel
    private lateinit var binding: FragmentBikeSpecsPreferenceBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        viewModel =
            BikeSpecsPreferenceViewModel(
                PreferenceManager.getDefaultSharedPreferences(
                    requireContext()
                )
            )
        binding = FragmentBikeSpecsPreferenceBinding.inflate(
            inflater,
            container,
            false
        )
        binding.viewmodel = viewModel

        activity?.title = "Settings: Bike specs"

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val circumferencePref =
            view.findViewById<EditText>(R.id.preference_edit_wheel_circumference)
        circumferencePref.inputType =
            EditorInfo.TYPE_CLASS_NUMBER or EditorInfo.TYPE_NUMBER_FLAG_DECIMAL
        circumferencePref.isSingleLine = true
    }

    override fun onResume() {
        super.onResume()
        viewLifecycleOwner.lifecycleScope.launch {
        }
    }
}