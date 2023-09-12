package com.kvl.cyclotrack

import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.*
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.android.material.textfield.TextInputLayout
import com.kvl.cyclotrack.databinding.FragmentTripSummariesBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TripSummariesFragment @Inject constructor() : Fragment() {
    private val logTag = "TripSummariesFragment"
    private val viewModel: TripSummariesViewModel by navGraphViewModels(R.id.cyclotrack_nav_graph) {
        defaultViewModelProviderFactory
    }
    private lateinit var binding: FragmentTripSummariesBinding

    private lateinit var tripListView: RecyclerView
    private lateinit var searchTextLayout: TextInputLayout
    private lateinit var menu: Menu

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentTripSummariesBinding.inflate(inflater, container, false)
        binding.viewmodel = viewModel
        return binding.root
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
        addMenuProvider()
        val viewManager = LinearLayoutManager(activity)
        val listState: Parcelable? =
            when {
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ->
                    savedInstanceState?.getParcelable("MY_KEY", Bundle::class.java)

                else -> @Suppress("DEPRECATION") savedInstanceState?.getParcelable("MY_KEY")
            }
        if (listState != null) viewManager.onRestoreInstanceState(listState)

        activity?.title = ""
        tripListView = view.findViewById(R.id.trip_summary_card_list)
        searchTextLayout = binding.tripSummarySearchTextLayout

        binding.tripSummarySearchTextInput.setOnEditorActionListener { v, actionId, event ->
            when (actionId) {
                EditorInfo.IME_ACTION_SEARCH -> {
                    Log.d(logTag, "Clicked search icon: ${viewModel.searchText}")
                    viewModel.filterTrips()
                }

                else -> {
                    Log.d(logTag, "unhandle action")
                }
            }
            return@setOnEditorActionListener true
        }

        viewModel.allTrips.observe(viewLifecycleOwner) {
            viewModel.filterTrips()
        }
        viewModel.filteredTrips.observe(viewLifecycleOwner) { trips ->
            if (trips.isNullOrEmpty()) return@observe
            Log.d(
                logTag,
                "There were ${trips.size} trips returned from the database"
            )
            val viewAdapter =
                TripSummariesAdapter(
                    trips,
                    viewModel,
                    viewLifecycleOwner,
                    requireContext(),
                    enableMultiSelectControls,
                    savedInstanceState
                )
            tripListView.apply {
                setHasFixedSize(true)
                layoutManager = viewManager
                adapter = viewAdapter
            }
        }
    }

    private fun addMenuProvider() {
        Log.d(logTag, "addMenuProvider")
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
                this@TripSummariesFragment.menu = menu
                inflater.inflate(R.menu.menu_rides, menu)
                Log.d(logTag, "Options menu created")
            }

            override fun onMenuItemSelected(item: MenuItem): Boolean {
                Log.d(logTag, "Options menu clicked")
                return when (item.itemId) {
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

                    else -> {
                        Log.w(logTag, "unimplemented menu item selected")
                        false
                    }
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun stitchSelectedTrips() {
        activity?.let {
            val builder = AlertDialog.Builder(it)
            builder.apply {
                val selectedTrips =
                    (tripListView.adapter as TripSummariesAdapter).selectedTrips.toTypedArray()
                setPositiveButton(
                    "MERGE"
                ) { _, _ ->
                    Log.d("TRIP_STITCH_DIALOG", "CLICKED STITCH")
                    WorkManager.getInstance(requireContext())
                        .enqueue(
                            OneTimeWorkRequestBuilder<StitchWorker>()
                                .setInputData(workDataOf("tripIds" to selectedTrips))
                                .build()
                        )
                    (tripListView.adapter as TripSummariesAdapter).multiSelectMode = false
                }
                setNegativeButton(
                    "CANCEL"
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

                setPositiveButton(
                    "DELETE"
                ) { _, _ ->
                    Log.d(logTag, "CLICKED DELETE")
                    WorkManager.getInstance(requireContext())
                        .beginWith(
                            listOf(
                                OneTimeWorkRequestBuilder<GoogleFitDeleteSessionWorker>()
                                    .setInputData(workDataOf("tripIds" to selectedTrips))
                                    .build()
                            )
                        )
                        .then(
                            OneTimeWorkRequestBuilder<RemoveTripWorker>()
                                .setInputData(workDataOf("tripIds" to selectedTrips))
                                .build()
                        )
                        .enqueue()
                    (tripListView.adapter as TripSummariesAdapter).multiSelectMode = false
                }
                setNegativeButton(
                    "CANCEL"
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
                setPositiveButton(
                    "CLEANUP"
                ) { _, _ ->
                    Log.d("TRIP_CLEANUP_DIALOG", "CLICKED CLEANUP")
                    viewModel.cleanupTrips()
                }
                setNegativeButton(
                    "CANCEL"
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
        viewModel.tripListState.putParcelable(
            "MY_KEY",
            tripListView.layoutManager?.onSaveInstanceState()
        )
    }

    override fun onResume() {
        super.onResume()
        if (this::tripListView.isInitialized) {
            when {
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ->
                    tripListView.layoutManager?.onRestoreInstanceState(
                        viewModel.tripListState.getParcelable(
                            "MY_KEY", Bundle::class.java
                        )
                    )

                else ->
                    @Suppress("DEPRECATION")
                    tripListView.layoutManager?.onRestoreInstanceState(
                        viewModel.tripListState.getParcelable(
                            "MY_KEY"
                        )
                    )
            }
        }
    }
}
