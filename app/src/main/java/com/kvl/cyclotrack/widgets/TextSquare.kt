package com.kvl.cyclotrack.widgets

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.kvl.cyclotrack.R
import kotlin.math.max

/**
 * TODO: document your custom view class.
 */
open class TextSquare : ConstraintLayout {
    private val primaryHeightSp = 28f
    private val secondaryHeightSp = 12f
    private val kerningPx = 4f

    private var viewHeight = spToPx(primaryHeightSp)
    private var contentWidth = viewHeight
    private var viewWidth = contentWidth

    private var textColor: Int = 0

    var primary: String = "M"
        set(new) {
            field = new
            invalidate()
        }
    var secondaryTop: String = "W"
        set(new) {
            field = new
            invalidate()
        }
    var secondaryBottom: String = "W"
        set(new) {
            field = new
            invalidate()
        }

    init {
        setWillNotDraw(false)
        viewWidth = getViewWidth()
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

    @ColorInt
    fun Context.resolveColorAttr(@AttrRes colorAttr: Int): Int {
        val resolvedAttr = resolveThemeAttr(colorAttr)
        // resourceId is used if it's a ColorStateList, and data if it's a color reference or a hex color
        val colorRes =
            if (resolvedAttr.resourceId != 0) resolvedAttr.resourceId else resolvedAttr.data
        return ContextCompat.getColor(this, colorRes)
    }

    fun Context.resolveThemeAttr(@AttrRes attrRes: Int): TypedValue {
        val typedValue = TypedValue()
        theme.resolveAttribute(attrRes, typedValue, true)
        return typedValue
    }

    private fun init(attrs: AttributeSet?, defStyle: Int) {
        context.obtainStyledAttributes(
            attrs, R.styleable.TextSquare, defStyle, 0
        ).let { a ->
            a.getString(R.styleable.TextSquare_primaryText)
                ?.let { primary = it }
            a.getString(R.styleable.TextSquare_topText)
                ?.let { secondaryTop = it }
            a.getString(R.styleable.TextSquare_bottomText)
                ?.let { secondaryBottom = it }
            a.getColor(
                R.styleable.TextSquare_android_textColor,
                context.resolveColorAttr(android.R.attr.textColorSecondary)
            ).let { textColor = it }
            a.recycle()
        }
        this.invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension((viewWidth + 0.5).toInt(), (viewHeight + 24.5).toInt())
    }

    private fun spToPx(sp: Float) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        sp,
        Resources.getSystem().displayMetrics
    )

    private fun getTextPaint(): Paint = Paint().apply {
        style = Paint.Style.FILL_AND_STROKE
        isAntiAlias = true
        strokeWidth = 1f
        color = textColor
        //typeface = ResourcesCompat.getFont(context, R.font.orbitron)
        //typeface = ResourcesCompat.getFont(context, R.font.roboto_mono)
    }

    private fun getViewWidth(): Float = getTextPaint().let { paint ->
        paint.textSize = spToPx(secondaryHeightSp)
        max(
            paint.measureText("E"),
            paint.measureText("D")
        ).let { secondaryWidth ->
            paint.textSize = viewHeight
            paint.measureText("W") + secondaryWidth + kerningPx
        }
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        //canvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        getTextPaint().apply {
            textSize = viewHeight
            measureText(primary).let { primaryWidth ->
                textSize = spToPx(secondaryHeightSp)
                contentWidth = max(
                    measureText(secondaryTop),
                    measureText(secondaryBottom)
                ).let { secondaryWidth ->
                    primaryWidth + secondaryWidth + kerningPx
                }
                primaryWidth
            }.let { primaryWidth ->
                val offset = (viewWidth - contentWidth) / 2
                textSize = viewHeight
                canvas?.drawText(primary, offset, viewHeight, this)
                textSize = spToPx(secondaryHeightSp)
                canvas?.drawText(
                    secondaryTop,
                    offset + primaryWidth + kerningPx,
                    spToPx((primaryHeightSp / 2f) + 2.5f),
                    this
                )
                canvas?.drawText(
                    secondaryBottom,
                    offset + primaryWidth + kerningPx,
                    viewHeight,
                    this
                )
            }
        }

        minimumWidth = viewWidth.toInt()
        minimumHeight = viewHeight.toInt()
    }
}