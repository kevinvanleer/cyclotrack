package com.kvl.cyclotrack.preferences

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ProgressBar
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.kvl.cyclotrack.R

class ProgressPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    var progress: Int = 0
        set(value) {
            field = value
            notifyChanged()
        }

    var max: Int = 0
        set(value) {
            field = value
            notifyChanged()
        }

    init {
        layoutResource = R.layout.preference_progress
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        (holder.findViewById(R.id.preference_progress_bar) as ProgressBar).apply {
            visibility = if (this@ProgressPreference.max > 0) View.VISIBLE else View.GONE
            max = this@ProgressPreference.max
            progress = this@ProgressPreference.progress
        }
    }
}
