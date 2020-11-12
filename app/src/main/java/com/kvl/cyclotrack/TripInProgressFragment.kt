package com.kvl.cyclotrack

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.text.format.DateUtils
import android.util.Log
import android.view.*
import android.view.View.OnClickListener
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import dagger.hilt.android.AndroidEntryPoint
import java.util.*

@AndroidEntryPoint
class TripInProgressFragment : Fragment(), View.OnTouchListener {
    private val viewModel: TripInProgressViewModel by navGraphViewModels(R.id.trip_in_progress_graph) {
        defaultViewModelProviderFactory
    }
    private lateinit var pauseButton: Button
    private lateinit var stopButton: Button
    private lateinit var resumeButton: Button
    private lateinit var clockView: MeasurementView

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
        //stopButton.translationX = -10000f
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

    private val startTripListener: OnClickListener = OnClickListener {
        viewModel.startTrip()
        hidePause()
        pauseButton.setOnClickListener(null)
        pauseButton.text = "PAUSE"
        pauseButton.setOnClickListener(pauseTripListener)
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d("UI", "TripInProgressFragment::onViewCreated")

        viewModel.startGps()

        val speedTextView: MeasurementView = view.findViewById(R.id.textview_speed)
        val distanceTextView: MeasurementView = view.findViewById(R.id.textview_distance)
        val durationTextView: MeasurementView = view.findViewById(R.id.textview_duration)
        val averageSpeedTextView: MeasurementView = view.findViewById(R.id.textview_average_speed)
        val heartRateTextView: MeasurementView = view.findViewById(R.id.textview_heart_rate)
        val splitSpeedTextView: MeasurementView = view.findViewById(R.id.textview_split_speed)

        val trackingImage: ImageView = view.findViewById(R.id.image_tracking)
        val accuracyTextView: TextView = view.findViewById(R.id.textview_accuracy)
        pauseButton = view.findViewById(R.id.pause_button)
        resumeButton = view.findViewById(R.id.resume_button)
        stopButton = view.findViewById(R.id.stop_button)
        clockView = view.findViewById(R.id.textview_time)

        speedTextView.label = "SPLIT MPH"
        distanceTextView.label = "MILES"
        durationTextView.label = "DURATION"
        averageSpeedTextView.label = "AVG MPH"
        heartRateTextView.label = "SLOPE"
        splitSpeedTextView.label = "MPH"

        accuracyTextView.text = "-.-"

        updateClock()

        context?.registerReceiver(timeTickReceiver, IntentFilter(Intent.ACTION_TIME_TICK))

        view.setOnTouchListener(this)
        view.doOnPreDraw { hideResumeStop()}

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

        //viewModel.startTrip()
        Log.d("TIP", "view created")
        viewModel.currentProgress.observe(viewLifecycleOwner,
            { it ->
                Log.d("UI", "Location observer detected change")
                val averageSpeed = it.distance / it.duration * 2.23694
                splitSpeedTextView.value = String.format("%.1f", it.speed * 2.23694)
                averageSpeedTextView.value =
                    String.format("%.1f", if (averageSpeed.isFinite()) averageSpeed else 0f)

                distanceTextView.value = "${String.format("%.2f", it.distance * 0.000621371)}"
                //durationTextView.text = DateUtils.formatElapsedTime((it.duration).toLong())
                heartRateTextView.value =
                    String.format("%.3f", if (it.slope.isFinite()) it.slope else 0f)

                trackingImage.visibility = if (it.tracking) View.VISIBLE else View.INVISIBLE
                accuracyTextView.text = String.format("%.2f", it.accuracy)
                speedTextView.value = String.format("%.1f", it.splitSpeed * 2.23694)
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
    }

    private fun formatWallTime(): String {
        return String.format("%d:%02d %s",
            Calendar.getInstance().get(Calendar.HOUR),
            Calendar.getInstance().get(Calendar.MINUTE),
            if (Calendar.getInstance().get(Calendar.AM_PM) == 0) "am" else "pm")
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
        context?.unregisterReceiver(timeTickReceiver)
    }

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        Log.d("TIP_FRAG", "TOUCH")
        return if (viewModel.currentState == TimeStateEnum.START || viewModel.currentState == TimeStateEnum.RESUME) performClick() else false
    }

    private fun performClick(): Boolean {
        Log.d("TIP_FRAG", "CLICK")
        pauseButton.animate().setDuration(100).translationY(0f)
        android.os.Handler().postDelayed(
            Runnable {
                pauseButton.animate().setDuration(100).translationY(pauseButton.height.toFloat())
            }, 5000
        )
        return true
    }
}