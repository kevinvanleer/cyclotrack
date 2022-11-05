package com.kvl.cyclotrack.preferences

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import com.kvl.cyclotrack.R
import com.kvl.cyclotrack.getUserSpeed
import com.kvl.cyclotrack.getUserSpeedUnitShort
import kotlin.math.roundToInt

class AutoPausePreferencesFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.autopause_preferences, rootKey)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        findPreference<SeekBarPreference>(getString(R.string.preference_key_autopause_pause_threshold))?.apply {
            min = 3
            max = 30
            setDefaultValue(5)
            showSeekBarValue = true
            seekBarIncrement = 1
        }
        findPreference<SeekBarPreference>(getString(R.string.preference_key_autopause_resume_threshold))?.apply {
            min = 3
            max = 30
            setDefaultValue(5)
            showSeekBarValue = true
            seekBarIncrement = 1
        }
        findPreference<SeekBarPreference>(getString(R.string.preference_key_autopause_speed_threshold))?.apply {
            min = 0
            max = getUserSpeed(requireContext(), 4.5).roundToInt()
            setDefaultValue(getUserSpeed(requireContext(), 1.4).roundToInt())
            showSeekBarValue = true
            seekBarIncrement = 1
            title = "${getString(R.string.seekbar_preference_autopause_speedThreshold_title)} (${
                getUserSpeedUnitShort(requireContext())
            })"
        }
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        requireActivity().findViewById<Toolbar>(R.id.preferences_toolbar).title =
            "Settings: Auto-pause"
    }
}
