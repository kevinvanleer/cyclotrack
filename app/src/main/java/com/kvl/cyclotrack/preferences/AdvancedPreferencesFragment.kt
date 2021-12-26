package com.kvl.cyclotrack.preferences

import android.os.Bundle
import androidx.appcompat.widget.Toolbar
import androidx.preference.PreferenceFragmentCompat
import com.kvl.cyclotrack.R

class AdvancedPreferencesFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.advanced_preferences, rootKey)
    }

    override fun onResume() {
        super.onResume()
        requireActivity().findViewById<Toolbar>(R.id.preferences_toolbar).title =
            "Settings: Advanced"
    }
}