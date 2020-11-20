package com.kvl.cyclotrack

import android.os.Bundle

class AppPreferencesFragment : androidx.preference.PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.app_preferences, rootKey)
    }
}