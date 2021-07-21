package com.kvl.cyclotrack

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.*
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TripSummariesFragment @Inject constructor() : Fragment() {
    private val logTag = "TripSummariesFragment"
    private val viewModel: TripSummariesViewModel by navGraphViewModels(R.id.cyclotrack_nav_graph) {
        defaultViewModelProviderFactory
    }

    private var newRidesDisabledDialog: AlertDialog? = null
    private var noBackgroundLocationDialog: AlertDialog? = null
    private lateinit var tripListView: RecyclerView
    private lateinit var rollupView: RollupView
    private lateinit var menu: Menu

    private val requestLocationPermissions = registerForActivityResult(RequestMultiplePermissions()
    ) { permissions ->
        for (entry in permissions.entries) {

            Log.d("LOCATION_PERMISSIONS", "${entry.key} = ${entry.value}")
            if (entry.key == Manifest.permission.ACCESS_FINE_LOCATION) {
                if (entry.value == true
                ) {
                    try {
                        findNavController().navigate(R.id.action_start_trip)
                    } catch (e: IllegalArgumentException) {
                        Log.d("TRIP_SUMMARIES", "CANNOT HANDLE MULTIPLE TRIP START TOUCHES")
                    }
                } else {
                    // Explain to the user that the feature is unavailable because
                    // the features requires a permission that the user has denied.
                    // At the same time, respect the user's decision. Don't link to
                    // system settings in an effort to convince the user to change
                    // their decision.

                    newRidesDisabledDialog?.show()
                }
            }
        }
    }

    private fun initializeLocationService() {
        when {
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                val eduDialog: AlertDialog? = activity?.let {
                    val builder = AlertDialog.Builder(it)
                    builder.apply {
                        setPositiveButton("PROCEED"
                        ) { _, _ ->
                            // User clicked OK button
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                requestLocationPermissions.launch(arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                ))
                            }
                        }
                        setNegativeButton("DENY"
                        ) { _, _ ->
                            // User cancelled the dialog
                            Log.d("TRIP_SUMMARIES", "CLICKED DENY")
                            newRidesDisabledDialog?.show()
                        }
                        setTitle("Grant Location Access")
                        setMessage("This app collects location data to enable the in-ride dashboard, and post-ride maps and graphs even when the app is closed or not in use.\n\nCyclotrack needs access to GPS location data and background location data to calculate speed and distance traveled during your rides. Data is only collected and recorded while rides are in progress. Data collected is stored on your device for your personal use. Your data is not sent to any third parties, including the developer. Please select PROCEED and then grant Cyclotrack access to location data.")
                    }

                    builder.create()
                }
                eduDialog?.show()
            }
            else -> {
                // You can directly ask for the permission.
                // The registered ActivityResultCallback gets the result of this request.
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    requestLocationPermissions.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ))
                }
            }
        }
    }

    private fun handleFabClick(trip: Trip?): View.OnClickListener = View.OnClickListener {
        // TODO: Multiple touches causes fatal exception
        try {
            val tripId = trip?.id.takeIf { trip?.inProgress ?: false }
            when (PackageManager.PERMISSION_GRANTED) {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                -> findNavController().navigate(R.id.action_start_trip,
                    Bundle().apply {
                        Log.d(logTag, "Start dashboard with trip ${tripId}")
                        putLong("tripId", tripId ?: -1L)
                    })
                else -> initializeLocationService()
            }
        } catch (e: IllegalArgumentException) {
            Log.d("TRIP_SUMMARIES", "CANNOT HANDLE MULTIPLE TRIP START TOUCHES")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.trip_summaries_fragment, container, false)
    }

    private val enableMultiSelectControls: (enable: Boolean) -> Unit = { enable ->
        (menu.findItem(R.id.action_clear_multiselect) as MenuItem).isVisible = enable
        (menu.findItem(R.id.action_stitch) as MenuItem).isVisible = enable
        if (FeatureFlags.devBuild) {
            (menu.findItem(R.id.action_delete) as MenuItem).isVisible = enable
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)
        val viewManager = LinearLayoutManager(activity)
        val listState: Parcelable? = savedInstanceState?.getParcelable("MY_KEY")
        if (listState != null) viewManager.onRestoreInstanceState(listState)

        activity?.title = "Cyclotrack"
        tripListView = view.findViewById(R.id.trip_summary_card_list)
        rollupView = view.findViewById(R.id.trips_rollup)

        viewModel.allTrips.observe(viewLifecycleOwner, { trips ->
            Log.d("TRIP_SUMMARIES",
                "There were ${trips.size} trips returned from the database")
            val viewAdapter =
                TripSummariesAdapter(trips,
                    viewModel,
                    viewLifecycleOwner,
                    requireContext(),
                    enableMultiSelectControls,
                    savedInstanceState)
            tripListView.apply {
                setHasFixedSize(true)
                layoutManager = viewManager
                adapter = viewAdapter
            }

            rollupView.rollupTripData(trips)

            view.findViewById<FloatingActionButton>(R.id.fab).apply {
                isEnabled = true
                visibility = View.VISIBLE
                if (trips.isNotEmpty()) {
                    setOnClickListener(handleFabClick(trips.first()))
                } else {
                    setOnClickListener(handleFabClick(null))
                }
            }
        })
        noBackgroundLocationDialog = activity?.let {
            val builder = AlertDialog.Builder(it)
            builder.apply {
                setPositiveButton("OK"
                ) { _, _ ->
                    Log.d("ALERT DIALOG", "CLICKED")
                }
                setTitle("For best results")
                setMessage("Keep phone on and dashboard in view while riding. Cyclotrack will not be able to access location data while it is in the background. This may degrade the accuracy of speed and distance measurements.")
            }

            builder.create()
        }
        newRidesDisabledDialog = activity?.let {
            val builder = AlertDialog.Builder(it)
            builder.apply {
                setPositiveButton("OK"
                ) { _, _ ->
                    Log.d("ALERT DIALOG", "CLICKED")
                }
                setTitle("New rides disabled")
                setMessage("Please access Cyclotrack app settings and grant permission to Location data. You will not be able to start a ride until location permission is granted.\n\nCyclotrack needs access to GPS location data and background location data to calculate speed and distance traveled during your rides. Data is only recorded while rides are in progress. Data collected is stored on your device for your personal use. Your data is not sent to any third parties, including the developer.")
            }

            builder.create()
        }

    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        this.menu = menu
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.menu_main, menu)
        Log.d("TRIP_SUMMARIES", "Options menu created")
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.d("TRIP_SUMMARIES", "Options menu clicked")
        return when (item.itemId) {
            R.id.action_settings -> {
                Log.d("TRIP_SUMMARIES", "Options menu clicked settings")
                findNavController().let {
                    Log.d("TRIP_SUMMARIES", it.toString())
                    it.navigate(R.id.action_go_to_settings)
                    true
                }
            }
            R.id.action_stitch -> {
                stitchSelectedTrips()
                true
            }
            R.id.action_delete -> {
                deleteSelectedTrips()
                true
            }
            R.id.action_cleanup -> {
                cleanupTrips()
                true
            }
            R.id.action_clear_multiselect -> {
                (tripListView.adapter as TripSummariesAdapter).multiSelectMode = false
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun stitchSelectedTrips() {
        activity?.let {
            val builder = AlertDialog.Builder(it)
            builder.apply {
                val selectedTrips =
                    (tripListView.adapter as TripSummariesAdapter).selectedTrips.toTypedArray()
                setPositiveButton("MERGE"
                ) { _, _ ->
                    Log.d("TRIP_STITCH_DIALOG", "CLICKED STITCH")
                    WorkManager.getInstance(requireContext())
                        .enqueue(OneTimeWorkRequestBuilder<StitchWorker>()
                            .setInputData(workDataOf("tripIds" to selectedTrips))
                            .build())
                    (tripListView.adapter as TripSummariesAdapter).multiSelectMode = false
                }
                setNegativeButton("CANCEL"
                ) { _, _ ->
                    Log.d("TRIP_STITCH_DIALOG", "CLICKED CANCEL")
                }
                setTitle("Merge rides?")
                setMessage("You are about to combine ${selectedTrips.size} rides into a single ride. This change cannot be undone.")
            }.show()

            builder.create()
        }
    }

    private fun deleteSelectedTrips() {
        activity?.let {
            val builder = AlertDialog.Builder(it)
            builder.apply {
                val selectedTrips =
                    (tripListView.adapter as TripSummariesAdapter).selectedTrips.toTypedArray()

                setPositiveButton("DELETE"
                ) { _, _ ->
                    Log.d(logTag, "CLICKED DELETE")
                    WorkManager.getInstance(requireContext())
                        .beginWith(listOf(
                            OneTimeWorkRequestBuilder<GoogleFitDeleteSessionWorker>()
                                .setInputData(workDataOf("tripIds" to selectedTrips))
                                .build()))
                        .then(OneTimeWorkRequestBuilder<RemoveTripWorker>()
                            .setInputData(workDataOf("tripIds" to selectedTrips))
                            .build())
                        .enqueue()
                    (tripListView.adapter as TripSummariesAdapter).multiSelectMode = false
                }
                setNegativeButton("CANCEL"
                ) { _, _ ->
                    Log.d("TRIP_DELETE_DIALOG", "CLICKED CANCEL")
                }
                setTitle("Delete rides?")
                setMessage("You are about to remove ${selectedTrips.size} rides. This change cannot be undone.")
            }.show()

            builder.create()
        }
    }

    private fun cleanupTrips() {
        activity?.let {
            val builder = AlertDialog.Builder(it)
            builder.apply {
                setPositiveButton("CLEANUP"
                ) { _, _ ->
                    Log.d("TRIP_CLEANUP_DIALOG", "CLICKED CLEANUP")
                    viewModel.cleanupTrips()
                }
                setNegativeButton("CANCEL"
                ) { _, _ ->
                    Log.d("TRIP_CLEANUP_DIALOG", "CLICKED CANCEL")
                }
                setTitle("Cleanup rides?")
                setMessage("You are about to remove all rides less than a minute long or less than a meter in distance. This change cannot be undone.")
            }.show()

            builder.create()
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.tripListState.putParcelable("MY_KEY",
            tripListView.layoutManager?.onSaveInstanceState())
    }

    override fun onResume() {
        super.onResume()
        if (this::tripListView.isInitialized) {
            tripListView.layoutManager?.onRestoreInstanceState(viewModel.tripListState.getParcelable(
                "MY_KEY"))
        }
        WorkManager.getInstance(requireContext())
            .enqueue(OneTimeWorkRequestBuilder<GoogleFitSyncTripsWorker>().build())
        //WorkManager.getInstance(requireContext())
        //    .enqueue(OneTimeWorkRequestBuilder<GoogleFitSyncBiometricsWorker>().build())
    }
}