package com.kvl.cyclotrack

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.navGraphViewModels
import com.google.android.gms.maps.model.RoundCap
import com.kvl.cyclotrack.data.DailySummary
import com.kvl.cyclotrack.widgets.AnalyticsCard
import com.kvl.cyclotrack.widgets.TableColumn
import com.kvl.cyclotrack.widgets.ThreeStat
import com.kvl.cyclotrack.widgets.WeeklySummaryTable
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt


@AndroidEntryPoint
class AnalyticsFragment : Fragment() {
    private val logTag = AnalyticsFragment::class.simpleName

    private fun initializeWeeklySummaryData(startDay: Instant): MutableList<DailySummary> {
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
    private lateinit var spotlightTripCard: TripSummaryCard
    private lateinit var spotlightTripHeading: TextView
    private lateinit var topLinearLayout: LinearLayout


    private fun insertAnalyticsCard(
        parentLayout: LinearLayout = topLinearLayout,
        id: Int,
        position: Int = -1,
        initializeCardFun: AnalyticsCard.() -> Unit,
    ) {
        parentLayout.apply {
            findViewById<AnalyticsCard>(id)?.apply(initializeCardFun) ?: addView(
                (LayoutInflater.from(context).inflate(
                    R.layout.analytics_card_analytics_fragment,
                    this,
                    false
                ) as AnalyticsCard)
                    .apply(initializeCardFun).apply { this.id = id }, position
            )
        }
    }

    private fun insertPrCard(
        view: View,
        id: Int,
        position: Int = -1,
        initializeCardFun: AnalyticsCard.() -> Unit,
    ) = insertAnalyticsCard(
        view.findViewById(R.id.fragmentAnalytics_prLinearLayout),
        id,
        position,
        initializeCardFun
    )

    private fun buildPeriodTotalsTable(
        analyticsCard: AnalyticsCard,
        periodTotals: Array<PeriodTotals>,
        headingText: String,
        periodHeaderText: String,
    ) {
        analyticsCard.apply {
            heading.text = headingText
            threeStat.visibility = View.GONE
            table.apply {
                columns = listOf(
                    TableColumn(id = "period", label = periodHeaderText),
                    TableColumn(id = "count", label = "RIDES"),
                    TableColumn(id = "distance", label = "DISTANCE"),
                    TableColumn(id = "duration", label = "DURATION"),
                )
                populate(periodTotals.map {
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
        }
    }

    private fun doBikeTotals(bikes: Array<BikeTotals>) {
        insertAnalyticsCard(id = "Bike totals".hashCode()) {
            heading.text = "Bike totals"
            threeStat.visibility = View.GONE
            table.apply {
                columns = listOf(
                    TableColumn(id = "bikeName", label = "BIKE"),
                    TableColumn(id = "count", label = "RIDES"),
                    TableColumn(id = "distance", label = "DISTANCE"),
                    TableColumn(id = "duration", label = "DURATION"),
                )
                populate(bikes.map {
                    listOf(
                        it.name,
                        it.count.toString(),
                        "%.1f %s".format(
                            getUserDistance(context, it.distance),
                            getUserDistanceUnitShort(context)
                        ),
                        formatDuration(it.duration),
                    )
                })
            }
        }
    }

    private fun doSpotlightRide(trip: Trip?) {
        spotlightTripHeading.visibility = if (trip == null) View.GONE else View.VISIBLE
        spotlightTripCard.apply {
            if (trip == null) visibility = View.GONE
            else {
                visibility = View.VISIBLE
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
            view.findViewById<AnalyticsCard>(R.id.fragmentAnalytics_analyticsCard_thisYear).apply {
                table.visibility = View.GONE
                heading.text = "This year"
                threeStat.populate(
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
            view.findViewById<AnalyticsCard>(R.id.fragmentAnalytics_analyticsCard_thisMonth).apply {
                table.visibility = View.GONE
                heading.text = "This month"
                threeStat.populate(
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

    private fun doTopWeeks(view: View) {
        viewModel.weeklyTotals().observe(viewLifecycleOwner, { totals ->
            view.findViewById<AnalyticsCard>(R.id.fragmentAnalytics_analyticsCard_topWeeks).apply {
                buildPeriodTotalsTable(this, totals, "Top weeks", "WEEK")
            }
        })
    }

    private fun doTopMonths(view: View) {
        viewModel.monthlyTotals().observe(viewLifecycleOwner, { totals ->
            view.findViewById<AnalyticsCard>(R.id.fragmentAnalytics_analyticsCard_topMonths).apply {
                buildPeriodTotalsTable(this, totals, "Top months", "MONTH")
            }
        })
    }

    private fun doLongestRides(view: View, trips: Array<Trip>) {
        view.findViewById<AnalyticsCard>(R.id.fragmentAnalytics_analyticsCard_longestRides).apply {
            heading.text = "Longest rides"
            threeStat.visibility = View.GONE
            table.apply {
                columns = listOf(
                    TableColumn(id = "date", label = "DATE"),
                    TableColumn(id = "distance", label = "DISTANCE"),
                    TableColumn(id = "duration", label = "DURATION"),
                )
                populate(trips.map {
                    listOf(
                        Instant.ofEpochMilli(it.timestamp)
                            .atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ISO_LOCAL_DATE),
                        "%.1f %s".format(
                            getUserDistance(context, it.distance!!),
                            getUserDistanceUnitShort(context)
                        ),
                        formatDuration(it.duration!!),
                    )
                })
            }
        }
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
    }

    private fun doPopularRides(view: View) {
        val conversionFactor = getUserDistance(requireContext(), 1.0)
        viewModel.popularDistances(conversionFactor, 3)
            .observe(viewLifecycleOwner, { buckets ->
                buckets.forEach { distance ->
                    if (distance.count >= 3) viewModel.fastestDistance(
                        distance.bucket,
                        conversionFactor,
                        -1
                    )
                        .observe(viewLifecycleOwner, { splits ->
                            val headingText =
                                "${distance.bucket} ${getUserDistanceUnitShort(view.context)} PR"
                            insertPrCard(view, headingText.hashCode()) {
                                heading.text = headingText
                                threeStat
                                    .apply {
                                        populate(
                                            arrayOf(
                                                Pair("RIDES", splits.size.toString()),
                                                Pair(
                                                    getUserDistanceUnitShort(requireContext()).uppercase(),
                                                    getUserDistance(
                                                        requireContext(),
                                                        splits.map { it.totalDistance }.sum()
                                                    ).roundToInt()
                                                        .toString()
                                                ),
                                                Pair(
                                                    "HOURS",
                                                    formatDurationHours(
                                                        splits.map { it.totalDuration }.sum()
                                                    )
                                                )
                                            )
                                        )
                                    }
                                table.apply {
                                    columns = listOf(
                                        TableColumn(id = "date", label = "DATE"),
                                        TableColumn(id = "speed", label = "SPEED"),
                                        TableColumn(id = "duration", label = "DURATION"),
                                    )
                                    populate(splits.slice(IntRange(0, 2)).map { split ->
                                        listOf(
                                            Instant.ofEpochMilli(split.timestamp)
                                                .atZone(ZoneId.systemDefault())
                                                .format(DateTimeFormatter.ISO_LOCAL_DATE),
                                            "%.1f %s".format(
                                                getUserSpeed(
                                                    context,
                                                    split.totalDistance / split.totalDuration
                                                ),
                                                getUserSpeedUnitShort(context)
                                            ),
                                            formatDuration(split.totalDuration),
                                        )
                                    })
                                }
                            }
                        })
                }
            })
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_analytics, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        topLinearLayout = view.findViewById(R.id.fragmentAnalytics_topLinearLayout)
        rollupView = view.findViewById(R.id.trips_rollup)
        spotlightTripCard = view.findViewById(R.id.fragmentAnalytics_spotlightRide)
        spotlightTripHeading = view.findViewById(R.id.fragmentAnalytics_spotlightRideHeading)

        viewModel.tripTotals
            .observe(viewLifecycleOwner, { totals -> rollupView.applyTotals(totals) })

        viewModel.realTrips.observe(viewLifecycleOwner, { trips ->
            doSpotlightRide(trips.find { !it.inProgress })
        })

        doWeeklySummary(view)
        doMonthlyTotals(view)
        doAnnualTotals(view)

        doTopWeeks(view)
        doTopMonths(view)

        doPopularRides(view)

        viewModel.longestTrips(3)
            .observe(viewLifecycleOwner, { trips -> doLongestRides(view, trips) })

        viewModel.bikeTotals.observe(viewLifecycleOwner, { bikes ->
            if (bikes != null && bikes.size > 1) {
                doBikeTotals(bikes)
            }
        })


    }
}