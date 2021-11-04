package com.kvl.cyclotrack

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.navGraphViewModels
import com.google.android.gms.maps.model.RoundCap
import com.kvl.cyclotrack.data.DailySummary
import com.kvl.cyclotrack.widgets.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

@AndroidEntryPoint
class AnalyticsFragment : Fragment() {
    private val logTag = AnalyticsFragment::class.simpleName

    fun initializeWeeklySummaryData(startDay: Instant): MutableList<DailySummary> {
        val summaries = mutableListOf<DailySummary>()
        (0..6).forEach {
            summaries.add(
                DailySummary(
                    date = startDay.atZone(ZoneId.systemDefault()).plusDays(it.toLong())
                        .truncatedTo(ChronoUnit.DAYS)
                )
            )
        }
        return summaries
    }

    private val viewModel: AnalyticsViewModel by navGraphViewModels(R.id.cyclotrack_nav_graph) {
        defaultViewModelProviderFactory
    }
    private lateinit var rollupView: RollupView
    private lateinit var thisWeekSummaryTable: WeeklySummaryTable
    private lateinit var weeklySummaryStatLeft: StatView
    private lateinit var weeklySummaryStatRight: StatView
    private lateinit var longestTrips: TripTable
    private lateinit var spotlightTripCard: TripSummaryCard

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_analytics, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rollupView = view.findViewById(R.id.trips_rollup)
        spotlightTripCard = view.findViewById(R.id.fragmentAnalytics_spotlightRide)
        Log.d(logTag, "inflate spotlight")
        viewModel.allTrips.observe(viewLifecycleOwner, { trips ->
            rollupView.rollupTripData(trips)
            doSpotlightRide(trips.find { !it.inProgress })
        })

