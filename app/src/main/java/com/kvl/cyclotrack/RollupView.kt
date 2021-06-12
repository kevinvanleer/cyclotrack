package com.kvl.cyclotrack

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout

class RollupView(context: Context, attrs: AttributeSet) : ConstraintLayout(context, attrs) {

    lateinit var totalDistanceView: TextView
    lateinit var totalDurationView: TextView
    lateinit var inspiringMessageView: TextView

    fun rollupTripData(trips: Array<Trip>) {
        var distanceText = "Welcome to"
        var durationText = "Cyclotrack"
        var inspiringMessage =
            context.getString(R.string.initial_inspiring_message)

        if (!trips.isNullOrEmpty()) {
            var totalDistance = 0.0
            var totalDuration = 0.0

            trips.forEach { trip ->
                totalDistance += trip.distance ?: 0.0
                totalDuration += trip.duration ?: 0.0
            }
            distanceText = String.format("%.2f %s",
                getUserDistance(context, totalDistance),
                getUserDistanceUnitLong(context))
            durationText = formatDuration(totalDuration)

            inspiringMessage =
                getInspiringMessage(System.currentTimeMillis() - trips.first().timestamp)
        }
        totalDistanceView.text = distanceText
        totalDurationView.text = durationText
        inspiringMessageView.text = inspiringMessage
    }

    init {
        View.inflate(context, R.layout.rollup_view, this)
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