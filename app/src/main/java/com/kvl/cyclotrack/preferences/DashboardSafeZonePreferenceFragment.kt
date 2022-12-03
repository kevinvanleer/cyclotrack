package com.kvl.cyclotrack.preferences

import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.Button
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import com.kvl.cyclotrack.R
import com.kvl.cyclotrack.util.getSafeZoneMargins
import com.kvl.cyclotrack.util.putSafeZoneMargins
import kotlin.math.ceil
import kotlin.math.floor

class DashboardSafeZonePreferenceFragment : Fragment(), OnTouchListener {
    val logTag = "DashboardSafeZoneFrag"
    private val touchPoints = mutableListOf<MutableList<Pair<Float, Float>>>()
    private var safeZone: Rect = Rect(0, 0, 0, 0)
    private lateinit var resetButton: Button
    private lateinit var dashboard: ConstraintLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dashboard_safe_zone, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val pauseButton: Button = view.findViewById(R.id.pause_button)
        val resumeButton: Button = view.findViewById(R.id.resume_button)
        val autoPauseChip: Button = view.findViewById(R.id.autopause_button)
        val stopButton: Button = view.findViewById(R.id.stop_button)
        val trackingImage: View = view.findViewById(R.id.image_tracking)
        val debugTextView: View = view.findViewById(R.id.textview_debug)
        val canvas: ImageView = view.findViewById(R.id.imageView_dashboard_safe_zone_canvas)
        resetButton = view.findViewById(R.id.button_dashboard_safe_zone_reset)
        dashboard = view.findViewById(R.id.included_dashboard_layout)

        pauseButton.visibility = View.GONE
        resumeButton.visibility = View.GONE
        autoPauseChip.visibility = View.GONE
        stopButton.visibility = View.GONE
        trackingImage.visibility = View.GONE
        debugTextView.visibility = View.GONE

        safeZone = getSafeZoneMargins(requireContext())
        (dashboard.layoutParams as MarginLayoutParams).apply {
            topMargin = safeZone.top
            bottomMargin = safeZone.bottom
            leftMargin = safeZone.left
            rightMargin = safeZone.right
        }

        when (safeZone.top + safeZone.bottom + safeZone.left + safeZone.right) {
            0 -> resetButton.visibility = View.GONE
            else -> resetButton.visibility = View.VISIBLE
        }

        //canvas.setImageDrawable()
        view.setOnTouchListener(this)

        resetButton.setOnClickListener {
            safeZone = Rect()
            touchPoints.clear()
            putSafeZoneMargins(requireContext(), safeZone)
            resetButton.visibility = View.GONE
            dashboard.layoutParams = (dashboard.layoutParams as MarginLayoutParams).apply {
                topMargin = safeZone.top
                bottomMargin = safeZone.bottom
                leftMargin = safeZone.left
                rightMargin = safeZone.right
            }
        }
    }

    private fun setSafeZoneMargins() {
        Log.v(logTag, touchPoints.toString())
        val displayMetrics = resources.displayMetrics
        touchPoints.forEach { zone ->
            val xCoords = zone.map { coord -> coord.first }
            val yCoords = zone.map { coord -> coord.second }
            val yAvg = yCoords.average()
            val xAvg = xCoords.average()

            val isTopToBottom = yCoords.max() - yCoords.min() > displayMetrics.heightPixels * 0.8
            val isSideToSide = xCoords.max() - xCoords.min() > displayMetrics.widthPixels * 0.8

            if (isSideToSide) {
                val isBottomMargin = yAvg / displayMetrics.heightPixels > 0.5
                if (isBottomMargin) {
                    safeZone.bottom = displayMetrics.heightPixels - floor(yCoords.min()).toInt()
                } else {
                    safeZone.top = ceil(yCoords.max()).toInt()
                }
            }

            if (isTopToBottom) {
                val isLeftMargin = xAvg / displayMetrics.widthPixels < 0.5
                if (isLeftMargin) {
                    safeZone.left = ceil(xCoords.max()).toInt()
                } else {
                    safeZone.right = displayMetrics.widthPixels - floor(xCoords.min()).toInt()
                }
            }

        }
        //One segment
        ////Is segment a loop
        //More than one
        ////Is upper
        ////Is lower
        ////Is left
        ////Is right
        Log.v(logTag, safeZone.toString())
        dashboard.layoutParams = (dashboard.layoutParams as MarginLayoutParams).apply {
            Log.v(logTag, "UPDATING SAFE ZONE MARGINS")
            topMargin = safeZone.top
            bottomMargin = safeZone.bottom
            leftMargin = safeZone.left
            rightMargin = safeZone.right
        }

        when (safeZone.top + safeZone.bottom + safeZone.left + safeZone.right) {
            0 -> resetButton.visibility = View.GONE
            else -> resetButton.visibility = View.VISIBLE
        }
        putSafeZoneMargins(requireContext(), safeZone)
    }

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        Log.v(logTag, event.toString())
        when (event?.action) {
            MotionEvent.ACTION_UP -> setSafeZoneMargins()
            MotionEvent.ACTION_DOWN -> touchPoints.add(mutableListOf())
            MotionEvent.ACTION_MOVE -> touchPoints.last().add(Pair(event.x, event.y))
        }
        return true
    }

}