package com.kvl.cyclotrack.widgets

import android.content.Context
import android.util.AttributeSet
import android.widget.TableLayout

data class TableColumn(
    val id: String,
    val label: String,
    val shrinkable: Boolean = false,
    val stretchable: Boolean = true,
    val collapsed: Boolean = false
)

class Table : TableLayout {
    var columns = listOf<TableColumn>()
        set(newValue) {
            newValue.forEachIndexed { idx, it ->
                setColumnCollapsed(idx, it.collapsed)
                setColumnShrinkable(idx, it.shrinkable)
                setColumnStretchable(idx, it.stretchable)
            }
            field = newValue
        }

    constructor(context: Context) : super(context)
    constructor(context: Context, columns: List<TableColumn>) : super(context) {
        this.columns = columns
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    fun populate(data: List<List<String>>) {
        removeAllViews()

        addView(TextTableRow(context, columns.map { it.label }))
        data.forEach { row ->
            addView(TextTableRow(context, row))
        }
    }
}