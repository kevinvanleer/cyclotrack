package com.kvl.cyclotrack.widgets

import android.content.Context
import android.util.AttributeSet
import android.widget.TableRow
import android.widget.TextView

class TextTableRow : TableRow {
    constructor(context: Context) : super(context)

    constructor(context: Context, rowData: List<String>) : super(context) {
        setFields(rowData)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    fun setFields(rowData: List<String>) {
        removeAllViews()
        rowData.forEach {
            addView(TextView(context).apply {
                text = it
            })
        }
    }
}
