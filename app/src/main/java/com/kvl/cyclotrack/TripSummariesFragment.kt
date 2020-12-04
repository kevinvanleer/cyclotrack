package com.kvl.cyclotrack

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.*
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

    private var alertDialog: AlertDialog? = null

    private fun initializeLocationService() {
        /*when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            -> {
                // You can use the API that requires the permission.
                //performAction(...)
                findNavController().navigate(R.id.action_start_trip)
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION),
            -> {
                // In an educational UI, explain to the user why your app requires this
                // permission for a specific feature to behave as expected. In this UI,
                // include a "cancel" or "no thanks" button that allows the user to
                // continue using your app without granting the permission.
                showInContextUI(...)
            }
            else -> {
                // You can directly ask for the permission.
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    requestPermissions(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                        0)
                }
            }
        }*/
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                findNavController().navigate(R.id.action_start_trip)
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                val eduDialog: AlertDialog? = activity?.let {
                    val builder = AlertDialog.Builder(it)
                    builder.apply {
                        setPositiveButton("PROCEED"
                        ) { dialog, id ->
                            // User clicked OK button
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                requestPermissions(
                                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                                    0)
                            }
                        }
                        setNegativeButton("DENY"
                        ) { dialog, id ->
                            // User cancelled the dialog
                            Log.d("TRIP_SUMMARIES", "CLICKED DENY")
                            alertDialog?.show()
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
                    requestPermissions(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                        0)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>, grantResults: IntArray,
    ) {
        when (requestCode) {
            0 -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() &&
                            grantResults[0] == PackageManager.PERMISSION_GRANTED)
                ) {
                    //TODO: SOMETHING WHEN PERMISSION IS GRANTED
                    findNavController().navigate(R.id.action_start_trip)
                } else {
                    // Explain to the user that the feature is unavailable because
                    // the features requires a permission that the user has denied.
                    // At the same time, respect the user's decision. Don't link to
                    // system settings in an effort to convince the user to change
                    // their decision.

                    alertDialog?.show()
                }
                return
            }

            // Add other 'when' lines to check for other
            // permissions this app might request.
            else -> {
                // Ignore all other requests.
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

        viewModel.realTrips.observe(viewLifecycleOwner, { trips ->
            Log.d("TRIP_SUMMARIES",
                "There were ${trips.size} trips returned from the database")
            val viewAdapter =
                TripSummariesAdapter(trips, viewModel, viewLifecycleOwner, savedInstanceState)
            view.findViewById<RecyclerView>(R.id.trip_summary_card_list).apply {
                setHasFixedSize(true)
                layoutManager = viewManager
                adapter = viewAdapter
            }
        })

        view.findViewById<FloatingActionButton>(R.id.fab).setOnClickListener {
            initializeLocationService()
        }
        alertDialog = activity?.let {
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
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

}