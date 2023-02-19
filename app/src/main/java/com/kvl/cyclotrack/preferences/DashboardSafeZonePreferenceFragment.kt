package com.kvl.cyclotrack.preferences

import android.graphics.Rect
import android.graphics.drawable.ShapeDrawable
import android.os.Build
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
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.*
import androidx.fragment.app.Fragment
import com.kvl.cyclotrack.R
import com.kvl.cyclotrack.util.getSafeZoneMargins
import com.kvl.cyclotrack.util.isLoop
import com.kvl.cyclotrack.util.putSafeZoneMargins
import com.kvl.cyclotrack.widgets.SafeZone
import java.lang.Float.min
import kotlin.math.ceil
import kotlin.math.floor

class DashboardSafeZonePreferenceFragment : Fragment(), OnTouchListener {
    val logTag = "DashboardSafeZoneFrag"
    private val touchPoints = mutableListOf<MutableList<Pair<Float, Float>>>()
    private var safeZone: Rect = Rect(0, 0, 0, 0)
    private lateinit var resetButton: Button
    private lateinit var backButton: Button
    private lateinit var dashboard: ConstraintLayout
    private lateinit var canvas: ImageView

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
        resetButton = view.findViewById(R.id.button_dashboard_safe_zone_reset)
        backButton = view.findViewById(R.id.button_dashboard_safe_zone_back)
        dashboard = view.findViewById(R.id.included_dashboard_layout)
        canvas = view.findViewById(R.id.imageView_dashboard_safe_zone_canvas)

        view.findViewById<TextView>(R.id.dashboard_textview_timeOfDay).apply {
            text = "12:03 PM"
        }
        view.findViewById<TextView>(R.id.measurement_footer).apply {
            text = "N"
        }
        view.findViewById<TextView>(R.id.measurement_footer_right).apply {
            visibility = View.VISIBLE
            text = "12"
        }
        view.findViewById<ImageView>(R.id.image_wind_icon).apply { visibility = View.VISIBLE }
        view.findViewById<ImageView>(R.id.image_arrow_wind_direction)
            .apply { visibility = View.VISIBLE }

        pauseButton.visibility = View.GONE
        resumeButton.visibility = View.GONE
        autoPauseChip.visibility = View.GONE
        stopButton.visibility = View.GONE
        trackingImage.visibility = View.GONE
        debugTextView.visibility = View.GONE


        WindowCompat.getInsetsController(
            requireActivity().window,
            view
        ).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        fun getExclusionRects(view: View): List<Rect> {
            var rects = mutableListOf<Rect>();
            val displayMetrics = resources.displayMetrics
            /*for (y in 0..view.height step 200) {
                rects.add(Rect(0, y, 50, y + 200))
                rects.add(Rect(view.width - 50, y, view.width, y + 200))
            }*/
            rects.add(Rect(0, 0, 50, displayMetrics.heightPixels))
            rects.add(
                Rect(
                    displayMetrics.widthPixels - 50,
                    0,
                    displayMetrics.widthPixels,
                    displayMetrics.heightPixels
                )
            )
            return rects.toList();
        }
        view.doOnPreDraw { v ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Log.d(logTag, "Setting exclusion zones");
                v.systemGestureExclusionRects = getExclusionRects(v)
            }
        }
        view.doOnLayout { v ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Log.d(logTag, "Setting exclusion zones");
                v.systemGestureExclusionRects = getExclusionRects(v)
            }
        }

        safeZone = getSafeZoneMargins(requireContext())
        (dashboard.layoutParams as MarginLayoutParams).apply {
            topMargin = safeZone.top
            bottomMargin = safeZone.bottom
            leftMargin = safeZone.left
            rightMargin = safeZone.right
        }

        when (safeZone.top + safeZone.bottom + safeZone.left + safeZone.right) {
            0 -> resetButton.visibility = View.INVISIBLE
            else -> resetButton.visibility = View.VISIBLE
        }

        view.setOnTouchListener(this)

        resetButton.setOnClickListener {
            canvas.setImageDrawable(ShapeDrawable())
            safeZone = Rect()
            touchPoints.clear()
            putSafeZoneMargins(requireContext(), safeZone)
            resetButton.visibility = View.INVISIBLE
            dashboard.layoutParams = (dashboard.layoutParams as MarginLayoutParams).apply {
                topMargin = safeZone.top
                bottomMargin = safeZone.bottom
                leftMargin = safeZone.left
                rightMargin = safeZone.right
            }
        }

        backButton.setOnClickListener {
            WindowCompat.getInsetsController(
                requireActivity().window,
                requireActivity().window.decorView
            ).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE
                show(WindowInsetsCompat.Type.systemBars())
            }
            activity?.finish();
        }
    }


    private fun setSafeZoneMargins(strokeWidth: Float) {
        Log.v(logTag, touchPoints.toString())
        val displayMetrics = resources.displayMetrics
        touchPoints.forEach { zone ->
            val xCoords = zone.map { coord -> coord.first }
            val yCoords = zone.map { coord -> coord.second }
            val yAvg = yCoords.average()
            val xAvg = xCoords.average()

            val isLoop = isLoop(zone)
            val isTopToBottom = yCoords.max() - yCoords.min() > displayMetrics.heightPixels * 0.8
            val isSideToSide = xCoords.max() - xCoords.min() > displayMetrics.widthPixels * 0.8

            when {
                isSideToSide -> {
                    val isBottomMargin = yAvg / displayMetrics.heightPixels > 0.5
                    if (isBottomMargin) {
                        safeZone.bottom =
                            (displayMetrics.heightPixels - floor(yCoords.min()).toInt() + strokeWidth / 2).toInt()
                    } else {
                        safeZone.top = (ceil(yCoords.max()).toInt() - strokeWidth / 2).toInt()
                    }
                }
                isTopToBottom -> {
                    val isLeftMargin = xAvg / displayMetrics.widthPixels < 0.5
                    if (isLeftMargin) {
                        safeZone.left = (ceil(xCoords.max()).toInt() + strokeWidth / 2).toInt()
                    } else {
                        safeZone.right =
                            (displayMetrics.widthPixels - floor(xCoords.min()).toInt() - strokeWidth / 2).toInt()
                    }
                }
                else -> touchPoints.remove(zone)
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
            0 -> resetButton.visibility = View.INVISIBLE
            else -> resetButton.visibility = View.VISIBLE
        }
        putSafeZoneMargins(requireContext(), safeZone)
    }

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        Log.v(logTag, event.toString())
        val strokeWidth = 0.25f * min(resources.displayMetrics.xdpi, resources.displayMetrics.ydpi)
        when (event?.action) {
            MotionEvent.ACTION_UP -> setSafeZoneMargins(strokeWidth)
            MotionEvent.ACTION_DOWN -> touchPoints.add(mutableListOf(Pair(event.x, event.y)))
            MotionEvent.ACTION_MOVE -> touchPoints.last().add(Pair(event.x, event.y))
        }
        Log.v(logTag, "${resources.displayMetrics.xdpi}, ${resources.displayMetrics.ydpi}")
        canvas.setImageDrawable(
            SafeZone(
                touchPoints,
                strokeWidth,
                ResourcesCompat.getColor(requireContext().resources, R.color.accentColor, null)
            )
        )
        return true
    }

}
