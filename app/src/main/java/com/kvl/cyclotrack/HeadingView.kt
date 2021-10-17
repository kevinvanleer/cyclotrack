package com.kvl.cyclotrack

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout

class HeadingView(context: Context, attrs: AttributeSet) : ConstraintLayout(context, attrs) {

    private var labelAttr = "LABEL"
    private lateinit var headingLabelView: TextView
    private lateinit var headingValueView: TextView

    var label: CharSequence
        get() = headingLabelView.text
        set(newValue) {
            headingLabelView.text = newValue
        }

    var value: CharSequence
        get() = headingValueView.text
        set(newValue) {
            headingValueView.text = newValue
        }


    init {
        View.inflate(context, R.layout.view_heading, this)

        val attributes = context.obtainStyledAttributes(attrs, R.styleable.Heading, 0, 0)

        labelAttr = attributes.getString(R.styleable.Heading_headingLabel) ?: ""

        attributes.recycle()
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        headingLabelView = findViewById(R.id.heading_label)
        headingValueView = findViewById(R.id.heading_value)

        label = labelAttr
    }
}