package com.kvl.cyclotrack

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout

class MeasurementView(context: Context, attrs: AttributeSet) : ConstraintLayout(context, attrs) {

    private var _valueTextSizeAttr = -1f
    private var _valueTextAttr = "0.0"
    private var _labelTextAttr = "LABEL"
    private lateinit var measurementLabelView: TextView
    private lateinit var measurementValueView: TextView
    private lateinit var measurementExtraInfoTextView: TextView
    private lateinit var measurementExtraInfoImageView: ImageView

    var value: CharSequence
        get() = measurementValueView.text
        set(newValue) {
            measurementValueView.text = newValue
        }

    var label: CharSequence
        get() = measurementLabelView.text
        set(newValue) {
            measurementLabelView.text = newValue
        }

    var extraInfo: CharSequence
        get() = measurementExtraInfoTextView.text
        set(newValue) {
            measurementExtraInfoTextView.visibility = View.VISIBLE
            measurementExtraInfoImageView.visibility = View.VISIBLE
            measurementExtraInfoTextView.text = newValue
        }

    init {
        View.inflate(context, R.layout.measurement_view, this)
        initialize(attrs)
    }

    private fun initialize(attrs: AttributeSet?) {
        val attributes = context.obtainStyledAttributes(
            attrs, R.styleable.MeasurementView, 0, 0)

        _valueTextSizeAttr = attributes.getDimension(R.styleable.MeasurementView_textSize, -1f)
        _labelTextAttr = attributes.getString(R.styleable.MeasurementView_label) ?: "LABEL"
        _valueTextAttr = attributes.getString(R.styleable.MeasurementView_value) ?: "--.--"

        attributes.recycle()
    }


    override fun onFinishInflate() {
        super.onFinishInflate()
        measurementLabelView = findViewById(R.id.measurement_label)
        measurementValueView = findViewById(R.id.measurement_value)
        measurementExtraInfoImageView = findViewById(R.id.measurement_extra_info_icon)
        measurementExtraInfoTextView = findViewById(R.id.measurement_extra_info)

        if (_valueTextSizeAttr >= 0) {
            measurementValueView.setTextSize(TypedValue.COMPLEX_UNIT_PX, _valueTextSizeAttr)
        }

        measurementExtraInfoTextView.visibility = View.GONE
        measurementExtraInfoImageView.visibility = View.GONE

        value = _valueTextAttr
        label = _labelTextAttr
    }
}