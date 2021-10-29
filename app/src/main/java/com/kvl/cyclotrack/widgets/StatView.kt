package com.kvl.cyclotrack.widgets

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.kvl.cyclotrack.R

/**
 * TODO: document your custom view class.
 */
class StatView : ConstraintLayout {

    private lateinit var statUnitsView: TextView
    private lateinit var statValueView: TextView

    private var attrTextSizeUnits: Float? = null
    private var attrTextSizeValue: Float? = null

    var statValue: String
        get() = statValueView.text.toString()
        set(value) {
            statValueView.text = value
        }

    var statUnits: String
        get() = statUnitsView.text.toString()
        set(value) {
            statUnitsView.text = value
        }

    init {
        View.inflate(context, R.layout.view_stat_view, this)
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
            attrs, R.styleable.StatView, defStyle, 0
        ).let { a ->
            a.getString(R.styleable.StatView_value)
                ?.let { statValue = it }
            a.getString(R.styleable.StatView_units)
                ?.let { statUnits = it }
            a.getDimension(R.styleable.StatView_textSizeUnits, -1f)
                .takeIf { it > 0f }?.let { attrTextSizeUnits = it }
            a.getDimension(R.styleable.StatView_textSizeValue, -1f)
                .takeIf { it > 0f }?.let { attrTextSizeValue = it }
            a.recycle()
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        statUnitsView = findViewById(R.id.statView_units)
        statValueView = findViewById(R.id.statView_value)

        attrTextSizeUnits?.let { statUnitsView.setTextSize(TypedValue.COMPLEX_UNIT_PX, it) }
        attrTextSizeValue?.let { statValueView.setTextSize(TypedValue.COMPLEX_UNIT_PX, it) }
    }
}