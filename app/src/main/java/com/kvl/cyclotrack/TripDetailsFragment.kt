package com.kvl.cyclotrack

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.navArgs
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.RoundCap
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

@AndroidEntryPoint
class TripDetailsFragment : Fragment() {
    private val viewModel: TripDetailsViewModel by viewModels()
    private val args: TripDetailsFragmentArgs by navArgs()
    private lateinit var map: GoogleMap
    private var mapView: MapView? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.trip_details_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        mapView = view.findViewById(R.id.trip_details_map_view)
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync {
            Log.d("TRIP_DETAILS_FRAGMENT", "GOT MAP")
            map = it
            map.setMapStyle(MapStyleOptions.loadRawResourceStyle(context, R.raw.summary_map_style))
        }

        val titleNameView: TextView = view.findViewById(R.id.trip_details_title_name)
        val titleDateView: TextView = view.findViewById(R.id.trip_details_title_date)
        val distanceHeadingView: HeadingView = view.findViewById(R.id.trip_details_distance)
        val durationHeadingView: HeadingView = view.findViewById(R.id.trip_details_time)
        val speedHeadingView: HeadingView = view.findViewById(R.id.trip_details_speed)
        val splitsHeadingView: HeadingView = view.findViewById(R.id.trip_details_splits)
        val speedChartView: LineChart = view.findViewById(R.id.trip_details_speed_chart)
        val splitsGridView: GridLayout = view.findViewById(R.id.trip_details_splits_grid)
        val elevationChartView: LineChart = view.findViewById(R.id.trip_details_elevation_chart)

        view.findViewById<HeadingView>(R.id.trip_details_heart_rate).visibility = View.GONE
        view.findViewById<HeadingView>(R.id.trip_details_splits).value = ""
        view.findViewById<HeadingView>(R.id.trip_details_elevation).value = ""

        fun configureLineChart(chart: LineChart) {
            chart.setDrawBorders(true)
            chart.setBorderColor(Color.GRAY)
            chart.setNoDataText("No data")
            chart.legend.isEnabled = false
            chart.setDrawGridBackground(false)

            chart.xAxis.setDrawLabels(true)
            chart.xAxis.axisMinimum = 0f
            chart.xAxis.setDrawGridLines(true)
            chart.xAxis.textColor = Color.WHITE
            chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
            chart.xAxis.valueFormatter = object : ValueFormatter() {
                override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                    return if (value == 0f) "" else formatDuration(value.toDouble())
                }
            }

