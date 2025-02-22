package com.kvl.cyclotrack

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.SharedPreferences
import android.graphics.Rect
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.text.format.DateUtils
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
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.setPadding
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
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.kvl.cyclotrack.events.StartTripEvent
import com.kvl.cyclotrack.events.WheelCircumferenceEvent
import com.kvl.cyclotrack.util.getBrightnessPreference
import com.kvl.cyclotrack.util.getSafeZoneMargins
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
    private lateinit var trackingImage: ImageView
    private lateinit var debugTextView: TextView
    private lateinit var timeOfDayTextView: TextView
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

    private val timeTickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.v(logTag, "Received time tick")
            updateClock()
        }
    }

    companion object {
        fun newInstance() = TripInProgressFragment()
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
        requireContext().startService(Intent(
            requireContext(),
            TripInProgressService::class.java
        ).apply {
            this.action = getString(R.string.action_start_trip_service)
        })
    }

    private fun pauseTrip(tripId: Long) {
        requireActivity().startService(Intent(
            requireContext(),
            TripInProgressService::class.java
        ).apply {
            this.action = getString(R.string.action_pause_trip_service)
            this.putExtra("tripId", tripId)
        })
    }

    private fun resumeTrip(tripId: Long) {
        requireActivity().startService(Intent(
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
            }
        requireActivity().startService(Intent(
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
            }

            TimeStateEnum.PAUSE -> {
                view?.doOnPreDraw { hidePause() }
                autoPauseChip.visibility = if (newState.auto) VISIBLE else GONE
                if (viewModel.burnInReductionEnabled()) {
                    burnInReductionHandler.removeCallbacks(burnInReductionCallback)
                    viewModel.burnInReductionActive.value = false
                }
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
    fun onStartTripEvent(event: StartTripEvent) {
        event.tripId.takeIf { it >= 0 }?.let { tripId ->
            initializeAfterTripCreated(tripId)
            viewModel.startTrip(tripId, viewLifecycleOwner)
            FirebaseAnalytics.getInstance(requireContext())
                .logEvent("StartTrip") {
                    param(
                        "CadenceEnabled",
                        (viewModel.cadenceSensor.value != null).compareTo(false).toLong()
                    )
                    param(
                        "HrmEnabled",
                        (viewModel.hrmSensor.value != null).compareTo(false).toLong()
                    )
                    param(
                        "SpeedEnabled",
                        (viewModel.speedSensor.value != null).compareTo(false).toLong()
                    )
                    param(
                        "GpsEnabled",
                        (viewModel.gpsEnabled.value != null).compareTo(false).toLong()
                    )
                }
        }
    }


    private val startTripListener: OnClickListener = OnClickListener {
        if (!gpsEnabled) {
            turnOnGps()
        } else {
            startTrip()
            hidePause()
            pauseButton.text = getString(R.string.pause_label)
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
        viewModel.burnInReductionUserPref = burnInReductionUserPref;
        viewModel.burnInReductionActive.value = viewModel.burnInReductionUserPref

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

            if (!hasHeartRate()) topLeftView.value =
                String.format("%.3f", if (it.slope.isFinite()) it.slope else 0f)

            trackingImage.visibility =
                if (it.tracking && viewModel.burnInReductionActive.value == false) VISIBLE else INVISIBLE
        }

        viewModel.currentTime.observe(viewLifecycleOwner) {
            bottomLeftView.value = DateUtils.formatElapsedTime((it).toLong())
        }

        viewModel.lastCompleteSplit.observe(viewLifecycleOwner) { split ->
            Log.d(logTag, "Observed last split change")
            middleRightView.value =
                String.format(
                    "%.1f",
                    getUserSpeed(
                        requireContext(),
                        (split.distance / split.duration.coerceAtLeast(0.0001))
                    )
                )
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

        viewModel.cadenceSensor.observe(viewLifecycleOwner) { cadence ->
            Log.d(logTag, "cadence battery: ${cadence.batteryLevel}")
            Log.d(logTag, "cadence: ${cadence.rpm}")
            if (cadence.rpm != null) {
                bottomRightView.label = "RPM"
                bottomRightView.value = when {
                    cadence.rpm.isFinite() && cadence.rpm < 1e3f -> cadence.rpm.toInt().toString()
                    else -> bottomRightView.value
                }
            }
            if (cadence.batteryLevel != null && cadence.batteryLevel < lowBatteryThreshold) bottomRightView.extraInfo =
                "${cadence.batteryLevel}%"
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
                if (viewModel.burnInReductionActive.value != true) {
                    temperatureTextView.visibility = VISIBLE
                    footerRightView.visibility = VISIBLE
                    windDirectionArrow.visibility = VISIBLE
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
                footerRightView.text =
                    String.format(
                        "%.1f %s",
                        getUserSpeed(requireContext(), weather.windSpeed),
                        getUserSpeedUnitShort(requireContext())
                    )
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

    private fun initializeBurnInReduction() {
        viewModel.burnInReductionActive.observe(viewLifecycleOwner) { birEnabled ->
            activity?.window?.let { window ->
                val windowInsetsController =
                    WindowCompat.getInsetsController(window, window.decorView)
                // Configure the behavior of the hidden system bars.
                if (birEnabled) windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
                else windowInsetsController.show(WindowInsetsCompat.Type.statusBars())
            }
            trackingImage.visibility = if (birEnabled) INVISIBLE else VISIBLE
            compassImage.visibility = if (birEnabled) INVISIBLE else VISIBLE
            windIcon.visibility = if (birEnabled) INVISIBLE else VISIBLE
            footerView.visibility = if (birEnabled) INVISIBLE else VISIBLE
            footerRightView.visibility = if (birEnabled) INVISIBLE else VISIBLE
            temperatureTextView.visibility = if (birEnabled) INVISIBLE else VISIBLE
            timeOfDayTextView.visibility = if (birEnabled) INVISIBLE else VISIBLE
            windDirectionArrow.visibility = if (birEnabled) INVISIBLE else VISIBLE
            debugTextView.visibility = if (birEnabled) INVISIBLE else VISIBLE
            topView.enableBurnInReduction(birEnabled)
            topLeftView.enableBurnInReduction(birEnabled)
            topRightView.enableBurnInReduction(birEnabled)
            middleLeftView.enableBurnInReduction(birEnabled)
            middleRightView.enableBurnInReduction(birEnabled)
            bottomLeftView.enableBurnInReduction(birEnabled)
            bottomRightView.enableBurnInReduction(birEnabled)
        }
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
        trackingImage = view.findViewById(R.id.image_tracking)
        debugTextView = view.findViewById(R.id.textview_debug)
        timeOfDayTextView = view.findViewById(R.id.dashboard_textview_timeOfDay)
        temperatureTextView = view.findViewById(R.id.dashboard_textview_temperature)
        windDirectionArrow = view.findViewById(R.id.image_arrow_wind_direction)
        compassImage = view.findViewById(R.id.compass_image)
        windIcon = view.findViewById(R.id.image_wind_icon)

        //TODO: Cleanup below
        //This is a lot of implementation specific initialization

        if (FeatureFlags.productionBuild) {
            trackingImage.visibility = GONE
            debugTextView.visibility = GONE
        }

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
        viewModel.currentProgress.value?.slope?.takeIf { it.isFinite() }
            ?.let { s -> debugString += "S%.1f".format(s * 100f) }
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
                    requireActivity().startService(Intent(
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
                    requireActivity().startService(
                        Intent(
                            requireContext(),
                            TripInProgressService::class.java
                        ).apply {
                            this.action = getString(R.string.action_start_trip_service)
                            this.putExtra("tripId", tripId)
                        })

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
        if (viewModel.cadenceSensor.value?.rpm == null) {
            timeOfDayTextView.visibility = INVISIBLE
            bottomRightView.value = time
            bottomRightView.label = amPm
        } else {
            timeOfDayTextView.apply {
                visibility =
                    if (viewModel.burnInReductionActive.value == true) INVISIBLE else VISIBLE
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
            requireContext().startService(Intent(
                requireContext(),
                TripInProgressService::class.java
            ).apply {
                this.action = getString(R.string.action_shutdown_trip_service)
            })
        FirebaseAnalytics.getInstance(requireContext()).logEvent("LeaveDashboard") {}
    }

    private var swipeRect: Rect = Rect()
    private var pauseButtonVisible = false
    private var burnInReductionPriorState = false
    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        Log.v(logTag, event.toString())
        Log.v(logTag, "viewModel.currentState = ${viewModel.currentState}")

        if (viewModel.currentState == TimeStateEnum.STOP) return false
        return when (event?.action) {
            MotionEvent.ACTION_UP -> if (viewModel.currentState == TimeStateEnum.START || viewModel.currentState == TimeStateEnum.RESUME) {
                handlePauseButtonVisibility()
                if (viewModel.burnInReductionEnabled()) handleBurnInReductionState()
                v?.performClick()
                true
            } else false

            MotionEvent.ACTION_DOWN -> {
                pauseButtonVisible = pauseButton.translationY == 0f
                if (viewModel.burnInReductionEnabled()) {
                    burnInReductionPriorState = viewModel.burnInReductionActive.value ?: false
                    viewModel.burnInReductionActive.value = false
                }
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
        viewModel.burnInReductionActive.value = viewModel.burnInReductionEnabled()
    }
    private val hidePauseHandler = android.os.Handler(Looper.getMainLooper())
    private val hidePauseCallback = Runnable {
        slidePauseDown()
    }

    private fun isPauseButtonHidden() = pauseButton.translationY > pauseButton.height / 2f

    private fun handleBurnInReductionState(): Boolean {
        Log.d(logTag, "handleBurnInReductionState")

        viewModel.burnInReductionActive.value =
            !burnInReductionPriorState && isPauseButtonHidden()

        burnInReductionHandler.removeCallbacks(burnInReductionCallback)
        if (viewModel.burnInReductionActive.value != viewModel.burnInReductionEnabled()) {
            burnInReductionHandler.postDelayed(
                burnInReductionCallback, 5000
            )
        }
        return true
    }

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
