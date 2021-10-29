package com.kvl.cyclotrack

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.navGraphViewModels
import com.kvl.cyclotrack.data.DailySummary
import com.kvl.cyclotrack.widgets.*
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

@AndroidEntryPoint
class AnalyticsFragment : Fragment() {

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
        viewModel.allTrips.observe(viewLifecycleOwner, { trips ->
            rollupView.rollupTripData(trips)
        })

        doWeeklySummary(view)
        doMonthlyTotals(view)
        doAnnualTotals(view)
        doTopRides(view)
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
                view.findViewById<StatView>(R.id.fragmentAnalytics_yearlyTripCount).apply {
                    statValue = it.tripCount.toString()
                    statUnits = "RIDES"
                }
                view.findViewById<StatView>(R.id.fragmentAnalytics_yearlyTotalDistance).apply {
                    statValue =
                        getUserDistance(requireContext(), it.totalDistance).roundToInt().toString()
                    statUnits = getUserDistanceUnitShort(requireContext()).uppercase()
                }

                view.findViewById<StatView>(R.id.fragmentAnalytics_yearlyTotalDuration).apply {
                    statValue = formatDurationHours(it.totalDuration)
                    statUnits = "HOURS"
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
            view.findViewById<StatView>(R.id.fragmentAnalytics_monthlyTripCount).apply {
                statValue = it.tripCount.toString()
                statUnits = "RIDES"
            }
            view.findViewById<StatView>(R.id.fragmentAnalytics_monthlyTotalDistance).apply {
                statValue =
                    getUserDistance(requireContext(), it.totalDistance).roundToInt().toString()
                statUnits = getUserDistanceUnitShort(requireContext()).uppercase()
            }

            view.findViewById<StatView>(R.id.fragmentAnalytics_monthlyTotalDuration).apply {
                statValue = formatDurationHours(it.totalDuration)
                statUnits = "HOURS"
            }
        })
        viewModel.monthlyTotals().observe(viewLifecycleOwner, {
            view.findViewById<Table>(R.id.fragmentAnalytics_topMonthlyTrips).apply {
                columns = listOf(
                    TableColumn(id = "period", label = "DATE"),
                    TableColumn(id = "distance", label = "DISTANCE"),
                    TableColumn(id = "duration", label = "DURATION"),
                    TableColumn(id = "count", label = "RIDES"),
                )
                populate(it.map {
                    listOf(
                        it.period,
                        "%.1f %s".format(
                            getUserDistance(context, it.totalDistance),
                            getUserDistanceUnitShort(context)
                        ),
                        formatDuration(it.totalDuration),
                        it.tripCount.toString()
                    )
                })
            }
        })
    }

    private fun doTopRides(view: View) {
        longestTrips = view.findViewById(R.id.fragmentTopTrips_longestTrips)
        viewModel.longestTrips(3)
            .observe(viewLifecycleOwner, { trips -> longestTrips.populate(trips) })
    }

    private fun doWeeklySummary(view: View) {
        weeklySummaryStatLeft = view.findViewById(R.id.fragmentAnalytics_lastSevenDayTotalStatLeft)
        weeklySummaryStatRight =
            view.findViewById(R.id.fragmentAnalytics_lastSevenDayTotalStatRight)
        thisWeekSummaryTable = view.findViewById(R.id.analyticsFragment_thisWeekSummaryTable)

        // replace minus(6, DAYS) with minus(offset.toLong, DAYS)
        // for this week instead of last 7 days
        //Instant.parse("2021-09-04T05:00:00.00Z").atZone(ZoneId.systemDefault())
        //    .truncatedTo(ChronoUnit.DAYS)
        Instant.now().atZone(ZoneId.systemDefault()).truncatedTo(ChronoUnit.DAYS)
            //.minus(offset.toLong(), ChronoUnit.DAYS).toInstant()
            .minus(6, ChronoUnit.DAYS).toInstant()
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
                        weeklySummaryStatLeft.statValue =
                            (summaryData.mapNotNull { it.distance }.ifEmpty { null }
                                ?.reduce { acc, it -> acc + it } ?: 0.0)
                                .let { getUserDistance(requireContext(), it) }.roundToInt()
                                .toString()
                        weeklySummaryStatLeft.statUnits =
                            getUserDistanceUnitShort(requireContext()).uppercase()
                        weeklySummaryStatRight.statValue =
                            (summaryData.mapNotNull { it.duration }.ifEmpty { null }
                                ?.reduce { acc, it -> acc + it } ?: 0.0)
                                .let { formatDurationHours(it) }
                        weeklySummaryStatRight.statUnits = "HOURS"
                        thisWeekSummaryTable.populate(summaryData.toTypedArray())
                    })
            }
    }
}