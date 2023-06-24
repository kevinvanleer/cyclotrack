package com.kvl.cyclotrack

import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.view.animation.Transformation
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.MenuProvider
import androidx.core.view.doOnPreDraw
import androidx.core.view.marginTop
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.android.gms.maps.CameraUpdate
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.RoundCap
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.kvl.cyclotrack.data.CadenceSpeedMeasurement
import com.kvl.cyclotrack.data.HeartRateMeasurement
import com.kvl.cyclotrack.util.configureGoogleFit
import com.kvl.cyclotrack.util.getCaloriesBurnedLabel
import com.kvl.cyclotrack.util.getDatasets
import com.kvl.cyclotrack.util.getSpeedDataFromGps
import com.kvl.cyclotrack.util.getSpeedDataFromSensor
import com.kvl.cyclotrack.util.getTrendData
import com.kvl.cyclotrack.util.hasFitnessPermissions
import com.kvl.cyclotrack.util.useBleSpeedData
import com.kvl.cyclotrack.widgets.AxisLabelOrientation
import com.kvl.cyclotrack.widgets.AxisLabels
import com.kvl.cyclotrack.widgets.Entry
import com.kvl.cyclotrack.widgets.HeadingView
import com.kvl.cyclotrack.widgets.LineGraph
import com.kvl.cyclotrack.widgets.LineGraphAreaDataset
import com.kvl.cyclotrack.widgets.LineGraphDataset
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

data class LineChartDataset(
    val entries: ArrayList<Pair<Float, Float>>,
    val trend: ArrayList<Pair<Float, Float>>,
    val hi: ArrayList<Pair<Float, Float>>,
    val lo: ArrayList<Pair<Float, Float>>,
)

@AndroidEntryPoint
class TripDetailsFragment : Fragment(), View.OnTouchListener {
    private val logTag = "TripDetailsFragment"

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
    private lateinit var googleFitSyncStatus: GoogleFitSyncStatusEnum
    private lateinit var stravaSyncStatus: GoogleFitSyncStatusEnum


