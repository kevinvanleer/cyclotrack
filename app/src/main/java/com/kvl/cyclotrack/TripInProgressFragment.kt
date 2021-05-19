package com.kvl.cyclotrack

import android.content.*
import android.os.Bundle
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
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import dagger.hilt.android.AndroidEntryPoint
import java.util.*
import javax.inject.Inject

fun bleFeatureFlag() = true

@AndroidEntryPoint
class TripInProgressFragment :
    Fragment(), View.OnTouchListener {
    val logTag = "DASHBOARD_FRAGMENT"
    private val viewModel: TripInProgressViewModel by navGraphViewModels(R.id.trip_in_progress_graph) {
        defaultViewModelProviderFactory
    }
    private lateinit var pauseButton: Button
    private lateinit var stopButton: Button
    private lateinit var resumeButton: Button
    private lateinit var clockView: MeasurementView
    private var gpsEnabled = true
    private var isTimeTickRegistered = false
    private val lowBatteryThreshold = 15

    @Inject
    lateinit var tipService: TripInProgressService

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
        if (BuildConfig.BUILD_TYPE == "dev") {
            menu.add(0, R.id.menu_item_show_details, 0, "Debug")
        }
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_item_show_details -> findNavController().navigate(R.id.action_to_debug_view)
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
        val locationRequest = LocationRequest.create()?.apply {
            interval = 1000
            fastestInterval = 100
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
        val builder =
            locationRequest?.let { LocationSettingsRequest.Builder().addLocationRequest(it) }
        val client = LocationServices.getSettingsClient(requireActivity())
        val task = client.checkLocationSettings(builder?.build())
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

    private fun startTrip(tripId: Long) {
        requireActivity().startService(Intent(requireContext(),
            TripInProgressService::class.java).apply {
            this.action = getString(R.string.action_start_trip_service)
            this.putExtra("tripId", tripId)
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
        viewModel.currentTimeState(tripId)?.observe(viewLifecycleOwner, { currentState ->
            Log.d(logTag, "Observed currentTimeState change: ${currentState.state}")
            if (currentState.state == TimeStateEnum.START || currentState.state == TimeStateEnum.RESUME) {
                view?.doOnPreDraw { hideResumeStop() }
                view?.doOnPreDraw { hidePause() }
                pauseButton.text = "PAUSE"
            } else if (currentState.state == TimeStateEnum.PAUSE) {
                view?.doOnPreDraw { hidePause() }
                slideInResumeStop()
            } else {
                pauseButton.setOnClickListener(startTripListener)
                pauseButton.text = "START"
            }
        })

    private val startTripListener: OnClickListener = OnClickListener {
        if (!gpsEnabled) {
            turnOnGps()
        } else {
            viewModel.startTrip(viewLifecycleOwner).let { liveData ->
                liveData.observeForever(object : Observer<Long> {
                    override fun onChanged(tripId: Long) {
                        startTrip(tripId)
                        handleTimeStateChanges(tripId)
                        liveData.removeObserver(this)
                    }
                })
            }
            hidePause()
            pauseButton.setOnClickListener(null)
            pauseButton.text = "PAUSE"
            pauseButton.setOnClickListener(pauseTripListener)
        }
    }

    private val pauseTripListener: OnClickListener = OnClickListener {
        pauseTrip(viewModel.tripId!!)
        hidePause()
        slideInResumeStop()
    }

    private val resumeTripListener: OnClickListener = OnClickListener {
        resumeTrip(viewModel.tripId!!)
        slideOutResumeStop()
    }
    private val stopTripListener: OnClickListener = OnClickListener {
        endTrip(viewModel.tripId!!)
        findNavController()
            .navigate(TripInProgressFragmentDirections.actionFinishTrip(viewModel.tripId!!))
    }

    override fun onDestroy() {
        Log.d(logTag, "Destroying TIP View")
        super.onDestroy()
        if (bleFeatureFlag()) tipService.stopBle()
        activity?.window?.apply {
            val params = attributes
            params.screenBrightness = -1f
            attributes = params
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d(logTag, "TripInProgressFragment::onViewCreated")

        arguments?.getLong("tripId", -1L)?.takeIf { it != -1L }?.let { tripId ->
            Log.d(logTag, "Received trip ID argument $tripId")
            Log.d(logTag, "Resuming trip $tripId")
            handleTimeStateChanges(tripId)
            viewModel.resumeTrip(tripId, viewLifecycleOwner)
            tipService.tripId = tripId
        }
        if (tipService.tripId == null) {
            //TODO: THIS SHOULD MOVE TO TripInProgressService::start
            Log.d(logTag, "Starting new trip")
            tipService.startGps()
            if (bleFeatureFlag()) tipService.startBle()
        }

        val speedTextView: MeasurementView = view.findViewById(R.id.textview_speed)
        val distanceTextView: MeasurementView = view.findViewById(R.id.textview_distance)
        val durationTextView: MeasurementView = view.findViewById(R.id.textview_duration)
        val averageSpeedTextView: MeasurementView = view.findViewById(R.id.textview_average_speed)
        val heartRateTextView: MeasurementView = view.findViewById(R.id.textview_heart_rate)
        val splitSpeedTextView: MeasurementView = view.findViewById(R.id.textview_split_speed)
        val bearingTextView: TextView = view.findViewById(R.id.textview_bearing)

        val trackingImage: ImageView = view.findViewById(R.id.image_tracking)
        val accuracyTextView: TextView = view.findViewById(R.id.textview_accuracy)
        if (BuildConfig.BUILD_TYPE == "prod") {
            trackingImage.visibility = View.GONE
            accuracyTextView.visibility = View.GONE
        }

        pauseButton = view.findViewById(R.id.pause_button)
        resumeButton = view.findViewById(R.id.resume_button)
        stopButton = view.findViewById(R.id.stop_button)
        clockView = view.findViewById(R.id.textview_time)

        pauseButton.setOnClickListener(pauseTripListener)
        resumeButton.setOnClickListener(resumeTripListener)
        stopButton.setOnClickListener(stopTripListener)

        speedTextView.label =
            "SPLIT ${getUserSpeedUnitShort(requireContext()).toUpperCase(Locale.getDefault())}"
        distanceTextView.label =
            getUserDistanceUnitLong(requireContext()).toUpperCase(Locale.getDefault())
        durationTextView.label = "DURATION"
        averageSpeedTextView.label = "AVG ${
            getUserSpeedUnitShort(requireContext()).toUpperCase(
                Locale.getDefault())
        }"
        heartRateTextView.label = "SLOPE"
        splitSpeedTextView.label =
            "GPS ${getUserSpeedUnitShort(requireContext()).toUpperCase(Locale.getDefault())}"

        fun hasHeartRate() = viewModel.hrmSensor.value?.bpm ?: 0 > 0

        accuracyTextView.text = "-.-"

        updateClock()

        context?.registerReceiver(timeTickReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
        isTimeTickRegistered = true

        view.setOnTouchListener(this)
        Log.d(logTag, "view created")
        viewModel.currentProgress.observe(viewLifecycleOwner, {
            Log.d(logTag, "Location observer detected change")
            val averageSpeed = getUserSpeed(requireContext(), it.distance / it.duration)
            if (viewModel.speedSensor.value?.rpm == null || viewModel.circumference == null) {
                splitSpeedTextView.value =
                    String.format("%.1f", getUserSpeed(requireContext(), it.speed.toDouble()))
            }
            bearingTextView.text = degreesToCardinal(it.bearing)
            averageSpeedTextView.value =
                String.format("%.1f", if (averageSpeed.isFinite()) averageSpeed else 0f)

            distanceTextView.value =
                String.format("%.2f", getUserDistance(requireContext(), it.distance))

            if (!hasHeartRate()) heartRateTextView.value =
                String.format("%.3f", if (it.slope.isFinite()) it.slope else 0f)

            trackingImage.visibility = if (it.tracking) View.VISIBLE else View.INVISIBLE

            accuracyTextView.text = when (viewModel.autoCircumference == null) {
                true -> String.format("%.2f / %d°", it.accuracy, it.bearing.toInt())
                else -> String.format("%.2f / C%.3f / %d°",
                    it.accuracy,
                    viewModel.autoCircumference,
                    it.bearing.toInt())
            }
            speedTextView.value =
                String.format("%.1f", getUserSpeed(requireContext(), it.splitSpeed.toDouble()))
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

        viewModel.gpsEnabled.observe(viewLifecycleOwner, { status ->
            if (!status && gpsEnabled) {
                gpsEnabled = false
                trackingImage.visibility = View.INVISIBLE
                accuracyTextView.text = "GPS disabled"
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
        if (bleFeatureFlag()) {
            viewModel.hrmSensor.observe(viewLifecycleOwner, {
                Log.d(logTag, "hrm battery: ${it.batteryLevel}")
                Log.d(logTag, "hrm bpm: ${it.bpm}")
                if (it.bpm != null) {
                    heartRateTextView.label = "BPM"
                    heartRateTextView.value = it.bpm.toString()
                }
                if (it.batteryLevel != null && it.batteryLevel!! < lowBatteryThreshold) heartRateTextView.extraInfo =
                    "${it.batteryLevel}%"
            })
            viewModel.cadenceSensor.observe(viewLifecycleOwner, {
                Log.d(logTag, "cadence battery: ${it.batteryLevel}")
                Log.d(logTag, "cadence: ${it.rpm}")
                if (it.rpm != null) {
                    clockView.label = "RPM"
                    clockView.value = when {
                        it.rpm.isFinite() -> it.rpm.toInt().toString()
                        else -> "0"
                    }
                }
                if (it.batteryLevel != null && it.batteryLevel < lowBatteryThreshold) clockView.extraInfo =
                    "${it.batteryLevel}%"
            })
            viewModel.speedSensor.observe(viewLifecycleOwner, {
                Log.d(logTag, "speed battery: ${it.batteryLevel}")
                Log.d(logTag, "speed rpm: ${it.rpm}")
                if (it.rpm != null && viewModel.circumference != null) {
                    splitSpeedTextView.label =
                        "${getUserSpeedUnitShort(requireContext()).toUpperCase(Locale.getDefault())}"
                    splitSpeedTextView.value = when {
                        it.rpm.isFinite() -> String.format("%.1f",
                            getUserSpeed(requireContext(),
                                it.rpm / 60 * viewModel.circumference!!))
                        else -> "0.0"
                    }
                }
                if (it.batteryLevel != null && it.batteryLevel < lowBatteryThreshold) splitSpeedTextView.extraInfo =
                    "${it.batteryLevel}%"
            })
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.startObserving(viewLifecycleOwner)
        Log.d(logTag, "Called onResume: currentState = ${viewModel.currentState}")

        if (viewModel.tripId == null) {
            Log.d(logTag, "onResume: Trip null")
            view?.doOnPreDraw { hideResumeStop() }
            pauseButton.setOnClickListener(startTripListener)
            pauseButton.text = "START"
        } else {
            Log.d(logTag, "onResume: use observer")
            handleTimeStateChanges(viewModel.tripId!!)
        }
    }

    private fun updateClock() {
        var hour = Calendar.getInstance().get(Calendar.HOUR)
        if (hour == 0) hour = 12
        val time = String.format("%d:%02d",
            hour,
            Calendar.getInstance().get(Calendar.MINUTE))
        val amPm = if (Calendar.getInstance().get(Calendar.AM_PM) == 0) "AM" else "PM"
        if (viewModel.cadenceSensor.value?.rpm == null) {
            clockView.value = time
            clockView.label = amPm
        } else {
            activity?.title = "$time $amPm"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (isTimeTickRegistered) context?.unregisterReceiver(timeTickReceiver)
    }

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        Log.d("TIP_FRAG", event.toString())
        Log.d("TIP_FRAG", "current state = ${viewModel.currentState}")

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

    private val hidePauseHandler = android.os.Handler()
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