package com.kvl.cyclotrack

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout

class MeasurementView(context: Context, attrs: AttributeSet) : ConstraintLayout(context, attrs) {

    init {
        View.inflate(context, R.layout.measurement_view, this)
    }

    private lateinit var measurementLabelView: TextView
    private lateinit var measurementValueView: TextView

    override fun onFinishInflate() {
        super.onFinishInflate()
        measurementLabelView = findViewById(R.id.measurement_label)
        measurementValueView = findViewById(R.id.measurement_value)
    }

    var value: CharSequence
        get()  = measurementValueView.text
        set(newValue) { measurementValueView.text = newValue}

    var label: CharSequence
        get() = measurementLabelView.text
        set(newValue) { measurementLabelView.text = newValue }
}