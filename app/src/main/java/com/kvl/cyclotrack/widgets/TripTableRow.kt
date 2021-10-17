package com.kvl.cyclotrack.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TableRow
import android.widget.TextView
import com.kvl.cyclotrack.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TripTableRow : TableRow {

    var dateText: TextView
    var nameText: TextView
    var distanceText: TextView
    var durationText: TextView

    init {
        View.inflate(context, R.layout.view_trip_table_row, this)
        dateText = findViewById(R.id.tripTableRow_date)
        nameText = findViewById(R.id.tripTableRow_name)
        distanceText = findViewById(R.id.tripTableRow_distance)
        durationText = findViewById(R.id.tripTableRow_duration)
    }

    constructor(context: Context) : super(context) {
        init(null, 0)
    }

    constructor(context: Context, trip: Trip) : super(context) {
        init(null, 0)
        setFields(trip)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(attrs, 0)
    }

    private fun init(attrs: AttributeSet?, defStyle: Int) {
        context.obtainStyledAttributes(
            attrs, R.styleable.TextSquare, defStyle, 0
        ).let { a ->

            a.recycle()
        }
    }

    fun setFields(trip: Trip) {
        dateText.text = Instant.ofEpochMilli(trip.timestamp).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
        nameText.text = trip.name
        distanceText.text = "%.1f %s".format(
            getUserDistance(context, trip.distance ?: 0.0),
            getUserDistanceUnitShort(context)
        )
        durationText.text = formatDuration(trip.duration ?: 0.0)
    }
}
