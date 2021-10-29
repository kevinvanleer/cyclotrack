package com.kvl.cyclotrack.widgets

import android.content.Context
import android.util.AttributeSet
import android.widget.TableRow
import android.widget.TextView

class TextTableRow : TableRow {
    constructor(context: Context) : super(context) {
        init(null, 0)
    }

    constructor(context: Context, rowData: List<String>) : super(context) {
        init(null, 0)
        setFields(rowData)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(attrs, 0)
    }

    private fun init(attrs: AttributeSet?, defStyle: Int) {
    }

    fun setFields(rowData: List<String>) {
        removeAllViews()
        rowData.forEach {
            addView(TextView(context).apply {
                text = it
            })
        }
    }
}
