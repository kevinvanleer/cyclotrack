package com.kvl.cyclotrack

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.kvl.cyclotrack.util.SystemUtils

class RollupView(context: Context, attrs: AttributeSet) : ConstraintLayout(context, attrs) {

    lateinit var totalDistanceView: TextView
    lateinit var totalDurationView: TextView
    lateinit var inspiringMessageView: TextView

    fun applyTotals(totals: TripAggregation) {
        var distanceText = "Welcome to"
        var durationText = "Cyclotrack"
        var inspiringMessage =
            context.getString(R.string.initial_inspiring_message)

        with(totals) {
            if (tripCount > 0) {
                distanceText = String.format(
                    "%.2f %s",
                    getUserDistance(context, totalDistance),
                    getUserDistanceUnitLong(context)
                )
                durationText = formatDuration(totalDuration)

                inspiringMessage =
                    getInspiringMessage(SystemUtils.currentTimeMillis() - lastStart)
            }
        }

        totalDistanceView.text = distanceText
        totalDurationView.text = durationText
        inspiringMessageView.text = inspiringMessage
    }

    init {
        View.inflate(context, R.layout.view_rollup_view, this)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        totalDistanceView = findViewById(R.id.rollup_view_total_distance)
        totalDurationView = findViewById(R.id.rollup_view_total_duration)
        inspiringMessageView = findViewById(R.id.rollup_view_inspiring_message)

        totalDistanceView.text = "Finding your"
        totalDurationView.text = "rides..."
    }

}