            chart.axisLeft.setDrawLabels(true)
            chart.axisLeft.textColor = Color.WHITE
            chart.axisLeft.axisMinimum = 0f
            chart.axisLeft.setDrawGridLines(true)
            chart.axisRight.setDrawLabels(true)
            chart.axisRight.textColor = Color.WHITE
            chart.axisRight.axisMinimum = 0f
            chart.axisRight.setDrawGridLines(false)
        }

        configureLineChart(speedChartView)
        configureLineChart(elevationChartView)

        val tripId = args.tripId
        Log.d("TRIP_DETAILS_FRAGMENT", String.format("Displaying details for trip %d", tripId))
        viewModel.tripId = tripId
        viewModel.tripOverview().observe(viewLifecycleOwner, Observer { overview ->
            if (overview != null) {
                val userDistance = overview.distance?.times(0.000621371) ?: 0.0
                val userTime = overview.duration?.div(3600) ?: 0.0
                val averageSpeed = userDistance / userTime
                distanceHeadingView.value =
                    String.format("%.2f mi", userDistance)
                durationHeadingView.value = formatDuration(overview.duration ?: 0.0)
                speedHeadingView.value = String.format("%.1f mph", averageSpeed)

                titleNameView.text = overview.name
                titleDateView.text = String.format("%s: %s - %s",
                    SimpleDateFormat("MMMM d").format(Date(overview.timestamp)),
                    SimpleDateFormat("hh:mm").format(Date(overview.timestamp)),
                    SimpleDateFormat("hh:mm").format(Date(overview.timestamp + (overview.duration?.times(
                        1000) ?: 0).toLong())))

            } else {
                Log.d("TRIP_DETAILS_FRAG", "overview is null")
            }
        })
        viewModel.measurements().observe(viewLifecycleOwner, { measurements ->
            Log.d("TRIP_DETAILS_FRAGMENT",
                "Recorded ${measurements.size} measurements for trip ${tripId}")
            val mapData = plotPath(measurements)
            if (mapData.bounds != null) {
                Log.d("TRIP_DETAILS_FRAGMENT", "Plotting path")
                mapData.path.startCap(RoundCap())
                mapData.path.endCap(RoundCap())
                mapData.path.width(5f)
                mapData.path.color(0xff007700.toInt())
                map.addPolyline(mapData.path)
                map.moveCamera(CameraUpdateFactory.newLatLngBounds(mapData.bounds, 1000, 1000, 100))
            }

            fun makeSpeedLineChart() {
                val entries = ArrayList<Entry>()
                val startTime = measurements[0].elapsedRealtimeNanos

                measurements.forEach {
                    entries.add(Entry(((it.elapsedRealtimeNanos - startTime) / 1e9).toFloat(),
                        (it.speed * 2.23694).toFloat()))
                }
                val dataset = LineDataSet(entries, "Speed")
                dataset.setDrawCircles(false)
                dataset.color = ResourcesCompat.getColor(resources, R.color.colorAccent, null)
                speedChartView.data = LineData(dataset)
                speedChartView.invalidate()
            }
            makeSpeedLineChart()

            fun makeElevationLineChart() {
                val entries = ArrayList<Entry>()
                val startTime = measurements[0].elapsedRealtimeNanos

                measurements.forEach {
                    entries.add(Entry(((it.elapsedRealtimeNanos - startTime) / 1e9).toFloat(),
                        (it.altitude).toFloat()))
                }
                val dataset = LineDataSet(entries, "Elevation")
                dataset.setDrawCircles(false)
                dataset.color = ResourcesCompat.getColor(resources, R.color.colorAccent, null)
                elevationChartView.data = LineData(dataset)
                elevationChartView.invalidate()
            }
            makeElevationLineChart()
        })

        //viewModel.clearSplits()
        viewModel.splits().observeForever(object : Observer<Array<Split>> {
            override fun onChanged(splits: Array<Split>) {
                if (splits.isEmpty()) {
                    viewModel.addSplits()
                }
                viewModel.splits().removeObserver(this)
            }
        })

        viewModel.splits().observe(viewLifecycleOwner, Observer
        { splits ->
            fun makeSplitsGrid() {
                splitsGridView.visibility = View.VISIBLE
                splitsHeadingView.visibility = View.VISIBLE
                if (splits.isEmpty()) {
                    splitsGridView.visibility = View.GONE
                    splitsHeadingView.visibility = View.GONE
                    return
                }

                var maxSpeed = 0.0
                splits.forEach {
                    val splitSpeed = getUserSpeed(it.distance, it.duration)
                    if (splitSpeed > maxSpeed) maxSpeed = splitSpeed
                }
                val maxWidth = (maxSpeed / 10).toInt() * 10 + 10

                splits.forEachIndexed { idx, split ->
                    val distanceView = TextView(activity)
                    val speedView = LinearLayout(activity)
                    val speedText = TextView(activity)
                    val timeView = TextView(activity)

                    distanceView.text = String.format("%d mi",
                        kotlin.math.floor(getUserDistance(split.totalDistance)).toInt())
                    timeView.text = formatDuration(split.totalDuration)
                    speedText.text = String.format("%.2f mph",
                        getUserSpeed(split.distance, split.duration).toFloat())


                    val distanceLayout =
                        GridLayout.LayoutParams(GridLayout.spec(idx + 1, GridLayout.CENTER),
                            GridLayout.spec(0))
                    val speedLayout =
                        GridLayout.LayoutParams(GridLayout.spec(idx + 1), GridLayout.spec(1, 100f))
                    val timeLayout =
                        GridLayout.LayoutParams(GridLayout.spec(idx + 1, GridLayout.CENTER),
                            GridLayout.spec(2))

                    distanceView.layoutParams = distanceLayout
                    speedView.layoutParams = speedLayout
                    speedText.background = resources.getDrawable(R.drawable.rounded_corner, null)
                    speedView.doOnPreDraw {
                        speedText.width = (speedView.width * getUserSpeed(split.distance,
                            split.duration) / maxWidth).toInt()
                    }
                    timeView.layoutParams = timeLayout


                    speedView.addView(speedText)
                    splitsGridView.addView(distanceView)
                    splitsGridView.addView(speedView)
                    splitsGridView.addView(timeView)
                }
            }
            makeSplitsGrid()
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (mapView != null) mapView?.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        if (mapView != null) mapView?.onResume()
    }

    override fun onStart() {
        super.onStart()
        if (mapView != null) mapView?.onStart()
    }

    override fun onPause() {
        super.onPause()
        if (mapView != null) mapView?.onPause()
    }

    override fun onStop() {
        super.onStop()
        if (mapView != null) mapView?.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mapView != null) mapView?.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (mapView != null) mapView?.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (mapView != null) mapView?.onLowMemory()
    }
}