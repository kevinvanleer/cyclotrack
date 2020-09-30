package com.kvl.cyclotrack

import android.os.Bundle
import android.text.format.DateUtils
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels

class LiveDataFragment : Fragment() {
    private val viewModel: LiveDataViewModel by navGraphViewModels(R.id.nav_graph)

    companion object {
        fun newInstance() = LiveDataFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        setHasOptionsMenu(true)
        return inflater.inflate(R.layout.live_data_fragment, container, false)
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d("UI", "LiveDataFragment::onViewCreated")
        //viewModel = ViewModelProviders.of(this).get(LiveDataViewModel::class.java)
        val speedTextView: TextView = view.findViewById(R.id.textview_speed)
        val distanceTextView: TextView = view.findViewById(R.id.textview_distance)
        val durationTextView: TextView = view.findViewById(R.id.textview_duration)
        val averageSpeedTextView: TextView = view.findViewById(R.id.textview_average_speed)
        val heartRateTextView: TextView = view.findViewById(R.id.textview_heart_rate)
        val splitSpeedTextView: TextView = view.findViewById(R.id.textview_split_speed)

        val trackingImage: ImageView = view.findViewById(R.id.image_tracking)
        val accuracyTextView: TextView = view.findViewById(R.id.textview_accuracy)


        viewModel.getLocationData().observe(this, object : Observer<LocationModel> {
            override fun onChanged(it: LocationModel) {
                Log.d("UI", "Location observer detected change")
                val averageSpeed = it.distance / it.duration * 2.23694
                splitSpeedTextView.text =
                    "${String.format("%.1f", (it.location?.speed ?: 0f) * 2.23694)} mph"
                averageSpeedTextView.text =
                    "${
                        String.format("%.1f",
                            if (averageSpeed.isFinite()) averageSpeed else 0f)
                    } avg"
                distanceTextView.text = "${String.format("%.2f", it.distance * 0.000621371)} mi"
                durationTextView.text = DateUtils.formatElapsedTime((it.duration).toLong())
                heartRateTextView.text =
                    String.format("%.3f", if (it.slope.isFinite()) it.slope else 0f)

                trackingImage.visibility = if (it.tracking) View.VISIBLE else View.INVISIBLE
                accuracyTextView.text = String.format("%.2f", it.accuracy)
                speedTextView.text = String.format("%.1f spl", it.splitSpeed * 2.23694)
            }
        })
        viewModel.getSensorData().observe(this, object : Observer<SensorModel> {
            override fun onChanged(it: SensorModel) {
                Log.d("UI", "Sensor observer detected change")
                //heartRateTextView.text = String.format("%.1f", it.tilt?.get(0))
            }
        })
    }
}