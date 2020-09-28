package com.kvl.cyclotrack

import android.os.Bundle
import android.text.format.DateUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders

class LiveDataFragment : Fragment() {
    private lateinit var speedTextView: TextView
    private lateinit var distanceTextView: TextView
    private lateinit var durationTextView: TextView
    private lateinit var averageSpeedTextView: TextView
    private lateinit var heartRateTextView: TextView


    companion object {
        fun newInstance() = LiveDataFragment()
    }

    private lateinit var viewModel: LiveDataViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.live_data_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d("UI", "LiveDataFragment::onViewCreated")
        viewModel = ViewModelProviders.of(this).get(LiveDataViewModel::class.java)
        speedTextView = view.findViewById(R.id.textview_speed)
        distanceTextView = view.findViewById(R.id.textview_distance)
        durationTextView = view.findViewById(R.id.textview_duration)
        averageSpeedTextView = view.findViewById(R.id.textview_average_speed)
        heartRateTextView = view.findViewById(R.id.textview_heart_rate)

        val trackingImage: ImageView = view.findViewById(R.id.image_tracking)
        val accuracyTextView: TextView = view.findViewById(R.id.textview_accuracy)


        viewModel.getLocationData().observe(this, Observer {
            Log.d("UI", "Location observer detected change")
            /*speedTextView.text = "${String.format("%.1f", it.speed * 2.23694)} ±${
                String.format("%.1f",
                    (it.location?.speedAccuracyMetersPerSecond?.times(2.23694)) ?: 0f)
            } mph"*/
            val averageSpeed = it.distance / it.duration * 1e9 * 2.23694
            speedTextView.text = "${
                String.format("%.1f",
                    if (it.speed.isFinite()) it.speed * 1e9 * 2.23694 else 888f)
            } mph"
            averageSpeedTextView.text =
                "${String.format("%.1f", if (averageSpeed.isFinite()) averageSpeed else 0f)} avg"
            distanceTextView.text = "${String.format("%.2f", it.distance * 0.000621371)} mi"
            durationTextView.text = DateUtils.formatElapsedTime((it.duration / 1e9).toLong())
            heartRateTextView.text =
                String.format("%.2f", if (it.slope.isFinite()) it.slope else 0f)
            //heartRateTextView.text = String.format("%.2f", it.location?.accuracy ?: 0f)
            trackingImage.visibility = if (it.tracking) View.VISIBLE else View.INVISIBLE
            accuracyTextView.text = String.format("%.2f", it.accuracy)
        })
        viewModel.getSensorData().observe(this, Observer {
            Log.d("UI", "Sensor observer detected change")
            //heartRateTextView.text = String.format("%.1f", it.tilt?.get(0))
        })
    }
}