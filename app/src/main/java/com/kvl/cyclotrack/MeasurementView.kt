package com.kvl.cyclotrack

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.doOnTextChanged
import kotlin.math.min

class MeasurementView(context: Context, attrs: AttributeSet) : ConstraintLayout(context, attrs) {
    private var burnInReductionEnabled = false
    private val logTag = "MeasurementView"
    private var _valueTextSizeAttr = -1f
    private var _valueTextAttr = "0.0"
    private var _labelTextAttr = "LABEL"
    private var _autoShrinkTextAttr = false
    private lateinit var measurementLabelView: TextView
    private lateinit var measurementValueView: TextView
    private lateinit var measurementExtraInfoTextView: TextView
    private lateinit var measurementExtraInfoImageView: ImageView
    private var defaultValueTextSize: Float = 0.0f
    private var maxValueTextSize: Float = 0.0f

    var value: CharSequence
        get() = measurementValueView.text
        set(newValue) {
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
            measurementExtraInfoTextView.visibility =
                if (burnInReductionEnabled) View.INVISIBLE else View.VISIBLE
            measurementExtraInfoImageView.visibility =
                if (burnInReductionEnabled) View.INVISIBLE else View.VISIBLE
            measurementExtraInfoTextView.text = newValue
        }

    fun setIcon(drawableId: Int) =
        measurementExtraInfoImageView.setImageDrawable(
            ResourcesCompat.getDrawable(
                resources,
                drawableId, null
            )
        )

    fun setIconVisibility(visibility: Int) {
        measurementExtraInfoImageView.visibility =
            if (burnInReductionEnabled) INVISIBLE else visibility
    }

    init {
        View.inflate(context, R.layout.view_measurement_view, this)
        initialize(attrs)
    }

    private fun initialize(attrs: AttributeSet?) {
        val attributes = context.obtainStyledAttributes(
            attrs, R.styleable.MeasurementView, 0, 0
        )

        _valueTextSizeAttr = attributes.getDimension(R.styleable.MeasurementView_textSize, -1f)
        _labelTextAttr = attributes.getString(R.styleable.MeasurementView_label) ?: "LABEL"
        _valueTextAttr = attributes.getString(R.styleable.MeasurementView_value) ?: "--.--"
        _autoShrinkTextAttr =
            attributes.getBoolean(R.styleable.MeasurementView_autoShrinkText, false)

        attributes.recycle()
    }

    fun enableBurnInReduction(enabled: Boolean) {
        burnInReductionEnabled = enabled
        when (enabled) {
            true -> {
                measurementValueView.paint.apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 1f
                }
                measurementLabelView.paint.apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 1f
                }
                measurementValueView.setTextColor(Color.argb(0.95f, 1f, 1f, 1f))
                measurementLabelView.setTextColor(Color.argb(0.75f, 1f, 1f, 1f))
                measurementExtraInfoImageView.visibility = INVISIBLE
                measurementExtraInfoTextView.visibility = INVISIBLE
            }

            else -> {
                measurementValueView.paint.apply {
                    style = Paint.Style.FILL
                }
                measurementLabelView.paint.apply {
                    style = Paint.Style.FILL
                }
                measurementValueView.setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    defaultValueTextSize
                )
                measurementValueView.setTextColor(Color.WHITE)
                measurementLabelView.setTextColor(Color.WHITE)
                if (measurementExtraInfoTextView.text.isNotEmpty()) {
                    measurementExtraInfoTextView.visibility = VISIBLE
                    measurementExtraInfoImageView.visibility = VISIBLE
                }
            }
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        measurementLabelView = findViewById(R.id.measurement_label)
        measurementValueView = findViewById(R.id.measurement_value)
        measurementExtraInfoImageView = findViewById(R.id.measurement_extra_info_icon)
        measurementExtraInfoTextView = findViewById(R.id.measurement_extra_info)

        measurementExtraInfoTextView.text = null

        if (_valueTextSizeAttr >= 0) {
            measurementValueView.setTextSize(TypedValue.COMPLEX_UNIT_PX, _valueTextSizeAttr)
        }

        defaultValueTextSize = measurementValueView.textSize
        maxValueTextSize = defaultValueTextSize + 4f

        if (_autoShrinkTextAttr) {
            measurementValueView.doOnTextChanged { newText, _, _, _ ->
                //TODO: SOMETIMES measurementValueView.width != this.width
                measurementValueView.paint.let {
                    while (this.width > 0 && this.width < it.measureText(
                            newText.toString()
                        )
                    ) {
                        Log.d(
                            logTag,
                            "SHRINKING: string width = ${it.measureText(newText.toString())}"
                        )
                        Log.d(
                            logTag,
                            "SHRINKING: view width = ${this.width}:${measurementValueView.width}"
                        )
                        Log.d(
                            logTag,
                            "SHRINKING: text to wide = $newText"
                        )
                        measurementValueView.setTextSize(
                            TypedValue.COMPLEX_UNIT_PX,
                            measurementValueView.textSize - 1f
                        )
                        maxValueTextSize = min(measurementValueView.textSize, defaultValueTextSize)
                    }
                }
            }
        }
        var direction = 1f
        measurementValueView.doOnTextChanged { _, _, _, _ ->
            if (burnInReductionEnabled) {
                if (measurementValueView.textSize >= maxValueTextSize
                ) {
                    direction = -1f
                } else if (measurementValueView.textSize <= maxValueTextSize - 8f
                ) {
                    direction = 1f
                }
                measurementValueView.setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    measurementValueView.textSize + (direction * 0.1f)
                )

                Log.v(
                    logTag,
                    "${measurementLabelView.text} default text size = $defaultValueTextSize"
                )
                Log.v(
                    logTag,
                    "${measurementLabelView.text} max text size = $maxValueTextSize"
                )
                Log.v(
                    logTag,
                    "${measurementLabelView.text} text size = ${measurementValueView.textSize}"
                )
            }
        }

        measurementExtraInfoTextView.visibility = View.GONE
        measurementExtraInfoImageView.visibility = View.GONE

        value = _valueTextAttr
        label = _labelTextAttr
    }
}