        doWeeklySummary(view)
        doMonthlyTotals(view)
        doAnnualTotals(view)
        doTopRides(view)
    }

    private fun doSpotlightRide(trip: Trip?) {
        Log.d(logTag, "doSpotlightRide")
        spotlightTripCard.apply {
            if (trip == null) visibility = View.GONE
            else {
                tripId = trip.id!!
                title = trip.name ?: "Unnamed trip"
                setStartTime(trip.timestamp)
                setDate(trip.timestamp)
                setTripDetails(trip.duration ?: 0.0, trip.distance ?: 0.0)
                onResumeMap()
                clearMap()
                showSelectionIndicator = false
                isSelected = false

                viewLifecycleOwner.lifecycleScope.launch {
                    val measurements = viewModel.getTripMeasurements(tripId)
                    val timeStates = viewModel.getTripTimeStates(tripId)
                    val mapData = plotPath(measurements, timeStates)
                    if (mapData.bounds != null) {
                        mapData.paths.forEach { path ->
                            path.startCap(RoundCap())
                            path.endCap(RoundCap())
                            path.width(5f)
                            path.color(
                                ResourcesCompat.getColor(
                                    context.resources,
                                    R.color.accentColor,
                                    null
                                )
                            )
                            drawPath(path, mapData.bounds)
                        }
                    }
                }
                setOnClickListener { view ->
                    view.findNavController()
                        .navigate(TripSummariesFragmentDirections.actionViewTripDetails(tripId))
                }
            }
        }
    }

    private fun doAnnualTotals(view: View) {
        viewModel.tripTotals(
            Instant.now().atZone(ZoneId.systemDefault()).with(
                TemporalAdjusters.firstDayOfYear()
            ).truncatedTo(ChronoUnit.DAYS)
                .toInstant().toEpochMilli(),
            Instant.now().atZone(ZoneId.systemDefault()).with(
                TemporalAdjusters.lastDayOfYear()
            ).truncatedTo(ChronoUnit.DAYS).plusDays(1)
                .toInstant().toEpochMilli()
        ).observe(viewLifecycleOwner, {
            view.findViewById<ThreeStat>(R.id.fragmentAnalytics_threeStat_thisYear).apply {
                populate(
                    arrayOf(
                        Pair("RIDES", it.tripCount.toString()),
                        Pair(
                            getUserDistanceUnitShort(requireContext()).uppercase(),
                            getUserDistance(requireContext(), it.totalDistance).roundToInt()
                                .toString()
                        ),
                        Pair(
                            "HOURS",
                            formatDurationHours(it.totalDuration)
                        )
                    )
                )
            }
        })
    }

    private fun doMonthlyTotals(view: View) {
        viewModel.tripTotals(
            Instant.now().atZone(ZoneId.systemDefault()).with(
                TemporalAdjusters.firstDayOfMonth()
            ).truncatedTo(ChronoUnit.DAYS)
                .toInstant().toEpochMilli(),
            Instant.now().atZone(ZoneId.systemDefault()).with(
                TemporalAdjusters.lastDayOfMonth()
            ).truncatedTo(ChronoUnit.DAYS).plusDays(1)
                .toInstant().toEpochMilli()
        ).observe(viewLifecycleOwner, {
            view.findViewById<ThreeStat>(R.id.fragmentAnalytics_threeStat_thisMonth).apply {
                populate(
                    arrayOf(
                        Pair("RIDES", it.tripCount.toString()),
                        Pair(
                            getUserDistanceUnitShort(requireContext()).uppercase(),
                            getUserDistance(requireContext(), it.totalDistance).roundToInt()
                                .toString()
                        ),
                        Pair(
                            "HOURS",
                            formatDurationHours(it.totalDuration)
                        )
                    )
                )
            }
        })
        viewModel.monthlyTotals().observe(viewLifecycleOwner, {
            view.findViewById<Table>(R.id.fragmentAnalytics_topMonthlyTrips).apply {
                columns = listOf(
                    TableColumn(id = "period", label = "MONTH"),
                    TableColumn(id = "count", label = "RIDES"),
                    TableColumn(id = "distance", label = "DISTANCE"),
                    TableColumn(id = "duration", label = "DURATION"),
                )
                populate(it.map {
                    listOf(
                        it.period,
                        it.tripCount.toString(),
                        "%.1f %s".format(
                            getUserDistance(context, it.totalDistance),
                            getUserDistanceUnitShort(context)
                        ),
                        formatDuration(it.totalDuration),
                    )
                })
            }
        })
    }

    private fun doTopRides(view: View) {
        longestTrips = view.findViewById(R.id.fragmentAnalytics_longestTrips)
        viewModel.longestTrips(3)
            .observe(viewLifecycleOwner, { trips -> longestTrips.populate(trips) })
    }

    private fun doWeeklySummary(view: View) {
        thisWeekSummaryTable = view.findViewById(R.id.fragmentAnalytics_thisWeekSummaryTable)

        Instant.now().atZone(ZoneId.systemDefault()).with(
            TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)
        ).truncatedTo(ChronoUnit.DAYS)
            .toInstant()
            // replace minus(6, DAYS) with minus(offset.toLong, DAYS)
            // for this week instead of last 7 days
            //Instant.parse("2021-09-04T05:00:00.00Z").atZone(ZoneId.systemDefault())
            //    .truncatedTo(ChronoUnit.DAYS)
            //Instant.now().atZone(ZoneId.systemDefault()).truncatedTo(ChronoUnit.DAYS)
            //.minus(6, ChronoUnit.DAYS).toInstant()
            .let { startDay ->
                viewModel.recentTrips(
                    startDay.toEpochMilli(),
                    startDay.plus(7, ChronoUnit.DAYS).toEpochMilli(),
                )
                    .observe(viewLifecycleOwner, { trips ->
                        val summaryData = initializeWeeklySummaryData(startDay)
                        trips.forEach { trip ->
                            summaryData.find {
                                it.date == Instant.ofEpochMilli(trip.timestamp)
                                    .atZone(ZoneId.systemDefault()).truncatedTo(ChronoUnit.DAYS)
                            }
                                ?.let {
                                    summaryData.add(
                                        it.copy(
                                            distance = (it.distance ?: 0.0) + (trip.distance
                                                ?: 0.0),
                                            duration = (it.duration ?: 0.0) + (trip.duration ?: 0.0)
                                        )
                                    )
                                    summaryData.remove(it)
                                }

                        }
                        view.findViewById<ThreeStat>(R.id.fragmentAnalytics_threeStat_thisThisWeek)
                            .apply {
                                populate(
                                    arrayOf(
                                        Pair("RIDES", trips.size.toString()),
                                        Pair(
                                            getUserDistanceUnitShort(requireContext()).uppercase(),
                                            (summaryData.mapNotNull { it.distance }.ifEmpty { null }
                                                ?.reduce { acc, it -> acc + it } ?: 0.0)
                                                .let { getUserDistance(requireContext(), it) }
                                                .roundToInt()
                                                .toString()
                                        ),
                                        Pair(
                                            "HOURS",
                                            (summaryData.mapNotNull { it.duration }.ifEmpty { null }
                                                ?.reduce { acc, it -> acc + it } ?: 0.0)
                                                .let { formatDurationHours(it) }
                                        )
                                    )
                                )
                            }
                        thisWeekSummaryTable.populate(summaryData.toTypedArray())
                    })
            }
        viewModel.weeklyTotals().observe(viewLifecycleOwner, {
            view.findViewById<Table>(R.id.fragmentAnalytics_topWeeklyTrips).apply {
                columns = listOf(
                    TableColumn(id = "period", label = "WEEK"),
                    TableColumn(id = "count", label = "RIDES"),
                    TableColumn(id = "distance", label = "DISTANCE"),
                    TableColumn(id = "duration", label = "DURATION"),
                )
                populate(it.map {
                    listOf(
                        it.period,
                        it.tripCount.toString(),
                        "%.1f %s".format(
                            getUserDistance(context, it.totalDistance),
                            getUserDistanceUnitShort(context)
                        ),
                        formatDuration(it.totalDuration),
                    )
                })
            }
        })
    }
}