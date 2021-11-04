package com.kvl.cyclotrack.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.kvl.cyclotrack.R

class ThreeStat : ConstraintLayout {
    var statArray = emptyList<StatView>().toMutableList()

    init {
        View.inflate(context, R.layout.view_three_stat, this)
        statArray.add(findViewById(R.id.threeStat_statOne))
        statArray.add(findViewById(R.id.threeStat_statTwo))
        statArray.add(findViewById(R.id.threeStat_statThree))
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

    private fun init(attrs: AttributeSet?, defStyle: Int) {}

    fun populate(contents: Array<Pair<String, String>>) {
        contents.forEachIndexed { idx, it ->
            statArray[idx].statUnits = it.first
            statArray[idx].statValue = it.second
        }
    }
}