package com.kvl.cyclotrack

import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.view.animation.AccelerateInterpolator
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.view.animation.Transformation
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
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
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.gms.maps.CameraUpdate
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.*
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.kvl.cyclotrack.data.CadenceSpeedMeasurement
import com.kvl.cyclotrack.data.HeartRateMeasurement
import com.kvl.cyclotrack.util.*
import com.kvl.cyclotrack.widgets.HeadingView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt


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
        viewModel.measurements.removeObservers(viewLifecycleOwner)
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
        viewModel.measurements.removeObservers(viewLifecycleOwner)
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
                temperatureText.text = "${
                    getUserTemperature(
                        requireContext(),
                        weathers.map { it.temperature }.average()
                    )
                } ${getUserTemperatureUnit(requireContext())}"

                windIcon.visibility = View.VISIBLE
                windText.visibility = View.VISIBLE
                weathers.getAverageWind().let { wind ->
                    windText.text = "%.1f %s %s".format(
                        getUserSpeed(requireContext(), wind.first),
                        getUserSpeedUnitShort(requireContext()),
                        degreesToCardinal(wind.second.toFloat())
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
        val speedChartView: LineChart = view.findViewById(R.id.trip_details_speed_chart)
        val elevationChartView: LineChart = view.findViewById(R.id.trip_details_elevation_chart)
        val heartRateChartView: LineChart = view.findViewById(R.id.trip_details_heart_rate_chart)
        val heartRateHeadingView: HeadingView = view.findViewById(R.id.trip_details_heart_rate)
        val cadenceChartView: LineChart = view.findViewById(R.id.trip_details_cadence_chart)
        val cadenceHeadingView: HeadingView = view.findViewById(R.id.trip_details_cadence)

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
            Log.d(logTag, String.format("Displaying details for trip %d", tripId))
            viewModel.tripId = tripId

            observeWeather(view)
            viewModel.updateSplits()
            drawSplitsGrid()

            observeHeartRate(heartRateHeadingView, heartRateChartView)
            observeCadence(cadenceHeadingView, cadenceChartView)

            observeSpeed(speedChartView)

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
                ): LineDataSet {
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
                    val dataset = LineDataSet(entries, "Elevation")
                    dataset.setDrawCircles(false)
                    dataset.setDrawValues(false)
                    dataset.color =
                        ResourcesCompat.getColor(
                            resources,
                            R.color.accentColor,
                            null
                        )
                    dataset.lineWidth = 3f
                    return dataset
                }

                fun makeElevationLineChart(intervals: Array<LongRange>) {
                    configureLineChart(elevationChartView)

                    elevationChartView.xAxis.valueFormatter = object : ValueFormatter() {
                        override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                            return if (value == 0f) "" else "${
                                getUserDistance(
                                    requireContext(),
                                    value.toDouble()
                                ).roundToInt()
                            } ${getUserDistanceUnitShort(requireContext())}"
                        }
                    }

                    val legs = getTripLegs(tripMeasurements, intervals)
                    val data = LineData()

                    var totalDistance = 0f
                    legs.forEach { leg ->
                        if (leg.isNotEmpty()) {
                            makeElevationDataset(leg, totalDistance).let { dataset ->
                                data.addDataSet(dataset)
                                dataset.values.takeIf { it.isNotEmpty() }?.let {
                                    totalDistance = it.last().x
                                }
                            }
                        }
                    }
                    elevationChartView.data = data
                    elevationChartView.invalidate()
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
        } catch (e: IllegalArgumentException) {
            Log.e(logTag, "Failed to parse navigation args", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            AlertDialog.Builder(requireContext()).apply {
                setTitle("Something went wrong!")
                setMessage("There was a problem accessing the data for this ride. Please try again.")
                setPositiveButton("OK") { _, _ -> }
            }.create()
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

    private fun observeSpeed(speedChartView: LineChart) {
        viewModel.speedLiveData().observe(
            viewLifecycleOwner
        ) { observed ->
            fun makeSpeedDataset(
                getDataFunc: (ArrayList<Entry>, ArrayList<Entry>) -> Unit,
            ): Pair<LineDataSet, LineDataSet> {
                val entries = ArrayList<Entry>()
                val trend = ArrayList<Entry>()

                getDataFunc(entries, trend)

                return formatRawTrendLineData(resources, entries, trend)
            }

            fun observeGpsSpeed(
                timeStates: Array<TimeState>,
                locationMeasurements: Array<Measurements>
            ): LineData {
                Log.d(logTag, "observeGpsSpeed")
                val intervals = getTripIntervals(timeStates, locationMeasurements)
                val legs = getTripLegs(locationMeasurements, intervals)

                val data = LineData()

                legs.forEachIndexed { idx, leg ->
                    val (raw, trend) =
                        when (leg.isNotEmpty()) {
                            true -> makeSpeedDataset(
                                getSpeedDataFromGps(
                                    requireContext(), leg,
                                    intervals.sliceArray(IntRange(0, idx))
                                )
                            )
                            else -> Pair(
                                LineDataSet(
                                    emptyList(),
                                    "Speed"
                                ),
                                LineDataSet(emptyList(), "Trend")
                            )


                        }
                    data.addDataSet(raw)
                    data.addDataSet(trend)
                }
                return data
            }

            fun observeBleSpeed(
                overview: Trip,
                timeStates: Array<TimeState>,
                speedMeasurements: Array<CadenceSpeedMeasurement>
            ): LineData {
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

                val data = LineData()

                legs.forEachIndexed { idx, leg ->
                    val (raw, trend) =
                        when (leg.isNotEmpty()) {
                            true -> makeSpeedDataset(
                                getSpeedDataFromSensor(
                                    requireContext(),
                                    observed.summary!!,
                                    effectiveCircumference,
                                    leg,
                                    intervals.sliceArray(IntRange(0, idx))
                                )
                            )
                            else -> Pair(
                                LineDataSet(
                                    emptyList(),
                                    "Speed"
                                ),
                                LineDataSet(emptyList(), "Trend")
                            )


                        }
                    data.addDataSet(raw)
                    data.addDataSet(trend)
                }
                return data
            }

            fun makeSpeedLineChart() {
                configureLineChart(speedChartView)

                val speedMeasurements = observed.speedMeasurements

                if (observed.summary == null ||
                    observed.timeStates.isEmpty() ||
                    (speedMeasurements.isEmpty() && observed.locationMeasurements.isEmpty())
                ) return

                val timeStates = observed.timeStates
                val overview = observed.summary

                speedChartView.data = when (useBleSpeedData(
                    observed.speedMeasurements,
                    observed.locationMeasurements
                )) {
                    true -> observeBleSpeed(overview, timeStates, observed.speedMeasurements)
                    else -> {
                        observeGpsSpeed(
                            timeStates,
                            observed.locationMeasurements
                        )
                    }
                }

                speedChartView.invalidate()
            }

            if (observed != null) {
                makeSpeedLineChart()
            }
        }
    }

    private fun observeCadence(cadenceHeadingView: HeadingView, cadenceChartView: LineChart) {
        zipLiveData(viewModel.cadenceMeasurements, viewModel.timeState).observe(
            viewLifecycleOwner
        ) { pair ->
            val measurements = pair.first
            val timeStates = pair.second
            fun makeCadenceDataset(
                measurementsList: Array<CadenceSpeedMeasurement>,
                intervals: Array<LongRange>,
            ): Pair<LineDataSet, LineDataSet> {
                val entries = ArrayList<Entry>()
                val trend = ArrayList<Entry>()
                val intervalStart = intervals.last().first
                var trendLast: Float? = null
                var trendAlpha = 0.5f

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
                                    ).takeIf { it.isFinite() }?.let { rpm ->
                                        val timestamp =
                                            (accumulatedTime + (measurements.timestamp - intervalStart) / 1e3).toFloat()
                                        entries.add(Entry(timestamp, rpm))
                                        trendLast =
                                            (trendAlpha * rpm) + ((1 - trendAlpha) * (trendLast
                                                ?: rpm))
                                        trend.add(Entry(timestamp, trendLast!!))
                                        if (trendAlpha > 0.01f) trendAlpha -= 0.005f
                                        if (trendAlpha < 0.01f) trendAlpha = 0.01f
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

                val dataset = LineDataSet(entries, "Cadence")
                dataset.setDrawCircles(false)
                dataset.setDrawValues(false)
                val trendData = LineDataSet(trend, "Trend")
                trendData.setDrawCircles(false)
                trendData.setDrawValues(false)
                dataset.color =
                    ResourcesCompat.getColor(
                        resources,
                        R.color.secondaryGraphColor,
                        null
                    )
                dataset.lineWidth = 10f
                trendData.color =
                    ResourcesCompat.getColor(
                        resources,
                        R.color.accentColor,
                        null
                    )
                trendData.lineWidth = 3f
                return Pair(dataset, trendData)
            }

            fun makeCadenceLineChart() {
                configureLineChart(cadenceChartView)

                val intervals = getTripIntervals(timeStates, measurements)
                val legs = getTripLegs(measurements, intervals)
                val data = LineData()

                legs.forEachIndexed { idx, leg ->
                    val (raw, trend) = makeCadenceDataset(
                        leg,
                        intervals.sliceArray(IntRange(0, idx))
                    )

                    data.addDataSet(raw)
                    data.addDataSet(trend)
                }

                cadenceChartView.data = data
                cadenceChartView.invalidate()
            }

            val avgCadence = getAverageCadence(measurements)
            if (avgCadence != null) {
                cadenceHeadingView.visibility = View.VISIBLE
                cadenceChartView.visibility = View.VISIBLE
                cadenceHeadingView.value =
                    "${
                        avgCadence.takeIf { it.isFinite() }?.roundToInt() ?: 0
                    } rpm (average)"
                makeCadenceLineChart()
            }
        }
    }

    private fun observeHeartRate(heartRateView: HeadingView, heartRateChartView: LineChart) {
        zipLiveData(viewModel.heartRateMeasurements, viewModel.timeState).observe(
            viewLifecycleOwner
        ) { pair ->
            val hrmData = pair.first
            val timeStates = pair.second
            fun makeHeartRateDataset(
                measurements: Array<HeartRateMeasurement>,
                intervals: Array<LongRange>,
            ): LineDataSet {
                val entries = ArrayList<Entry>()
                val intervalStart = intervals.last().first

                val accumulatedTime = accumulateTime(intervals)

                measurements.forEach {
                    val timestamp =
                        (accumulatedTime + (it.timestamp - intervalStart) / 1e3).toFloat()
                    entries.add(Entry(timestamp, it.heartRate.toFloat()))
                }
                val dataset = LineDataSet(entries, "Heart rate")
                dataset.setDrawCircles(false)
                dataset.setDrawValues(false)
                dataset.color =
                    ResourcesCompat.getColor(
                        resources,
                        R.color.accentColor,
                        null
                    )
                dataset.lineWidth = 3f
                return dataset
            }

            fun makeHeartRateLineChart() {
                configureLineChart(heartRateChartView, 50f)

                val intervals = getTripIntervals(timeStates, hrmData)
                val legs = getTripLegs(hrmData, intervals)
                val data = LineData()

                legs.forEachIndexed { idx, leg ->
                    data.addDataSet(
                        makeHeartRateDataset(
                            leg,
                            intervals.sliceArray(IntRange(0, idx))
                        )
                    )
                }
                heartRateChartView.data = data
                heartRateChartView.invalidate()
            }

            val avgHeartRate = getAverageHeartRate(hrmData)
            if (avgHeartRate != null) {
                heartRateView.visibility = View.VISIBLE
                heartRateChartView.visibility = View.VISIBLE
                heartRateView.value = "$avgHeartRate bpm (average)"
                makeHeartRateLineChart()
            }
        }
    }
}
