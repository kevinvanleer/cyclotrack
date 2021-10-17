package com.kvl.cyclotrack.widgets

import android.content.Context
import android.util.AttributeSet
import android.widget.TableLayout
import androidx.core.view.updatePadding
import com.kvl.cyclotrack.R
import com.kvl.cyclotrack.Trip

/**
 * TODO: document your custom view class.
 */
class TripTable : TableLayout {

    init {
        //View.inflate(context, R.layout.trip_table, this)
        this.orientation = VERTICAL
        this.setColumnShrinkable(1, true)
        this.setColumnCollapsed(1, true)
        this.setColumnStretchable(0, true)
        this.setColumnStretchable(2, true)
        this.setColumnStretchable(3, true)
    }

    constructor(context: Context) : super(context) {
        init(null, 0)
    }

    constructor(context: Context, data: Array<Trip>) : super(context) {
        init(null, 0)
        populate(data)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(attrs, 0)
    }

    private fun init(attrs: AttributeSet?, defStyle: Int) {
        // Load attributes
        val a = context.obtainStyledAttributes(
            attrs, R.styleable.WeeklySummaryTable, defStyle, 0
        )
    }

    fun populate(data: Array<Trip>) {
        removeAllViews()
        TripTableRow(context).let { header ->
            header.dateText.text = "DATE"
            header.nameText.text = "NAME"
            header.distanceText.text = "DISTANCE"
            header.durationText.text = "DURATION"
            header.updatePadding(bottom = 16)
            addView(header)
        }
        data.forEach { trip ->
            addView(TripTableRow(context, trip))
        }
    }
}