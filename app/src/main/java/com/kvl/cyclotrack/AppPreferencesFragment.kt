package com.kvl.cyclotrack

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.content.edit
import androidx.navigation.findNavController
import androidx.preference.*
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.kvl.cyclotrack.events.GoogleFitAccessGranted
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class AppPreferencesFragment : PreferenceFragmentCompat() {
    private lateinit var userGoogleFitBiometricsDialog: AlertDialog
    private val logTag = "PREFERENCES"

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.app_preferences, rootKey)

        findPreference<Preference>(getString(R.string.preference_key_bike_specs))?.apply {
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                view?.findNavController()?.let {
                    Log.d(logTag, it.toString())
                    it.navigate(R.id.action_edit_bike_specs)
                    true
                } == true
            }
        }

        findPreference<Preference>(getString(R.string.preferences_paired_ble_devices_key))?.apply {
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                view?.findNavController()?.let {
                    Log.d(logTag, it.toString())
                    it.navigate(R.id.action_show_linked_sensors)
                    true
                } == true
            }
        }

        findPreference<Preference>(getString(R.string.preferences_key_strava))?.apply {
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                val intentUri = Uri.parse("https://www.strava.com/oauth/mobile/authorize")
                    .buildUpon()
                    .appendQueryParameter("client_id", getString(R.string.strava_client_id))
                    .appendQueryParameter("redirect_uri", "https://www.kevinvanleer.com/cyclotrack")
                    .appendQueryParameter("response_type", "code")
                    .appendQueryParameter("approval_prompt", "auto")
                    .appendQueryParameter("scope", "activity:write,read")
                    .build()

                val intent = Intent(Intent.ACTION_VIEW, intentUri)
                startActivity(intent)
                true
            }
        }

        findPreference<Preference>(getString(R.string.preference_key_advanced_preferences))?.apply {
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                view?.findNavController()?.let {
                    Log.d(logTag, it.toString())
                    it.navigate(R.id.action_advanced_preferences)
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

        if (!BleService.isBluetoothSupported(requireContext())) {
            findPreference<Preference>(getString(R.string.preferences_paired_ble_devices_key))?.apply {
                isVisible = false
            }
        }

        if (FeatureFlags.devBuild) {
            configureClearPreferences()
        }

        if (FeatureFlags.betaBuild) {
            findPreference<Preference>(getString(R.string.preferences_display_app_version))?.apply {
                isVisible = true
                summary =
                    "v${BuildConfig.VERSION_CODE}: ${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_HASH})"
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onGoogleFitAccessGranted(@Suppress("UNUSED_PARAMETER") event: GoogleFitAccessGranted) {
        userGoogleFitBiometricsDialog.show()
        configureGoogleFitPreference(requireContext())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configureGoogleFitPreference(requireContext())

        userGoogleFitBiometricsDialog = AlertDialog.Builder(context).apply {
            val dialogView =
                View.inflate(context, R.layout.use_google_fit_biometrics_dialog, null)
            val useGoogleFitSwitch =
                dialogView.findViewById<SwitchCompat>(R.id.useGoogleFitBiometricsDialog_switch_useGoogleFit)
            useGoogleFitSwitch.isChecked =
                PreferenceManager.getDefaultSharedPreferences(context)
                    .getBoolean(
                        requireContext().getString(R.string.preference_key_biometrics_use_google_fit_biometrics),
                        true
                    )
            setPositiveButton("OK") { _, _ ->
                PreferenceManager.getDefaultSharedPreferences(requireContext()).edit {
                    putBoolean(
                        requireContext().getString(R.string.preference_key_biometrics_use_google_fit_biometrics),
                        useGoogleFitSwitch.isChecked
                    )
                    commit()
                }
            }
            setTitle(getString(R.string.useGoogleFitBiometricsDialog_title))
            setMessage(getString(R.string.useGoogleFitBiometricsDialog_message))
            setView(dialogView)
        }.create()
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }

    private fun configureGoogleFitPreference(context: Context) {
        findPreference<Preference>(getString(R.string.preferences_key_google_fit))?.apply {
            if (hasFitnessPermissions(context)) {
                this.title = getString(R.string.preferences_disconnect_google_fit_title)
                this.summary = getString(R.string.preferences_disconnect_google_fit_summary)
                onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    AlertDialog.Builder(context).apply {
                        val removeAllCheckboxView =
                            View.inflate(
                                context,
                                R.layout.remove_all_google_fit_dialog_option,
                                null
                            )
                        setPositiveButton("DISCONNECT") { _, _ ->
                            if (removeAllCheckboxView.findViewById<CheckBox>(R.id.checkbox_removeAllGoogleFit).isChecked) {
                                Log.i(logTag, "Remove all data from Google Fit")
                                WorkManager.getInstance(context)
                                    .enqueue(OneTimeWorkRequestBuilder<RemoveAllGoogleFitDataWorker>()
                                        .build().apply {
                                            WorkManager.getInstance(context)
                                                .getWorkInfoByIdLiveData(id)
                                                .observe(viewLifecycleOwner) { workInfo ->
                                                    if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                                                        Log.i(
                                                            logTag,
                                                            "Sign out from Google Fit"
                                                        )
                                                        GoogleSignIn.getClient(
                                                            context,
                                                            GoogleSignInOptions.DEFAULT_SIGN_IN
                                                        )
                                                            .signOut()
                                                            .addOnSuccessListener {
                                                                configureGoogleFitPreference(
                                                                    context
                                                                )
                                                            }
                                                    }
                                                }
                                        })
                            } else {
                                Log.i(logTag, "Sign out from Google Fit")
                                GoogleSignIn.getClient(
                                    context,
                                    GoogleSignInOptions.DEFAULT_SIGN_IN
                                ).signOut()
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
    ): View {
        if (!BleService.isBluetoothSupported(requireContext())) {
            preferenceManager.findPreference<Preference>("paired_blue_devices")?.isEnabled =
                false
        }

        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        requireActivity().findViewById<Toolbar>(R.id.preferences_toolbar).title = "Settings"
    }
}