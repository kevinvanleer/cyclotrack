package com.kvl.cyclotrack.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.kvl.cyclotrack.R
import java.time.DayOfWeek

/**
 * TODO: document your custom view class.
 */
class DaySummary : ConstraintLayout {
    var dayOfWeek: DayOfWeek = DayOfWeek.of(1)
        set(value) {
            if (this::daySquare.isInitialized) {
                daySquare.dayOfWeek = value
            }
            field = value
        }

    private lateinit var daySquare: DaySquare
    private lateinit var statTopView: StatView
    private lateinit var statBottomView: StatView

    var statTopValue: String
        get() = statTopView.statValue
        set(value) {
            statTopView.statValue = value
        }
    var statBottomValue: String
        get() = statBottomView.statValue
        set(value) {
            statBottomView.statValue = value
        }

    var statTopUnits: String
        get() = statTopView.statUnits
        set(value) {
            statTopView.statUnits = value
        }
    var statBottomUnits: String
        get() = statBottomView.statUnits
        set(value) {
            statBottomView.statUnits = value
        }

    init {
        View.inflate(context, R.layout.view_day_summary, this)
    }

    constructor(context: Context) : super(context) {
        init(null, 0)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(attrs, 0)
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        init(attrs, defStyle)
    }

    private fun init(attrs: AttributeSet?, defStyle: Int) {
        context.obtainStyledAttributes(
            attrs, R.styleable.DaySummary, defStyle, 0
        ).let { a ->
            a.getInt(R.styleable.DaySummary_dayOfWeek, 0).takeIf { it >= 0 }
                ?.let { dayOfWeek = DayOfWeek.of(it) }
            a.recycle()
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        daySquare = findViewById(R.id.daySummary_daySquare)
        statTopView = findViewById(R.id.daySummary_stat1)
        statBottomView = findViewById(R.id.daySummary_stat2)

        daySquare.dayOfWeek = dayOfWeek
    }
}