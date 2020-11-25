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
import androidx.preference.PreferenceManager
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
import kotlin.math.abs

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
        view.findViewById<HeadingView>(R.id.trip_details_elevation).value =
            getUserAltitudeUnitLong(requireContext())


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
                distanceHeadingView.value =
                    String.format("%.2f %s",
                        getUserDistance(requireContext(), overview.distance ?: 0.0),
                        getUserDistanceUnitShort(requireContext()))
                durationHeadingView.value = formatDuration(overview.duration ?: 0.0)
                speedHeadingView.value = String.format("%.1f %s (average)",
                    getUserSpeed(requireContext(),
                        overview.distance ?: 0.0,
                        overview.duration ?: 1.0),
                    getUserSpeedUnitShort(requireContext()))

                titleNameView.text = overview.name

            } else {
                Log.d("TRIP_DETAILS_FRAG", "overview is null")
            }
        })

        zipLiveData(viewModel.measurements(), viewModel.timeState()).observe(viewLifecycleOwner,
            { pairs ->
                val measurements = pairs.first
                val timeStates = pairs.second

                Log.d("TRIP_DETAILS_FRAGMENT",
                    "Recorded ${measurements.size} measurements for trip ${tripId}")

                if (timeStates.isNotEmpty()) titleDateView.text = String.format("%s: %s - %s",
                    SimpleDateFormat("MMMM d").format(Date(timeStates.first().timestamp)),
                    SimpleDateFormat("h:mm").format(Date(timeStates.first().timestamp)),
                    SimpleDateFormat("h:mm").format(Date(timeStates.last().timestamp)))

                val mapData = plotPath(measurements, timeStates)
                if (mapData.bounds != null) {
                    Log.d("TRIP_DETAILS_FRAGMENT", "Plotting path")
                    mapData.paths.forEach { path ->
                        path.startCap(RoundCap())
                        path.endCap(RoundCap())
                        path.width(5f)
                        path.color(0xff007700.toInt())
                        map.addPolyline(path)
                    }
                    map.moveCamera(CameraUpdateFactory.newLatLngBounds(mapData.bounds,
                        1000,
                        1000,
                        100))
                }

                fun makeSpeedDataset(
                    measurements: Array<Measurements>,
                    intervals: Array<LongRange>,
                ): Pair<LineDataSet, LineDataSet> {
                    val entries = ArrayList<Entry>()
                    val trend = ArrayList<Entry>()
                    val intervalStart = intervals.last().first

                    val accumulatedTime = accumulateTime(intervals)

                    var trendLast = getUserSpeed(requireContext(), measurements[0].speed.toDouble())
                    var trendAlpha = 0.01
                    measurements.forEach {
                        if (it.accuracy < 5) {
                            var timestamp =
                                (accumulatedTime + (it.time - intervalStart) / 1e3).toFloat()
                            entries.add(Entry(timestamp,
                                getUserSpeed(requireContext(), it.speed.toDouble()).toFloat()))
                            trendLast = (trendAlpha * getUserSpeed(requireContext(),
                                it.speed.toDouble())) + ((1 - trendAlpha) * trendLast)
                            trend.add(Entry(timestamp, trendLast.toFloat()))
                            if (trendAlpha > 0.01) trendAlpha -= 0.01
                        }
                    }
                    val dataset = LineDataSet(entries, "Speed")
                    val trendData = LineDataSet(trend, "Trend")
                    dataset.setDrawCircles(false)
                    trendData.setDrawCircles(false)
                    dataset.color = Color.rgb(0, 45, 0)
                    dataset.lineWidth = 15f
                    trendData.color = ResourcesCompat.getColor(resources, R.color.colorAccent, null)
                    trendData.lineWidth = 3f
                    return Pair(dataset, trendData)
                }

                fun makeSpeedLineChart() {
                    var intervals = getTripIntervals(timeStates, measurements)
                    val legs = getTripLegs(measurements, intervals)
                    val data = LineData()

                    legs.forEachIndexed { idx, leg ->
                        val (raw, trend) = makeSpeedDataset(leg,
                            intervals.sliceArray(IntRange(0, idx)))

                        data.addDataSet(raw)
                        data.addDataSet(trend)
                    }
                    speedChartView.data = data
                    speedChartView.invalidate()
                }
                makeSpeedLineChart()

                fun makeElevationDataset(
                    measurements: Array<Measurements>,
                    intervals: Array<LongRange>,
                ): LineDataSet {
                    val entries = ArrayList<Entry>()
                    val trend = ArrayList<Entry>()
                    val intervalStart = intervals.last().first

                    val accumulatedTime = accumulateTime(intervals)

                    measurements.forEach {
                        if (it.accuracy < 5) {
                            var timestamp =
                                (accumulatedTime + (it.time - intervalStart) / 1e3).toFloat()
                            entries.add(Entry(timestamp,
                                getUserAltitude(requireContext(), it.altitude).toFloat()))
                        }
                    }
                    val dataset = LineDataSet(entries, "Elevation")
                    dataset.setDrawCircles(false)
                    dataset.color = ResourcesCompat.getColor(resources, R.color.colorAccent, null)
                    dataset.lineWidth = 3f
                    return dataset
                }

                fun makeElevationLineChart() {
                    var intervals = getTripIntervals(timeStates, measurements)
                    val legs = getTripLegs(measurements, intervals)
                    val data = LineData()

                    legs.forEachIndexed { idx, leg ->
                        data.addDataSet(makeElevationDataset(leg,
                            intervals.sliceArray(IntRange(0, idx))))
                    }
                    elevationChartView.data = data
                    elevationChartView.invalidate()
                }
                makeElevationLineChart()
                /*
                fun makeElevationLineChart() {
                    val entries = ArrayList<Entry>()
                    val startTime = measurements[0].elapsedRealtimeNanos

                    measurements.forEach {
                        entries.add(Entry(((it.elapsedRealtimeNanos - startTime) / 1e9).toFloat(),
                            (getUserAltitude(requireContext(), it.altitude)).toFloat()))
                    }
                    val dataset = LineDataSet(entries, "Elevation")
                    dataset.setDrawCircles(false)
                    dataset.color = ResourcesCompat.getColor(resources, R.color.colorAccent, null)
                    elevationChartView.data = LineData(dataset)
                    elevationChartView.invalidate()
                }
                makeElevationLineChart()
                 */
            })

        viewModel.splits().observeForever(object : Observer<Array<Split>> {
            var called = false
            override fun onChanged(splits: Array<Split>) {
                if (!called) {
                    //TODO: Figure out how to do this without LiveData
                    called = true
                    var areSplitsInSystem = false
                    if (splits.isNotEmpty()) areSplitsInSystem =
                        abs(getSplitThreshold(PreferenceManager.getDefaultSharedPreferences(context)) * splits[0].totalDistance - 1.0) < 0.01
                    //if (splits.isEmpty() || !areSplitsInSystem) {
                    viewModel.clearSplits()
                    viewModel.addSplits()
                    //}
                    viewModel.splits().removeObserver(this)
                }
            }
        })

        viewModel.splits().observe(viewLifecycleOwner, Observer
        { splits ->
            fun makeSplitsGrid() {
                splitsGridView.removeAllViews()
                splitsGridView.visibility = View.VISIBLE
                splitsHeadingView.visibility = View.VISIBLE
                if (splits.isEmpty()) {
                    splitsGridView.visibility = View.GONE
                    splitsHeadingView.visibility = View.GONE
                    return
                }

                var maxSpeed = 0.0
                splits.forEach {
                    val splitSpeed = getUserSpeed(requireContext(), it.distance, it.duration)
                    if (splitSpeed > maxSpeed) maxSpeed = splitSpeed
                }
                val maxWidth = (maxSpeed / 10).toInt() * 10 + 10

                splits.forEachIndexed { idx, split ->
                    val distanceView = TextView(activity)
                    val speedView = LinearLayout(activity)
                    val speedText = TextView(activity)
                    val timeView = TextView(activity)

                    distanceView.text = String.format("%d %s",
                        kotlin.math.floor(getUserDistance(requireContext(), split.totalDistance))
                            .toInt(), getUserDistanceUnitShort(requireContext()))
                    timeView.text = formatDuration(split.totalDuration)
                    speedText.text = String.format("%.2f %s",
                        getUserSpeed(requireContext(), split.distance, split.duration).toFloat(),
                        getUserSpeedUnitShort(requireContext()))


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
                        speedText.width =
                            (speedView.width * getUserSpeed(requireContext(), split.distance,
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