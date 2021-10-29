package com.kvl.cyclotrack.widgets

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.kvl.cyclotrack.R
import com.kvl.cyclotrack.Trip
import com.kvl.cyclotrack.data.DailySummary
import com.kvl.cyclotrack.getUserDistance
import com.kvl.cyclotrack.getUserDistanceUnitShort
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.*
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * TODO: document your custom view class.
 */
class WeeklySummaryTable : ConstraintLayout {

    private var firstDayOfWeek: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

    lateinit var daySummary1: DaySummary
    lateinit var daySummary2: DaySummary
    lateinit var daySummary3: DaySummary
    lateinit var daySummary4: DaySummary
    lateinit var daySummary5: DaySummary
    lateinit var daySummary6: DaySummary
    lateinit var daySummary7: DaySummary

    private val daySummaries = mutableListOf<DaySummary>()

    init {
        View.inflate(context, R.layout.view_weekly_summary_table, this)
    }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    )


    override fun onFinishInflate() {
        super.onFinishInflate()

        daySummaries.add(findViewById(R.id.day_summary_1))
        daySummaries.add(findViewById(R.id.day_summary_2))
        daySummaries.add(findViewById(R.id.day_summary_3))
        daySummaries.add(findViewById(R.id.day_summary_4))
        daySummaries.add(findViewById(R.id.day_summary_5))
        daySummaries.add(findViewById(R.id.day_summary_6))
        daySummaries.add(findViewById(R.id.day_summary_7))

        val daysInWeek = DayOfWeek.values().size
        daySummaries.forEachIndexed { idx, view ->
            view.dayOfWeek = DayOfWeek.of((firstDayOfWeek.value + idx - 1) % daysInWeek + 1)
            view.statTopUnits = ""
            view.statTopValue = ""
            view.statBottomUnits = ""
            view.statBottomValue = ""
            view.alpha = 0.5f
        }

    }

    fun populate(data: Array<DailySummary>) {
        data.sortBy { it.date }
        data.forEachIndexed { idx, day ->
            daySummaries[idx].dayOfWeek = day.date.dayOfWeek
            if (day.distance != null && day.duration != null) {
                daySummaries[idx].alpha = 1f
                daySummaries[idx].statTopValue =
                    getUserDistance(context, day.distance).roundToInt().toString()
                daySummaries[idx].statTopUnits = getUserDistanceUnitShort(context).uppercase()
                daySummaries[idx].statBottomValue =
                    Duration.of(day.duration.roundToLong(), ChronoUnit.SECONDS).plusSeconds(30)
                        .toMinutes()
                        .toString()
                daySummaries[idx].statBottomUnits = "MIN"
            }
        }
    }

    fun populate(data: Array<Trip>) {
        data.forEach { trip ->
            Log.d(this.javaClass.simpleName, trip.id.toString())
            daySummaries.find {
                it.dayOfWeek == Instant.ofEpochMilli(trip.timestamp).atZone(ZoneId.systemDefault())
                    .dayOfWeek
            }?.let {
                trip.distance?.let { dist ->
                    it.statTopValue =
                        getUserDistance(context, dist).roundToInt().toString()
                    it.statTopUnits = getUserDistanceUnitShort(context).uppercase()
                    it.alpha = 1f
                }
                trip.duration?.let { time ->
                    it.statBottomValue =
                        Duration.of(time.roundToLong(), ChronoUnit.SECONDS).plusSeconds(30)
                            .toMinutes()
                            .toString()
                    it.statBottomUnits = "MIN"
                    it.alpha = 1f
                }
            }
        }
    }

    override fun onDescendantInvalidated(child: View, target: View) {
        super.onDescendantInvalidated(child, target)
        child.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
    }
}