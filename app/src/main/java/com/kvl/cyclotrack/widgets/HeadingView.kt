package com.kvl.cyclotrack.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.kvl.cyclotrack.R
import kotlin.properties.Delegates

class HeadingView(context: Context, attrs: AttributeSet) : ConstraintLayout(context, attrs) {

    private var labelAttr = "LABEL"
    private var srcAttr by Delegates.notNull<Int>()
    private lateinit var headingLabelView: TextView
    private lateinit var headingValueView: TextView
    private lateinit var headingIcon: ImageView

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

    var icon: Int
        get() = 0
        set(newValue) {
            headingIcon.setImageResource(newValue)
            headingIcon.visibility = View.VISIBLE
        }

    init {
        View.inflate(context, R.layout.view_heading, this)

        val attributes = context.obtainStyledAttributes(attrs, R.styleable.Heading, 0, 0)

        labelAttr = attributes.getString(R.styleable.Heading_headingLabel) ?: ""
        srcAttr = attributes.getResourceId(R.styleable.Heading_iconSrc, 0)

        attributes.recycle()
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        headingLabelView = findViewById(R.id.heading_label)
        headingValueView = findViewById(R.id.heading_value)
        headingIcon = findViewById(R.id.heading_icon)

        label = labelAttr
        if (srcAttr > 0) icon = srcAttr
    }
}