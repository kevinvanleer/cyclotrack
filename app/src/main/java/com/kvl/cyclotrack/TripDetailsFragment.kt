package com.kvl.cyclotrack

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.view.animation.AccelerateInterpolator
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.view.animation.Transformation
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.marginTop
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
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
import kotlin.math.roundToInt


@AndroidEntryPoint
class TripDetailsFragment : Fragment(), View.OnTouchListener {
    val TAG = "TRIP_DETAILS_FRAGMENT"
    val logTag = "TripDetailsFragment"

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

    private val requestCreateExport =
        registerForActivityResult(ActivityResultContracts.CreateDocument()) { uri ->
            if (uri != null) {
                exportTripData(requireContext().contentResolver, uri)
            }
        }


    private fun exportTripData(contentResolver: ContentResolver, uri: Uri) {
        fun getUriFilePart(): String? {
            val result = uri.path
            val cut = result!!.lastIndexOf('/')
            return if (cut != -1) {
                result.substring(cut + 1)
            } else null
        }

        fun getFileName(): String? {
            return if (uri.scheme == "content") {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        return cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                    } else null
                }
            } else {
                getUriFilePart()
            }
        }

        val inProgressBuilder = NotificationCompat.Builder(requireContext(),
            getString(R.string.notification_export_trip_in_progress_id))
            .setSmallIcon(R.drawable.ic_cyclotrack_notification)
            .setContentTitle("Export in progress...")
            .setProgress(1, 0, true)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Data export \"${getFileName()}\" will finish soon."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        val inProgressId = getUriFilePart()?.toIntOrNull() ?: 0
        with(NotificationManagerCompat.from(requireContext())) {
            notify(inProgressId, inProgressBuilder.build())
        }
        Toast.makeText(requireContext(),
            "You'll be notified when the export is complete.",
            Toast.LENGTH_SHORT).show()
        val exporter = viewModel.exportData()
        exporter.observeForever(
            object : Observer<TripDetailsViewModel.ExportData> {
                override fun onChanged(exportData: TripDetailsViewModel.ExportData?) {
                    if (exportData?.summary != null &&
                        exportData.measurements != null &&
                        exportData.timeStates != null &&
                        exportData.splits != null &&
                        exportData.onboardSensors != null
                    ) {
                        Log.d(TAG,
                            "Exporting trip...")
                        Log.d(TAG, "${getFileName()}")

                        exportRideToXlsx(contentResolver,
                            uri,
                            exportData)
                        exporter.removeObserver(this)

                        //val exportMimeType = "application/zip"
                        val exportMimeType =
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        val viewFileIntent = Intent().apply {
                            action = Intent.ACTION_VIEW
                            setDataAndType(uri, exportMimeType)
                            flags = flags or Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_GRANT_READ_URI_PERMISSION
                        }
                        val viewFilePendingIntent = PendingIntent.getActivity(requireContext(),
                            uri.hashCode() * 100 + 1,
                            viewFileIntent,
                            PendingIntent.FLAG_IMMUTABLE)

                        val chooserIntent = Intent.createChooser(Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_STREAM, uri)
                            setDataAndType(uri, exportMimeType)
                            flags = flags or Intent.FLAG_ACTIVITY_NEW_TASK
                        }, "title")
                        val sharePendingIntent = PendingIntent.getActivity(requireContext(),
                            uri.hashCode() * 100 + 2,
                            chooserIntent,
                            PendingIntent.FLAG_IMMUTABLE)

                        val deleteIntent = Intent(requireContext(),
                            DeleteExportBroadcastReceiver::class.java).apply {
                            action = getString(R.string.intent_action_delete_exported_data)
                            putExtra("TRIP_ID", exportData.summary?.id)
                            data = uri
                        }
                        val deletePendingIntent = PendingIntent.getBroadcast(requireContext(),
                            uri.hashCode() * 100 + 3,
                            deleteIntent,
                            PendingIntent.FLAG_ONE_SHOT)
                        val builder = NotificationCompat.Builder(requireContext(),
                            getString(R.string.notification_export_trip_id))
                            .setSmallIcon(R.drawable.ic_cyclotrack_notification)
                            .setContentTitle("Export complete!")
                            .setContentIntent(viewFilePendingIntent)
                            .setAutoCancel(true)
                            .setStyle(NotificationCompat.BigTextStyle()
                                .bigText("Data export \"${getFileName()}\" is ready in your downloads folder."))
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            .addAction(0,
                                "SHARE",
                                sharePendingIntent)
                            .addAction(0,
                                "DELETE",
                                deletePendingIntent)
                        with(NotificationManagerCompat.from(requireContext())) {
                            cancel(inProgressId)
                            notify(exportData.summary?.id?.toInt() ?: 0, builder.build())
                        }
                    }
                }
            })
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.trip_details_fragment, container, false)
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
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.details_menu, menu)
        Log.d(TAG, "Options menu created")
        viewModel.tripOverview().observe(viewLifecycleOwner, {
            googleFitSyncStatus = it.googleFitSyncStatus
            configureSyncOptions(menu)
        })
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
        WorkManager.getInstance(requireContext())
            .beginWith(listOf(
                OneTimeWorkRequestBuilder<GoogleFitDeleteSessionWorker>()
                    .setInputData(workDataOf("tripIds" to arrayOf(
                        viewModel.tripId)))
                    .build()))
            .then(OneTimeWorkRequestBuilder<RemoveTripWorker>()
                .setInputData(workDataOf("tripIds" to arrayOf(viewModel.tripId)))
                .build())
            .enqueue()
        findNavController().navigate(R.id.action_remove_trip)
    }

    private fun deleteTrip() {
        WorkManager.getInstance(requireContext())
            .enqueue(OneTimeWorkRequestBuilder<RemoveTripWorker>()
                .setInputData(workDataOf("tripIds" to arrayOf(viewModel.tripId)))
                .build())
        findNavController().navigate(R.id.action_remove_trip)
    }

    private fun showUnsyncAndDeleteDialog() =
        activity?.let {
            val builder = AlertDialog.Builder(it)
            builder.apply {
                setPositiveButton("DELETE"
                ) { _, _ ->
                    unsyncAndDeleteTrip()
                }
                setNegativeButton("CANCEL"
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
                setPositiveButton("UNSYNC"
                ) { _, _ ->
                    WorkManager.getInstance(requireContext())
                        .enqueue(OneTimeWorkRequestBuilder<GoogleFitDeleteSessionWorker>()
                            .setInputData(workDataOf("tripIds" to arrayOf(viewModel.tripId)))
                            .build())
                }
                setNegativeButton("CANCEL"
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
                setPositiveButton("DELETE"
                ) { _, _ ->
                    deleteTrip()
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.d(TAG, "Options menu clicked")
        return when (item.itemId) {
            R.id.details_menu_action_edit -> {
                try {
                    findNavController()
                        .navigate(TripDetailsFragmentDirections.actionEditTrip(args.tripId,
                            titleNameView.text.toString(),
                            titleDateView.text.toString(),
                            notesView.text.toString()))
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, e.message, e)
                }
                true
            }
            R.id.details_menu_action_delete -> {
                Log.d(TAG, "Options menu clicked delete")
                when (hasFitnessPermissions(requireContext())) {
                    false -> showMustBeLoggedInDialog()
                    else ->
                        when (googleFitSyncStatus) {
                            GoogleFitSyncStatusEnum.SYNCED,
                            GoogleFitSyncStatusEnum.FAILED,
                            -> showUnsyncAndDeleteDialog()
                            else -> showDeleteDialog()
                        }
                }
                true
            }
            R.id.details_menu_action_export -> {
                viewModel.tripOverview().observe(viewLifecycleOwner, object : Observer<Trip> {
                    override fun onChanged(t: Trip) {
                        requestCreateExport.launch("cyclotrack_${
                            String.format("%06d", t.id)
                        }_${t.name?.replace(" ", "-") ?: "unknown"}.xlsx")
                        viewModel.tripOverview().removeObserver(this)
                    }
                })
                true
            }
            R.id.details_menu_action_sync -> {
                WorkManager.getInstance(requireContext())
                    .enqueue(OneTimeWorkRequestBuilder<GoogleFitCreateSessionWorker>()
                        .setInputData(workDataOf("tripId" to viewModel.tripId)).build())
                true
            }
            R.id.details_menu_action_unsync -> {
                showUnsyncDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)
        activity?.title = "Cyclotrack"

        constraintLayout = view.findViewById(R.id.TripDetailsFragment)
        maxGuide = view.findViewById(R.id.trip_details_max_map_guide)
        minGuide = view.findViewById(R.id.trip_details_min_map_guide)
        defaultGuide = view.findViewById(R.id.trip_details_default_map_guide)


        mapView = view.findViewById(R.id.trip_details_map_view)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync {
            Log.d(TAG, "GOT MAP")
            map = it
            map.setMapStyle(MapStyleOptions.loadRawResourceStyle(context, R.raw.summary_map_style))
            map.uiSettings.setAllGesturesEnabled(false)
        }

        titleNameView = view.findViewById(R.id.trip_details_title_name)
        titleDateView = view.findViewById(R.id.trip_details_title_date)
        notesView = view.findViewById(R.id.trip_details_notes)
        val distanceHeadingView: HeadingView = view.findViewById(R.id.trip_details_distance)
        val durationHeadingView: HeadingView = view.findViewById(R.id.trip_details_time)
        val caloriesHeadingView: HeadingView = view.findViewById(R.id.trip_details_calories)
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


        val elevationAlpha = 0.05
        fun getElevationChange(measurements: Array<CriticalMeasurements>): Pair<Double, Double> {
            return smooth(elevationAlpha,
                measurements.map { it.altitude }.toTypedArray()).let {
                accumulateAscentDescent(it, 10.0)
            }
        }

        fun getAverageHeartRate(measurements: Array<CriticalMeasurements>): Short? {
            var sum = 0f
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
                (sum / count).roundToInt().toShort()
            }
        }

        fun getAverageSpeedRpm(measurements: Array<CriticalMeasurements>): Float? {
            return try {
                val speedMeasurements = measurements.filter { it.speedRevolutions != null }
                val totalRevs = speedMeasurements.last().speedRevolutions?.let {
                    getDifferenceRollover(it,
                        speedMeasurements.first().speedRevolutions!!)
                }
                val duration =
                    (speedMeasurements.last().time - speedMeasurements.first().time) / 1000 / 60

                totalRevs?.toFloat()?.div(duration).takeIf { it?.isFinite() ?: false }
            } catch (e: Exception) {
                null
            }
        }

        fun getAverageCadence(measurements: Array<CriticalMeasurements>): Float? {
            return try {
                val cadenceMeasurements = measurements.filter { it.cadenceRevolutions != null }
                val totalRevs = cadenceMeasurements.last().cadenceRevolutions?.let {
                    getDifferenceRollover(it,
                        cadenceMeasurements.first().cadenceRevolutions!!)
                }
                val duration =
                    (cadenceMeasurements.last().time - cadenceMeasurements.first().time) / 1000 / 60

                totalRevs?.toFloat()?.div(duration).takeIf { it?.isFinite() ?: false }
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
        Log.d(TAG, String.format("Displaying details for trip %d", tripId))
        viewModel.tripId = tripId

        viewModel.updateSplits()
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
                    val splitSpeed =
                        getUserSpeed(requireContext(), it.distance, it.duration)
                    if (splitSpeed > maxSpeed) maxSpeed = splitSpeed
                }
                val maxWidth = (maxSpeed / 10).toInt() * 10 + 10

                splits.forEachIndexed { idx, split ->
                    val distanceView = TextView(activity)
                    val speedView = LinearLayout(activity)
                    val speedText = TextView(activity)
                    val timeView = TextView(activity)

                    distanceView.text = String.format("%d %s",
                        idx + 1, getUserDistanceUnitShort(requireContext()))
                    timeView.text = formatDuration(split.totalDuration)
                    speedText.text = String.format("%.2f %s",
                        getUserSpeed(requireContext(),
                            split.distance,
                            split.duration),
                        getUserSpeedUnitShort(requireContext()))


                    val distanceLayout =
                        GridLayout.LayoutParams(GridLayout.spec(idx + 1,
                            GridLayout.CENTER),
                            GridLayout.spec(0))
                    val speedLayout =
                        GridLayout.LayoutParams(GridLayout.spec(idx + 1),
                            GridLayout.spec(1, 100f))
                    val timeLayout =
                        GridLayout.LayoutParams(GridLayout.spec(idx + 1,
                            GridLayout.CENTER),
                            GridLayout.spec(2))

                    distanceView.layoutParams = distanceLayout
                    speedView.layoutParams = speedLayout
                    speedText.background =
                        ResourcesCompat.getDrawable(resources,
                            R.drawable.rounded_corner,
                            null)
                    speedView.doOnPreDraw {
                        speedText.width =
                            (speedView.width * getUserSpeed(requireContext(),
                                split.distance,
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
                Log.d(TAG, "overview is null")
            }
            zipLiveData(viewModel.measurements(), viewModel.timeState()).observe(viewLifecycleOwner,
                ZipLiveData@{ pairs ->
                    val tripMeasurements = pairs.first
                    val timeStates = pairs.second
                    Log.d(TAG, "Observed change to measurements and time state")

                    val effectiveCircumference =
                        getEffectiveCircumference(overview, tripMeasurements)

                    Log.d(TAG, "Effective circumference trip $tripId: $effectiveCircumference")
                    Log.d(TAG,
                        "Auto circumference trip $tripId: ${overview.autoWheelCircumference}")
                    Log.d(TAG,
                        "User circumference trip $tripId: ${overview.userWheelCircumference}")

                    fun getSpeedDataFromGps(
                        entries: ArrayList<Entry>,
                        trend: ArrayList<Entry>,
                        measurements: Array<CriticalMeasurements>,
                        intervals: Array<LongRange>,
                    ) {
                        val intervalStart = intervals.last().first
                        val accumulatedTime = accumulateTime(intervals)
                        var trendLast =
                            getUserSpeed(requireContext(), measurements[0].speed.toDouble())
                        var trendAlpha = 0.01f
                        measurements.forEach {
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

                    fun getSpeedDataFromSensor(
                        entries: ArrayList<Entry>,
                        trend: ArrayList<Entry>,
                        measurementsList: Array<CriticalMeasurements>,
                        intervals: Array<LongRange>,
                    ) {
                        val intervalStart = intervals.last().first
                        val accumulatedTime = accumulateTime(intervals)
                        var trendLast =
                            getUserSpeed(requireContext(), measurementsList[0].speed.toDouble())
                        var trendAlpha = 0.5f

                        val circumference =
                            effectiveCircumference ?: overview.autoWheelCircumference
                            ?: overview.userWheelCircumference
                            ?: getUserCircumference(requireContext())
                        Log.d(TAG, "Using circumference: $circumference")

                        var lastMeasurements: CriticalMeasurements? = null
                        measurementsList.forEach { measurements ->
                            if (measurements.speedRevolutions != null) {
                                lastMeasurements?.takeIf { it.speedRevolutions != null }
                                    ?.let { last ->
                                        if (measurements.speedLastEvent != last.speedLastEvent) {
                                            try {
                                                getRpm(rev = measurements.speedRevolutions,
                                                    revLast = last.speedRevolutions ?: 0,
                                                    time = measurements.speedLastEvent ?: 0,
                                                    timeLast = last.speedLastEvent ?: 0,
                                                    delta = measurements.time - last.time
                                                ).takeIf { it.isFinite() }
                                                    ?.let { it * circumference / 60 }
                                                    ?.let { speed ->
                                                        val timestamp =
                                                            (accumulatedTime + (measurements.time - intervalStart) / 1e3).toFloat()
                                                        entries.add(Entry(timestamp,
                                                            getUserSpeed(requireContext(), speed)))
                                                        trendLast =
                                                            (trendAlpha * getUserSpeed(requireContext(),
                                                                speed)) + ((1 - trendAlpha) * trendLast)
                                                        trend.add(Entry(timestamp, trendLast))
                                                        if (trendAlpha > 0.01f) trendAlpha -= 0.005f
                                                        if (trendAlpha < 0.01f) trendAlpha = 0.01f
                                                    }
                                            } catch (e: Exception) {
                                                Log.e(logTag,
                                                    "Could not calculate speed for time ${measurements.time}")
                                            }
                                        }
                                    }
                                lastMeasurements = measurements
                            }
                        }
                    }

                    fun makeSpeedDataset(
                        measurements: Array<CriticalMeasurements>,
                        intervals: Array<LongRange>,
                    ): Pair<LineDataSet, LineDataSet> {
                        val entries = ArrayList<Entry>()
                        val trend = ArrayList<Entry>()

                        if (measurements.isNullOrEmpty()) return Pair(LineDataSet(
                            entries,
                            "Speed"),
                            LineDataSet(trend, "Trend"))

                        if (getAverageSpeedRpm(measurements) == null) {
                            getSpeedDataFromGps(entries, trend, measurements, intervals)
                        } else {
                            getSpeedDataFromSensor(entries, trend, measurements, intervals)
                        }
                        val dataset = LineDataSet(entries, "Speed")
                        dataset.setDrawCircles(false)
                        dataset.setDrawValues(false)
                        val trendData = LineDataSet(trend, "Trend")
                        trendData.setDrawCircles(false)
                        trendData.setDrawValues(false)
                        dataset.color =
                            ResourcesCompat.getColor(resources,
                                R.color.colorGraphSecondary,
                                null)
                        dataset.lineWidth = 10f
                        trendData.color =
                            ResourcesCompat.getColor(resources,
                                R.color.colorAccent,
                                null)
                        trendData.lineWidth = 3f
                        return Pair(dataset, trendData)
                    }

                    fun makeSpeedLineChart() {
                        configureLineChart(speedChartView)

                        val intervals = getTripIntervals(timeStates, tripMeasurements)
                        val legs = getTripLegs(tripMeasurements, intervals)
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

                    fun makeHeartRateDataset(
                        measurements: Array<CriticalMeasurements>,
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
                        dataset.color =
                            ResourcesCompat.getColor(resources,
                                R.color.colorAccent,
                                null)
                        dataset.lineWidth = 3f
                        return dataset
                    }

                    fun makeHeartRateLineChart() {
                        configureLineChart(heartRateChartView, 50f)

                        val intervals = getTripIntervals(timeStates, tripMeasurements)
                        val legs = getTripLegs(tripMeasurements, intervals)
                        val data = LineData()

                        legs.forEachIndexed { idx, leg ->
                            data.addDataSet(makeHeartRateDataset(leg,
                                intervals.sliceArray(IntRange(0, idx))))
                        }
                        heartRateChartView.data = data
                        heartRateChartView.invalidate()
                    }

                    fun makeCadenceDataset(
                        measurementsList: Array<CriticalMeasurements>,
                        intervals: Array<LongRange>,
                    ): Pair<LineDataSet, LineDataSet> {
                        val entries = ArrayList<Entry>()
                        val trend = ArrayList<Entry>()
                        val intervalStart = intervals.last().first
                        var trendLast: Float? = null
                        var trendAlpha = 0.5f

                        val accumulatedTime = accumulateTime(intervals)

                        var lastMeasurements: CriticalMeasurements? = null
                        measurementsList.forEach { measurements ->
                            if (measurements.cadenceRevolutions != null) {
                                lastMeasurements?.takeIf { it.cadenceRevolutions != null }
                                    ?.let { last ->
                                        if (validateCadence(measurements, last)) {
                                            try {
                                                getRpm(rev = measurements.cadenceRevolutions,
                                                    revLast = last.cadenceRevolutions ?: 0,
                                                    time = measurements.cadenceLastEvent ?: 0,
                                                    timeLast = last.cadenceLastEvent ?: 0,
                                                    delta = measurements.time - last.time
                                                ).takeIf { it.isFinite() }?.let { rpm ->
                                                    val timestamp =
                                                        (accumulatedTime + (measurements.time - intervalStart) / 1e3).toFloat()
                                                    entries.add(Entry(timestamp, rpm))
                                                    trendLast =
                                                        (trendAlpha * rpm) + ((1 - trendAlpha) * (trendLast
                                                            ?: rpm))
                                                    trend.add(Entry(timestamp, trendLast!!))
                                                    if (trendAlpha > 0.01f) trendAlpha -= 0.005f
                                                    if (trendAlpha < 0.01f) trendAlpha = 0.01f
                                                }
                                            } catch (e: Exception) {
                                                Log.e(logTag,
                                                    "Could not create rpm value for timestamp ${measurements.time}")
                                            }
                                        }
                                    }
                                lastMeasurements = measurements
                            }
                        }

                        val dataset = LineDataSet(entries, "Cadence")
                        dataset.setDrawCircles(false)
                        dataset.setDrawValues(false)
                        val trendData = LineDataSet(trend, "Trend")
                        trendData.setDrawCircles(false)
                        trendData.setDrawValues(false)
                        dataset.color =
                            ResourcesCompat.getColor(resources,
                                R.color.colorGraphSecondary,
                                null)
                        dataset.lineWidth = 10f
                        trendData.color =
                            ResourcesCompat.getColor(resources,
                                R.color.colorAccent,
                                null)
                        trendData.lineWidth = 3f
                        return Pair(dataset, trendData)
                    }

                    fun makeCadenceLineChart() {
                        configureLineChart(cadenceChartView)

                        val intervals = getTripIntervals(timeStates, tripMeasurements)
                        val legs = getTripLegs(tripMeasurements, intervals)
                        val data = LineData()

                        legs.forEachIndexed { idx, leg ->
                            val (raw, trend) = makeCadenceDataset(leg,
                                intervals.sliceArray(IntRange(0, idx)))

                            data.addDataSet(raw)
                            data.addDataSet(trend)
                        }

                        cadenceChartView.data = data
                        cadenceChartView.invalidate()
                    }

                    fun makeElevationDataset(
                        measurements: Array<CriticalMeasurements>,
                        _totalDistance: Float,
                    ): Pair<LineDataSet, LineDataSet> {
                        val entries = ArrayList<Entry>()
                        val raw = ArrayList<Entry>()
                        var totalDistance = _totalDistance
                        var lastMeasurements: CriticalMeasurements? = null
                        var smoothed: Double = measurements[0].altitude
                        var smoothedLast = smoothed
                        measurements.forEach {
                            smoothed =
                                exponentialSmoothing(elevationAlpha,
                                    it.altitude,
                                    smoothedLast)
                            smoothedLast = smoothed

                            lastMeasurements?.let { last ->
                                totalDistance += getDistance(it, last)
                            }
                            lastMeasurements = it

                            entries.add(Entry(totalDistance,
                                getUserAltitude(requireContext(),
                                    smoothed).toFloat()))
                            raw.add(Entry(totalDistance,
                                getUserAltitude(requireContext(), it.altitude).toFloat()))
                        }
                        val dataset = LineDataSet(entries, "Elevation")
                        dataset.setDrawCircles(false)
                        dataset.setDrawValues(false)
                        dataset.color =
                            ResourcesCompat.getColor(resources,
                                R.color.colorAccent,
                                null)
                        dataset.lineWidth = 1f
                        val rawdataset = LineDataSet(raw, "raw")
                        rawdataset.setDrawCircles(false)
                        rawdataset.setDrawValues(false)
                        rawdataset.color = Color.CYAN
                        rawdataset.lineWidth = 1f
                        return Pair(rawdataset, dataset)
                    }

                    fun makeElevationLineChart() {
                        configureLineChart(elevationChartView)

                        elevationChartView.xAxis.valueFormatter = object : ValueFormatter() {
                            override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                                return if (value == 0f) "" else "${
                                    getUserDistance(requireContext(),
                                        value.toDouble()).roundToInt()
                                } ${getUserDistanceUnitShort(requireContext())}"
                            }
                        }

                        val intervals = getTripIntervals(timeStates, tripMeasurements)
                        val legs = getTripLegs(tripMeasurements, intervals)
                        val data = LineData()

                        var totalDistance = 0f
                        legs.forEach { leg ->
                            makeElevationDataset(leg, totalDistance).let { dataset ->
                                data.addDataSet(dataset.first)
                                data.addDataSet(dataset.second)
                                dataset.first.values.takeIf { it.isNotEmpty() }?.let {
                                    totalDistance = it.last().x
                                }
                            }
                        }
                        elevationChartView.data = data
                        elevationChartView.invalidate()
                    }
                    Log.d(TAG,
                        "Recorded ${tripMeasurements.size} measurements for trip ${tripId}")

                    if (tripMeasurements.isNullOrEmpty()) return@ZipLiveData
                    if (timeStates.isNotEmpty()) titleDateView.text =
                        String.format("%s: %s - %s",
                            SimpleDateFormat("MMMM d",
                                Locale.US).format(Date(timeStates.first().timestamp)),
                            SimpleDateFormat("h:mm",
                                Locale.US).format(Date(timeStates.first().timestamp)),
                            SimpleDateFormat("h:mm",
                                Locale.US).format(Date(timeStates.last().timestamp)))

                    val thisFragment = this
                    viewLifecycleOwner.lifecycleScope.launch {
                        val mapData = plotPath(tripMeasurements, timeStates)
                        if (mapData.bounds != null) {
                            Log.d(TAG, "Plotting path")
                            mapData.paths.forEach { path ->
                                path.startCap(RoundCap())
                                path.endCap(RoundCap())
                                path.width(5f)
                                path.color(ResourcesCompat.getColor(resources,
                                    R.color.colorAccent,
                                    null))
                                map.addPolyline(path)
                            }
                            try {
                                map.moveCamera(CameraUpdateFactory.newLatLngBounds(mapData.bounds,
                                    mapView.width,
                                    scrollView.marginTop,
                                    100))
                                maxCameraPosition = map.cameraPosition
                                map.moveCamera(CameraUpdateFactory.scrollBy(0f,
                                    (mapView.height - scrollView.marginTop) / 2f))
                                defaultCameraPosition = map.cameraPosition
                            } catch (e: Exception) {
                                Log.e(logTag, "Couldn't draw trip details map", e)
                            }
                        }
                        makeSpeedLineChart()

                        val elevationChange = getElevationChange(tripMeasurements)
                        view.findViewById<HeadingView>(R.id.trip_details_elevation).value =
                            "+${
                                getUserAltitude(requireContext(),
                                    elevationChange.first).roundToInt()
                            }/${
                                getUserAltitude(requireContext(),
                                    elevationChange.second).roundToInt()
                            } ${
                                getUserAltitudeUnitLong(requireContext())
                            }"
                        makeElevationLineChart()

                        val avgHeartRate = getAverageHeartRate(tripMeasurements)
                        if (avgHeartRate != null) {
                            heartRateView.visibility = View.VISIBLE
                            heartRateChartView.visibility = View.VISIBLE
                            heartRateView.value = "$avgHeartRate bpm (average)"
                            makeHeartRateLineChart()
                        }
                        val avgCadence = getAverageCadence(tripMeasurements)
                        if (avgCadence != null) {
                            cadenceView.visibility = View.VISIBLE
                            cadenceChartView.visibility = View.VISIBLE
                            cadenceView.value =
                                "${avgCadence.roundToInt()} rpm (average)"
                            makeCadenceLineChart()
                        }
                        scrollView.setOnTouchListener(thisFragment)
                    }
                })
        })

        fun getCaloriesBurned(
            biometrics: Biometrics,
            overview: Trip,
            measurements: Array<CriticalMeasurements>,
        ) {
            Log.d(TAG, "User weight: ${overview.userWeight}")
            val avgHr = getAverageHeartRate(measurements)
            try {
                getCaloriesBurned(
                    biometrics,
                    overview,
                    avgHr,
                )?.let {
                    caloriesHeadingView.label = getCaloriesBurnedLabel(
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
        zipLiveData(viewModel.measurements(), viewModel.tripOverview()).observe(
            viewLifecycleOwner,
            { pairs ->
                val measurements = pairs.first
                val overview = pairs.second
                Log.d(TAG, "Observed change to measurements and overview")
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.getCombinedBiometrics(overview.timestamp, requireContext())
                        .let { biometrics ->
                            Log.d(TAG, "biometrics: ${biometrics}")
                            if (biometrics.userWeight != null) {
                                Log.d(TAG, "Calculating calories burned")
                                getCaloriesBurned(biometrics, overview, measurements)
                            }
                        }
                }
            })
        if (FeatureFlags.devBuild) {
            viewModel.tripOverview().observe(viewLifecycleOwner, { trip ->
                getDatasets(requireActivity(),
                    trip.timestamp,
                    (trip.timestamp + (trip.duration?.times(
                        1000) ?: 1).toLong()))
            })
        }
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
        Log.v("TOUCH_SEQ", "Start sequence: $startY")
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

        Log.v("TOUCH_SEQ_MOVE",
            "startHeight:$startHeight, rawY:${event?.rawY}, startY:$startY, newHeight:$newHeight")
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
        Log.v("TOUCH_SEQ_END",
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