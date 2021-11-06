package com.kvl.cyclotrack.widgets

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.CompoundButton
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.databinding.BindingAdapter
import androidx.databinding.InverseBindingListener
import com.kvl.cyclotrack.R


class SwitchPreference : ConstraintLayout {
    private val logTag = "kvl::SwitchPreference"
    private var _titleTextAttr: String? = ""
    private var _summaryTextAttr: String? = ""

    private lateinit var titleTextView: TextView
    private lateinit var summaryTextView: TextView
    private lateinit var switchView: SwitchCompat

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        initializeAttributes(attrs, 0)
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        initializeAttributes(attrs, defStyle)
    }

    init {
        View.inflate(context, R.layout.view_switch_preference, this)
    }

    var checked: Boolean
        get() = switchView.isChecked
        set(newValue) {
            switchView.isChecked = newValue
        }

    companion object {
        /*
        @BindingAdapter("enabled")
        @JvmStatic
        fun setEnabled(view: SwitchPreference, newValue: Boolean) {
            view.isEnabled = newValue
            view.switchView.isEnabled = view.isEnabled
            view.titleTextView.isEnabled = view.isEnabled
            view.summaryTextView.visibility = if (view.isEnabled) VISIBLE else GONE
        }

        @InverseBindingAdapter(attribute = "checked")
        @JvmStatic
        fun getChecked(view: SwitchPreference) = view.checked

        @BindingAdapter("checked")
        @JvmStatic
        fun setChecked(view: SwitchPreference, newValue: Boolean) {
            view.checked = newValue
        }*/


        @BindingAdapter(value = ["onCheckedChanged", "checkedAttrChanged"], requireAll = false)
        @JvmStatic
        fun setListeners(
            view: SwitchPreference,
            listener: CompoundButton.OnCheckedChangeListener?,
            attrChange: InverseBindingListener?
        ) {
            if (attrChange == null) view.switchView.setOnCheckedChangeListener(listener)
            else view.switchView.setOnCheckedChangeListener { buttonView: CompoundButton, isChecked: Boolean ->
                Log.d(view.logTag, "Triggered setOnCheckedChangeListener")
                listener?.onCheckedChanged(buttonView, isChecked)
                attrChange.onChange()
            }
        }

    }

    private fun initializeAttributes(attrs: AttributeSet, defStyleAttr: Int) {

        val attributes = context.obtainStyledAttributes(
            attrs, R.styleable.SwitchPreference, defStyleAttr, 0
        )

        _titleTextAttr = attributes.getString(R.styleable.SwitchPreference_title)
        _summaryTextAttr = attributes.getString(R.styleable.SwitchPreference_summary)

        attributes.recycle()
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        titleTextView = findViewById(R.id.textView_switchPreferenceTitle)
        summaryTextView = findViewById(R.id.textView_switchPreferenceSummary)
        switchView = findViewById(R.id.switch_switchPreferenceToggle)

        titleTextView.text = _titleTextAttr
        summaryTextView.text = _summaryTextAttr

        titleTextView.setOnClickListener {
            switchView.performClick()
        }
        summaryTextView.setOnClickListener {
            switchView.performClick()
        }
    }
}