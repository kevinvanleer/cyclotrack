package com.kvl.cyclotrack

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager

class AppPreferencesFragment : PreferenceFragmentCompat(),
    PreferenceFragmentCompat.OnPreferenceDisplayDialogCallback {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.app_preferences, rootKey)

        findPreference<Preference>(getString(R.string.preferences_biometrics_key))?.apply {
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                view?.findNavController()?.let {
                    Log.d("PREFERENCES", it.toString())
                    it.navigate(R.id.action_edit_biometrics_preferences)
                    true
                } == true
            }
        }

        if (BuildConfig.BUILD_TYPE == "dev") {
            findPreference<Preference>(getString(R.string.preferences_clear_preferences))?.apply {
                isVisible = true
                onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    AlertDialog.Builder(context).apply {
                        setPositiveButton("CLEAR") { _, _ ->
                            PreferenceManager.getDefaultSharedPreferences(context).edit().clear()
                                .apply()
                        }
                        setTitle("Clear Preferences?")
                        setMessage("You are about to clear all shared preferences. This cannot be undone.")
                    }.create().show()
                    true
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val circumferencePref: EditTextPreference? =
            preferenceManager.findPreference("wheel_circumference")

        if (!BleService.isBluetoothSupported(requireContext())) {
            val linkDevicesPref: DiscoverSensorDialogPreference? =
                preferenceManager.findPreference("paired_blue_devices")
            linkDevicesPref?.isEnabled = false
        }

        circumferencePref?.setOnBindEditTextListener { editText ->
            Log.d("PREFERENCES", "Updating circumference editor")
            editText.inputType = EditorInfo.TYPE_CLASS_NUMBER or EditorInfo.TYPE_NUMBER_FLAG_DECIMAL
            editText.isSingleLine = true
        }

        activity?.title = "Settings"
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun getCallbackFragment(): Fragment = this
    override fun onPreferenceDisplayDialog(
        caller: PreferenceFragmentCompat,
        pref: Preference?,
    ): Boolean {
        if (pref != null && DiscoverSensorDialogPreference::class.isInstance(pref)) {
            DiscoverSensorDialogFragmentCompat.getInstance(pref.key).let {
                it.setTargetFragment(this, 0)
                it.show(this.parentFragmentManager,
                    "androidx.preference.PreferenceFragment.DIALOG")
                return true
            }
        }
        //super.onDisplayPreferenceDialog(pref)
        return false
    }
}