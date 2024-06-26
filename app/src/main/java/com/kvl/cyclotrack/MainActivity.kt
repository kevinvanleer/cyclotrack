package com.kvl.cyclotrack

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.CheckBox
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.security.ProviderInstaller
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import com.kvl.cyclotrack.util.hasFitnessPermissions
import com.kvl.cyclotrack.util.isStravaSynced
import com.kvl.cyclotrack.util.shouldSyncGoogleFitBiometrics
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val logTag = MainActivity::class.simpleName
    lateinit var googleFitApiService: GoogleFitApiService
    private var newRidesDisabledDialog: AlertDialog? = null
    private val viewModel: MainActivityViewModel by viewModels()

    private lateinit var startTripHandler: () -> Unit

    private val requestLocationPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        for (entry in permissions.entries) {

            Log.d("LOCATION_PERMISSIONS", "${entry.key} = ${entry.value}")
            if (entry.key == Manifest.permission.ACCESS_FINE_LOCATION) {
                if (entry.value
                ) {
                    try {
                        startTripHandler()
                    } catch (e: IllegalArgumentException) {
                        Log.d(logTag, "CANNOT HANDLE MULTIPLE TRIP START TOUCHES")
                    }
                } else {
                    // Explain to the user that the feature is unavailable because
                    // the features requires a permission that the user has denied.
                    // At the same time, respect the user's decision. Don't link to
                    // system settings in an effort to convince the user to change
                    // their decision.

                    newRidesDisabledDialog?.show()
                }
            }
        }
    }

    private fun initializeLocationService() {
        when {
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                this.let {
                    AlertDialog.Builder(it).apply {
                        setPositiveButton(
                            "PROCEED"
                        ) { _, _ ->
                            // User clicked OK button
                            requestLocationPermissions.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                )
                            )
                        }
                        setNegativeButton(
                            "DENY"
                        ) { _, _ ->
                            // User cancelled the dialog
                            Log.d("TRIP_SUMMARIES", "CLICKED DENY")
                            newRidesDisabledDialog?.show()
                        }
                        setTitle("Grant Location Access")
                        setMessage("This app collects location data to enable the in-ride dashboard, and post-ride maps and graphs even when the app is closed or not in use.\n\nCyclotrack needs access to GPS location data and background location data to calculate speed and distance traveled during your rides. Data is only collected and recorded while rides are in progress. Data collected is stored on your device for your personal use. Your data is not sent to any third parties, including the developer (unless you enable the Google Fit integration). Please select PROCEED and then grant Cyclotrack access to location data.")
                    }.create()
                }.show()
            }

            else -> {
                // You can directly ask for the permission.
                // The registered ActivityResultCallback gets the result of this request.
                requestLocationPermissions.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                )
            }
        }
    }

    private fun handleFabClick(): View.OnClickListener = View.OnClickListener {
        // TODO: Multiple touches causes fatal exception
        try {
            when (PackageManager.PERMISSION_GRANTED) {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                -> startTripHandler()

                else -> initializeLocationService()
            }
        } catch (e: IllegalArgumentException) {
            Log.d("TRIP_SUMMARIES", "CANNOT HANDLE MULTIPLE TRIP START TOUCHES")
        }
    }

    private fun requestAnalytics() {
        AlertDialog.Builder(this)
            .apply {
                val optInCheckbox =
                    View.inflate(
                        context,
                        R.layout.remove_all_google_fit_dialog_option,
                        null
                    ).findViewById<CheckBox>(R.id.checkbox_removeAllGoogleFit)
                        .also { checkBox ->
                            checkBox.isChecked = true
                            checkBox.text = "Allow Cyclotrack to collect data"
                        }
                setPositiveButton(
                    "OK"
                ) { _, _ ->
                    FirebaseAnalytics.getInstance(context)
                        .logEvent("AnalyticsOptInDialogOk") {
                            param(
                                "analytics_enabled",
                                optInCheckbox.isChecked.compareTo(false).toLong()
                            )
                        }
                    PreferenceManager.getDefaultSharedPreferences(context).edit {
                        putBoolean(
                            getString(R.string.preferences_key_enable_analytics),
                            optInCheckbox.isChecked
                        )
                        putBoolean(
                            getString(R.string.preference_key_analytics_opt_in_presented),
                            true
                        )
                        commit()
                    }
                }
                setTitle("Data collection")
                setMessage("Cyclotrack would like to use Google Analytics to collect data about how you use the app to help improve it. Your participation is appreciated. Would you like to enable Google Analytics? You may change this option at any time from the settings menu. See the Cyclotrack privacy policy, also available in the Settings menu, for more details.")
                setView(optInCheckbox.rootView)
            }.create().show()
    }

    override fun onResume() {
        super.onResume()

        Log.d(logTag, "MainActivity onResume")
        if (hasFitnessPermissions(this)) {
            WorkManager.getInstance(this).beginUniqueWork(
                "GoogleFitSyncTripsWorker",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<GoogleFitSyncTripsWorker>().build()
            ).enqueue()
        }
        if (shouldSyncGoogleFitBiometrics(this) &&
            hasFitnessPermissions(this)
        ) {
            WorkManager.getInstance(this).beginUniqueWork(
                "GoogleFitSyncBiometricsWorker",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<GoogleFitSyncBiometricsWorker>().build()
            ).enqueue()
        }
        if (isStravaSynced(this)) {
            WorkManager.getInstance(this).beginUniqueWork(
                "StravaSyncTripsWorker",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<StravaSyncTripsWorker>()
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                    .build()
            ).enqueue()
        }
    }

    private fun showWhatsNewDialog() {
        getString(R.string.whats_new_message).takeIf { it.isNotEmpty() }?.let { message ->
            AlertDialog.Builder(this).apply {
                setTitle("What's new in version ${BuildConfig.VERSION_CODE}")
                setMessage(message)
                setPositiveButton(
                    "OK"
                ) { _, _ ->
                    Log.d(logTag, "What's new acknowledged")
                }
            }.create().show()
        }
    }

    private fun checkUserCurrentVersion() {
        Log.v(logTag, "checkUserCurrentVersion")
        PreferenceManager.getDefaultSharedPreferences(this)
            .getInt(getString(R.string.preference_key_user_current_version), -1)
            .let { currentVersion ->
                if (currentVersion != BuildConfig.VERSION_CODE) {
                    Log.d(logTag, "New app version detected. Version ${BuildConfig.VERSION_CODE}")
                    PreferenceManager.getDefaultSharedPreferences(this)
                        .edit {
                            putInt(
                                getString(R.string.preference_key_user_current_version),
                                BuildConfig.VERSION_CODE
                            )
                            commit()
                        }
                    if (currentVersion < 63) {
                        showWhatsNewDialog()
                    }
                }
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        Log.d(logTag, "onCreate")
        title = ""
        when (intent.getStringExtra("destinationView")) {
            TripInProgressFragment.toString() -> {

            }

            else -> setContentView(R.layout.activity_main)
        }
        setSupportActionBar(findViewById(R.id.toolbar_main))
        googleFitApiService = GoogleFitApiService(this)

        findViewById<BottomNavigationView>(R.id.main_activity_bottom_menu).setupWithNavController(
            findNavController(R.id.nav_host_fragment)
        )

        PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean(getString(R.string.preference_key_analytics_opt_in_presented), false).let {
                if (!it) {
                    Log.d(logTag, "Requesting analytics opt-in")
                    requestAnalytics()
                }
            }

        checkUserCurrentVersion()

        newRidesDisabledDialog = this.let {
            AlertDialog.Builder(it).apply {
                setPositiveButton(
                    "OK"
                ) { _, _ ->
                    Log.d("ALERT DIALOG", "CLICKED")
                }
                setTitle("New rides disabled")
                setMessage("Please access Cyclotrack app settings and grant permission to Location data. You will not be able to start a ride until location permission is granted.\n\nCyclotrack needs access to GPS location data and background location data to calculate speed and distance traveled during your rides. Data is only recorded while rides are in progress. Data collected is stored on your device for your personal use. Your data is not sent to any third parties, including the developer (unless you enable the Google Fit integration).")
            }.create()
        }

        findViewById<FloatingActionButton>(R.id.fab).apply {
            isEnabled = false
            visibility = View.INVISIBLE
            viewModel.latestTrip.observe(this@MainActivity) { trip: Trip? ->
                startTripHandler = {
                    findNavController(R.id.nav_host_fragment).navigate(
                        TripSummariesFragmentDirections.actionStartTrip(
                            trip?.takeIf { it.inProgress }?.id ?: -1
                        )
                    )
                }
                isEnabled = true
                visibility = View.VISIBLE
                setOnClickListener(handleFabClick())
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {

            //GoogleApiAvailability().isGooglePlayServicesAvailable(applicationContext)

            try {
                ProviderInstaller.installIfNeeded(applicationContext)
            } catch (e: GooglePlayServicesRepairableException) {
                Log.e("GooglePlayServices", "Google Play Services needs to be installed", e)
                /*GooglePlayServicesUtil.showErrorNotification(
                    e.connectionStatusCode,
                    applicationContext
                )*/
                GoogleApiAvailability().showErrorNotification(
                    applicationContext,
                    e.connectionStatusCode
                )
            } catch (e: GooglePlayServicesNotAvailableException) {
                Log.e("GooglePlayServices", "Google Play Services cannot be installed", e)
            }
        }
    }
}
