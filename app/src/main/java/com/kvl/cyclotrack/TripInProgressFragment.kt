package com.kvl.cyclotrack

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.location.Location
import android.os.BatteryManager
import android.os.Bundle
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.format.DateUtils
import android.text.style.RelativeSizeSpan
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.GONE
import android.view.View.INVISIBLE
import android.view.View.OnClickListener
import android.view.View.OnTouchListener
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.setPadding
import androidx.core.view.updatePaddingRelative
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.preference.PreferenceManager
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.google.android.material.button.MaterialButton
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.kvl.cyclotrack.events.BatteryEvent
import com.kvl.cyclotrack.events.SlopeEvent
import com.kvl.cyclotrack.events.StartTripEvent
import com.kvl.cyclotrack.events.ThermalTierEvent
import com.kvl.cyclotrack.events.WheelCircumferenceEvent
import com.kvl.cyclotrack.util.SystemUtils
import com.kvl.cyclotrack.util.getBrightnessPreference
import com.kvl.cyclotrack.util.getSafeZoneMargins
import com.kvl.cyclotrack.util.lockScreenDashboardEnabled
import com.kvl.cyclotrack.util.thermalProtectionEnabled
import com.kvl.cyclotrack.widgets.batteryLevelDrawable
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt


@AndroidEntryPoint
class TripInProgressFragment :
    Fragment(), OnTouchListener {
    val logTag = "TripInProgressFragment"

    private val viewModel: TripInProgressViewModel by viewModels()
    private val args: TripInProgressFragmentArgs by navArgs()
    private lateinit var pauseButton: Button
    private lateinit var stopButton: Button
    private lateinit var resumeButton: Button
    private lateinit var autoPauseChip: Button
    private lateinit var bottomRightView: MeasurementView
    private lateinit var middleRightView: MeasurementView
    private lateinit var topView: MeasurementView
    private lateinit var bottomLeftView: MeasurementView
    private lateinit var middleLeftView: MeasurementView
    private lateinit var topLeftView: MeasurementView
    private lateinit var topRightView: MeasurementView
    private lateinit var footerView: TextView
    private lateinit var footerRightView: TextView
    private lateinit var footerLeftLayout: LinearLayout
    private lateinit var textViewSlope: TextView
    private lateinit var imageViewSlopeIcon: ImageView
    private lateinit var trackingImage: ImageView
    private lateinit var debugTextView: TextView
    private lateinit var blackoutOverlay: View
    private lateinit var blackoutMessage: TextView
    private lateinit var thermalIndicator: ImageView
    private lateinit var timeOfDayTextView: TextView
    private lateinit var batteryLevelTextView: TextView
    private lateinit var batteryLevelIcon: ImageView
    private lateinit var temperatureTextView: TextView
    private lateinit var windDirectionArrow: ImageView
    private lateinit var compassImage: ImageView
    private lateinit var windIcon: ImageView

    private var gpsEnabled = true
    private var isTimeTickRegistered = false
    private val lowBatteryThreshold = 15
    private lateinit var sharedPreferences: SharedPreferences
    private val userCircumference: Float?
        get() = viewModel.bikeWheelCircumference
    private var autoCircumference: Float? = null
    private var autoCircumferenceVariance: Double? = null

    val circumference: Float?
        get() = when (sharedPreferences.getBoolean(
            requireContext().applicationContext.getString(
                R.string.preference_key_useAutoCircumference
            ), true
        )) {
            true -> autoCircumference ?: userCircumference
            else -> userCircumference ?: autoCircumference
        }

    private val burnInReductionUserPref: Boolean
        get() = sharedPreferences.getBoolean(
            requireContext().applicationContext.getString(R.string.preference_key_burn_in_reduction),
            false
        )

    @Subscribe
    fun onWheelCircumferenceEvent(event: WheelCircumferenceEvent) {
        autoCircumference = event.circumference
        autoCircumferenceVariance = event.variance
    }

    // small always-visible cue that the display changes are thermal protection, not a
    // glitch; exempt from bezel hiding on purpose. Mercury level tracks the tier and
    // the tint tracks the presentation: white at WARM, green from HOT up.
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onThermalTierEvent(event: ThermalTierEvent) {
        thermalIndicator.visibility =
            if (event.tier >= ThermalTier.WARM) VISIBLE else GONE
        thermalIndicator.setImageResource(
            when (event.tier) {
                ThermalTier.HOT -> R.drawable.ic_thermometer_half_outline
                ThermalTier.EXTREME -> R.drawable.ic_thermometer_full_outline
                else -> R.drawable.ic_thermometer_quarter_outline
            }
        )
        thermalIndicator.imageTintList = ColorStateList.valueOf(
            if (event.tier >= ThermalTier.HOT) DIM_STROKE_COLOR
            else MeasurementView.DEFAULT_STROKE_COLOR
        )
    }

    private val timeTickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.v(logTag, "Received time tick")
            updateClock()
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onBatteryEvent(event: BatteryEvent.Level) {
        batteryLevelTextView.text = "${event.percent.roundToInt()}%"
        batteryLevelIcon.setImageResource(batteryLevelDrawable(event.percent.roundToInt()))
    }

    companion object {
        fun newInstance() = TripInProgressFragment()

        // pure green: only the green subpixel emits (blue ages fastest, stays off)
        val DIM_STROKE_COLOR = Color.argb(0.85f, 0f, 1f, 0f)

        // 1.0 = no brightness reduction at DIM: dimmed output was unreadable in sun,
        // green strokes carry the burn-in/thermal protection instead
        const val DIM_BRIGHTNESS_CAP = 1.0f
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        activity?.window?.apply {
            val params = attributes
            getBrightnessPreference(context).let { brightness ->
                Log.d(logTag, "User brightness $brightness")
                if (params.screenBrightness < brightness) {
                    params.screenBrightness = brightness
                    attributes = params
                }
            }
        }

        return inflater.inflate(R.layout.fragment_trip_in_progress, container, false)
    }

    private fun hidePause() {
        pauseButton.translationY = pauseButton.height.toFloat()
    }

    private fun hideResume() {
        resumeButton.translationX = resumeButton.width.toFloat()
    }

    private fun hideStop() {
        stopButton.translationX = -stopButton.width.toFloat()
    }

    private fun hideResumeStop() {
        hideResume()
        hideStop()
    }

    private fun setPausePeek() {
        if (stopButton.translationX != 0f && resumeButton.translationX != 0f) {
            if (pauseButtonVisible)
                pauseButton.animate().setDuration(0)
                    .translationY(min(pauseButton.height, swipeRect.height()).toFloat())
            else
                pauseButton.animate().setDuration(0)
                    .translationY(max(0, pauseButton.height - swipeRect.height()).toFloat())
        }
    }

    private fun slidePauseDown() =
        pauseButton.animate().setDuration(100).translationY(pauseButton.height.toFloat())

    private fun slidePauseUp() =
        pauseButton.animate().setDuration(100).translationY(0f)

    private fun slideResumeOut() =
        resumeButton.animate().setDuration(100).translationX(resumeButton.width.toFloat())

    private fun slideStopOut() =
        stopButton.animate().setDuration(100).translationX(-stopButton.width.toFloat())

    private fun slideResumeIn() =
        resumeButton.animate().setDuration(100).translationX(0f)

    private fun slideStopIn() =
        stopButton.animate().setDuration(100).translationX(0f)

    private fun slideOutResumeStop() {
        slideResumeOut()
        slideStopOut()
    }

    private fun slideInResumeStop() {
        slideResumeIn()
        slideStopIn()
    }

    private fun turnOnGps() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMinUpdateIntervalMillis(100)
            .build()
        val builder =
            locationRequest.let { LocationSettingsRequest.Builder().addLocationRequest(it) }
        val client = LocationServices.getSettingsClient(requireActivity())
        val task = client.checkLocationSettings(builder.build())
        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                // Location settings are not satisfied, but this can be fixed
                // by showing the user a dialog.
                try {
                    // Show the dialog by calling startResolutionForResult(),
                    // and check the result in onActivityResult().
                    exception.startResolutionForResult(
                        requireActivity(),
                        1000
                    )
                } catch (sendEx: IntentSender.SendIntentException) {
                    // Ignore the error.
                }
            }
        }
    }

    private fun startTrip() {
        Log.d(logTag, "$host")
        requireContext().startService(
            Intent(
                requireContext(),
                TripInProgressService::class.java
            ).apply {
                this.action = getString(R.string.action_start_trip_service)
            })
    }

    private fun pauseTrip(tripId: Long) {
        requireActivity().startService(
            Intent(
                requireContext(),
                TripInProgressService::class.java
            ).apply {
                this.action = getString(R.string.action_pause_trip_service)
                this.putExtra("tripId", tripId)
            })
    }

    private fun resumeTrip(tripId: Long) {
        requireActivity().startService(
            Intent(
                requireContext(),
                TripInProgressService::class.java
            ).apply {
                this.action = getString(R.string.action_resume_trip_service)
                this.putExtra("tripId", tripId)
            })
    }

    private fun endTrip(tripId: Long) {
        FirebaseAnalytics.getInstance(requireContext())
            .logEvent("StopTrip") {
                param("TripDuration", viewModel.currentProgress.value?.duration ?: 0.0)
                param("TripDistance", viewModel.currentProgress.value?.distance ?: 0.0)
            }
        requireActivity().startService(
            Intent(
                requireContext(),
                TripInProgressService::class.java
            ).apply {
                this.action = getString(R.string.action_stop_trip_service)
                this.putExtra("tripId", tripId)
            })

        when (tripId >= 0) {
            true -> {
                requireActivity().finish()
                findNavController().navigate(
                    TripInProgressFragmentDirections.actionFinishTrip(
                        tripId
                    )
                )
            }

            else -> findNavController()
                .navigate(R.id.action_back_to_summaries)
        }
    }

    private fun setTimeStateButtonState(newState: TimeState) {
        val tripId = newState.tripId
        Log.d(logTag, "Observed currentTimeState change: ${newState.state}")
        pauseButton.setOnClickListener(pauseTripListener(tripId))
        resumeButton.setOnClickListener(resumeTripListener(tripId))
        stopButton.setOnClickListener(stopTripListener(tripId))
        autoPauseChip.visibility = GONE
        when (newState.state) {
            TimeStateEnum.START, TimeStateEnum.RESUME -> {
                view?.doOnPreDraw { hideResumeStop() }
                view?.doOnPreDraw { hidePause() }
                pauseButton.text = getString(R.string.pause_label)
                if (viewModel.burnInReductionEnabled()) {
                    burnInReductionHandler.postDelayed(
                        burnInReductionCallback, 5000
                    )
                }
                armTwoHourBirTimer()
            }

            TimeStateEnum.PAUSE -> {
                view?.doOnPreDraw { hidePause() }
                autoPauseChip.visibility = if (newState.auto) VISIBLE else GONE
                burnInReductionHandler.removeCallbacks(burnInReductionCallback)
                // recompute rather than force NORMAL: protection stays active while
                // paused (a hot stop is the peak-damage scenario) and resume/stop
                // render in the level's button styling; peek is the reveal
                viewModel.publishBurnInRequest()
                viewModel.peekDisplay()
                slideInResumeStop()
            }

            else -> {
                pauseButton.setOnClickListener(startTripListener)
                pauseButton.text = getString(R.string.start_label)
                slidePauseUp()
            }
        }
    }

    private fun handleTimeStateChanges(tripId: Long) =
        viewModel.currentTimeState(tripId).observe(viewLifecycleOwner) { currentState ->
            currentState?.let {
                setTimeStateButtonState(it)
            }
        }

    private fun initializeAfterTripCreated(tripId: Long) {
        handleTimeStateChanges(tripId)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onSlopeEvent(event: SlopeEvent) {
        textViewSlope.text = String.format(
            Locale.getDefault(), "%d%%",
            if (event.slope.isFinite()) (event.slope * 100f).roundToInt() else 0
        )
        if (!hasHeartRate()) topLeftView.value =
            String.format(
                Locale.getDefault(),
                "%.3f",
                if (event.slope.isFinite()) event.slope else 0f
            )
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onStartTripEvent(event: StartTripEvent) {
        event.tripId.takeIf { it >= 0 }?.let { tripId ->
            initializeAfterTripCreated(tripId)
            viewModel.startTrip(tripId, viewLifecycleOwner)
            FirebaseAnalytics.getInstance(requireContext())
                .logEvent("StartTrip") {
                    param(
                        "CadenceEnabled",
                        (viewModel.cadenceSensor.value?.rpm != null).compareTo(false).toString()
                    )
                    param(
                        "HrmEnabled",
                        (viewModel.hrmSensor.value?.bpm != null).compareTo(false).toString()
                    )
                    param(
                        "SpeedEnabled",
                        (viewModel.speedSensor.value?.rpm != null).compareTo(false).toString()
                    )
                    param(
                        "PowerEnabled",
                        (viewModel.powerSensor.value?.instantaneousPower != null).compareTo(false)
                            .toString()
                    )
                    param(
                        "GpsEnabled",
                        (viewModel.gpsEnabled.value == true).compareTo(false).toString()
                    )
                }
        }
    }


    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) beginTripStart() else showLocationRequiredDialog()
    }

    private fun showLocationRequiredDialog() {
        AlertDialog.Builder(requireContext()).apply {
            setPositiveButton("OK") { _, _ -> }
            setTitle(getString(R.string.new_rides_disabled_title))
            setMessage(getString(R.string.new_rides_disabled_message))
        }.create().show()
    }

    private fun hasLocationPermission() = ContextCompat.checkSelfPermission(
        requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    private fun beginTripStart() {
        if (!gpsEnabled) {
            turnOnGps()
        } else {
            startTrip()
            hidePause()
            pauseButton.text = getString(R.string.pause_label)
        }
    }

    // Starting the foreground service without the runtime location permission
    // throws SecurityException in startForegroundCompat and the ride silently
    // never starts — request the permission instead
    private val startTripListener: OnClickListener = OnClickListener {
        if (!hasLocationPermission()) {
            requestLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            beginTripStart()
        }
    }

    private fun pauseTripListener(tripId: Long): OnClickListener = OnClickListener {
        pauseTrip(tripId)
        hidePause()
        slideInResumeStop()
    }

    private fun resumeTripListener(tripId: Long): OnClickListener = OnClickListener {
        resumeTrip(tripId)
        slideOutResumeStop()
    }

    private fun stopTripListener(tripId: Long): OnClickListener = OnClickListener {
        endTrip(tripId)
    }

    override fun onDestroy() {
        Log.d(logTag, "Destroying TIP View")
        super.onDestroy()
        activity?.window?.apply {
            val params = attributes
            params.screenBrightness = -1f
            attributes = params
        }
    }

    private fun hasHeartRate() = (viewModel.hrmSensor.value?.bpm ?: 0) > 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.burnInReductionUserPref = burnInReductionUserPref
        viewModel.thermalProtectionUserPref = thermalProtectionEnabled(requireContext())
        viewModel.publishBurnInRequest()

        val safeZone = getSafeZoneMargins(requireContext())
        (view.layoutParams as ViewGroup.MarginLayoutParams).apply {
            topMargin = safeZone.top
            bottomMargin = safeZone.bottom
            leftMargin = safeZone.left
            rightMargin = safeZone.right
        }

        Log.d(logTag, "TripInProgressFragment::onViewCreated")
        FirebaseAnalytics.getInstance(requireContext()).logEvent("EnterDashboard") {}

        savedInstanceState?.getLong("tripId", -1)
            .takeIf { t -> t != -1L }.let { tripId ->
                viewModel.tripId = tripId
            }

        initializeViews(view)
        initializeClockTick()
        view.setOnTouchListener(this)
        initializeLocationServiceStateChangeHandler()
        initializeMeasurementUpdateObservers()
        initializeWeatherObservers()
        initializeBurnInReduction()
    }

    private fun initializeClockTick() {
        updateClock()
        requireContext().registerReceiver(timeTickReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
        isTimeTickRegistered = true
    }

    private fun initializeLocationServiceStateChangeHandler() {
        viewModel.gpsEnabled.observe(viewLifecycleOwner) { status ->
            if (!status && gpsEnabled) {
                trackingImage.visibility = INVISIBLE
                debugTextView.text = getString(R.string.gps_disabled_message)
                Log.d(logTag, "Location service access disabled")
                activity?.let {
                    val builder = AlertDialog.Builder(it)
                    builder.apply {
                        setPositiveButton(
                            "ENABLE"
                        ) { _, _ ->
                            Log.d("TIP_GPS_DISABLED", "CLICKED ENABLE")
                            turnOnGps()
                        }
                        setNegativeButton(
                            "CANCEL"
                        ) { _, _ ->
                            Log.d("TIP_GPS_DISABLED", "CLICKED CANCEL")
                            endTrip(viewModel.tripId ?: -1)
                        }
                        setTitle("Enable location service")
                        setMessage("Location service has been disabled. Please enable location services before starting your ride.")
                    }

                    builder.create()
                }?.show()
            } else if (status && !gpsEnabled) {
                debugTextView.text = ""
            } else {
                Log.d(logTag, "Location service status unchanged")
            }
            gpsEnabled = status
        }
    }

    private fun initializeMeasurementUpdateObservers() {
        viewModel.location.observe(viewLifecycleOwner) { location ->
            if (viewModel.speedSensor.value?.rpm == null || circumference == null)
                topRightView.value =
                    getGpsSpeed(location, topRightView.value.toString().toDoubleOrNull())
            footerView.text = degreesToCardinal(location.bearing)
            debugTextView.text = getDebugString()
        }

        viewModel.currentProgress.observe(viewLifecycleOwner) {
            Log.d(logTag, "Location observer detected change")

            topView.value =
                String.format("%.2f", getUserDistance(requireContext(), it.distance))

            middleLeftView.value =
                getUserSpeed(requireContext(), it.distance / it.duration).let { averageSpeed ->
                    String.format("%.1f", if (averageSpeed.isFinite()) averageSpeed else 0f)
                }

            trackingImage.visibility =
                if (it.tracking && !decorationHidden()) VISIBLE else INVISIBLE
        }

        viewModel.currentTime.observe(viewLifecycleOwner) {
            bottomLeftView.value = DateUtils.formatElapsedTime((it).toLong())
        }

        viewModel.lastCompleteSplit.observe(viewLifecycleOwner) { split ->
            Log.d(logTag, "Observed last split change")
            if (viewModel.powerSensor.value?.instantaneousPower == null) {
                middleRightView.value =
                    String.format(
                        "%.1f",
                        getUserSpeed(
                            requireContext(),
                            (split.distance / split.duration.coerceAtLeast(0.0001))
                        )
                    )
            }
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.getFastestDistance(
                    getUserDistance(requireContext(), split.totalDistance).roundToInt(),
                    getUserDistance(requireContext(), 1.0),
                    3
                ).let {
                    if (it.size == 3 && it.firstOrNull()?.tripId == split.tripId) {
                        topView.setIconVisibility(VISIBLE)
                    } else {
                        topView.setIconVisibility(INVISIBLE)
                    }
                }
                if (viewModel.powerSensor.value?.instantaneousPower == null) {
                    viewModel.getFastestSplit(
                        getUserDistance(requireContext(), split.totalDistance).roundToInt(),
                        getUserDistance(requireContext(), 1.0),
                        3
                    ).let {
                        if (it.size == 3 && it.firstOrNull()?.tripId == split.tripId) {
                            middleRightView.setIconVisibility(VISIBLE)
                        } else {
                            middleRightView.setIconVisibility(INVISIBLE)
                        }
                    }
                }
            }
        }

        viewModel.hrmSensor.observe(viewLifecycleOwner) { hrm ->
            Log.d(logTag, "hrm battery: ${hrm.batteryLevel}")
            Log.d(logTag, "hrm bpm: ${hrm.bpm}")
            if (hrm.bpm != null) {
                topLeftView.label = "BPM"
                topLeftView.value = hrm.bpm.toString()
            }
            if (hrm.batteryLevel != null && hrm.batteryLevel < lowBatteryThreshold) topLeftView.extraInfo =
                "${hrm.batteryLevel}%"
        }

        viewModel.cadenceRpm.observe(viewLifecycleOwner) { cadence ->
            Log.d(logTag, "cadenceRpm: ${cadence?.rpm}")
            if (cadence?.rpm != null) {
                bottomRightView.label = "RPM"
                bottomRightView.value = when {
                    cadence.rpm.isFinite() && cadence.rpm < 1e3f -> cadence.rpm.toInt().toString()
                    else -> bottomRightView.value
                }
            }
            if (cadence?.batteryLevel != null && cadence.batteryLevel < lowBatteryThreshold) bottomRightView.extraInfo =
                "${cadence.batteryLevel}%"
        }

        viewModel.powerSensor.observe(viewLifecycleOwner) { power ->
            Log.d(logTag, "power: ${power.instantaneousPower}")
            val displayPower = when (power.instantaneousPower) {
                0 -> 0
                else -> power.emaPower ?: power.instantaneousPower
            }
            if (displayPower != null) {
                middleRightView.label = "WATTS"
                middleRightView.value = when {
                    displayPower < 10000 -> displayPower.toString()
                    else -> middleRightView.value
                }
            }
            if (power.batteryLevel != null && power.batteryLevel < lowBatteryThreshold) middleRightView.extraInfo =
                "${power.batteryLevel}%"
        }

        viewModel.speedSensor.observe(viewLifecycleOwner) { speed ->
            Log.d(logTag, "speed battery: ${speed.batteryLevel}")
            Log.d(logTag, "speed rpm: ${speed.rpm}")
            if (speed.rpm != null && circumference != null) {
                topRightView.label =
                    getUserSpeedUnitShort(requireContext()).uppercase(Locale.getDefault())
                topRightView.value = when {
                    speed.rpm.isFinite() && speed.rpm < 1e4f -> String.format(
                        "%.1f",
                        getUserSpeed(
                            requireContext(),
                            speed.rpm / 60 * circumference!!
                        )
                    )

                    else -> topRightView.value
                }
            }
            if (speed.batteryLevel != null && speed.batteryLevel < lowBatteryThreshold) topRightView.extraInfo =
                "${speed.batteryLevel}%"
        }
    }

    private fun initializeWeatherObservers() {
        viewModel.latestWeather.observe(viewLifecycleOwner) { weather ->
            if (weather != null) {
                if (!measurementHidden()) {
                    temperatureTextView.visibility = VISIBLE
                    footerRightView.visibility = VISIBLE
                    windDirectionArrow.visibility = VISIBLE
                }
                if (!decorationHidden()) {
                    windIcon.visibility = VISIBLE
                }

                //TODO: This will not update dynamically, maybe start a timer
                when (weather.timestamp < (System.currentTimeMillis() / 1000 - 60 * 15)) {
                    true -> {
                        temperatureTextView.alpha = 0.3f
                        footerRightView.alpha = 0.3f
                        windDirectionArrow.alpha = 0.3f
                        windIcon.alpha = 0.3f
                    }

                    else -> {
                        temperatureTextView.alpha = 1f
                        footerRightView.alpha = 1f
                        windDirectionArrow.alpha = 1f
                        windIcon.alpha = 1f
                    }
                }

                temperatureTextView.text = String.format(
                    "%s %s",
                    getUserTemperature(
                        requireContext(),
                        weather.temperature
                    ),
                    getUserTemperatureUnit(requireContext())
                )
                footerRightView.text = run {
                    val value =
                        String.format("%.1f", getUserSpeed(requireContext(), weather.windSpeed))
                    val unit =
                        getUserSpeedUnitShort(requireContext()).uppercase(Locale.getDefault())
                    SpannableString("$value $unit").apply {
                        setSpan(
                            RelativeSizeSpan(0.5f), value.length, length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
                when (weather.windSpeed) {
                    0.0 -> {
                        windDirectionArrow.setPadding(
                            TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP,
                                6f, resources.displayMetrics
                            ).toInt()
                        )
                        windDirectionArrow.setImageResource(R.drawable.ic_circle)
                    }

                    else -> {
                        windDirectionArrow.setPadding(0)
                        windDirectionArrow.setImageResource(R.drawable.ic_long_arrow_up)
                    }
                }
            }
        }
        zipLiveData(viewModel.location, viewModel.latestWeather).observe(viewLifecycleOwner) {
            val location = it.first
            val weather = it.second

            windDirectionArrow.rotation = bearingToIconRotation(
                bearingToWindAngle(location.bearing, weather.windDirection)
            ).toFloat()
        }
    }

    private fun applySleep(sleep: Boolean) {
        if (lockScreenDashboardEnabled(requireContext())) {
            view?.keepScreenOn = !sleep              // OS timeout turns display off for real
        } else {
            val wasBlack = blackoutOverlay.visibility == VISIBLE
            blackoutOverlay.visibility = if (sleep) VISIBLE else GONE  // OLED black = pixels off
            if (sleep && !wasBlack) showBlackoutMessage() else if (!sleep) hideBlackoutMessage()
        }
    }

    private fun showBlackoutMessage() {
        blackoutMessage.animate().cancel()
        blackoutMessage.alpha = 1f
        blackoutMessage.visibility = VISIBLE
        blackoutMessage.animate()
            .alpha(0f)
            .setStartDelay(5000)
            .setDuration(3000)
            .withEndAction { blackoutMessage.visibility = GONE }
    }

    private fun hideBlackoutMessage() {
        blackoutMessage.animate().cancel()
        blackoutMessage.visibility = GONE
    }

    private fun faceMeasurements() = listOf(
        topView, topLeftView, topRightView, middleLeftView,
        middleRightView, bottomLeftView, bottomRightView
    )

    private fun applyOutline(outline: Boolean, textColor: Int) {
        faceMeasurements().forEach { it.setOutlineMode(outline, textColor) }
        applyButtonOutline(outline, textColor)
    }

    private fun dashboardButtons() = listOf(pauseButton, resumeButton, stopButton, autoPauseChip)

    private var defaultButtonTint: ColorStateList? = null

    private fun applyButtonOutline(outline: Boolean, textColor: Int) {
        dashboardButtons().forEach { button ->
            (button as? MaterialButton)?.apply {
                if (defaultButtonTint == null) defaultButtonTint = backgroundTintList
                if (outline) {
                    backgroundTintList = ColorStateList.valueOf(Color.BLACK)
                    setStrokeColor(ColorStateList.valueOf(textColor))
                    strokeWidth = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 2f, resources.displayMetrics
                    ).toInt()
                } else {
                    backgroundTintList = defaultButtonTint
                    strokeWidth = 0
                }
                setTextColor(textColor)
            }
        }
    }


    // green + the Helmholtz–Kohlrausch effect buy back the perceived-brightness cost
    // that made dimmed WHITE outlines unreadable; a 100% boost is itself a heat source
    private fun applyDim(dim: Boolean) {
        activity?.window?.apply {
            attributes = attributes.also {
                val pref = getBrightnessPreference(requireContext())
                it.screenBrightness = when {
                    !dim -> pref
                    pref < 0 -> DIM_BRIGHTNESS_CAP // boost off: cap system brightness too
                    else -> minOf(pref, DIM_BRIGHTNESS_CAP)
                }
            }
        }
    }

    private fun initializeBurnInReduction() {
        viewModel.displayLevel.observe(viewLifecycleOwner) { renderDisplay() }
        viewModel.displayPeek.observe(viewLifecycleOwner) { renderDisplay() }
    }

    private fun peeking() = viewModel.displayPeek.value == true
    private fun currentLevel() = viewModel.displayLevel.value ?: DisplayLevel.NORMAL

    fun decorationHidden() = !peeking() && currentLevel() >= DisplayLevel.OUTLINE
    fun measurementHidden() = !peeking() && currentLevel() >= DisplayLevel.DIM

    // Two independent layers: COLOR/STYLE is a function of the level and is applied to
    // every view, visible or not (so views revealed later are already correct);
    // VISIBILITY is a function of the level, and a peek overrides visibility only —
    // revealing the full dashboard in the level's colors for the peek window.
    private fun renderDisplay() {
        val level = currentLevel()
        val peeking = peeking()

        val green = level >= DisplayLevel.DIM
        val textColor = when {
            green -> DIM_STROKE_COLOR
            level >= DisplayLevel.OUTLINE -> MeasurementView.DEFAULT_STROKE_COLOR
            else -> Color.WHITE
        }
        // outline style follows the level even through a peek: a peek reveals hidden
        // views, it never re-fills text
        val outlineText = level >= DisplayLevel.OUTLINE

        applyOutline(outline = outlineText, textColor = textColor)
        applyBezel(outlineText, textColor, green)
        faceMeasurements().forEach { it.setLabelVisible(!measurementHidden()) }
        applyDim(!peeking && level >= DisplayLevel.DIM)
        applySleep(!peeking && level >= DisplayLevel.SLEEP)
    }

    private fun bezelText() = listOfNotNull(
        footerView, footerRightView, temperatureTextView,
        timeOfDayTextView, batteryLevelTextView, debugTextView,
        if (::textViewSlope.isInitialized) textViewSlope else null,
    )

    private fun bezelImages() = listOfNotNull(
        trackingImage, compassImage, windIcon, windDirectionArrow, batteryLevelIcon,
        if (::imageViewSlopeIcon.isInitialized) imageViewSlopeIcon else null,
    )

    private fun applyBezel(outlineText: Boolean, textColor: Int, green: Boolean) {
        activity?.window?.let { window ->
            val windowInsetsController =
                WindowCompat.getInsetsController(window, window.decorView)
            if (decorationHidden())
                windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
            else windowInsetsController.show(WindowInsetsCompat.Type.statusBars())
        }
        val hasWeather = viewModel.latestWeather.value != null
        fun decorationVis(allowed: Boolean) =
            if (decorationHidden() || !allowed) INVISIBLE else VISIBLE

        trackingImage.visibility =
            if (FeatureFlags.productionBuild) GONE
            else decorationVis(viewModel.currentProgress.value?.tracking == true)
        compassImage.visibility = decorationVis(true)
        windIcon.visibility = decorationVis(hasWeather)
        batteryLevelIcon.visibility = decorationVis(true)

        val imageTint = when {
            green -> ColorStateList.valueOf(DIM_STROKE_COLOR)
            outlineText -> ColorStateList.valueOf(MeasurementView.DEFAULT_STROKE_COLOR)
            else -> null
        }
        bezelImages().forEach { it.imageTintList = imageTint }

        fun measurementVis(allowed: Boolean) =
            if (measurementHidden() || !allowed) INVISIBLE else VISIBLE

        temperatureTextView.visibility = measurementVis(hasWeather)
        windDirectionArrow.visibility = measurementVis(hasWeather)
        footerRightView.visibility = measurementVis(hasWeather)
        footerView.visibility = measurementVis(true)
        batteryLevelTextView.visibility = measurementVis(true)
        debugTextView.visibility =
            if (FeatureFlags.productionBuild) GONE else measurementVis(true)
        if (::textViewSlope.isInitialized) {
            footerLeftLayout.visibility = measurementVis(true)
            imageViewSlopeIcon.visibility = if (decorationHidden()) GONE else VISIBLE
        }

        bezelText().forEach { text ->
            text.paint.style = if (outlineText) Paint.Style.STROKE else Paint.Style.FILL
            text.paint.strokeWidth = 2f
            text.setTextColor(textColor)
            text.invalidate()
        }
        updateClock()
    }

    private fun setupSlopeView() {
        footerLeftLayout.visibility = VISIBLE
        textViewSlope = TextView(requireContext())
        textViewSlope.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        textViewSlope.typeface = ResourcesCompat.getFont(requireContext(), R.font.orbitron_medium)
        textViewSlope.setTextColor(Color.WHITE)
        textViewSlope.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        textViewSlope.updatePaddingRelative(
            start = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics
            ).toInt()
        )
        textViewSlope.text = "-%"
        imageViewSlopeIcon = ImageView(requireContext())
        val slopeIconSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 30f, resources.displayMetrics
        ).toInt()
        imageViewSlopeIcon.layoutParams = LinearLayout.LayoutParams(slopeIconSize, slopeIconSize)
        // white-fill variant: bezel images paint raw fill at NORMAL, and the shared
        // themed ic_mountains is black — black-on-black on the always-dark dashboard
        imageViewSlopeIcon.setImageResource(R.drawable.ic_mountains_solid)
        footerLeftLayout.addView(imageViewSlopeIcon)
        footerLeftLayout.addView(textViewSlope)
    }

    private fun initializeViews(view: View) {
        pauseButton = view.findViewById(R.id.pause_button)
        resumeButton = view.findViewById(R.id.resume_button)
        autoPauseChip = view.findViewById(R.id.autopause_button)
        stopButton = view.findViewById(R.id.stop_button)
        bottomRightView = view.findViewById(R.id.measurement_bottomRight)
        middleRightView = view.findViewById(R.id.measurement_middleRight)
        topView = view.findViewById(R.id.measurement_top)
        bottomLeftView = view.findViewById(R.id.measurement_bottomLeft)
        middleLeftView = view.findViewById(R.id.measurement_middleLeft)
        topLeftView = view.findViewById(R.id.measurement_topLeft)
        topRightView = view.findViewById(R.id.measurement_topRight)
        footerView = view.findViewById(R.id.measurement_footer)
        footerRightView = view.findViewById(R.id.measurement_footer_right)
        footerLeftLayout = view.findViewById(R.id.measurement_footer_left)
        trackingImage = view.findViewById(R.id.image_tracking)
        debugTextView = view.findViewById(R.id.textview_debug)
        timeOfDayTextView = view.findViewById(R.id.dashboard_textview_timeOfDay)
        batteryLevelTextView = view.findViewById(R.id.dashboard_textview_batteryLevel)
        batteryLevelIcon = view.findViewById(R.id.image_battery_level)
        temperatureTextView = view.findViewById(R.id.dashboard_textview_temperature)
        windDirectionArrow = view.findViewById(R.id.image_arrow_wind_direction)
        compassImage = view.findViewById(R.id.compass_image)
        windIcon = view.findViewById(R.id.image_wind_icon)
        blackoutOverlay = view.findViewById(R.id.blackout_overlay)
        blackoutOverlay.setOnClickListener { viewModel.peekDisplay() }
        blackoutMessage = view.findViewById(R.id.blackout_message)
        thermalIndicator = view.findViewById(R.id.image_thermal_indicator)

        context?.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let {
            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) onBatteryEvent(
                BatteryEvent.Level(level * 100f / scale, SystemUtils.currentTimeMillis())
            )
        }
        // flat instrument panel: shadows are invisible on black and the dark-theme
        // elevation overlay would lighten black fills; ripple is the press feedback
        dashboardButtons().forEach {
            it.stateListAnimator = null
            it.elevation = 0f
        }

        //TODO: Cleanup below
        //This is a lot of implementation specific initialization

        if (FeatureFlags.productionBuild) {
            trackingImage.visibility = GONE
            debugTextView.visibility = GONE
        }

        setupSlopeView();

        middleRightView.label =
            "SPLIT ${getUserSpeedUnitShort(requireContext()).uppercase(Locale.getDefault())}"
        middleRightView.setIcon(R.drawable.ic_trophy)
        topView.label =
            getUserDistanceUnitLong(requireContext()).uppercase(Locale.getDefault())
        topView.setIcon(R.drawable.ic_trophy)
        bottomLeftView.label = "DURATION"
        middleLeftView.label = "AVG ${
            getUserSpeedUnitShort(requireContext()).uppercase(
                Locale.getDefault()
            )
        }"
        topLeftView.label = "SLOPE"
        topRightView.label =
            "GPS ${getUserSpeedUnitShort(requireContext()).uppercase(Locale.getDefault())}"


        debugTextView.text = "-.-"
    }

    private fun getDebugString(): String {
        var debugString = ""
        //debugString += "%.2f".format(location.accuracy)
        //autoCircumference?.let { c -> debugString += " | C%.3f".format(c) }
        //autoCircumferenceVariance?.let { v -> debugString += " | ±%.7f".format(v) }
        //viewModel.currentProgress.value?.slope?.let { s -> debugString += " | S%.3f".format(s) }
        //viewModel.currentProgress.value?.slope?.takeIf { it.isFinite() }
        //    ?.let { s -> debugString += "S%.1f".format(s * 100f) }
        return debugString
    }

    private fun getGpsSpeed(
        location: Location,
        averageSpeed: Double?,
        alpha: Double = 1.0
    ) = when (location.speed < 0.5) {
        true -> 0.0
        else -> {
            val weight =
                when (location.hasSpeedAccuracy()) {
                    true -> (location.speedAccuracyMetersPerSecond.coerceAtMost(
                        10f
                    ) / 10.0 - 1).pow(8)

                    else -> 0.0
                }
            (averageSpeed
                ?: getUserSpeed(requireContext(), location.speed.toDouble()).toDouble())
                .takeIf { it.isFinite() }
                ?.let { oldSpeed ->
                    exponentialSmoothing(
                        alpha * weight,
                        getUserSpeed(
                            requireContext(),
                            location.speed.toDouble()
                        ).toDouble(),
                        oldSpeed
                    )
                }
        }
    }.let { speed ->
        String.format("%.1f", speed)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        viewModel.tripId?.let { outState.putLong("tripId", it) }
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        Log.d(logTag, "Called onResume: currentState = ${viewModel.currentState}")

        view?.doOnPreDraw { hideResumeStop() }

        try {
            //arguments?.getLong("tripId", args.tripId ?: -1)
            args.tripId.takeIf { tripId -> tripId != -1L }?.let { tripId ->
                viewModel.tripId = tripId
            }

            when (val tripId = viewModel.tripId) {
                null -> {
                    requireActivity().startService(
                        Intent(
                            requireContext(),
                            TripInProgressService::class.java
                        ).apply {
                            this.action = getString(R.string.action_initialize_trip_service)
                        })
                    pauseButton.setOnClickListener(startTripListener)
                    pauseButton.text = getString(R.string.start_label)
                }

                else -> {
                    view?.doOnPreDraw { hidePause() }
                    Log.d(logTag, "Received trip ID argument $tripId")
                    Log.d(logTag, "Resuming trip $tripId")
                    initializeAfterTripCreated(tripId)
                    viewModel.resumeTrip(tripId, viewLifecycleOwner)
                    if (!TripInProgressService.running) {
                        requireActivity().startService(
                            Intent(
                                requireContext(),
                                TripInProgressService::class.java
                            ).apply {
                                this.action = getString(R.string.action_start_trip_service)
                                this.putExtra("tripId", tripId)
                            })
                    }

                    viewLifecycleOwner.lifecycleScope.launch {
                        setTimeStateButtonState(viewModel.getCurrentTimeState(tripId))
                    }
                }
            }
        } catch (e: Exception) {
            when (e) {
                is java.lang.IllegalArgumentException,
                is java.lang.reflect.InvocationTargetException -> {
                    Log.e(logTag, "Failed to parse navigation args", e)
                    FirebaseCrashlytics.getInstance().recordException(e)
                    endTrip(-1)
                }

                is java.lang.IllegalStateException -> {
                    Log.e(logTag, "Failed to start trip service", e)
                    FirebaseCrashlytics.getInstance().recordException(e)
                }

                else -> throw e
            }
        }
    }

    private fun updateClock() {
        var hour = Calendar.getInstance().get(Calendar.HOUR)
        if (hour == 0) hour = 12
        val time = String.format(
            "%d:%02d",
            hour,
            Calendar.getInstance().get(Calendar.MINUTE)
        )
        val amPm = if (Calendar.getInstance().get(Calendar.AM_PM) == 0) "AM" else "PM"
        if (viewModel.cadenceRpm.value?.rpm == null) {
            timeOfDayTextView.visibility = INVISIBLE
            bottomRightView.value = time
            bottomRightView.label = amPm
        } else {
            timeOfDayTextView.apply {
                visibility =
                    if (measurementHidden()) INVISIBLE else VISIBLE
                text = String.format("%s %s", time, amPm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
        Log.d(logTag, "onStop")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(logTag, "onDestroyView")
        if (isTimeTickRegistered) context?.unregisterReceiver(timeTickReceiver)

        if (viewModel.tripId == null)
            requireContext().startService(
                Intent(
                    requireContext(),
                    TripInProgressService::class.java
                ).apply {
                    this.action = getString(R.string.action_shutdown_trip_service)
                })
        FirebaseAnalytics.getInstance(requireContext()).logEvent("LeaveDashboard") {}
    }

    private var swipeRect: Rect = Rect()
    private var pauseButtonVisible = false
    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        Log.v(logTag, event.toString())
        Log.v(logTag, "viewModel.currentState = ${viewModel.currentState}")

        if (viewModel.currentState == TimeStateEnum.STOP) return false
        return when (event?.action) {
            MotionEvent.ACTION_UP -> if (viewModel.currentState == TimeStateEnum.START || viewModel.currentState == TimeStateEnum.RESUME) {
                handlePauseButtonVisibility()
                if ((viewModel.displayLevel.value
                        ?: DisplayLevel.NORMAL) > DisplayLevel.NORMAL
                ) viewModel.peekDisplay()
                v?.performClick()
                true
            } else {
                // paused: no pause-button gymnastics, but a tap still reveals
                if ((viewModel.displayLevel.value
                        ?: DisplayLevel.NORMAL) > DisplayLevel.NORMAL
                ) viewModel.peekDisplay()
                false
            }

            MotionEvent.ACTION_DOWN -> {
                pauseButtonVisible = pauseButton.translationY == 0f
                swipeRect.apply {
                    top = event.getY(0).roundToInt()
                    bottom = event.getY(0).roundToInt()
                    left = event.getX(0).roundToInt()
                    right = event.getX(0).roundToInt()
                }
                true
            }

            MotionEvent.ACTION_MOVE -> {
                swipeRect.apply {
                    if (event.getX(0) < left) left = event.getX(0).roundToInt()
                    if (event.getX(0) > right) right = event.getX(0).roundToInt()
                    if (pauseButtonVisible) {
                        if (event.getY(0) > top)
                            bottom = event.getY(0).roundToInt()
                    } else {
                        if (event.getY(0) < bottom)
                            top = event.getY(0).roundToInt()
                    }
                }
                setPausePeek()
                true
            }

            else -> false
        }
    }

    private val burnInReductionHandler = android.os.Handler(Looper.getMainLooper())
    private val burnInReductionCallback = Runnable {
        viewModel.publishBurnInRequest()
    }

    // fire exactly at the 2h auto-enable boundary instead of waiting for a UI event;
    // the boundary is trip start + 2h, absolute and invariant, so scheduling is
    // idempotent (+100ms clears the boundary) and needs no re-arm or pause handling
    private val twoHourBirCallback = Runnable { viewModel.publishBurnInRequest() }
    private fun armTwoHourBirTimer() {
        burnInReductionHandler.removeCallbacks(twoHourBirCallback)
        val remainingMs = (viewModel.twoHourRemainingMs() ?: 7_200_000).coerceAtLeast(0)
        burnInReductionHandler.postDelayed(twoHourBirCallback, remainingMs + 100)
    }

    private val hidePauseHandler = android.os.Handler(Looper.getMainLooper())
    private val hidePauseCallback = Runnable {
        slidePauseDown()
    }

    private fun isPauseButtonHidden() = pauseButton.translationY > pauseButton.height / 2f

    private fun handlePauseButtonVisibility(): Boolean {
        Log.d(logTag, "handlePauseButtonVisibility")

        hidePauseHandler.removeCallbacks(hidePauseCallback)
        if (isPauseButtonHidden()) {
            hidePauseCallback.run()
        } else {
            slidePauseUp()
            hidePauseHandler.postDelayed(
                hidePauseCallback, 5000
            )
        }
        return true
    }
}
