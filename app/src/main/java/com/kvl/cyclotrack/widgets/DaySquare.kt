package com.kvl.cyclotrack.widgets

import android.content.Context
import android.util.AttributeSet
import com.kvl.cyclotrack.R
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.*

/**
 * TODO: document your custom view class.
 */
class DaySquare : TextSquare {
    var dayOfWeek: DayOfWeek = DayOfWeek.of(1)
        set(value) = value.getDisplayName(TextStyle.FULL, Locale.getDefault()).let {
            primary = it[0].toString().uppercase(Locale.getDefault())
            secondaryTop = it[1].toString().uppercase(Locale.getDefault())
            secondaryBottom = it[2].toString().uppercase(Locale.getDefault())
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
            attrs, R.styleable.DaySquare, defStyle, 0
        ).let { a ->
            a.getInt(R.styleable.DaySquare_dayOfWeek, 0).takeIf { it > 0 }
                ?.let { dayOfWeek = DayOfWeek.of(it) }
            a.recycle()
        }
    }
}