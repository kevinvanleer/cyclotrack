package com.kvl.cyclotrack.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.kvl.cyclotrack.R

class AnalyticsCard : CardView {
    val heading: TextView
    val threeStat: ThreeStat
    val table: Table
    val layout: LinearLayout

    init {
        inflate(context, R.layout.view_analytics_card, this)
        heading = findViewById(R.id.analyticsCard_heading)
        threeStat = findViewById(R.id.analyticsCard_threeStat)
        table = findViewById(R.id.analyticsCard_table)
        layout = findViewById(R.id.analyticsCard_layout)
    }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    )

    @Override
    override fun addView(child: View?) {
        //super.addView(child)
        layout.addView(child)
    }
}
