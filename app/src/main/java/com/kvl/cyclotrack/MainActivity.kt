package com.kvl.cyclotrack

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
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
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.kvl.cyclotrack.util.SystemUtils
import com.kvl.cyclotrack.util.hasFitnessPermissions
import com.kvl.cyclotrack.util.isHealthConnectAvailable
import com.kvl.cyclotrack.util.isStravaSynced
import com.kvl.cyclotrack.util.setGoogleFitSyncEnabled
import com.kvl.cyclotrack.util.shouldSyncGoogleFitBiometrics
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val logTag = MainActivity::class.simpleName

    @Inject
    lateinit var tripsRepository: TripsRepository

    @Inject
    lateinit var timeStateRepository: TimeStateRepository

    @Inject
    lateinit var measurementsRepository: MeasurementsRepository
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
                                optInCheckbox.isChecked.compareTo(false).toString()
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

    // The 33->34 migration drains any remaining legacy sensor rows inline while
    // the first DB query blocks; surface that as a fullscreen overlay when there
    // is enough data for the pause to be noticeable. Trivial drains finish
    // before this collector ever sees a non-null value.
    private fun showLegacySensorDropProgress() {
        var overlay: View? = null
        var bar: ProgressBar? = null
        var label: TextView? = null
        val counts = NumberFormat.getIntegerInstance()
        lifecycleScope.launch {
            LegacySensorDropStatus.progress.collect { p ->
                if (p == null) {
                    overlay?.let {
                        findViewById<ViewGroup>(android.R.id.content).removeView(it)
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        Toast.makeText(
                            this@MainActivity,
                            "Ride database optimized",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    overlay = null
                    return@collect
                }
                if (p.total < LegacySensorDropStatus.UI_THRESHOLD_ROWS) return@collect
                if (overlay == null) {
                    val dp = resources.displayMetrics.density
                    overlay = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        val bg = TypedValue()
                        theme.resolveAttribute(android.R.attr.colorBackground, bg, true)
                        setBackgroundColor(bg.data)
                        isClickable = true
                        elevation = 24 * dp
                        val pad = (32 * dp).toInt()
                        setPadding(pad, pad, pad, pad)
                        addView(TextView(this@MainActivity).apply {
                            text = "Optimizing ride database"
                            textSize = 22f
                        })
                        label = TextView(this@MainActivity).also {
                            addView(
                                it,
                                LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply { topMargin = (16 * dp).toInt() }
                            )
                        }
                        bar = ProgressBar(
                            this@MainActivity, null, android.R.attr.progressBarStyleHorizontal
                        ).apply { max = 1000 }.also {
                            addView(
                                it,
                                LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply { topMargin = (16 * dp).toInt() }
                            )
                        }
                    }
                    findViewById<ViewGroup>(android.R.id.content).addView(
                        overlay,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    // keep the user watching: screen-off invites OEM freezers
                    // and task cleaners to kill the migration
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                if (p.finalizing) {
                    bar?.isIndeterminate = true
                    label?.text = "Finalizing — cleaning up old sensor data…"
                } else {
                    bar?.isIndeterminate = false
                    bar?.progress = (p.migrated * 1000 / p.total).toInt()
                    label?.text = "Migrating sensor data: " +
                        "${counts.format(p.migrated)} of ${counts.format(p.total)} records"
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        Log.d(logTag, "MainActivity onResume")
        runCatching {
            if (hasFitnessPermissions(this)) {
                WorkManager.getInstance(this).beginUniqueWork(
                    "GoogleFitSyncTripsWorker",
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<GoogleFitSyncTripsWorker>().build()
                ).enqueue()
            }
            if (isHealthConnectAvailable(this)) {
                WorkManager.getInstance(this).beginUniqueWork(
                    "HealthConnectSyncTripsWorker",
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<HealthConnectSyncTripsWorker>().build()
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
                    // REPLACE cancels only cooperatively: the outgoing worker's full-trip
                    // Room loads keep running while the replacement starts its own,
                    // stacking multiple trips' data in the heap (OOM cluster on 256MB
                    // devices). APPEND_OR_REPLACE serializes behind live work but still
                    // replaces failed/cancelled chains.
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    OneTimeWorkRequestBuilder<StravaSyncTripsWorker>()
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                        .build()
                ).enqueue()
            }
        }.onFailure {
            Log.e(logTag, "Failed to enqueue sync work", it)
            FirebaseCrashlytics.getInstance().recordException(it)
        }

        lifecycleScope.launch {
            tripsRepository.getReviewGateStats().let {
                maybeRequestReview(it.rideCount, it.newestRideStart)
            }
        }
    }

    private fun maybeRequestReview(rideCount: Int, newestRideStart: Long) {
        val minRides = 3
        val prefKey = "last_review_prompt"
        if (rideCount < minRides) return
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (newestRideStart <= prefs.getLong(prefKey, 0)) return
        val manager = ReviewManagerFactory.create(this)
        manager.requestReviewFlow().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                manager.launchReviewFlow(this, task.result)
                prefs.edit { putLong(prefKey, System.currentTimeMillis()) }
            }
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
                    if (currentVersion < 74) {
                        showWhatsNewDialog()
                    }
                }
            }
    }

    // A trip idle past this long is abandoned (crash mid-ride, backup restore to a
    // new phone, forgotten stop): close it at its last recorded activity so stats
    // stay honest. Even a multi-day tour pauses less than this; past 24h idle
    // you're starting a new ride.
    private val staleTripTimeoutMs = TimeUnit.HOURS.toMillis(24)

    private suspend fun closeStaleTrips() {
        if (TripInProgressService.running) return
        tripsRepository.getInProgress().forEach { trip ->
            val tripId = trip.id ?: return@forEach
            val latestTimeState = timeStateRepository.getLatest(tripId)
            val lastActivity = maxOf(
                latestTimeState?.timestamp ?: trip.timestamp,
                measurementsRepository.getLatest(tripId)?.time ?: 0L
            )
            if (SystemUtils.currentTimeMillis() - lastActivity > staleTripTimeoutMs) {
                Log.i(logTag, "Auto-stopping stale in-progress trip $tripId")
                if (latestTimeState?.state != TimeStateEnum.STOP) {
                    timeStateRepository.appendTimeState(
                        TimeState(
                            tripId = tripId,
                            state = TimeStateEnum.STOP,
                            timestamp = lastActivity,
                            auto = true,
                        )
                    )
                }
                tripsRepository.endTrip(tripId)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        Log.d(logTag, "onCreate")
        title = ""
        lifecycleScope.launch(Dispatchers.IO) { closeStaleTrips() }
        when (intent.getStringExtra("destinationView")) {
            TripInProgressFragment.toString() -> {

            }

            else -> setContentView(R.layout.activity_main)
        }
        setSupportActionBar(findViewById(R.id.toolbar_main))
        val statusBarScrim = findViewById<View>(R.id.status_bar_scrim)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.toolbar_main)) { toolbar, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            toolbar.updatePadding(top = top)
            statusBarScrim.updateLayoutParams { height = top }
            insets
        }
        googleFitApiService = GoogleFitApiService(this)

        findViewById<BottomNavigationView>(R.id.main_activity_bottom_menu).setupWithNavController(
            findNavController(R.id.nav_host_fragment)
        )
        findNavController(R.id.nav_host_fragment).addOnDestinationChangedListener { _, _, _ ->
            findViewById<AppBarLayout>(R.id.app_bar_main).setExpanded(true, false)
        }

        PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean(getString(R.string.preference_key_analytics_opt_in_presented), false).let {
                if (!it) {
                    Log.d(logTag, "Requesting analytics opt-in")
                    requestAnalytics()
                }
            }

        checkUserCurrentVersion()
        showLegacySensorDropProgress()

        if (!PreferenceManager.getDefaultSharedPreferences(this)
                .contains(getString(R.string.preferences_key_google_fit))
        ) {
            setGoogleFitSyncEnabled(this, hasFitnessPermissions(this))
        }

        newRidesDisabledDialog = this.let {
            AlertDialog.Builder(it).apply {
                setPositiveButton(
                    "OK"
                ) { _, _ ->
                    Log.d("ALERT DIALOG", "CLICKED")
                }
                setTitle(getString(R.string.new_rides_disabled_title))
                setMessage(getString(R.string.new_rides_disabled_message))
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