    private fun drawPath(
        polyline: PolylineOptions,
        start: Boolean,
        end: Boolean,
        pathIndex: Int,
    ) {
        if (polyline.points.isEmpty()) return

        map.addMarker(MarkerOptions().apply {
            position(polyline.points.first())
            zIndex(pathIndex * 2f)
            when (start) {
                true -> {
                    icon(
                        BitmapDescriptorFactory.fromResource(
                            R.drawable.baseline_outbound_grey_24dp
                        )
                    )
                    anchor(0.5f, 0.5f)
                    if (polyline.points.size > 1) rotation(
                        getRotation(
                            polyline.points[0],
                            polyline.points[1]
                        )
                    )
                }

                else -> {
                    icon(
                        BitmapDescriptorFactory.fromResource(
                            R.drawable.baseline_tour_resume_white_24dp
                        )
                    )
                    anchor(0f, 1f)
                }
            }
        })
        map.addMarker(MarkerOptions().apply {
            position(polyline.points.last())
            zIndex(pathIndex * 2f + 1)
            when (end) {
                true -> {
                    icon(
                        BitmapDescriptorFactory.fromResource(
                            R.drawable.baseline_sports_score_white_24dp
                        )
                    )
                    anchor(0f, 1f)
                }

                else -> {
                    icon(
                        BitmapDescriptorFactory.fromResource(
                            R.drawable.baseline_tour_pause_white_24dp
                        )
                    )
                    anchor(0f, 1f)
                }
            }
        })
        map.addPolyline(polyline)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_trip_details, container, false)
    }

    private fun configureSyncOptions(menu: Menu) {
        when (hasFitnessPermissions(requireContext())) {
            true -> {
                when (googleFitSyncStatus) {
                    GoogleFitSyncStatusEnum.SYNCED -> {
                        menu.findItem(R.id.details_menu_action_unsync).isVisible = true
                        menu.findItem(R.id.details_menu_action_sync).isVisible = false
                    }

                    GoogleFitSyncStatusEnum.NOT_SYNCED, GoogleFitSyncStatusEnum.REMOVED -> {
                        menu.findItem(R.id.details_menu_action_unsync).isVisible = false
                        menu.findItem(R.id.details_menu_action_sync).isVisible = true
                    }

                    else -> {
                        menu.findItem(R.id.details_menu_action_sync).isVisible = false
                        menu.findItem(R.id.details_menu_action_unsync).isVisible = false
                    }
                }
            }

            else -> {
                menu.findItem(R.id.details_menu_action_sync).isVisible = false
                menu.findItem(R.id.details_menu_action_unsync).isVisible = false
            }
        }
        /*
        //NOTE: TEMPORARY OVERRIDE FOR STRAVA TESTING
        menu.findItem(R.id.details_menu_action_unsync).isVisible = false
        when (stravaSyncStatus) {
            GoogleFitSyncStatusEnum.SYNCED -> {
                menu.findItem(R.id.details_menu_action_sync).isVisible = false
            }
            GoogleFitSyncStatusEnum.NOT_SYNCED, GoogleFitSyncStatusEnum.REMOVED -> {
                menu.findItem(R.id.details_menu_action_sync).isVisible = true
            }
            else -> {
                menu.findItem(R.id.details_menu_action_sync).isVisible = false
            }
        }
        */
    }

    private fun showMustBeLoggedInDialog() {
        activity?.let {
            val builder = AlertDialog.Builder(it)
            builder.apply {
                setTitle("Sign-in required")
                setMessage("This ride is synced with Google Fit. Please sign-in then delete the ride.")
                setPositiveButton("SIGN IN") { _, _ ->
                    configureGoogleFit(requireActivity())
                    //TODO: Need to wait for login to finish
                    //or force user to login through settings first
                    //unsyncAndDeleteTrip()
                }
                setNegativeButton("CANCEL") { _, _ ->
                    Log.d(logTag, "CLICKED CANCEL SIGN-IN")
                }
            }
            builder.create()
        }?.show()
    }

    private fun unsyncAndDeleteTrip() {
        viewModel.tripOverview.removeObservers(viewLifecycleOwner)
        viewModel.locationMeasurements.removeObservers(viewLifecycleOwner)
        viewModel.splits.removeObservers(viewLifecycleOwner)
        viewModel.timeState.removeObservers(viewLifecycleOwner)
        viewModel.onboardSensors.removeObservers(viewLifecycleOwner)
        WorkManager.getInstance(requireContext())
            .beginWith(
                listOf(
                    OneTimeWorkRequestBuilder<GoogleFitDeleteSessionWorker>()
                        .setInputData(
                            workDataOf(
                                "tripIds" to arrayOf(viewModel.tripId)
                            )
                        ).build()
                )
            )
            .then(
                OneTimeWorkRequestBuilder<RemoveTripWorker>()
                    .setInputData(workDataOf("tripIds" to arrayOf(viewModel.tripId)))
                    .build()
            )
            .enqueue()
        requireActivity().finish()
    }

    private fun deleteTrip() {
        Log.d(logTag, "trip overview has observers: ${viewModel.tripOverview.hasObservers()}")
        viewModel.tripOverview.removeObservers(viewLifecycleOwner)
        viewModel.locationMeasurements.removeObservers(viewLifecycleOwner)
        viewModel.splits.removeObservers(viewLifecycleOwner)
        viewModel.timeState.removeObservers(viewLifecycleOwner)
        viewModel.onboardSensors.removeObservers(viewLifecycleOwner)
        Log.d(logTag, "trip overview has observers: ${viewModel.tripOverview.hasObservers()}")
        WorkManager.getInstance(requireContext())
            .enqueue(
                OneTimeWorkRequestBuilder<RemoveTripWorker>()
                    .setInputData(workDataOf("tripIds" to arrayOf(viewModel.tripId)))
                    .build()
            )
        requireActivity().finish()
    }

    private fun showUnsyncAndDeleteDialog() =
        activity?.let {
            val builder = AlertDialog.Builder(it)
            builder.apply {
                setPositiveButton(
                    "DELETE"
                ) { _, _ ->
                    unsyncAndDeleteTrip()
                }
                setNegativeButton(
                    "CANCEL"
                ) { _, _ ->
                    Log.d("TRIP_DELETE_DIALOG", "CLICKED CANCEL")
                }
                setTitle("Delete ride?")
                setMessage("You are about to remove this ride from Cyclotrack and Google Fit. This change cannot be undone.")
            }

            builder.create()
        }?.show()

    private fun showUnsyncDialog() =
        activity?.let {
            val builder = AlertDialog.Builder(it)
            builder.apply {
                setPositiveButton(
                    "UNSYNC"
                ) { _, _ ->
                    WorkManager.getInstance(requireContext())
                        .enqueue(
                            OneTimeWorkRequestBuilder<GoogleFitDeleteSessionWorker>()
                                .setInputData(workDataOf("tripIds" to arrayOf(viewModel.tripId)))
                                .build()
                        )
                }
                setNegativeButton(
                    "CANCEL"
                ) { _, _ ->
                    Log.d(logTag, "UNSYNC: CLICKED CANCEL")
                }
                setTitle("Remove from Google Fit?")
                setMessage("You are about to remove this ride from Google Fit. If you want to add it back later select \"Sync\" from the options menu.")
            }

            builder.create()
        }?.show()

    private fun showDeleteDialog() =
        activity?.let {
            val builder = AlertDialog.Builder(it)
            builder.apply {
                setPositiveButton(
                    "DELETE"
                ) { _, _ ->
                    deleteTrip()
                }
                setNegativeButton(
                    "CANCEL"
                ) { _, _ ->
                    Log.d("TRIP_DELETE_DIALOG", "CLICKED CANCEL")
                }
                setTitle("Delete ride?")
                setMessage("You are about to remove this ride from your history. This change cannot be undone.")
            }

            builder.create()
        }?.show()

    private fun observeWeather(view: View) {
        val temperatureText = view.findViewById<TextView>(R.id.trip_details_temperature_value)
        val windText = view.findViewById<TextView>(R.id.trip_details_wind_value)
        val temperatureIcon = view.findViewById<ImageView>(R.id.trip_details_temperature_icon)
        val windIcon = view.findViewById<ImageView>(R.id.trip_details_wind_icon)
        viewModel.tripWeather.observe(viewLifecycleOwner) { weathers ->
            if (weathers.isNotEmpty()) {
                temperatureIcon.visibility = View.VISIBLE
                temperatureText.visibility = View.VISIBLE
                temperatureText.text = String(
                    "${
                        getUserTemperature(
                            requireContext(),
                            weathers.map { it.temperature }.average()
                        )
                    } ${getUserTemperatureUnit(requireContext())}".toCharArray()
                )

                windIcon.visibility = View.VISIBLE
                windText.visibility = View.VISIBLE
                weathers.getAverageWind().let { wind ->
                    windText.text = String(
                        "%.1f %s %s".format(
                            getUserSpeed(requireContext(), wind.first),
                            getUserSpeedUnitShort(requireContext()),
                            degreesToCardinal(wind.second.toFloat())
                        ).toCharArray()
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.title = ""
        Log.d(
            "backStack", "${
                findNavController().backQueue.map { it.destination.label }
            }"
        )

        addMenuProvider()

        constraintLayout = view.findViewById(R.id.TripDetailsFragment)
        maxGuide = view.findViewById(R.id.trip_details_max_map_guide)
        minGuide = view.findViewById(R.id.trip_details_min_map_guide)
        defaultGuide = view.findViewById(R.id.trip_details_default_map_guide)


        mapView = view.findViewById(R.id.trip_details_map_view)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync {
            Log.d(logTag, "GOT MAP")
            map = it
            map.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(
                    requireContext(),
                    R.raw.summary_map_style
                )
            )
            map.uiSettings.setAllGesturesEnabled(false)
        }

        titleNameView = view.findViewById(R.id.trip_details_title_name)
        titleDateView = view.findViewById(R.id.trip_details_title_date)
        notesView = view.findViewById(R.id.trip_details_notes)
        val distanceHeadingView: HeadingView = view.findViewById(R.id.trip_details_distance)
        val durationHeadingView: HeadingView = view.findViewById(R.id.trip_details_time)
        val speedHeadingView: HeadingView = view.findViewById(R.id.trip_details_speed)
        val speedChartView: ImageView = view.findViewById(R.id.trip_details_speed_chart)
        val elevationChartView: ImageView = view.findViewById(R.id.trip_details_elevation_chart)
        val heartRateChartView: ImageView = view.findViewById(R.id.trip_details_heart_rate_chart)
        val heartRateHeadingView: HeadingView = view.findViewById(R.id.trip_details_heart_rate)
        val cadenceChartView: ImageView = view.findViewById(R.id.trip_details_cadence_chart)
        val cadenceHeadingView: HeadingView = view.findViewById(R.id.trip_details_cadence)

        val strokeStyle = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 5F
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = ResourcesCompat.getColor(
                resources,
                R.color.accentColor,
                null
            )
        }
        val trendStyle = Paint(strokeStyle).apply {
            color = ResourcesCompat.getColor(
                resources,
                R.color.secondaryGraphColor,
                null
            )
        }

        scrollView = view.findViewById(R.id.trip_details_scroll_view)

        heartRateHeadingView.visibility = View.GONE
        heartRateChartView.visibility = View.GONE
        cadenceHeadingView.visibility = View.GONE
        cadenceChartView.visibility = View.GONE
        notesView.visibility = View.GONE

        view.findViewById<HeadingView>(R.id.trip_details_elevation).value =
            getUserAltitudeUnitLong(requireContext())

        val elevationAlpha = 0.05
        try {
            val tripId = args.tripId
            if (args.tripId == -1L) throw IllegalArgumentException()
            Log.d(logTag, String.format("Displaying details for trip %d", tripId))
            viewModel.tripId = tripId

            observeWeather(view)
            viewModel.updateSplits()
            drawSplitsGrid()

            observeHeartRate(
                heartRateHeadingView,
                heartRateChartView,
                strokeStyle,
            )
            observeCadence(
                cadenceHeadingView,
                cadenceChartView,
                strokeStyle,
                trendStyle,
            )

            observeSpeed(speedChartView, strokeStyle, trendStyle)

            viewModel.tripOverview.observe(viewLifecycleOwner) { overview ->
                if (overview != null) {
                    distanceHeadingView.value =
                        String.format(
                            "%.2f %s",
                            getUserDistance(requireContext(), overview.distance ?: 0.0),
                            getUserDistanceUnitShort(requireContext())
                        )
                    durationHeadingView.value = formatDuration(overview.duration ?: 0.0)
                    speedHeadingView.value = String.format(
                        "%.1f %s (average)",
                        getUserSpeed(
                            requireContext(),
                            overview.distance ?: 0.0,
                            overview.duration ?: 1.0
                        ),
                        getUserSpeedUnitShort(requireContext())
                    )

                    titleNameView.text = overview.name
                    if (overview.notes != null) {
                        notesView.visibility = View.VISIBLE
                        notesView.text = overview.notes
                    }
                }
            }

            zipLiveData(viewModel.locationMeasurements, viewModel.timeState).observe(
                viewLifecycleOwner
            ) { pair ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val measurements = pair.first
                    val timeStates = pair.second
                    val mapData = plotPath(measurements, timeStates)
                    if (this@TripDetailsFragment::map.isInitialized && mapData.bounds != null) {
                        Log.d(logTag, "Plotting path")
                        mapData.paths.forEachIndexed { idx, path ->
                            path.startCap(RoundCap())
                            path.endCap(RoundCap())
                            path.width(5f)
                            path.color(
                                ResourcesCompat.getColor(
                                    resources,
                                    R.color.accentColor,
                                    null
                                )
                            )
                            this@TripDetailsFragment.drawPath(
                                path,
                                idx == 0,
                                idx == mapData.paths.lastIndex,
                                idx
                            )
                        }
                        try {
                            map.moveCamera(
                                CameraUpdateFactory.newLatLngBounds(
                                    mapData.bounds,
                                    mapView.width,
                                    scrollView.marginTop,
                                    100
                                )
                            )
                            maxCameraPosition = map.cameraPosition
                            map.moveCamera(
                                CameraUpdateFactory.scrollBy(
                                    0f,
                                    (mapView.height - scrollView.marginTop) / 2f
                                )
                            )
                            defaultCameraPosition = map.cameraPosition
                        } catch (e: Exception) {
                            Log.e(logTag, "Couldn't draw trip details map", e)
                        }
                    }
                }
            }

            zipLiveData(viewModel.locationMeasurements, viewModel.timeState).observe(
                viewLifecycleOwner
            ) { observed ->
                val tripMeasurements = observed.first
                val timeStates = observed.second
                Log.d(logTag, "Observed change to measurements and time state")

                fun makeElevationDataset(
                    measurements: Array<Measurements>,
                    _totalDistance: Float,
                ): ArrayList<Entry> {
                    val entries = ArrayList<Entry>()
                    var totalDistance = _totalDistance
                    var lastMeasurements: Measurements? = null
                    var smoothed: Double = measurements[0].altitude
                    var smoothedLast = smoothed
                    measurements.forEach {
                        smoothed =
                            exponentialSmoothing(
                                elevationAlpha,
                                it.altitude,
                                smoothedLast
                            )
                        smoothedLast = smoothed

                        lastMeasurements?.let { last ->
                            totalDistance += getDistance(it, last)
                        }
                        lastMeasurements = it

                        entries.add(
                            Entry(
                                totalDistance,
                                getUserAltitude(
                                    requireContext(),
                                    smoothed
                                ).toFloat()
                            )
                        )
                    }
                    return entries
                }

                fun makeElevationLineChart(intervals: Array<LongRange>) {
                    val legs = getTripLegs(tripMeasurements, intervals)
                    val data = ArrayList<Entry>()

                    var totalDistance = 0f
                    legs.forEach { leg ->
                        if (leg.isNotEmpty()) {
                            makeElevationDataset(leg, totalDistance).let { dataset ->
                                data.addAll(dataset)
                                dataset.takeIf { it.isNotEmpty() }?.let {
                                    totalDistance = it.last().first
                                }
                            }
                        }
                    }

                    val xMin = data.first().first
                    val xMax = data.last().first
                    val yMin = data.minBy { element -> element.second }.second
                    val yMax = data.maxBy { element -> element.second }.second
                    val yRangePadding = (yMax - yMin) * 0.2f
                    val yViewMin = max(yMin - yRangePadding, 0f)
                    val yViewMax = yMax + yRangePadding

                    elevationChartView.setImageDrawable(
                        LineGraph(
                            areas = listOf(
                                LineGraphAreaDataset(
                                    points1 = data.toList(),
                                    points2 = listOf(Entry(xMin, yViewMin), Entry(xMax, yViewMin)),
                                    xRange = Pair(xMin, xMax),
                                    yRange = Pair(yViewMin, yViewMax),
                                    xAxisWidth = xMax - xMin,
                                    yAxisHeight = yViewMax - yViewMin,
                                    paint = Paint(strokeStyle).apply {
                                        style = Paint.Style.FILL_AND_STROKE
                                        alpha = 50
                                    }
                                )
                            ),
                            datasets = listOf(
                                LineGraphDataset(
                                    points = data.toList(),
                                    xRange = Pair(xMin, xMax),
                                    yRange = Pair(yViewMin, yViewMax),
                                    xAxisWidth = xMax - xMin,
                                    yAxisHeight = yViewMax - yViewMin,
                                    paint = strokeStyle
                                ),
                            ),
                            yLabels = AxisLabels(
                                labels = listOf(
                                    Pair(
                                        yMin, "${yMin.roundToInt()} ${
                                            getUserAltitudeUnitShort(
                                                requireContext()
                                            )
                                        }"
                                    ),
                                    Pair(
                                        yMax, "${yMax.roundToInt()} ${
                                            getUserAltitudeUnitShort(
                                                requireContext()
                                            )
                                        }"
                                    )
                                ),
                                range = Pair(yViewMin, yViewMax),
                                lines = true,
                                background = (scrollView.background as ColorDrawable).color,
                                orientation = AxisLabelOrientation.INSIDE
                            )
                        )
                    )
                }

                Log.d(
                    logTag,
                    "Recorded ${tripMeasurements.size} measurements for trip $tripId"
                )

                if (tripMeasurements.isEmpty()) return@observe
                if (timeStates.isNotEmpty()) titleDateView.text =
                    String.format(
                        "%s: %s - %s",
                        SimpleDateFormat(
                            "MMMM d",
                            Locale.US
                        ).format(Date(timeStates.first().timestamp)),
                        SimpleDateFormat(
                            "h:mm",
                            Locale.US
                        ).format(Date(timeStates.first().timestamp)),
                        SimpleDateFormat(
                            "h:mm",
                            Locale.US
                        ).format(Date(timeStates.last().timestamp))
                    )
                val intervals = getTripIntervals(timeStates, tripMeasurements)
                viewLifecycleOwner.lifecycleScope.launch {
                    val elevationChange = getElevationChange(tripMeasurements)
                    view.findViewById<HeadingView>(R.id.trip_details_elevation).value =
                        "+${
                            getUserAltitude(
                                requireContext(),
                                elevationChange.first
                            ).roundToInt()
                        }/${
                            getUserAltitude(
                                requireContext(),
                                elevationChange.second
                            ).roundToInt()
                        } ${
                            getUserAltitudeUnitLong(requireContext())
                        }"
                    makeElevationLineChart(intervals)

                    scrollView.setOnTouchListener(this@TripDetailsFragment)
                }
            }

            zipLiveData(viewModel.heartRateMeasurements, viewModel.tripOverview).observe(
                viewLifecycleOwner
            ) { pairs ->
                val measurements = pairs.first
                val overview = pairs.second
                Log.d(logTag, "Observed change to measurements and overview")
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.getCombinedBiometrics(overview.timestamp, requireContext())
                        .let { biometrics ->
                            Log.d(logTag, "biometrics: $biometrics")
                            if (biometrics.userWeight != null) {
                                Log.d(logTag, "Calculating calories burned")
                                getCaloriesBurned(biometrics, overview, measurements)
                            }
                        }
                }
            }
            if (FeatureFlags.devBuild) {
                viewModel.tripOverview.observe(viewLifecycleOwner) { trip ->
                    trip?.timestamp?.let { timestamp ->
                        getDatasets(
                            requireActivity(),
                            timestamp,
                            (timestamp + (trip.duration?.times(
                                1000
                            ) ?: 1).toLong())
                        )
                    }
                }
            }
        } catch (e: Exception) {
            when (e) {
                is java.lang.IllegalArgumentException,
                is java.lang.reflect.InvocationTargetException -> {
                    Log.e(logTag, "Failed to parse navigation args", e)
                    FirebaseCrashlytics.getInstance().recordException(e)
                    AlertDialog.Builder(requireContext()).apply {
                        setTitle("Something went wrong!")
                        setMessage("There was a problem accessing the data for this ride. Please try again.")
                        setPositiveButton("OK") { _, _ -> }
                    }.create()
                }

                else -> throw e
            }
        }
    }

    private fun getCaloriesBurned(
        biometrics: Biometrics,
        overview: Trip,
        measurements: Array<HeartRateMeasurement>,
    ) {
        Log.d(logTag, "User weight: ${overview.userWeight}")
        val caloriesHeadingView: HeadingView =
            requireView().findViewById(R.id.trip_details_calories)
        val avgHr = getAverageHeartRate(measurements)
        try {
            com.kvl.cyclotrack.util.getCaloriesBurned(
                requireContext(),
                biometrics,
                overview,
                avgHr,
            ).let {
                caloriesHeadingView.label = getCaloriesBurnedLabel(
                    requireContext(),
                    biometrics,
                    overview,
                    avgHr,
                )
                caloriesHeadingView.visibility = View.VISIBLE
                caloriesHeadingView.value = it.toString()
            }

        } catch (e: NullPointerException) {
            caloriesHeadingView.visibility = View.GONE
            Log.e(tag, "Failed to calculate calories burned", e)
        }
    }

    private fun drawSplitsGrid(
    ) {
        val splitsHeadingView: HeadingView = requireView().findViewById(R.id.trip_details_splits)
        val splitsGridView: GridLayout = requireView().findViewById(R.id.trip_details_splits_grid)
        splitsHeadingView.value = ""

        fun makeSplitRow(
            idx: Int,
            split: Split,
            maxSpeed: Float
        ): Triple<TextView, LinearLayout, TextView> {
            val distanceView = TextView(activity).apply {
                text = String.format(
                    "%d %s",
                    idx + 1, getUserDistanceUnitShort(requireContext())
                )
                layoutParams = GridLayout.LayoutParams(
                    GridLayout.spec(
                        idx + 1,
                        GridLayout.CENTER
                    ),
                    GridLayout.spec(0)
                )
            }
            val speedText = TextView(activity).apply {
                maxLines = 1
                text = String.format(
                    "%.2f %s",
                    getUserSpeed(
                        requireContext(),
                        split.distance,
                        split.duration
                    ),
                    getUserSpeedUnitShort(requireContext())
                )
            }
            val splitPrIcon = ImageView(requireContext()).apply {
                val heightDip = 12f
                val heightPx = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    heightDip,
                    resources.displayMetrics
                ).toInt()
                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        heightPx
                    ).apply { gravity = Gravity.CENTER }
                setImageResource(R.drawable.ic_trophy)
                setColorFilter(
                    Color.WHITE,
                    PorterDuff.Mode.SRC_ATOP
                )
                visibility = View.INVISIBLE
            }
            val speedBar = LinearLayout(activity).apply {
                background = ResourcesCompat.getDrawable(
                    resources,
                    R.drawable.rounded_corner,
                    null
                )
                addView(speedText)
                addView(splitPrIcon)
            }
            val prIcon = ImageView(requireContext()).apply {
                val heightDip = 14f
                val heightPx = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    heightDip,
                    resources.displayMetrics
                ).toInt()
                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        heightPx
                    ).apply { gravity = Gravity.CENTER }
                setImageResource(R.drawable.ic_trophy)
                setColorFilter(
                    requireContext().getColor(R.color.primaryDarkColor),
                    PorterDuff.Mode.SRC_ATOP
                )
                visibility = View.INVISIBLE
            }
            val timeView = TextView(activity).apply {
                text = formatDuration(split.totalDuration)
                layoutParams = GridLayout.LayoutParams(
                    GridLayout.spec(
                        idx + 1,
                        GridLayout.CENTER
                    ),
                    GridLayout.spec(2)
                )
            }

            val speedView = LinearLayout(activity).apply {
                layoutParams =
                    GridLayout.LayoutParams(
                        GridLayout.spec(idx + 1),
                        GridLayout.spec(1, 100f)
                    )

                doOnPreDraw {
                    speedBar.minimumWidth =
                        (measuredWidth * split.distance / split.duration / maxSpeed).toInt() - prIcon.measuredWidth
                }
                addView(speedBar)
                addView(Space(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
                })
                addView(prIcon)
            }

            viewLifecycleOwner.lifecycleScope.launch {
                val fastestTotalDistance = viewModel.getFastestDistance(
                    getUserDistance(requireContext(), split.totalDistance).roundToInt(),
                    getUserDistance(requireContext(), 1.0), 3
                )
                prIcon.visibility =
                    when (fastestTotalDistance.firstOrNull()?.tripId == split.tripId && fastestTotalDistance.size == 3) {
                        true -> View.VISIBLE
                        else -> View.INVISIBLE
                    }
                val fastestSplit = viewModel.getFastestSplit(
                    getUserDistance(requireContext(), split.totalDistance).roundToInt(),
                    getUserDistance(requireContext(), 1.0), 3
                )
                splitPrIcon.visibility =
                    when (fastestSplit.firstOrNull()?.tripId == split.tripId && fastestSplit.size == 3) {
                        true -> View.VISIBLE
                        else -> View.INVISIBLE
                    }
            }

            return Triple(distanceView, speedView, timeView)
        }

        fun makeSplitsGrid(splits: Array<Split>) {
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
                val splitSpeed = it.distance / it.duration
                if (splitSpeed > maxSpeed) maxSpeed = splitSpeed.toFloat()
            }

            splits.forEachIndexed { idx, split ->
                val (distanceView, speedView, timeView) = makeSplitRow(idx, split, maxSpeed)
                splitsGridView.addView(distanceView)
                splitsGridView.addView(speedView)
                splitsGridView.addView(timeView)
            }
        }

        viewModel.splits.observe(viewLifecycleOwner) { splits ->
            makeSplitsGrid(splits)
        }
    }

    private fun addMenuProvider() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
                // Inflate the menu; this adds items to the action bar if it is present.
                inflater.inflate(R.menu.menu_details, menu)
                Log.d(logTag, "Options menu created")
                viewModel.tripOverview.observe(viewLifecycleOwner) {
                    googleFitSyncStatus = it.googleFitSyncStatus
                    stravaSyncStatus = it.stravaSyncStatus
                }
            }

            override fun onPrepareMenu(menu: Menu) {
                super.onPrepareMenu(menu)
                if (this@TripDetailsFragment::googleFitSyncStatus.isInitialized &&
                    this@TripDetailsFragment::stravaSyncStatus.isInitialized
                ) configureSyncOptions(menu)
            }

            override fun onMenuItemSelected(item: MenuItem): Boolean {
                Log.d(logTag, "Options menu clicked")
                return when (item.itemId) {
                    R.id.details_menu_action_edit -> {
                        try {
                            findNavController()
                                .navigate(
                                    TripDetailsFragmentDirections.actionEditTrip(
                                        args.tripId,
                                        titleNameView.text.toString(),
                                        titleDateView.text.toString(),
                                        notesView.text.toString()
                                    )
                                )
                        } catch (e: IllegalArgumentException) {
                            Log.e(logTag, e.message, e)
                        }
                        true
                    }

                    R.id.details_menu_action_delete -> {
                        Log.d(logTag, "Options menu clicked delete")
                        when (googleFitSyncStatus) {
                            GoogleFitSyncStatusEnum.SYNCED, GoogleFitSyncStatusEnum.DIRTY -> {
                                when (hasFitnessPermissions(requireContext())) {
                                    false -> showMustBeLoggedInDialog()
                                    else -> showUnsyncAndDeleteDialog()
                                }
                            }

                            else -> showDeleteDialog()
                        }
                        true
                    }

                    R.id.details_menu_action_export_xlsx -> {
                        if (requireActivity().checkSelfPermission(
                                "android.permission.WRITE_EXTERNAL_STORAGE"
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            ActivityCompat.requestPermissions(
                                requireActivity(),
                                arrayOf("android.permission.WRITE_EXTERNAL_STORAGE"),
                                0
                            )
                            // TODO: Consider calling
                            //    ActivityCompat#requestPermissions
                            // here to request the missing permissions, and then overriding
                            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                            //                                          int[] grantResults)
                            // to handle the case where the user grants the permission. See the documentation
                            // for ActivityCompat#requestPermissions for more details.
                        }
                        WorkManager.getInstance(requireContext())
                            .enqueue(
                                OneTimeWorkRequestBuilder<ExportTripWorker>()
                                    .setInputData(
                                        workDataOf(
                                            "tripId" to viewModel.tripId,
                                            "fileType" to "xlsx"
                                        )
                                    )
                                    .build()
                            )
                        true
                    }

                    R.id.details_menu_action_export_fit -> {
                        WorkManager.getInstance(requireContext())
                            .enqueue(
                                OneTimeWorkRequestBuilder<ExportTripWorker>()
                                    .setInputData(
                                        workDataOf(
                                            "tripId" to viewModel.tripId,
                                            "fileType" to "fit"
                                        )
                                    )
                                    .build()
                            )
                        true
                    }

                    R.id.details_menu_action_sync -> {
                        WorkManager.getInstance(requireContext())
                            .enqueue(
                                OneTimeWorkRequestBuilder<GoogleFitCreateSessionWorker>()
                                    .setInputData(workDataOf("tripId" to viewModel.tripId)).build()
                            )
                        /*WorkManager.getInstance(requireContext())
                            .enqueue(
                                OneTimeWorkRequestBuilder<StravaCreateActivityWorker>()
                                    .setInputData(workDataOf("tripId" to viewModel.tripId)).build()
                            )*/
                        true
                    }

                    R.id.details_menu_action_unsync -> {
                        showUnsyncDialog()
                        true
                    }

                    else -> {
                        Log.w(logTag, "unimplemented menu item selected")
                        false
                    }
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(logTag, "CREATING TRIP DETAILS FRAGMENT")
        Log.d(
            "backStack", "${
                findNavController().backQueue.map { it.destination.label }
            }"
        )
        if (this::mapView.isInitialized) mapView.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        Log.d(
            "backStack", "${
                findNavController().backQueue.map { it.destination.label }
            }"
        )
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
        Log.v("TOUCH_SEQ", "Start sequence: $startY")
        return true
    }

    private fun adjustMap(event: MotionEvent?): Boolean {
        val newHeight =
            (startHeight + (event?.rawY ?: startY) - startY).toInt()

        if (scrollView.scrollY > 0 || (startHeight == minGuide.top && ((event?.rawY
                ?: startY) - startY < 0))
        ) {
            startY = event?.rawY ?: startY
            return false
        }

        Log.v(
            "TOUCH_SEQ_MOVE",
            "startHeight:$startHeight, rawY:${event?.rawY}, startY:$startY, newHeight:$newHeight"
        )
        val marginParams: ViewGroup.MarginLayoutParams =
            scrollView.layoutParams as ViewGroup.MarginLayoutParams
        marginParams.topMargin = newHeight
        scrollView.layoutParams = marginParams

        return true
    }

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
        Log.v(
            "TOUCH_SEQ_END",
            "startHeight:$startHeight, rawY:${event?.rawY}, startY:$startY, newHeight:$newHeight, currentHeight:$currentHeight, delta:$delta"
        )

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

        if (this::map.isInitialized) {
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
        }

        return true
    }

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

    private fun observeSpeed(
        speedChartView: ImageView,
        strokeStyle: Paint,
        trendStyle: Paint,
    ) {
        viewModel.speedLiveData().observe(
            viewLifecycleOwner
        ) { observed ->
            fun makeSpeedDataset(
                getDataFunc: (ArrayList<Entry>, ArrayList<Entry>, ArrayList<Entry>, ArrayList<Entry>) -> Unit,
            ): LineChartDataset {
                val entries = ArrayList<Entry>()
                val trend = ArrayList<Entry>()
                val hi = ArrayList<Entry>()
                val lo = ArrayList<Entry>()

                getDataFunc(entries, trend, hi, lo)

                return LineChartDataset(entries = entries, trend = trend, hi = hi, lo = lo)
            }

            fun observeGpsSpeed(
                timeStates: Array<TimeState>,
                locationMeasurements: Array<Measurements>,
                avgSpeed: Float
            ): LineChartDataset {
                Log.d(logTag, "observeGpsSpeed")
                val intervals = getTripIntervals(timeStates, locationMeasurements)
                val legs = getTripLegs(locationMeasurements, intervals)

                val allData = LineChartDataset(
                    ArrayList(),
                    ArrayList(),
                    ArrayList(),
                    ArrayList(),
                )
                legs.forEachIndexed { idx, leg ->
                    when (leg.isNotEmpty()) {
                        true -> makeSpeedDataset(
                            getSpeedDataFromGps(
                                requireContext(), leg,
                                intervals.sliceArray(IntRange(0, idx)),
                                avgSpeed
                            )
                        )

                        else -> LineChartDataset(
                            ArrayList(),
                            ArrayList(),
                            ArrayList(),
                            ArrayList(),
                        )
                    }.let {
                        allData.entries.addAll(it.entries)
                        allData.trend.addAll(it.trend)
                        allData.hi.addAll(it.hi)
                        allData.lo.addAll(it.lo)
                    }
                }
                return allData
            }

            fun observeBleSpeed(
                overview: Trip,
                timeStates: Array<TimeState>,
                speedMeasurements: Array<CadenceSpeedMeasurement>,
                avgSpeed: Float
            ): LineChartDataset {
                val effectiveCircumference =
                    getEffectiveCircumference(overview, speedMeasurements)
                val tripId = overview.id

                val intervals = getTripIntervals(timeStates, speedMeasurements)
                val legs = getTripLegs(speedMeasurements, intervals)
                Log.d(logTag, "Effective circumference trip $tripId: $effectiveCircumference")
                Log.d(
                    logTag,
                    "Auto circumference trip $tripId: ${overview.autoWheelCircumference}"
                )

                effectiveCircumference?.let { e ->
                    overview.autoWheelCircumference?.let { a ->
                        Log.d(
                            logTag,
                            "Auto circumference variance: ${(a / e - 1f)}"
                        )
                    }
                }
                Log.d(
                    logTag,
                    "User circumference trip $tripId: ${overview.userWheelCircumference}"
                )

                val allData = LineChartDataset(
                    ArrayList(),
                    ArrayList(),
                    ArrayList(),
                    ArrayList(),
                )
                legs.forEachIndexed { idx, leg ->
                    when (leg.isNotEmpty()) {
                        true -> makeSpeedDataset(
                            getSpeedDataFromSensor(
                                requireContext(),
                                observed.summary!!,
                                effectiveCircumference,
                                leg,
                                intervals.sliceArray(IntRange(0, idx)),
                                avgSpeed
                            )
                        )

                        else -> LineChartDataset(
                            ArrayList(),
                            ArrayList(),
                            ArrayList(),
                            ArrayList(),
                        )
                    }.let {
                        allData.entries.addAll(it.entries)
                        allData.trend.addAll(it.trend)
                        allData.hi.addAll(it.hi)
                        allData.lo.addAll(it.lo)
                    }
                }
                return allData
            }

            fun makeSpeedLineChart(avgSpeed: Float) {
                val speedMeasurements = observed.speedMeasurements

                if (observed.summary == null ||
                    observed.timeStates.isEmpty() ||
                    (speedMeasurements.isEmpty() && observed.locationMeasurements.isEmpty())
                ) return

                val timeStates = observed.timeStates
                val overview = observed.summary

                when (useBleSpeedData(
                    observed.speedMeasurements,
                    observed.locationMeasurements
                )) {
                    true -> observeBleSpeed(
                        overview,
                        timeStates,
                        observed.speedMeasurements,
                        avgSpeed
                    )

                    else -> {
                        observeGpsSpeed(
                            timeStates,
                            observed.locationMeasurements,
                            avgSpeed
                        )
                    }
                }.let { lineData ->
                    val xMin = lineData.trend.first().first
                    val xMax = lineData.trend.last().first
                    val yMin = lineData.trend.minBy { element -> element.second }.second
                    val yMax = lineData.trend.maxBy { element -> element.second }.second
                    val dataMax = lineData.entries.maxBy { element -> element.second }.second
                    val yRangePadding = (yMax - yMin) * 0.2f
                    val yViewMin = max(yMin - yRangePadding, 0f)

                    val dataMaxPadding = (dataMax - yMin) * 0.2f

                    speedChartView.setImageDrawable(LineGraph(
                        datasets = listOf(
                            LineGraphDataset(
                                points = lineData.trend.toList(),
                                xRange = Pair(xMin, xMax),
                                xAxisWidth = xMax - xMin,
                                yRange = Pair(yViewMin, dataMax + dataMaxPadding),
                                yAxisHeight = dataMax + dataMaxPadding - yViewMin,
                                paint = strokeStyle
                            ),
                        ),
                        areas = listOf(
                            LineGraphAreaDataset(
                                points1 = lineData.hi.toList(),
                                points2 = lineData.lo.toList(),
                                xRange = Pair(xMin, xMax),
                                xAxisWidth = xMax - xMin,
                                yRange = Pair(yViewMin, dataMax + dataMaxPadding),
                                yAxisHeight = dataMax + dataMaxPadding - yViewMin,
                                paint = trendStyle.apply { style = Paint.Style.FILL_AND_STROKE }
                            ),
                        ),
                        yLabels = AxisLabels(
                            labels = listOf(
                                Pair(
                                    dataMax, String.format(
                                        "%.1f %s",
                                        dataMax,
                                        getUserSpeedUnitShort(requireContext())
                                    )
                                ),
                                Pair(
                                    avgSpeed, String.format(
                                        "%.1f %s",
                                        avgSpeed,
                                        getUserSpeedUnitShort(requireContext())
                                    )
                                )
                            ),
                            range = Pair(yViewMin, dataMax + dataMaxPadding),
                            lines = true,
                            background = (scrollView.background as ColorDrawable).color
                        )
                    ))
                }
            }

            if (observed != null) {
                val avgSpeed = getUserSpeed(
                    requireContext(),
                    observed.summary?.distance ?: 0.0,
                    observed.summary?.duration ?: 1.0
                )
                makeSpeedLineChart(avgSpeed)
            }
        }
    }

    private fun observeCadence(
        cadenceHeadingView: HeadingView,
        cadenceChartView: ImageView,
        strokeStyle: Paint,
        trendStyle: Paint,
    ) {
        zipLiveData(viewModel.cadenceMeasurements, viewModel.timeState).observe(
            viewLifecycleOwner
        ) { pair ->
            val measurements = pair.first
            val timeStates = pair.second
            fun makeCadenceDataset(
                measurementsList: Array<CadenceSpeedMeasurement>,
                intervals: Array<LongRange>,
                avgCadence: Float,
            ): LineChartDataset {
                val entries = ArrayList<Pair<Float, Float>>()
                val hi = ArrayList<Pair<Float, Float>>()
                val lo = ArrayList<Pair<Float, Float>>()
                val trend = ArrayList<Pair<Float, Float>>()
                val intervalStart = intervals.last().first
                var trendLast: Float? = null
                var trendAlpha = 0.5f
                var hiLast: Float? = null
                var loLast: Float? = null

                val accumulatedTime = accumulateTime(intervals)
                var lastMeasurements: CadenceSpeedMeasurement? = null
                measurementsList.forEach { measurements ->
                    lastMeasurements
                        ?.let { last ->
                            if (validateCadence(measurements, last)) {
                                try {
                                    getRpm(
                                        rev = measurements.revolutions,
                                        revLast = last.revolutions,
                                        time = measurements.lastEvent,
                                        timeLast = last.lastEvent,
                                        delta = measurements.timestamp - last.timestamp
                                    )
                                        .takeIf { it.isFinite() }
                                        ?.let { rpm ->
                                            val timestamp =
                                                (accumulatedTime + (measurements.timestamp - intervalStart) / 1e3).toFloat()

                                            entries.add(
                                                Pair(
                                                    timestamp,
                                                    rpm
                                                )
                                            )
                                            getTrendData(
                                                rpm,
                                                trendAlpha,
                                                avgCadence,
                                                trendLast,
                                                hiLast,
                                                loLast
                                            ).let { (trendNew, hiNew, loNew) ->
                                                trend.add(
                                                    Pair(
                                                        timestamp,
                                                        trendNew
                                                    )
                                                )
                                                trendLast = trendNew
                                                hiNew?.let {
                                                    hi.add(
                                                        Pair(
                                                            timestamp,
                                                            it
                                                        )
                                                    )
                                                    hiLast = it
                                                }
                                                loNew?.let {
                                                    lo.add(
                                                        Pair(
                                                            timestamp,
                                                            it
                                                        )
                                                    )
                                                    loLast = it
                                                }
                                            }
                                            if (trendAlpha > 0.01f) trendAlpha -= 0.005f
                                            if (trendAlpha < 0.01f) trendAlpha =
                                                0.01f
                                        }

                                } catch (e: Exception) {
                                    Log.e(
                                        logTag,
                                        "Could not create rpm value for timestamp ${measurements.timestamp}"
                                    )
                                }
                            }
                        }
                    lastMeasurements = measurements
                }

                return LineChartDataset(entries = entries, trend = trend, hi = hi, lo = lo)
            }

            fun makeCadenceLineChart(avgCadence: Float) {
                val intervals = getTripIntervals(timeStates, measurements)
                val legs = getTripLegs(measurements, intervals)
                val rawData = ArrayList<Pair<Float, Float>>()
                val trendData = ArrayList<Pair<Float, Float>>()
                val hiData = ArrayList<Pair<Float, Float>>()
                val loData = ArrayList<Pair<Float, Float>>()

                legs.forEachIndexed { idx, leg ->
                    val results = makeCadenceDataset(
                        leg,
                        intervals.sliceArray(IntRange(0, idx)),
                        avgCadence
                    )

                    rawData.addAll(results.entries)
                    trendData.addAll(results.trend)
                    hiData.addAll(results.hi)
                    loData.addAll(results.lo)
                }

                val xMin = trendData.first().first
                val xMax = trendData.last().first
                val yMin = trendData.minBy { element -> element.second }.second
                val yMax = trendData.maxBy { element -> element.second }.second
                val dataMax = rawData.maxBy { element -> element.second }.second
                val yRangePadding = (yMax - yMin) * 0.2f
                val yViewMin = max(yMin - yRangePadding, 0f)

                val dataMaxPadding = (dataMax - yMin) * 0.2f

                Log.d(logTag, "max hiData: ${hiData.maxBy { e -> e.second }.second}")
                cadenceChartView.setImageDrawable(
                    LineGraph(
                        datasets = listOf(
                            LineGraphDataset(
                                points = trendData.toList(),
                                xRange = Pair(xMin, xMax),
                                xAxisWidth = xMax - xMin,
                                yRange = Pair(yViewMin, dataMax + dataMaxPadding),
                                yAxisHeight = dataMax + dataMaxPadding - yViewMin,
                                paint = strokeStyle
                            ),
                        ),
                        areas = listOf(
                            LineGraphAreaDataset(
                                points1 = hiData.toList(),
                                points2 = loData.toList(),
                                xRange = Pair(xMin, xMax),
                                xAxisWidth = xMax - xMin,
                                yRange = Pair(yViewMin, dataMax + dataMaxPadding),
                                yAxisHeight = dataMax + dataMaxPadding - yViewMin,
                                paint = trendStyle.apply { style = Paint.Style.FILL_AND_STROKE }
                            ),
                        ),
                        yLabels = AxisLabels(
                            labels = listOf(
                                Pair(dataMax, "${dataMax.roundToInt()} rpm"),
                                Pair(avgCadence, "${avgCadence.roundToInt()} rpm")
                            ),
                            range = Pair(yViewMin, dataMax + dataMaxPadding),
                            lines = true,
                            background = (scrollView.background as ColorDrawable).color,
                            orientation = AxisLabelOrientation.INSIDE
                        )
                    )
                )
            }

            val avgCadence = getAverageCadence(measurements)
            if (avgCadence != null) {
                cadenceHeadingView.visibility = View.VISIBLE
                cadenceChartView.visibility = View.VISIBLE
                cadenceHeadingView.value =
                    "${
                        avgCadence.takeIf { it.isFinite() }?.roundToInt() ?: 0
                    } rpm (average)"
                makeCadenceLineChart(avgCadence)
            }
        }
    }

    private fun observeHeartRate(
        heartRateView: HeadingView,
        heartRateChartView: ImageView,
        strokeStyle: Paint,
    ) {
        zipLiveData(viewModel.heartRateMeasurements, viewModel.timeState).observe(
            viewLifecycleOwner
        ) { pair ->
            val hrmData = pair.first
            val timeStates = pair.second
            fun makeHeartRateDataset(
                measurements: Array<HeartRateMeasurement>,
                intervals: Array<LongRange>,
            ): ArrayList<Pair<Float, Float>> {
                val entries = ArrayList<Pair<Float, Float>>()
                val intervalStart = intervals.last().first

                val accumulatedTime = accumulateTime(intervals)

                measurements.forEach {
                    val timestamp =
                        (accumulatedTime + (it.timestamp - intervalStart) / 1e3).toFloat()
                    entries.add(Pair(timestamp, it.heartRate.toFloat()))
                }

                return entries
            }

            fun makeHeartRateLineChart(avgHeartRate: Short) {
                val intervals = getTripIntervals(timeStates, hrmData)
                val legs = getTripLegs(hrmData, intervals)
                val data = ArrayList<Pair<Float, Float>>()

                legs.forEachIndexed { idx, leg ->
                    data.addAll(
                        makeHeartRateDataset(
                            leg,
                            intervals.sliceArray(IntRange(0, idx))
                        )
                    )
                }

                val xMin = data.first().first
                val xMax = data.last().first
                val yMin = data.minBy { element -> element.second }.second
                val yMax = data.maxBy { element -> element.second }.second
                val yRangePadding = (yMax - yMin) * 0.2f
                val yViewMin = max(yMin - yRangePadding, 0f)
                val yViewMax = yMax + yRangePadding

                heartRateChartView.setImageDrawable(
                    LineGraph(
                        datasets = listOf(
                            LineGraphDataset(
                                points = data.toList(),
                                xRange = Pair(xMin, xMax),
                                yRange = Pair(yViewMin, yViewMax),
                                xAxisWidth = xMax - xMin,
                                yAxisHeight = yViewMax - yViewMin,
                                paint = strokeStyle
                            )
                        ),
                        yLabels = AxisLabels(
                            labels = listOf(
                                Pair(yMax, "${yMax.roundToInt()} bpm"),
                                Pair(avgHeartRate.toFloat(), "$avgHeartRate bpm")
                            ),
                            range = Pair(yViewMin, yViewMax),
                            lines = true,
                            background = (scrollView.background as ColorDrawable).color,
                        )
                    )
                )
            }

            val avgHeartRate = getAverageHeartRate(hrmData)
            if (avgHeartRate != null) {
                heartRateView.visibility = View.VISIBLE
                heartRateChartView.visibility = View.VISIBLE
                heartRateView.value = "$avgHeartRate bpm (average)"
                makeHeartRateLineChart(avgHeartRate)
            }
        }
    }
}
