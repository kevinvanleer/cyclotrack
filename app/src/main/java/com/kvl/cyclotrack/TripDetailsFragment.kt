package com.kvl.cyclotrack

import android.graphics.Color
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.view.animation.AccelerateInterpolator
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.view.animation.Transformation
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.marginTop
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.preference.PreferenceManager
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.gms.maps.CameraUpdate
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.RoundCap
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.roundToInt


@AndroidEntryPoint
class TripDetailsFragment : Fragment(), View.OnTouchListener {
    private var startHeight: Int = 0
    private var startY: Float = 0.0f
    private val viewModel: TripDetailsViewModel by viewModels()
    private val args: TripDetailsFragmentArgs by navArgs()
    private lateinit var map: GoogleMap
    private lateinit var mapView: MapView
    private lateinit var titleNameView: TextView
    private lateinit var titleDateView: TextView
    private lateinit var notesView: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var constraintLayout: ConstraintLayout
    private lateinit var maxGuide: View
    private lateinit var defaultGuide: View
    private lateinit var minGuide: View
    private lateinit var defaultCameraPosition: CameraPosition
    private lateinit var maxCameraPosition: CameraPosition

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.trip_details_fragment, container, false)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.details_menu, menu)
        Log.d("TRIP_SUMMARIES", "Options menu created")
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.d("TRIP_SUMMARIES", "Options menu clicked")
        return when (item.itemId) {
            R.id.details_menu_action_edit -> {
                try {
                    findNavController()
                        .navigate(TripDetailsFragmentDirections.actionEditTrip(args.tripId,
                            titleNameView.text.toString(),
                            titleDateView.text.toString(),
                            notesView.text.toString()))
                } catch (e: IllegalArgumentException) {
                    Log.e("EDIT_TRIP_FRAGMENT", e.message, e)
                }
                true
            }
            R.id.details_menu_action_delete -> {
                Log.d("TRIP_SUMMARIES", "Options menu clicked delete")
                activity?.let {
                    val builder = AlertDialog.Builder(it)
                    builder.apply {
                        setPositiveButton("DELETE"
                        ) { _, _ ->
                            Log.d("TRIP_DELETE_DIALOG", "CLICKED DELETE")
                            viewModel.removeTrip()
                            findNavController().navigate(R.id.action_remove_trip)
                        }
                        setNegativeButton("CANCEL"
                        ) { _, _ ->
                            Log.d("TRIP_DELETE_DIALOG", "CLICKED CANCEL")
                        }
                        setTitle("Delete ride?")
                        setMessage("You are about to remove this ride from your history. This change cannot be undone.")
                    }

                    builder.create()
                }?.show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)

        constraintLayout = view.findViewById(R.id.TripDetailsFragment)
        maxGuide = view.findViewById(R.id.trip_details_max_map_guide)
        minGuide = view.findViewById(R.id.trip_details_min_map_guide)
        defaultGuide = view.findViewById(R.id.trip_details_default_map_guide)


        mapView = view.findViewById(R.id.trip_details_map_view)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync {
            Log.d("TRIP_DETAILS_FRAGMENT", "GOT MAP")
            map = it
            map.setMapStyle(MapStyleOptions.loadRawResourceStyle(context, R.raw.summary_map_style))
            map.uiSettings.setAllGesturesEnabled(false)
        }

        titleNameView = view.findViewById(R.id.trip_details_title_name)
        titleDateView = view.findViewById(R.id.trip_details_title_date)
        notesView = view.findViewById(R.id.trip_details_notes)
        val distanceHeadingView: HeadingView = view.findViewById(R.id.trip_details_distance)
        val durationHeadingView: HeadingView = view.findViewById(R.id.trip_details_time)
        val speedHeadingView: HeadingView = view.findViewById(R.id.trip_details_speed)
        val splitsHeadingView: HeadingView = view.findViewById(R.id.trip_details_splits)
        val speedChartView: LineChart = view.findViewById(R.id.trip_details_speed_chart)
        val splitsGridView: GridLayout = view.findViewById(R.id.trip_details_splits_grid)
        val elevationChartView: LineChart = view.findViewById(R.id.trip_details_elevation_chart)
        val heartRateChartView: LineChart = view.findViewById(R.id.trip_details_heart_rate_chart)
        val heartRateView: HeadingView = view.findViewById(R.id.trip_details_heart_rate)
        val cadenceChartView: LineChart = view.findViewById(R.id.trip_details_cadence_chart)
        val cadenceView: HeadingView = view.findViewById(R.id.trip_details_cadence)

        scrollView = view.findViewById(R.id.trip_details_scroll_view)

        heartRateView.visibility = View.GONE
        heartRateChartView.visibility = View.GONE
        cadenceView.visibility = View.GONE
        cadenceChartView.visibility = View.GONE
        notesView.visibility = View.GONE

        splitsHeadingView.value = ""
        view.findViewById<HeadingView>(R.id.trip_details_elevation).value =
            getUserAltitudeUnitLong(requireContext())


        fun getAverageHeartRate(measurements: Array<Measurements>): Short? {
            var sum = 0
            var count = 0
            measurements.forEach {
                if (it.heartRate != null) {
                    sum += it.heartRate
                    ++count
                }
            }
            return if (count == 0) {
                null
            } else {
                (sum / count).toShort()
            }
        }

        fun getAverageCadence(measurements: Array<Measurements>): Float? {
            return try {
                val cadenceMeasurements = measurements.filter { it.cadenceRevolutions != null }
                val totalRevs = cadenceMeasurements.last().cadenceRevolutions?.let {
                    getDifferenceRollover(it,
                        cadenceMeasurements.first().cadenceRevolutions!!)
                } ?: null
                val duration =
                    (cadenceMeasurements.last().time - cadenceMeasurements.first().time) / 1000 / 60

                totalRevs?.toFloat()?.div(duration).takeIf { it?.isFinite() ?: false } ?: null
            } catch (e: Exception) {
                null
            }
        }

        fun configureLineChart(chart: LineChart, yMin: Float = 0f) {
            chart.setTouchEnabled(false)
            chart.setDrawBorders(true)
            chart.setBorderColor(Color.GRAY)
            chart.setNoDataText("Looking for data...")
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
            chart.axisLeft.axisMinimum = yMin
            chart.axisLeft.setDrawGridLines(true)
            chart.axisRight.setDrawLabels(true)
            chart.axisRight.textColor = Color.WHITE
            chart.axisRight.axisMinimum = yMin
            chart.axisRight.setDrawGridLines(false)
        }

        val tripId = args.tripId
        Log.d("TRIP_DETAILS_FRAGMENT", String.format("Displaying details for trip %d", tripId))
        viewModel.tripId = tripId
        viewModel.tripOverview().observe(viewLifecycleOwner, { overview ->
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
                if (overview.notes != null) {
                    notesView.visibility = View.VISIBLE
                    notesView.text = overview.notes
                }

            } else {
                Log.d("TRIP_DETAILS_FRAG", "overview is null")
            }
        })

        zipLiveData(viewModel.measurements(), viewModel.timeState()).observe(viewLifecycleOwner,
            { pairs ->
                val measurements = pairs.first
                val timeStates = pairs.second

                /*
                fun getSpeedDataFromGps(
                    entries: ArrayList<Entry>,
                    trend: ArrayList<Entry>,
                    intervals: Array<LongRange>,
                ) {
                    val intervalStart = intervals.last().first
                    val accumulatedTime = accumulateTime(intervals)
                    var trendLast = getUserSpeed(requireContext(), measurements[0].speed.toDouble())
                    var trendAlpha = 0.01f
                    measurements.forEach {
                        if (it.accuracy < 5) {
                            val timestamp =
                                (accumulatedTime + (it.time - intervalStart) / 1e3).toFloat()
                            entries.add(Entry(timestamp,
                                getUserSpeed(requireContext(), it.speed.toDouble())))
                            trendLast = (trendAlpha * getUserSpeed(requireContext(),
                                it.speed.toDouble())) + ((1 - trendAlpha) * trendLast)
                            trend.add(Entry(timestamp, trendLast))
                            if (trendAlpha > 0.01f) trendAlpha -= 0.01f
                        }
                    }
                }

                fun getSpeedDataFromSensor(
                    entries: ArrayList<Entry>,
                    trend: ArrayList<Entry>,
                    intervals: Array<LongRange>,
                ) {
                    val intervalStart = intervals.last().first
                    val accumulatedTime = accumulateTime(intervals)
                    var trendLast = getUserSpeed(requireContext(), measurements[0].speed.toDouble())
                    var trendAlpha = 0.01f
                    measurements.forEach {
                        if (it.speedRpm != null) {
                            val speed =
                                it.speedRpm * getUserCircumference(requireContext()) / 60
                            val timestamp =
                                (accumulatedTime + (it.time - intervalStart) / 1e3).toFloat()
                            entries.add(Entry(timestamp,
                                getUserSpeed(requireContext(), speed)))
                            trendLast = (trendAlpha * getUserSpeed(requireContext(),
                                speed)) + ((1 - trendAlpha) * trendLast)
                            trend.add(Entry(timestamp, trendLast))
                            if (trendAlpha > 0.01f) trendAlpha -= 0.01f
                        }
                    }
                }
                */

                fun getFusedSpeedData(
                    entries: ArrayList<Entry>,
                    trend: ArrayList<Entry>,
                    intervals: Array<LongRange>,
                ) {
                    val intervalStart = intervals.last().first
                    val accumulatedTime = accumulateTime(intervals)
                    var trendLast = getUserSpeed(requireContext(), measurements[0].speed.toDouble())
                    var trendAlpha = 0.01f
                    measurements.forEach {
                        if (it.speedRpm != null) {
                            val speed =
                                it.speedRpm * getUserCircumference(requireContext()) / 60
                            val timestamp =
                                (accumulatedTime + (it.time - intervalStart) / 1e3).toFloat()
                            entries.add(Entry(timestamp,
                                getUserSpeed(requireContext(), speed)))
                            trendLast = (trendAlpha * getUserSpeed(requireContext(),
                                speed)) + ((1 - trendAlpha) * trendLast)
                            trend.add(Entry(timestamp, trendLast))
                            if (trendAlpha > 0.01f) trendAlpha -= 0.01f
                        } else if (it.accuracy < 5) {
                            val timestamp =
                                (accumulatedTime + (it.time - intervalStart) / 1e3).toFloat()
                            entries.add(Entry(timestamp,
                                getUserSpeed(requireContext(), it.speed.toDouble())))
                            trendLast = (trendAlpha * getUserSpeed(requireContext(),
                                it.speed.toDouble())) + ((1 - trendAlpha) * trendLast)
                            trend.add(Entry(timestamp, trendLast))
                            if (trendAlpha > 0.01f) trendAlpha -= 0.01f
                        }
                    }
                }

                fun makeSpeedDataset(
                    measurements: Array<Measurements>,
                    intervals: Array<LongRange>,
                ): Pair<LineDataSet, LineDataSet> {
                    val entries = ArrayList<Entry>()
                    val trend = ArrayList<Entry>()

                    if (measurements.isNullOrEmpty()) return Pair(LineDataSet(entries, "Speed"),
                        LineDataSet(trend, "Trend"))

                    /* NOTE: THIS DIDN'T WORK
                    var trendLast: Double = viewModel.tripOverview().value?.let {
                        val speed = it.duration?.let { it1 ->
                            it.distance?.div(it1)
                        } ?: measurements[20].speed.toDouble()
                        getUserSpeed(requireContext(), speed)
                    } ?: getUserSpeed(requireContext(), measurements[20].speed.toDouble())*/

                    //getSpeedDataFromGps(entries, trend, intervals)
                    getFusedSpeedData(entries, trend, intervals)
                    val dataset = LineDataSet(entries, "Speed")
                    dataset.setDrawCircles(false)
                    dataset.setDrawValues(false)
                    val trendData = LineDataSet(trend, "Trend")
                    trendData.setDrawCircles(false)
                    trendData.setDrawValues(false)
                    //TODO: Use resource for this color
                    dataset.color =
                        ResourcesCompat.getColor(resources, R.color.colorGraphSecondary, null)
                    dataset.lineWidth = 15f
                    trendData.color = ResourcesCompat.getColor(resources, R.color.colorAccent, null)
                    trendData.lineWidth = 3f
                    return Pair(dataset, trendData)
                }

                /*
                fun makeSensorSpeedDataset(
                    measurements: Array<Measurements>,
                    intervals: Array<LongRange>,
                ): Pair<LineDataSet, LineDataSet> {
                    val entries = ArrayList<Entry>()
                    val trend = ArrayList<Entry>()

                    if (measurements.isNullOrEmpty()) return Pair(LineDataSet(entries, "Speed"),
                        LineDataSet(trend, "Trend"))

                    getSpeedDataFromSensor(entries, trend, intervals)
                    val dataset = LineDataSet(entries, "Speed")
                    val trendData = LineDataSet(trend, "Trend")
                    dataset.setDrawCircles(false)
                    trendData.setDrawCircles(false)
                    //TODO: Use resource for this color
                    dataset.color = Color.rgb(0, 0, 45)
                    dataset.lineWidth = 15f
                    trendData.color = Color.rgb(0, 0, 200)
                    //trendData.color = ResourcesCompat.getColor(resources, R.color.colorAccent, null)
                    trendData.lineWidth = 3f
                    return Pair(dataset, trendData)
                }
                 */

                fun makeSpeedLineChart() {
                    configureLineChart(speedChartView)

                    val intervals = getTripIntervals(timeStates, measurements)
                    val legs = getTripLegs(measurements, intervals)
                    val data = LineData()

                    legs.forEachIndexed { idx, leg ->
                        val (raw, trend) = makeSpeedDataset(leg,
                            intervals.sliceArray(IntRange(0, idx)))

                        data.addDataSet(raw)
                        data.addDataSet(trend)
                    }
                    /*legs.forEachIndexed { idx, leg ->
                        val (raw, trend) = makeSensorSpeedDataset(leg,
                            intervals.sliceArray(IntRange(0, idx)))

                        data.addDataSet(raw)
                        data.addDataSet(trend)
                    }*/
                    speedChartView.data = data
                    speedChartView.invalidate()
                }

                fun makeHeartRateDataset(
                    measurements: Array<Measurements>,
                    intervals: Array<LongRange>,
                ): LineDataSet {
                    val entries = ArrayList<Entry>()
                    val intervalStart = intervals.last().first

                    val accumulatedTime = accumulateTime(intervals)

                    measurements.forEach {
                        if (it.heartRate != null) {
                            val timestamp =
                                (accumulatedTime + (it.time - intervalStart) / 1e3).toFloat()
                            entries.add(Entry(timestamp, it.heartRate.toFloat()))
                        }
                    }
                    val dataset = LineDataSet(entries, "Heart rate")
                    dataset.setDrawCircles(false)
                    dataset.setDrawValues(false)
                    dataset.color = ResourcesCompat.getColor(resources, R.color.colorAccent, null)
                    dataset.lineWidth = 3f
                    return dataset
                }

                fun makeHeartRateLineChart() {
                    configureLineChart(heartRateChartView, 50f)

                    val intervals = getTripIntervals(timeStates, measurements)
                    val legs = getTripLegs(measurements, intervals)
                    val data = LineData()

                    legs.forEachIndexed { idx, leg ->
                        data.addDataSet(makeHeartRateDataset(leg,
                            intervals.sliceArray(IntRange(0, idx))))
                    }
                    heartRateChartView.data = data
                    heartRateChartView.invalidate()
                }

                fun makeCadenceDataset(
                    measurements: Array<Measurements>,
                    intervals: Array<LongRange>,
                ): LineDataSet {
                    val entries = ArrayList<Entry>()
                    val intervalStart = intervals.last().first

                    val accumulatedTime = accumulateTime(intervals)

                    measurements.forEach {
                        if (it.cadenceRpm != null) {
                            val timestamp =
                                (accumulatedTime + (it.time - intervalStart) / 1e3).toFloat()
                            entries.add(Entry(timestamp, it.cadenceRpm))
                        }
                    }
                    val dataset = LineDataSet(entries, "Cadence")
                    dataset.setDrawCircles(false)
                    dataset.setDrawValues(false)
                    dataset.color = ResourcesCompat.getColor(resources, R.color.colorAccent, null)
                    dataset.lineWidth = 3f
                    return dataset
                }

                fun makeCadenceLineChart() {
                    configureLineChart(cadenceChartView)

                    val intervals = getTripIntervals(timeStates, measurements)
                    val legs = getTripLegs(measurements, intervals)
                    val data = LineData()

                    legs.forEachIndexed { idx, leg ->
                        data.addDataSet(makeCadenceDataset(leg,
                            intervals.sliceArray(IntRange(0, idx))))
                    }
                    cadenceChartView.data = data
                    cadenceChartView.invalidate()
                }

                fun makeElevationDataset(
                    measurements: Array<Measurements>,
                    intervals: Array<LongRange>,
                ): LineDataSet {
                    val entries = ArrayList<Entry>()
                    val intervalStart = intervals.last().first

                    val accumulatedTime = accumulateTime(intervals)

                    measurements.forEach {
                        if (it.accuracy < 5) {
                            val timestamp =
                                (accumulatedTime + (it.time - intervalStart) / 1e3).toFloat()
                            entries.add(Entry(timestamp,
                                getUserAltitude(requireContext(), it.altitude).toFloat()))
                        }
                    }
                    val dataset = LineDataSet(entries, "Elevation")
                    dataset.setDrawCircles(false)
                    dataset.setDrawValues(false)
                    dataset.color = ResourcesCompat.getColor(resources, R.color.colorAccent, null)
                    dataset.lineWidth = 3f
                    return dataset
                }

                fun makeElevationLineChart() {
                    configureLineChart(elevationChartView)

                    val intervals = getTripIntervals(timeStates, measurements)
                    val legs = getTripLegs(measurements, intervals)
                    val data = LineData()

                    legs.forEachIndexed { idx, leg ->
                        data.addDataSet(makeElevationDataset(leg,
                            intervals.sliceArray(IntRange(0, idx))))
                    }
                    elevationChartView.data = data
                    elevationChartView.invalidate()
                }
                Log.d("TRIP_DETAILS_FRAGMENT",
                    "Recorded ${measurements.size} measurements for trip ${tripId}")

                if (measurements.isNullOrEmpty()) return@observe
                if (timeStates.isNotEmpty()) titleDateView.text = String.format("%s: %s - %s",
                    SimpleDateFormat("MMMM d",
                        Locale.US).format(Date(timeStates.first().timestamp)),
                    SimpleDateFormat("h:mm", Locale.US).format(Date(timeStates.first().timestamp)),
                    SimpleDateFormat("h:mm", Locale.US).format(Date(timeStates.last().timestamp)))

                val thisFragment = this
                viewLifecycleOwner.lifecycleScope.launch {
                    val mapData = plotPath(measurements, timeStates)
                    if (mapData.bounds != null) {
                        Log.d("TRIP_DETAILS_FRAGMENT", "Plotting path")
                        mapData.paths.forEach { path ->
                            path.startCap(RoundCap())
                            path.endCap(RoundCap())
                            path.width(5f)
                            //path.color(0xff007700.toInt())
                            path.color(ResourcesCompat.getColor(resources,
                                R.color.colorAccent,
                                null))
                            map.addPolyline(path)
                        }
                        map.moveCamera(CameraUpdateFactory.newLatLngBounds(mapData.bounds,
                            mapView.width,
                            scrollView.marginTop,
                            100))
                        maxCameraPosition = map.cameraPosition
                        map.moveCamera(CameraUpdateFactory.scrollBy(0f,
                            (mapView.height - scrollView.marginTop) / 2f))
                        defaultCameraPosition = map.cameraPosition
                    }
                    makeSpeedLineChart()
                    makeElevationLineChart()
                    val avgHeartRate = getAverageHeartRate(measurements)
                    if (avgHeartRate != null) {
                        heartRateView.visibility = View.VISIBLE
                        heartRateChartView.visibility = View.VISIBLE
                        heartRateView.value = "$avgHeartRate bpm (average)"
                        makeHeartRateLineChart()
                    }
                    val avgCadence = getAverageCadence(measurements)
                    if (avgCadence != null) {
                        cadenceView.visibility = View.VISIBLE
                        cadenceChartView.visibility = View.VISIBLE
                        cadenceView.value = "${avgCadence.roundToInt()} rpm (average)"
                        makeCadenceLineChart()
                    }
                    scrollView.setOnTouchListener(thisFragment)
                }
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
                    if (splits.isEmpty() || !areSplitsInSystem) {
                        viewModel.clearSplits()
                        viewModel.addSplits()
                    }
                    viewModel.splits().removeObserver(this)
                }
            }
        })

        viewModel.splits().observe(viewLifecycleOwner, { splits ->
            fun makeSplitsGrid() {
                splitsGridView.removeAllViews()
                splitsGridView.visibility = View.VISIBLE
                splitsHeadingView.visibility = View.VISIBLE
                if (splits.isEmpty()) {
                    splitsGridView.visibility = View.GONE
                    splitsHeadingView.visibility = View.GONE
                    return
                }

                var maxSpeed = 0.0f
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
                        getUserSpeed(requireContext(), split.distance, split.duration),
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
                    speedText.background =
                        ResourcesCompat.getDrawable(resources, R.drawable.rounded_corner, null)
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
        if (this::mapView.isInitialized) mapView.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        if (this::mapView.isInitialized) mapView.onResume()
    }

    override fun onStart() {
        super.onStart()
        if (this::mapView.isInitialized) mapView.onStart()
    }

    override fun onPause() {
        super.onPause()
        if (this::mapView.isInitialized) mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        if (this::mapView.isInitialized) mapView.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this::mapView.isInitialized) mapView.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (this::mapView.isInitialized) mapView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (this::mapView.isInitialized) mapView.onLowMemory()
    }

    private fun startTouchSequence(event: MotionEvent?): Boolean {
        startY = event?.rawY ?: 0f
        startHeight = scrollView.marginTop
        Log.d("TOUCH_SEQ", "Start sequence: $startY")
        return true
    }

    private fun adjustMap(event: MotionEvent?): Boolean {
        val displayMetrics = DisplayMetrics()
        activity?.windowManager?.defaultDisplay?.getMetrics(displayMetrics)

        val newHeight =
            (startHeight + (event?.rawY ?: startY) - startY).toInt()

        if (scrollView.scrollY > 0 || (startHeight == minGuide.top && ((event?.rawY
                ?: startY) - startY < 0))
        ) {
            startY = event?.rawY ?: startY
            return false
        }

        Log.d("TOUCH_SEQ_MOVE",
            "startHeight:$startHeight, rawY:${event?.rawY}, startY:$startY, newHeight:$newHeight")
        val marginParams: ViewGroup.MarginLayoutParams =
            scrollView.layoutParams as ViewGroup.MarginLayoutParams
        marginParams.topMargin = newHeight
        scrollView.layoutParams = marginParams

        return true
    }

    /*
    private fun constraints_adjustMap(event: MotionEvent?): Boolean {
        val displayMetrics = DisplayMetrics()
        activity?.windowManager?.defaultDisplay?.getMetrics(displayMetrics)

        val newHeight =
            (startHeight + (event?.rawY ?: startY) - startY).toInt()

        if (scrollView.scrollY > 0 || (startHeight == minGuide.top && ((event?.rawY
                ?: startY) - startY < 0))
        ) {
            startY = event?.rawY ?: startY
            return false
        }

        Log.d("TOUCH_SEQ_MOVE",
            "startHeight:$startHeight, rawY:${event?.rawY}, startY:$startY, newHeight:$newHeight")

        val constraintSet = ConstraintSet()
        constraintSet.clone(constraintLayout)
        constraintSet.connect(R.id.trip_details_scroll_view,
            ConstraintSet.TOP,
            R.id.trip_details_min_map_guide,
            ConstraintSet.TOP,
            newHeight)
        constraintSet.applyTo(constraintLayout)

        return true
    }
     */

    private fun endTouchSequence(event: MotionEvent?): Boolean {
        val thresholdDip = 100f
        val thresholdPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            thresholdDip,
            resources.displayMetrics
        )

        val currentHeight = scrollView.marginTop

        val expand = when {
            scrollView.marginTop < defaultGuide.top -> defaultGuide.top
            else -> maxGuide.top
        }
        val collapse = when {
            scrollView.marginTop < defaultGuide.top -> minGuide.top
            else -> defaultGuide.top
        }
        val newHeight = when {
            (event?.rawY ?: startY) - startY > thresholdPx -> expand
            startY - (event?.rawY ?: startY) > thresholdPx -> collapse
            else -> startHeight
        }
        val interpolator = when (newHeight) {
            startY.toInt() -> AccelerateInterpolator()
            else -> DecelerateInterpolator()
        }

        val delta = newHeight - currentHeight
        Log.d("TOUCH_SEQ_END",
            "startHeight:$startHeight, rawY:${event?.rawY}, startY:$startY, newHeight:$newHeight, currentHeight:$currentHeight, delta:$delta")

        val marginAnimation = object : Animation() {
            override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
                super.applyTransformation(interpolatedTime, t)
                val newParams: ViewGroup.MarginLayoutParams =
                    scrollView.layoutParams as ViewGroup.MarginLayoutParams
                newParams.topMargin = (newHeight - (delta * (1 - interpolatedTime))).toInt()
                scrollView.layoutParams = newParams
            }
        }

        marginAnimation.duration = 100
        marginAnimation.interpolator = interpolator
        scrollView.startAnimation(marginAnimation)

        var camFactory: CameraUpdate? = null
        when (newHeight) {
            maxGuide.top -> {
                if (this::maxCameraPosition.isInitialized) {
                    camFactory = CameraUpdateFactory.newCameraPosition(maxCameraPosition)
                }
                map.uiSettings.setAllGesturesEnabled(true)
            }
            else -> {
                if (this::maxCameraPosition.isInitialized) {
                    camFactory = CameraUpdateFactory.newCameraPosition(defaultCameraPosition)
                }
                map.uiSettings.setAllGesturesEnabled(false)
            }
        }
        if (camFactory != null) {
            map.animateCamera(camFactory, 700, null)
        }

        return true
    }

    /*
    private fun constraints_endTouchSequence(event: MotionEvent?): Boolean {
        val expand = when {
            scrollView.marginTop < defaultGuide.top -> R.id.trip_details_default_map_guide
            else -> R.id.trip_details_max_map_guide
        }
        val collapse = when {
            scrollView.marginTop < defaultGuide.top -> R.id.trip_details_min_map_guide
            else -> R.id.trip_details_default_map_guide
        }
        val newGuide = when {
            (event?.rawY ?: startY) - startY > 5 -> expand
            (event?.rawY ?: startY) - startY < -5 -> collapse
            else -> R.id.trip_details_default_map_guide
        }
        Log.d("TOUCH_SEQ_END", "$startHeight, ${event?.rawY}, $startY, $newGuide")

        val constraintSet = ConstraintSet()
        constraintSet.clone(constraintLayout)
        constraintSet.connect(R.id.trip_details_scroll_view,
            ConstraintSet.TOP,
            newGuide,
            ConstraintSet.TOP,
            0)
        constraintSet.applyTo(constraintLayout)

        return true
    }
     */

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        if (scrollView.scrollY != 0) return false
        return when (event?.action) {
            MotionEvent.ACTION_UP -> {
                val retVal = endTouchSequence(event)
                if (event.rawY == startY) {
                    //Get rid of warning
                    //This seems to do nothing
                    v?.performClick()
                }
                retVal
            }
            MotionEvent.ACTION_DOWN -> startTouchSequence(event)
            MotionEvent.ACTION_MOVE -> adjustMap(event)
            else -> false
        }
    }
}