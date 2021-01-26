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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TripSummariesFragment : Fragment() {
    private val viewModel: TripSummariesViewModel by navGraphViewModels(R.id.cyclotrack_nav_graph) {
        defaultViewModelProviderFactory
    }

    private var newRidesDisabledDialog: AlertDialog? = null
    private var noBackgroundLocationDialog: AlertDialog? = null
    private var tripsCleanupGuardDialog: AlertDialog? = null
    private lateinit var tripListView: RecyclerView
    private lateinit var rollupView: RollupView

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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.trip_summaries_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)
        val viewManager = LinearLayoutManager(activity)
        val listState: Parcelable? = savedInstanceState?.getParcelable("MY_KEY")
        if (listState != null) viewManager.onRestoreInstanceState(listState)

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
                    savedInstanceState)
            tripListView.apply {
                setHasFixedSize(true)
                layoutManager = viewManager
                adapter = viewAdapter
            }

            rollupView.rollupTripData(trips)
        })

        view.findViewById<FloatingActionButton>(R.id.fab).setOnClickListener {
            // TODO: Multiple touches causes fatal exception
            try {
                when (PackageManager.PERMISSION_GRANTED) {
                    ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ),
                    -> findNavController().navigate(R.id.action_start_trip)
                    else -> initializeLocationService()
                }
            } catch (e: IllegalArgumentException) {
                Log.d("TRIP_SUMMARIES", "CANNOT HANDLE MULTIPLE TRIP START TOUCHES")
            }
        }
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
        tripsCleanupGuardDialog = activity?.let {
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
            }

            builder.create()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.menu_main, menu)
        Log.d("TRIP_SUMMARIES", "Options menu created")
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.d("TRIP_SUMMARIES", "Options menu clicked")
        return when (item.itemId) {
            R.id.action_settings -> {
                Log.d("TRIP_SUMMARIES", "Options menu clicked settings")
                findNavController().navigate(R.id.action_go_to_settings)
                true
            }
            R.id.action_cleanup -> {
                tripsCleanupGuardDialog?.show()
                true
            }
            else -> super.onOptionsItemSelected(item)
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
            /*Handler().postDelayed({
            tripListView.layoutManager?.onRestoreInstanceState(viewModel.tripListState.getParcelable(
                "MY_KEY"))
        }, 50)*/
        }
    }
}