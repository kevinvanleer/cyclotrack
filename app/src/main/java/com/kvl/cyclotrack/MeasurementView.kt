package com.kvl.cyclotrack

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout

/**
 * TODO: document your custom view class.
 */
class MeasurementView(context: Context, attrs: AttributeSet) : ConstraintLayout(context, attrs) {

    /*private var _exampleString: String? = null // TODO: use a default from R.string...
    private var _exampleColor: Int = Color.RED // TODO: use a default from R.color...
    private var _exampleDimension: Float = 0f // TODO: use a default from R.dimen...

    private lateinit var textPaint: TextPaint
    private var textWidth: Float = 0f
    private var textHeight: Float = 0f*/

    private var _valueTextSizeAttr = -1f
    private var _valueTextAttr = "0.0"
    private var _labelTextAttr = "LABEL"
    private lateinit var measurementLabelView: TextView
    private lateinit var measurementValueView: TextView

    init {
        View.inflate(context, R.layout.measurement_view, this)
        initialize(attrs)
    }

    /*
    /**
     * The text to draw
     */
    var exampleString: String?
        get() = _exampleString
        set(value) {
            _exampleString = value
            invalidateTextPaintAndMeasurements()
        }

    /**
     * The font color
     */
    var exampleColor: Int
        get() = _exampleColor
        set(value) {
            _exampleColor = value
            invalidateTextPaintAndMeasurements()
        }

    /**
     * In the example view, this dimension is the font size.
     */
    var exampleDimension: Float
        get() = _exampleDimension
        set(value) {
            _exampleDimension = value
            invalidateTextPaintAndMeasurements()
        }

    /**
     * In the example view, this drawable is drawn above the text.
     */
    var exampleDrawable: Drawable? = null*/

    /*
    constructor(context: Context) : super(context) {
        init(null, 0)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(attrs, 0)
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context,
        attrs,
        defStyle) {
        init(attrs, defStyle)
    }
    */

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

        if(_valueTextSizeAttr >= 0) {
            measurementValueView.setTextSize(TypedValue.COMPLEX_UNIT_PX, _valueTextSizeAttr)
        }

        value = _valueTextAttr
        label = _labelTextAttr
    }

    var value: CharSequence
        get()  = measurementValueView.text
        set(newValue) { measurementValueView.text = newValue}

    var label: CharSequence
        get() = measurementLabelView.text
        set(newValue) { measurementLabelView.text = newValue }
}