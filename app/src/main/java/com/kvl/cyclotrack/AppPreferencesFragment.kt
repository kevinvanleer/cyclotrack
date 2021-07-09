package com.kvl.cyclotrack

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.CheckBox
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.findNavController
import androidx.preference.*
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

class AppPreferencesFragment : PreferenceFragmentCompat(),
    PreferenceFragmentCompat.OnPreferenceDisplayDialogCallback {
    private val logTag = "PREFERENCES"
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.app_preferences, rootKey)

        findPreference<Preference>(getString(R.string.preferences_biometrics_key))?.apply {
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                view?.findNavController()?.let {
                    Log.d(logTag, it.toString())
                    it.navigate(R.id.action_edit_biometrics_preferences)
                    true
                } == true
            }
        }

        val brightnessToggle =
            findPreference<SwitchPreference>(getString(R.string.preferences_dashboard_brightness_toggle_key))
        findPreference<SeekBarPreference>(getString(R.string.preferences_dashboard_brightness_key))?.apply {
            setOnPreferenceChangeListener { _, _ ->
                brightnessToggle?.isChecked = true
                true
            }
        }

        if (FeatureFlags.devBuild) {
            configureClearPreferences()
        }
    }

    private val configureGoogleFitBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            configureGoogleFitPreference(requireContext())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (FeatureFlags.betaBuild) {
            configureGoogleFitPreference(requireContext())
            LocalBroadcastManager.getInstance(requireContext())
                .registerReceiver(configureGoogleFitBroadcastReceiver,
                    IntentFilter(getString(R.string.intent_action_google_fit_access_granted)))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        LocalBroadcastManager.getInstance(requireContext())
            .unregisterReceiver(configureGoogleFitBroadcastReceiver)
    }

    private fun configureGoogleFitPreference(context: Context) {
        findPreference<Preference>(getString(R.string.preferences_key_google_fit))?.apply {
            if (GoogleSignIn.hasPermissions(getGoogleAccount(context), fitnessOptions)) {
                this.title = getString(R.string.preferences_disconnect_google_fit_title)
                this.summary = getString(R.string.preferences_disconnect_google_fit_summary)
                onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    AlertDialog.Builder(context).apply {
                        val removeAllCheckboxView =
                            View.inflate(context,
                                R.layout.remove_all_google_fit_dialog_option,
                                null)
                        setPositiveButton("DISCONNECT") { _, _ ->
                            if (removeAllCheckboxView.findViewById<CheckBox>(R.id.checkbox_removeAllGoogleFit).isChecked) {
                                Log.i(logTag, "Remove all data from Google Fit")
                                WorkManager.getInstance(context)
                                    .enqueue(OneTimeWorkRequestBuilder<RemoveAllGoogleFitDataWorker>()
                                        .build().apply {
                                            WorkManager.getInstance(context)
                                                .getWorkInfoByIdLiveData(id)
                                                .observe(viewLifecycleOwner, { workInfo ->
                                                    if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                                                        Log.i(logTag, "Sign out from Google Fit")
                                                        GoogleSignIn.getClient(context,
                                                            GoogleSignInOptions.DEFAULT_SIGN_IN)
                                                            .signOut()
                                                            .addOnSuccessListener {
                                                                configureGoogleFitPreference(context)
                                                            }
                                                    }
                                                })
                                        })
                            } else {
                                Log.i(logTag, "Sign out from Google Fit")
                                GoogleSignIn.getClient(context,
                                    GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
                                    .addOnSuccessListener {
                                        configureGoogleFitPreference(context)
                                    }
                            }
                        }
                        setView(removeAllCheckboxView)
                        setTitle(getString(R.string.preferences_disconnect_google_fit_title))
                        setMessage(getString(R.string.google_fit_logout_dialog_message))
                    }.create().show()
                    true
                }
            } else {
                this.title = getString(R.string.preferences_sync_with_google_fit_title)
                this.summary = getString(R.string.preferences_sync_with_google_fit_summary)
                onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    AlertDialog.Builder(context).apply {
                        setPositiveButton("SYNC") { _, _ ->
                            configureGoogleFit(requireActivity())
                        }
                        setTitle(getString(R.string.preferences_sync_with_google_fit_title))
                        setMessage(getString(R.string.google_fit_sync_dialog_description))
                    }.create().show()
                    true
                }
            }
            isVisible = true
        }

    }

    private fun configureClearPreferences() {
        findPreference<Preference>(getString(R.string.preferences_clear_preferences_key))?.apply {
            isVisible = true
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                AlertDialog.Builder(context).apply {
                    setPositiveButton("CLEAR") { _, _ ->
                        PreferenceManager.getDefaultSharedPreferences(context).edit().clear()
                            .commit()
                    }
                    setTitle("Clear Preferences?")
                    setMessage("You are about to clear all shared preferences. This cannot be undone.")
                }.create().show()
                true
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
            Log.d(logTag, "Updating circumference editor")
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