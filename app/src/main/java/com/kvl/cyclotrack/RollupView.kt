package com.kvl.cyclotrack

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.toDuration

class RollupView(context: Context, attrs: AttributeSet) : ConstraintLayout(context, attrs) {

    lateinit var totalDistanceView: TextView
    lateinit var totalDurationView: TextView
    lateinit var inspiringMessageView: TextView


    @ExperimentalTime
    fun rollupTripData(trips: Array<Trip>) {
        var distanceText = "Welcome to"
        var durationText = "Cyclotrack"
        var inspiringMessage =
            "Ready to start your first ride? Touch the bike button and let's get moving!"

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
                when ((System.currentTimeMillis() - trips.first().timestamp) / 3600000.0) {
                    in 0.0..0.5 -> "Great job! Keep up the good work!"
                    in 0.5..1.5 -> "Get some rest you earned it! Recovery is an important part of fitness."
                    in 1.5..3.0 -> "Alright! Let's hit the trail!"
                    else -> "It has been ${
                        (System.currentTimeMillis() - trips.first().timestamp).toDuration(
                            DurationUnit.MILLISECONDS).inDays.toInt()
                    } days since your last ride. Let's make it happen!"
                }
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