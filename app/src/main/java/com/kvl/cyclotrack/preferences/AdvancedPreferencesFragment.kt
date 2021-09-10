package com.kvl.cyclotrack.preferences

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.kvl.cyclotrack.R

class AdvancedPreferencesFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        requireActivity().title = "Settings: Advanced"
        setPreferencesFromResource(R.xml.advanced_preferences, rootKey)
    }
}