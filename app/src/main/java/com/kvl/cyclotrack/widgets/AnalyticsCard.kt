package com.kvl.cyclotrack.widgets

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.kvl.cyclotrack.R

class AnalyticsCard : CardView {
    lateinit var heading: TextView
        private set
    lateinit var threeStat: ThreeStat
        private set
    lateinit var table: Table
        private set

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    )

    init {
        inflate(context, R.layout.view_analytics_card, this)
        heading = findViewById(R.id.analyticsCard_heading)
        threeStat = findViewById(R.id.analyticsCard_threeStat)
        table = findViewById(R.id.analyticsCard_table)
    }
}