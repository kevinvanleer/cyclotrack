package com.kvl.cyclotrack

import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.navGraphViewModels
import com.google.android.gms.maps.model.RoundCap
import com.kvl.cyclotrack.data.DailySummary
import com.kvl.cyclotrack.util.getSystemOfMeasurement
import com.kvl.cyclotrack.widgets.AnalyticsCard
import com.kvl.cyclotrack.widgets.AxisLabelOrientation
import com.kvl.cyclotrack.widgets.AxisLabels
import com.kvl.cyclotrack.widgets.BordersEnum
import com.kvl.cyclotrack.widgets.LineGraph
import com.kvl.cyclotrack.widgets.LineGraphDataset
import com.kvl.cyclotrack.widgets.TableColumn
import com.kvl.cyclotrack.widgets.ThreeStat
import com.kvl.cyclotrack.widgets.WeeklySummaryTable
import com.kvl.cyclotrack.widgets.getTableFromSplits
import com.kvl.cyclotrack.widgets.getThreeStatFromSplits
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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
    private var systemOfMeasurement: String? = null


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
                    TableColumn(
                        id = "distance",
                        label = getUserDistanceUnitShort(context).uppercase()
                    ),
                    TableColumn(id = "duration", label = "DURATION"),
                )
                populate(periodTotals.map {
                    listOf(
                        it.period,
                        it.tripCount.toString(),
                        "%.1f".format(
                            getUserDistance(context, it.totalDistance)
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
                    TableColumn(
                        id = "distance",
                        label = getUserDistanceUnitShort(context).uppercase()
                    ),
                    TableColumn(id = "duration", label = "HOURS"),
                )
                populate(bikes.map {
                    listOf(
                        it.name,
                        it.count.toString(),
                        getUserDistance(context, it.distance).roundToInt().toString(),
                        formatDurationHours(it.duration),
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

    private fun buildPeriodTotalsAnalyticsCard(
        card: AnalyticsCard,
        title: String,
        thisPeriodStart: ZonedDateTime,
        thisPeriodEnd: ZonedDateTime,
        lastPeriodStart: ZonedDateTime,
        lastPeriodEnd: ZonedDateTime,
        lastPeriodToday: ZonedDateTime
    ) {
        viewModel.tripTotals(
            thisPeriodStart
                .toInstant().toEpochMilli(),
            thisPeriodEnd
                .toInstant().toEpochMilli()
        ).observe(viewLifecycleOwner) {
            card.table.visibility = View.GONE
            card.heading.text = title
            card.threeStat.populate(
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
        card.addView(TextView(requireContext()).apply {
            layoutParams =
                ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT

                ).apply {
                    setMargins(0, 0, 0, 20)
                }
            text =
                lastPeriodToday.minusDays(1)
                    .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
        })
        card.addView(ThreeStat(requireContext()).apply {
            viewModel.tripTotals(
                lastPeriodStart
                    .toInstant().toEpochMilli(),
                lastPeriodToday.toInstant().toEpochMilli()
            ).observe(viewLifecycleOwner) {
                populate(
                    arrayOf(
                        Pair("RIDES", it.tripCount.toString()),
                        Pair(
                            getUserDistanceUnitShort(requireContext()).uppercase(),
                            getUserDistance(
                                requireContext(),
                                it.totalDistance
                            ).roundToInt()
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
        card.addView(
            drawDistanceComparison(
                thisPeriodStart,
                thisPeriodEnd,
                lastPeriodStart,
                lastPeriodEnd,
                card.cardBackgroundColor.defaultColor
            )
        )
    }

    private fun doAnnualTotals(view: View) {
        val now = Instant.now().atZone(ZoneId.systemDefault())
        val thisYearStart = now.with(
            TemporalAdjusters.firstDayOfYear()
        ).truncatedTo(ChronoUnit.DAYS)
        val thisYearEnd = now.with(
            TemporalAdjusters.lastDayOfYear()
        ).truncatedTo(ChronoUnit.DAYS).plusDays(1)

        val lastYearStart = now.minusYears(1).with(
            TemporalAdjusters.firstDayOfYear()
        ).truncatedTo(ChronoUnit.DAYS)
        val lastYearToday = now.minusYears(1)
            .truncatedTo(ChronoUnit.DAYS).plusDays(1)
        val lastYearEnd = lastYearToday.with(
            TemporalAdjusters.lastDayOfYear()
        ).truncatedTo(ChronoUnit.DAYS).plusDays(1)

        buildPeriodTotalsAnalyticsCard(
            view.findViewById(R.id.fragmentAnalytics_analyticsCard_thisYear),
            "This year",
            thisYearStart,
            thisYearEnd,
            lastYearStart,
            lastYearEnd,
            lastYearToday
        )
    }

    private fun doMonthlyTotals(view: View) {
        val now = Instant.now().atZone(ZoneId.systemDefault())
        val thisMonthStart = now.with(
            TemporalAdjusters.firstDayOfMonth()
        ).truncatedTo(ChronoUnit.DAYS)
        val thisMonthEnd = now.with(
            TemporalAdjusters.lastDayOfMonth()
        ).truncatedTo(ChronoUnit.DAYS).plusDays(1)

        val lastMonthStart =
            thisMonthStart.minusMonths(1)
        val lastMonthToday =
            now.minusMonths(1)
                .truncatedTo(ChronoUnit.DAYS).plusDays(1)
        val lastMonthEnd = lastMonthStart.with(TemporalAdjusters.lastDayOfMonth()).plusDays(1)

        buildPeriodTotalsAnalyticsCard(
            view.findViewById(R.id.fragmentAnalytics_analyticsCard_thisMonth),
            "This month",
            thisMonthStart,
            thisMonthEnd,
            lastMonthStart,
            lastMonthEnd,
            lastMonthToday
        )
    }

    private fun drawSpeedGraph(
        thisMonthStart: ZonedDateTime,
        thisMonthEnd: ZonedDateTime,
    ) =
        ImageView(requireContext()).apply {
            layoutParams =
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            minimumHeight = 200
            viewModel.recentTrips(
                thisMonthStart.toInstant().toEpochMilli(),
                thisMonthEnd.toInstant().toEpochMilli()
            )
                .observe(viewLifecycleOwner) {
                    getSpeedGraphProps(
                        Pair(thisMonthStart, thisMonthEnd),
                        it,
                    ).let { datasets ->
                        setImageDrawable(
                            LineGraph(
                                datasets = listOf(datasets),
                            )
                        )
                    }
                }
        }

    private fun getSpeedGraphProps(
        thisPeriod: Pair<ZonedDateTime, ZonedDateTime>,
        thisPeriodPoints: Array<Trip>,
    ): LineGraphDataset {
        val (xRange, yRange, points) = getSpeedGraphPoints(
            thisPeriodPoints,
            thisPeriod
        )

        val xAxisWidth =
            (xRange.second - xRange.first
                    ).toFloat()
        val yAxisHeight = yRange.second - yRange.first

        val strokeStyle = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 5F
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        return LineGraphDataset(
            points = points,
            xRange = Pair(xRange.first.toFloat(), xRange.second.toFloat()),
            yRange = Pair(yRange.first, yRange.second),
            xAxisWidth = xAxisWidth,
            yAxisHeight = yAxisHeight,
            paint = Paint(strokeStyle).apply {
                color = requireContext().getColor(R.color.primaryDarkColor)
            }
        )
    }

    private fun getSpeedGraphPoints(
        thisPeriodPoints: Array<Trip>,
        thisPeriod: Pair<ZonedDateTime, ZonedDateTime>
    ): Triple<Pair<Long, Long>, Pair<Float, Float>, List<Pair<Float, Float>>> {
        val durationStart =
            thisPeriod.first.toInstant().toEpochMilli()
        return Triple(
            Pair(
                durationStart,
                thisPeriod.second.toInstant().toEpochMilli()
            ),
            Pair(
                thisPeriodPoints.minOf { point -> point.averageSpeed ?: 0f },
                thisPeriodPoints.maxOf { point -> point.averageSpeed ?: 0f }),
            thisPeriodPoints.map { point ->
                Pair(
                    (point.timestamp - durationStart).toFloat(),
                    (point.averageSpeed ?: 0.0).toFloat()
                )
            }
        )
    }

    private fun drawDistanceComparison(
        thisMonthStart: ZonedDateTime,
        thisMonthEnd: ZonedDateTime,
        lastMonthStart: ZonedDateTime,
        lastMonthEnd: ZonedDateTime,
        backgroundColor: Int
    ) =
        ImageView(requireContext()).apply {
            layoutParams =
                ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT

                ).apply {
                    setMargins(0, 50, 0, 50)
                }
            minimumHeight = 300
            zipLiveData(
                viewModel.recentTrips(
                    thisMonthStart.toInstant().toEpochMilli(),
                    thisMonthEnd.toInstant().toEpochMilli()
                ),
                viewModel.recentTrips(
                    lastMonthStart.toInstant().toEpochMilli(),
                    lastMonthEnd.toInstant().toEpochMilli()
                )
            ).observe(viewLifecycleOwner) {
                getDistanceComparisonGraph(
                    Pair(thisMonthStart, thisMonthEnd),
                    Pair(lastMonthStart, lastMonthEnd),
                    it.first,
                    it.second,
                    backgroundColor
                ).let { graph -> setImageDrawable(graph) }
            }
        }

    private fun getDistanceComparisonGraph(
        thisPeriod: Pair<ZonedDateTime, ZonedDateTime>,
        lastPeriod: Pair<ZonedDateTime, ZonedDateTime>,
        thisPeriodPoints: Array<Trip>,
        lastPeriodPoints: Array<Trip>,
        backgroundColor: Int
    ): LineGraph {
        val (xRangeThis, yRangeThis, thisPoints) = getDistanceGraphPoints(
            thisPeriodPoints.plus(
                Trip(
                    distance = 0.0,
                    timestamp = Instant.now().toEpochMilli(),
                    bikeId = -1L
                )
            ),
            thisPeriod
        )

        val (xRangeLast, yRangeLast, lastPoints) = getDistanceGraphPoints(
            lastPeriodPoints.plus(
                Trip(
                    distance = 0.0,
                    timestamp = lastPeriod.second.toInstant().toEpochMilli(),
                    bikeId = -1L
                )
            ),
            lastPeriod
        )

        val xAxisWidth =
            maxOf(
                xRangeThis.second - xRangeThis.first,
                xRangeLast.second - xRangeLast.first
            ).toFloat()
        val yAxisHeight =
            maxOf(
                yRangeThis.second - yRangeThis.first,
                yRangeLast.second - yRangeLast.first
            ).toFloat() * 1.2f

        val strokeStyle = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 5F
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val datasets = listOf(
            LineGraphDataset(
                points = lastPoints,
                xRange = Pair(xRangeThis.first.toFloat(), xRangeThis.second.toFloat()),
                yRange = Pair(yRangeThis.first.toFloat(), yRangeThis.second.toFloat()),
                xAxisWidth = xAxisWidth,
                yAxisHeight = yAxisHeight,
                paint = Paint(strokeStyle).apply {
                    color = requireContext().getColor(R.color.secondaryDarkColor)
                }
            ),
            LineGraphDataset(
                points = thisPoints,
                xRange = Pair(xRangeLast.first.toFloat(), xRangeLast.second.toFloat()),
                yRange = Pair(yRangeLast.first.toFloat(), yRangeLast.second.toFloat()),
                xAxisWidth = xAxisWidth,
                yAxisHeight = yAxisHeight,
                paint = Paint(strokeStyle).apply {
                    color = requireContext().getColor(R.color.primaryDarkColor)
                }
            ),
        )

        return LineGraph(
            datasets = datasets,
            yLabels = AxisLabels(
                labels = listOf(
                    Pair(
                        yRangeLast.second.toFloat(),
                        "${getUserDistance(requireContext(), yRangeLast.second).roundToInt()}"
                    )
                ),
                range = Pair(0f, yAxisHeight),
                lines = true,
                orientation = AxisLabelOrientation.INSIDE,
                background = backgroundColor,
            ),
            borders = BordersEnum.BOTTOM.value
        )
    }

    private fun getDistanceGraphPoints(
        thisPeriodPoints: Array<Trip>,
        thisPeriod: Pair<ZonedDateTime, ZonedDateTime>
    ): Triple<Pair<Long, Long>, Pair<Double, Double>, List<Pair<Float, Float>>> {
        var accDistance = 0f
        val durationStart =
            thisPeriod.first.toInstant().toEpochMilli()
        return Triple(
            Pair(
                durationStart,
                thisPeriod.second.toInstant().toEpochMilli()
            ),
            Pair(0.0, thisPeriodPoints.sumOf { point -> point.distance ?: 0.0 }),

            arrayOf(
                Trip(
                    distance = 0.0,
                    timestamp = thisPeriod.first.toInstant().toEpochMilli(),
                    bikeId = 0L
                )
            ).plus(thisPeriodPoints).map { point ->
                accDistance += (point.distance ?: 0.0).toFloat()
                Pair(
                    (point.timestamp - durationStart).toFloat(),
                    accDistance
                )
            }
        )
    }

    private fun doTopWeeks(view: View) {
        viewModel.weeklyTotals().observe(viewLifecycleOwner) { totals ->
            view.findViewById<AnalyticsCard>(R.id.fragmentAnalytics_analyticsCard_topWeeks).apply {
                buildPeriodTotalsTable(this, totals, "Top weeks", "WEEK")
            }
        }
    }

    private fun doTopMonths(view: View) {
        viewModel.monthlyTotals().observe(viewLifecycleOwner) { totals ->
            view.findViewById<AnalyticsCard>(R.id.fragmentAnalytics_analyticsCard_topMonths).apply {
                buildPeriodTotalsTable(this, totals, "Top months", "MONTH")
            }
        }
    }

    private fun doLongestRides(view: View, trips: Array<Trip>) {
        view.findViewById<AnalyticsCard>(R.id.fragmentAnalytics_analyticsCard_longestRides).apply {
            heading.text = "Longest rides"
            threeStat.visibility = View.GONE
            table.apply {
                columns = listOf(
                    TableColumn(id = "date", label = "DATE"),
                    TableColumn(
                        id = "distance",
                        label = getUserDistanceUnitShort(context).uppercase()
                    ),
                    TableColumn(id = "duration", label = "DURATION"),
                )
                populate(trips.map {
                    listOf(
                        Instant.ofEpochMilli(it.timestamp)
                            .atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ISO_LOCAL_DATE),
                        "%.1f".format(
                            getUserDistance(context, it.distance!!)
                        ),
                        formatDuration(it.duration!!),
                    )
                })
            }
        }
    }

    private fun doWeeklySummary(view: View) {
        thisWeekSummaryTable = view.findViewById(R.id.fragmentAnalytics_thisWeekSummaryTable)

        //Instant.now().atZone(ZoneId.systemDefault()).with(
        //    TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)
        //).truncatedTo(ChronoUnit.DAYS)
        //    .toInstant()
        // replace minus(6, DAYS) with minus(offset.toLong, DAYS)
        // for this week instead of last 7 days
        //Instant.parse("2021-09-04T05:00:00.00Z").atZone(ZoneId.systemDefault())
        //    .truncatedTo(ChronoUnit.DAYS)
        Instant.now().atZone(ZoneId.systemDefault()).truncatedTo(ChronoUnit.DAYS)
            .minus(6, ChronoUnit.DAYS).toInstant()
            .let { startDay ->
                viewModel.recentTrips(
                    startDay.toEpochMilli(),
                    startDay.plus(7, ChronoUnit.DAYS).toEpochMilli(),
                )
                    .observe(viewLifecycleOwner) { trips ->
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
                    }
            }
    }

    private fun doPopularRides(view: View) {
        val conversionFactor = getUserDistance(requireContext(), 1.0)
        viewModel.popularDistances(
            conversionFactor = conversionFactor,
            timestamp = Instant.now().atZone(ZoneId.systemDefault()).truncatedTo(ChronoUnit.DAYS)
                .minus(90, ChronoUnit.DAYS).toInstant().toEpochMilli(),
            bucketSize = 5,
            limit = 3
        )
            .observe(viewLifecycleOwner) { buckets ->
                buckets.forEach { distance ->
                    if (distance.count >= 3) viewModel.fastestDistance(
                        distance.bucket,
                        conversionFactor,
                        -1
                    )
                        .observe(viewLifecycleOwner) { splits ->
                            val headingText =
                                "${distance.bucket} ${getUserDistanceUnitShort(view.context)} PR"
                            insertPrCard(view, headingText.hashCode()) {
                                heading.text = headingText
                                threeStat
                                    .apply {
                                        populate(
                                            getThreeStatFromSplits(
                                                systemOfMeasurement,
                                                splits
                                            )
                                        )
                                    }
                                table.apply {
                                    columns = listOf(
                                        TableColumn(id = "date", label = "DATE"),
                                        TableColumn(
                                            id = "speed",
                                            label = getUserSpeedUnitShort(context).uppercase()
                                        ),
                                        TableColumn(id = "duration", label = "DURATION"),
                                    )
                                    populate(getTableFromSplits(systemOfMeasurement, splits))
                                }
                            }
                        }
                }
            }
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
        systemOfMeasurement = getSystemOfMeasurement(requireContext())

        viewModel.tripTotals
            .observe(viewLifecycleOwner) { totals -> rollupView.applyTotals(totals) }

        viewModel.realTrips.observe(viewLifecycleOwner) { trips ->
            doSpotlightRide(trips.find { !it.inProgress })
        }

        doWeeklySummary(view)
        doMonthlyTotals(view)
        doAnnualTotals(view)

        doTopWeeks(view)
        doTopMonths(view)

        doPopularRides(view)

        viewModel.longestTrips(3)
            .observe(viewLifecycleOwner) { trips -> doLongestRides(view, trips) }

        viewModel.bikeTotals.observe(viewLifecycleOwner) { bikes ->
            if (bikes != null && bikes.size > 1) {
                doBikeTotals(bikes)
            }
        }


    }
}
