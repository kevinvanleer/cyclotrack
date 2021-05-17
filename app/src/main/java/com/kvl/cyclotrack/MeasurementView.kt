package com.kvl.cyclotrack

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat

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
            val digitLength = newValue.filter { it.isDigit() }.length
            val multiplier = (digitLength - 4).coerceAtLeast(0)
            val textSize =
                if (_valueTextSizeAttr > 0) _valueTextSizeAttr else TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP,
                    40f,
                    resources.displayMetrics)
            val factor =
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 4f, resources.displayMetrics)
            val factorB = if (multiplier > 0) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                (newValue.length - digitLength).toFloat(),
                resources.displayMetrics) else 0f

            measurementValueView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                textSize - (multiplier * factor) - factorB)

            measurementValueView.text = newValue

            //TODO: Lookup table would be the next iteration. I already derived the values
            //Length / Size subtract
            //     6 /  8
            //     7 / 12
            //     8 / 15
            //     9 / 18
            //    10 / 20
            // Plus 1 for each colon
            // Still it is probably better to skip this step and go straight to getTextBounds
            // https://stackoverflow.com/questions/3630086/how-to-get-string-width-on-android
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

    fun setIcon(drawableId: Int) =
        measurementExtraInfoImageView.setImageDrawable(ResourcesCompat.getDrawable(resources,
            drawableId, null))

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