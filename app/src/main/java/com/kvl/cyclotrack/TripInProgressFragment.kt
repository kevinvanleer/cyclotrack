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
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import dagger.hilt.android.AndroidEntryPoint
import java.util.*

fun bleFeatureFlag() = true

@AndroidEntryPoint
class TripInProgressFragment : Fragment(), View.OnTouchListener {
    private val viewModel: TripInProgressViewModel by navGraphViewModels(R.id.trip_in_progress_graph) {
        defaultViewModelProviderFactory
    }
    private lateinit var pauseButton: Button
    private lateinit var stopButton: Button
    private lateinit var resumeButton: Button
    private lateinit var clockView: MeasurementView
    private var gpsEnabled = true
    private var isTimeTickRegistered = false

    private val timeTickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.v("TIP_FRAGMENT", "Received time tick")
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
        return inflater.inflate(R.layout.trip_in_progress_fragment, container, false)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.add(0, R.id.menu_item_show_details, 0, "Details")
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

    private val startTripListener: OnClickListener = OnClickListener {
        if (!gpsEnabled) {
            turnOnGps()
        } else {
            viewModel.startTrip(viewLifecycleOwner)
            hidePause()
            pauseButton.setOnClickListener(null)
            pauseButton.text = "PAUSE"
            pauseButton.setOnClickListener(pauseTripListener)
        }
    }

    private val pauseTripListener: OnClickListener = OnClickListener {
        viewModel.pauseTrip()
        hidePause()
        slideInResumeStop()
        resumeButton.setOnClickListener {
            viewModel.resumeTrip()
            slideOutResumeStop()
        }
        stopButton.setOnClickListener {
            viewModel.endTrip()
            findNavController().navigate(R.id.action_finish_trip)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bleFeatureFlag()) viewModel.stopBle()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d("UI", "TripInProgressFragment::onViewCreated")

        viewModel.startGps()
        if (bleFeatureFlag()) viewModel.startBle()

        val speedTextView: MeasurementView = view.findViewById(R.id.textview_speed)
        val distanceTextView: MeasurementView = view.findViewById(R.id.textview_distance)
        val durationTextView: MeasurementView = view.findViewById(R.id.textview_duration)
        val averageSpeedTextView: MeasurementView = view.findViewById(R.id.textview_average_speed)
        val heartRateTextView: MeasurementView = view.findViewById(R.id.textview_heart_rate)
        val splitSpeedTextView: MeasurementView = view.findViewById(R.id.textview_split_speed)

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
        view.doOnPreDraw { hideResumeStop() }

        if (viewModel.currentState == TimeStateEnum.START || viewModel.currentState == TimeStateEnum.RESUME) {
            hidePause()
            pauseButton.text = "PAUSE"
            pauseButton.setOnClickListener(pauseTripListener)
        } else if (viewModel.currentState == TimeStateEnum.PAUSE) {
            hidePause()
            resumeButton.setOnClickListener {
                viewModel.resumeTrip()
                slideOutResumeStop()
            }
            stopButton.setOnClickListener {
                viewModel.endTrip()
                findNavController().navigate(R.id.action_finish_trip)
            }
        } else {
            pauseButton.setOnClickListener(startTripListener)
            pauseButton.text = "START"
        }

        Log.d("TIP", "view created")
        viewModel.currentProgress.observe(viewLifecycleOwner, {
            Log.d("UI", "Location observer detected change")
            val averageSpeed = getUserSpeed(requireContext(), it.distance / it.duration)
            if (viewModel.speedSensor.value?.rpm == null || viewModel.circumference == null) {
                splitSpeedTextView.value =
                    String.format("%.1f", getUserSpeed(requireContext(), it.speed.toDouble()))
            }
            averageSpeedTextView.value =
                String.format("%.1f", if (averageSpeed.isFinite()) averageSpeed else 0f)

            distanceTextView.value =
                String.format("%.2f", getUserDistance(requireContext(), it.distance))

            if (!hasHeartRate()) heartRateTextView.value =
                String.format("%.3f", if (it.slope.isFinite()) it.slope else 0f)

            trackingImage.visibility = if (it.tracking) View.VISIBLE else View.INVISIBLE

            accuracyTextView.text = when (viewModel.autoCircumference == null) {
                true -> String.format("%.2f", it.accuracy)
                else -> String.format("%.2f / C%.3f", it.accuracy, viewModel.autoCircumference)
            }
            speedTextView.value =
                String.format("%.1f", getUserSpeed(requireContext(), it.splitSpeed.toDouble()))
        })
        /*viewModel.getSensorData().observe(this, object : Observer<SensorModel> {
            override fun onChanged(it: SensorModel) {
                Log.d("UI", "Sensor observer detected change")
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
                Log.d("TIP_FRAGMENT", "Location service access disabled")
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
                Log.d("TIP_FRAGMENT", "hrm battery: ${it.batteryLevel}")
                Log.d("TIP_FRAGMENT", "hrm bpm: ${it.bpm}")
                if (it.bpm != null) {
                    heartRateTextView.label = "BPM"
                    heartRateTextView.value = it.bpm.toString()
                }
            })
            viewModel.cadenceSensor.observe(viewLifecycleOwner, {
                Log.d("TIP_FRAGMENT", "cadence battery: ${it.batteryLevel}")
                Log.d("TIP_FRAGMENT", "cadence: ${it.rpm}")
                if (it.rpm != null) {
                    if (isTimeTickRegistered) {
                        isTimeTickRegistered = false
                        context?.unregisterReceiver(timeTickReceiver)
                        clockView.label = "RPM"
                    }
                    clockView.value = it.rpm.toInt().toString()
                }
            })
            viewModel.speedSensor.observe(viewLifecycleOwner, {
                Log.d("TIP_FRAGMENT", "speed battery: ${it.batteryLevel}")
                Log.d("TIP_FRAGMENT", "speed rpm: ${it.rpm}")
                if (it.rpm != null && viewModel.circumference != null) {
                    splitSpeedTextView.label =
                        "${getUserSpeedUnitShort(requireContext()).toUpperCase(Locale.getDefault())}"
                    splitSpeedTextView.value = when {
                        it.rpm.isNaN() -> "0.0"
                        else -> String.format("%.1f",
                            getUserSpeed(requireContext(),
                                it.rpm / 60 * viewModel.circumference!!))
                    }
                }
            })
        }
    }

    private fun updateClock() {
        var hour = Calendar.getInstance().get(Calendar.HOUR)
        if (hour == 0) hour = 12
        clockView.value = String.format("%d:%02d",
            hour,
            Calendar.getInstance().get(Calendar.MINUTE))
        clockView.label = if (Calendar.getInstance().get(Calendar.AM_PM) == 0) "AM" else "PM"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (isTimeTickRegistered) context?.unregisterReceiver(timeTickReceiver)
    }

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        Log.d("TIP_FRAG", event.toString())
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