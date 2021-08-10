package com.kvl.cyclotrack

import android.app.ActivityManager
import android.content.*
import android.os.Bundle
import android.os.Looper
import android.text.format.DateUtils
import android.util.Log
import android.view.*
import android.view.View.OnClickListener
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import androidx.preference.PreferenceManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.kvl.cyclotrack.events.StartTripEvent
import com.kvl.cyclotrack.events.WheelCircumferenceEvent
import dagger.hilt.android.AndroidEntryPoint
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*


@AndroidEntryPoint
class TripInProgressFragment :
    Fragment(), View.OnTouchListener {
    val logTag = "TripInProgressFragment"
    private val viewModel: TripInProgressViewModel by navGraphViewModels(R.id.dashboard_nav_graph) {
        defaultViewModelProviderFactory
    }
    private lateinit var pauseButton: Button
    private lateinit var stopButton: Button
    private lateinit var resumeButton: Button
    private lateinit var clockView: MeasurementView
    private var gpsEnabled = true
    private var isTimeTickRegistered = false
    private val lowBatteryThreshold = 15
    private lateinit var sharedPreferences: SharedPreferences
    private val userCircumference: Float?
        get() = getUserCircumferenceOrNull(sharedPreferences)
    private var autoCircumference: Float? = null

    val circumference: Float?
        get() = when (sharedPreferences.getBoolean(requireContext().applicationContext.getString(
            R.string.preference_key_useAutoCircumference), true)) {
            true -> autoCircumference ?: userCircumference
            else -> userCircumference ?: autoCircumference
        }

    @Subscribe
    fun onWheelCircumferenceEvent(event: WheelCircumferenceEvent) {
        autoCircumference = event.circumference
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
        setHasOptionsMenu(true)

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

        return inflater.inflate(R.layout.trip_in_progress_fragment, container, false)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        if (FeatureFlags.devBuild) {
            menu.add(0, R.id.menu_item_show_details, 0, "Debug")
        }
        super.onCreateOptionsMenu(menu, inflater)
    }

    private var navigateToDebugView = navigateToDebugViewBuilder(-1L)
    private fun navigateToDebugViewBuilder(tripId: Long): () -> Unit = {
        findNavController().navigate(R.id.action_to_debug_view,
            Bundle().apply {
                Log.d(logTag, "Start dashboard with trip ${tripId}")
                putLong("tripId", tripId)
            })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_item_show_details -> navigateToDebugView()
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun hidePause() {
        pauseButton.translationY = pauseButton.height.toFloat()
    }

    private fun hideResume() {
        resumeButton.translationX = resumeButton.width.toFloat()
        //resumeButton.translationX = 10000f
    }

    private fun hideStop() {
        stopButton.translationX = -stopButton.width.toFloat()
    }

    private fun hideResumeStop() {
        hideResume()
        hideStop()
    }

    private fun slideResumeOut() {
        resumeButton.animate().setDuration(100).translationX(resumeButton.width.toFloat())
    }

    private fun slideStopOut() {
        stopButton.animate().setDuration(100).translationX(-stopButton.width.toFloat())
    }

    private fun slideOutResumeStop() {
        slideResumeOut()
        slideStopOut()
    }

    private fun slideInResumeStop() {
        resumeButton.animate().setDuration(100).translationX(0f)
        stopButton.animate().setDuration(100).translationX(0f)
    }

    fun turnOnGps() {
        val locationRequest = LocationRequest.create().apply {
            interval = 1000
            fastestInterval = 100
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
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
                    exception.startResolutionForResult(requireActivity(),
                        1000)
                } catch (sendEx: IntentSender.SendIntentException) {
                    // Ignore the error.
                }
            }
        }
    }

    private fun startTrip() {
        Log.d(logTag, "${host}")
        requireContext().startService(Intent(requireContext(),
            TripInProgressService::class.java).apply {
            this.action = getString(R.string.action_start_trip_service)
        })
    }

    private fun pauseTrip(tripId: Long) {
        requireActivity().startService(Intent(requireContext(),
            TripInProgressService::class.java).apply {
            this.action = getString(R.string.action_pause_trip_service)
            this.putExtra("tripId", tripId)
        })
    }

    private fun resumeTrip(tripId: Long) {
        requireActivity().startService(Intent(requireContext(),
            TripInProgressService::class.java).apply {
            this.action = getString(R.string.action_resume_trip_service)
            this.putExtra("tripId", tripId)
        })
    }

    private fun endTrip(tripId: Long) {
        requireActivity().startService(Intent(requireContext(),
            TripInProgressService::class.java).apply {
            this.action = getString(R.string.action_stop_trip_service)
            this.putExtra("tripId", tripId)
        })
    }

    private fun handleTimeStateChanges(tripId: Long) =
        viewModel.currentTimeState(tripId).observe(viewLifecycleOwner, { currentState ->
            currentState?.let {
                Log.d(logTag, "Observed currentTimeState change: ${currentState.state}")
                pauseButton.setOnClickListener(pauseTripListener(tripId))
                resumeButton.setOnClickListener(resumeTripListener(tripId))
                stopButton.setOnClickListener(stopTripListener(tripId))
                if (currentState.state == TimeStateEnum.START || currentState.state == TimeStateEnum.RESUME) {
                    view?.doOnPreDraw { hideResumeStop() }
                    view?.doOnPreDraw { hidePause() }
                    pauseButton.text = getString(R.string.pause_label)
                } else if (currentState.state == TimeStateEnum.PAUSE) {
                    view?.doOnPreDraw { hidePause() }
                    slideInResumeStop()
                } else {
                    pauseButton.setOnClickListener(startTripListener)
                    pauseButton.text = getString(R.string.start_label)
                }
            }
        })

    private fun initializeAfterTripCreated(tripId: Long) {
        navigateToDebugView = navigateToDebugViewBuilder(tripId)
        handleTimeStateChanges(tripId)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onStartTripEvent(event: StartTripEvent) {
        event.tripId.takeIf { it >= 0 }?.let { tripId ->
            initializeAfterTripCreated(tripId)
            viewModel.startTrip(tripId, viewLifecycleOwner)
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
        when (tripId >= 0) {
            true -> findNavController()
                .navigate(TripInProgressFragmentDirections.actionFinishTrip(tripId))
            else -> findNavController()
                .navigate(R.id.action_back_to_summaries)
        }
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d(logTag, "TripInProgressFragment::onViewCreated")
        savedInstanceState?.getLong("tripId", -1)
            .takeIf { t -> t != -1L }.let { tripId ->
                viewModel.tripId = tripId
            }

        pauseButton = view.findViewById(R.id.pause_button)
        resumeButton = view.findViewById(R.id.resume_button)
        stopButton = view.findViewById(R.id.stop_button)
        clockView = view.findViewById(R.id.textview_time)

        val speedTextView: MeasurementView = view.findViewById(R.id.textview_speed)
        val distanceTextView: MeasurementView = view.findViewById(R.id.textview_distance)
        val durationTextView: MeasurementView = view.findViewById(R.id.textview_duration)
        val averageSpeedTextView: MeasurementView = view.findViewById(R.id.textview_average_speed)
        val heartRateTextView: MeasurementView = view.findViewById(R.id.textview_heart_rate)
        val splitSpeedTextView: MeasurementView = view.findViewById(R.id.textview_split_speed)
        val bearingTextView: TextView = view.findViewById(R.id.textview_bearing)

        val trackingImage: ImageView = view.findViewById(R.id.image_tracking)
        val accuracyTextView: TextView = view.findViewById(R.id.textview_accuracy)
        if (FeatureFlags.productionBuild) {
            trackingImage.visibility = View.GONE
            accuracyTextView.visibility = View.GONE
        }

        speedTextView.label =
            "SPLIT ${getUserSpeedUnitShort(requireContext()).uppercase(Locale.getDefault())}"
        distanceTextView.label =
            getUserDistanceUnitLong(requireContext()).uppercase(Locale.getDefault())
        durationTextView.label = "DURATION"
        averageSpeedTextView.label = "AVG ${
            getUserSpeedUnitShort(requireContext()).uppercase(
                Locale.getDefault())
        }"
        heartRateTextView.label = "SLOPE"
        splitSpeedTextView.label =
            "GPS ${getUserSpeedUnitShort(requireContext()).uppercase(Locale.getDefault())}"

        fun hasHeartRate() = viewModel.hrmSensor().value?.bpm ?: 0 > 0

        accuracyTextView.text = "-.-"

        updateClock()

        requireContext().registerReceiver(timeTickReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
        isTimeTickRegistered = true

        view.setOnTouchListener(this)

        viewModel.gpsStatus().observe(viewLifecycleOwner, { location ->
            if (viewModel.speedSensor().value?.rpm == null || circumference == null) {
                when (location.speed < 0.5) {
                    true -> 0.0
                    else ->
                        (splitSpeedTextView.value.toString().toDoubleOrNull()
                            ?: getUserSpeed(requireContext(), location.speed.toDouble()).toDouble())
                }
                    .takeIf { it.isFinite() }
                    ?.let { oldSpeed ->
                        exponentialSmoothing(0.1,
                            getUserSpeed(requireContext(),
                                location.speed.toDouble()).toDouble(),
                            oldSpeed)
                    }.let { speed ->
                        splitSpeedTextView.value =
                            String.format("%.1f", speed)
                    }
            }
        })

        viewModel.currentProgress.observe(viewLifecycleOwner, {
            Log.d(logTag, "Location observer detected change")
            bearingTextView.text = degreesToCardinal(it.bearing)

            getUserSpeed(requireContext(), it.distance / it.duration).let { averageSpeed ->
                averageSpeedTextView.value =
                    String.format("%.1f", if (averageSpeed.isFinite()) averageSpeed else 0f)
            }

            distanceTextView.value =
                String.format("%.2f", getUserDistance(requireContext(), it.distance))

            if (!hasHeartRate()) heartRateTextView.value =
                String.format("%.3f", if (it.slope.isFinite()) it.slope else 0f)

            trackingImage.visibility = if (it.tracking) View.VISIBLE else View.INVISIBLE

            accuracyTextView.text = when (circumference == null) {
                true -> String.format("%.2f / %d°", it.accuracy, it.bearing.toInt())
                else -> String.format("%.2f / C%.3f / %d°",
                    it.accuracy,
                    circumference,
                    it.bearing.toInt())
            }
        })
        /*viewModel.getSensorData().observe(this, object : Observer<SensorModel> {
            override fun onChanged(it: SensorModel) {
                Log.d(TAG, "Sensor observer detected change")
                //heartRateTextView.text = String.format("%.1f", it.tilt?.get(0))
            }
        })*/
        viewModel.currentTime.observe(viewLifecycleOwner, {
            durationTextView.value = DateUtils.formatElapsedTime((it).toLong())
        })
        viewModel.lastSplit.observe(viewLifecycleOwner, {
            Log.d(logTag, "Observed last split change")
            speedTextView.value =
                String.format("%.1f",
                    getUserSpeed(requireContext(),
                        (it.distance / it.duration.coerceAtLeast(0.0001))))
        })

        viewModel.gpsEnabled().observe(viewLifecycleOwner, { status ->
            if (!status && gpsEnabled) {
                gpsEnabled = false
                trackingImage.visibility = View.INVISIBLE
                accuracyTextView.text = getString(R.string.gps_disabled_message)
                Log.d(logTag, "Location service access disabled")
                activity?.let {
                    val builder = AlertDialog.Builder(it)
                    builder.apply {
                        setPositiveButton("ENABLE"
                        ) { _, _ ->
                            Log.d("TIP_GPS_DISABLED", "CLICKED ENABLE")
                            turnOnGps()
                        }
                        setNegativeButton("CANCEL"
                        ) { _, _ ->
                            Log.d("TIP_GPS_DISABLED", "CLICKED CANCEL")
                            findNavController().navigate(R.id.action_finish_trip)
                        }
                        setTitle("Enable location service")
                        setMessage("Location service has been disabled. Please enable location services before starting your ride.")
                    }

                    builder.create()
                }?.show()
            } else {
                if (status) {
                    gpsEnabled = true
                    accuracyTextView.text = ""
                }
            }
        })

        viewModel.hrmSensor().observe(viewLifecycleOwner, {
            Log.d(logTag, "hrm battery: ${it.batteryLevel}")
            Log.d(logTag, "hrm bpm: ${it.bpm}")
            if (it.bpm != null) {
                heartRateTextView.label = "BPM"
                heartRateTextView.value = it.bpm.toString()
            }
            if (it.batteryLevel != null && it.batteryLevel!! < lowBatteryThreshold) heartRateTextView.extraInfo =
                "${it.batteryLevel}%"
        })
        viewModel.cadenceSensor().observe(viewLifecycleOwner, {
            Log.d(logTag, "cadence battery: ${it.batteryLevel}")
            Log.d(logTag, "cadence: ${it.rpm}")
            if (it.rpm != null) {
                clockView.label = "RPM"
                clockView.value = when {
                    it.rpm.isFinite() && it.rpm < 1e3f -> it.rpm.toInt().toString()
                    else -> clockView.value
                }
            }
            if (it.batteryLevel != null && it.batteryLevel < lowBatteryThreshold) clockView.extraInfo =
                "${it.batteryLevel}%"
        })
        viewModel.speedSensor().observe(viewLifecycleOwner, {
            Log.d(logTag, "speed battery: ${it.batteryLevel}")
            Log.d(logTag, "speed rpm: ${it.rpm}")
            if (it.rpm != null && circumference != null) {
                splitSpeedTextView.label =
                    getUserSpeedUnitShort(requireContext()).uppercase(Locale.getDefault())
                splitSpeedTextView.value = when {
                    it.rpm.isFinite() && it.rpm < 1e4f -> String.format("%.1f",
                        getUserSpeed(requireContext(),
                            it.rpm / 60 * circumference!!))
                    else -> splitSpeedTextView.value
                }
            }
            if (it.batteryLevel != null && it.batteryLevel < lowBatteryThreshold) splitSpeedTextView.extraInfo =
                "${it.batteryLevel}%"
        })
    }

    private fun isMyServiceRunning(serviceClass: Class<*>, context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                Log.i("Service already", "running")
                return true
            }
        }
        Log.i("Service not", "running")
        return false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        viewModel.tripId?.let { outState.putLong("tripId", it) }
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        Log.d(logTag, "Called onResume: currentState = ${viewModel.currentState}")

        view?.doOnPreDraw { hideResumeStop() }
        pauseButton.setOnClickListener(startTripListener)
        pauseButton.text = getString(R.string.start_label)

        arguments?.getLong("tripId", -1)
            .takeIf { tripId -> tripId != -1L }?.let { tripId ->
                viewModel.tripId = tripId
            }

        when (val tripId = viewModel.tripId) {
            null -> requireActivity().startService(Intent(requireContext(),
                TripInProgressService::class.java).apply {
                this.action = getString(R.string.action_initialize_trip_service)
            })
            else -> {
                Log.d(logTag, "Received trip ID argument $tripId")
                Log.d(logTag, "Resuming trip $tripId")
                initializeAfterTripCreated(tripId)
                viewModel.resumeTrip(tripId, viewLifecycleOwner)
                if (!isMyServiceRunning(TripInProgressService::class.java, requireContext())) {
                    requireActivity().startService(Intent(requireContext(),
                        TripInProgressService::class.java).apply {
                        this.action = getString(R.string.action_start_trip_service)
                        this.putExtra("tripId", tripId)
                    })
                } else {
                    requireActivity().startService(Intent(requireContext(),
                        TripInProgressService::class.java).apply {
                        this.action = getString(R.string.action_initialize_trip_service)
                    })
                }
            }
        }
    }

    private fun updateClock() {
        var hour = Calendar.getInstance().get(Calendar.HOUR)
        if (hour == 0) hour = 12
        val time = String.format("%d:%02d",
            hour,
            Calendar.getInstance().get(Calendar.MINUTE))
        val amPm = if (Calendar.getInstance().get(Calendar.AM_PM) == 0) "AM" else "PM"
        if (viewModel.cadenceSensor().value?.rpm == null) {
            clockView.value = time
            clockView.label = amPm
        } else {
            activity?.title = "$time $amPm"
        }
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        WorkManager.getInstance(requireContext())
            .enqueue(OneTimeWorkRequestBuilder<StopTripServiceWorker>().build())
        EventBus.getDefault().unregister(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(logTag, "onDestroyView")
        if (isTimeTickRegistered) context?.unregisterReceiver(timeTickReceiver)
    }

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        Log.v("TIP_FRAG", event.toString())
        Log.v("TIP_FRAG", "current state = ${viewModel.currentState}")

        return when (event?.action) {
            MotionEvent.ACTION_UP -> if (viewModel.currentState == TimeStateEnum.START || viewModel.currentState == TimeStateEnum.RESUME) {
                handleScreenTouchClick()
                v?.performClick()
                true
            } else false
            MotionEvent.ACTION_DOWN -> true
            else -> false
        }
    }

    private val hidePauseHandler = android.os.Handler(Looper.getMainLooper())
    private val hidePauseCallback = Runnable {
        pauseButton.animate().setDuration(100).translationY(pauseButton.height.toFloat())
    }

    private fun isPauseButtonHidden() = pauseButton.translationY != 0f

    private fun handleScreenTouchClick(): Boolean {
        Log.d("TIP_FRAG", "handleTouchClick")
        if (isPauseButtonHidden()) {
            pauseButton.animate().setDuration(100).translationY(0f)
            hidePauseHandler.postDelayed(
                hidePauseCallback, 5000
            )
        } else {
            hidePauseHandler.removeCallbacks(hidePauseCallback)
            hidePauseCallback.run()
        }
        return true
    }
}