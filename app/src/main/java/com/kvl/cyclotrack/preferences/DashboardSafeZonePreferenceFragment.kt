package com.kvl.cyclotrack.preferences

import android.graphics.Rect
import android.graphics.drawable.ShapeDrawable
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
import androidx.fragment.app.Fragment
import com.kvl.cyclotrack.R
import com.kvl.cyclotrack.util.getSafeZoneMargins
import com.kvl.cyclotrack.util.putSafeZoneMargins
import java.lang.Integer.max
import kotlin.math.roundToInt

class DashboardSafeZonePreferenceFragment : Fragment(), OnTouchListener {
    val logTag = "DashboardSafeZoneFrag"
    private val touchPoints = mutableListOf<MutableList<Pair<Float, Float>>>()
    private var safeZone: Rect = Rect(0, 0, 0, 0)
    private lateinit var resetButton: Button
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

        safeZone = getSafeZoneMargins(requireContext())
        (dashboard.layoutParams as MarginLayoutParams).apply {
            topMargin = safeZone.top
            bottomMargin = safeZone.bottom
            leftMargin = safeZone.left
            rightMargin = safeZone.right
        }

        setWidgetVisibility(view)

        view.setOnTouchListener(this)

        resetButton.setOnClickListener {
            canvas.setImageDrawable(ShapeDrawable())
            safeZone = Rect()
            touchPoints.clear()
            putSafeZoneMargins(requireContext(), safeZone)
            resetButton.visibility = View.INVISIBLE
            view.findViewById<View>(R.id.button_dashboard_safe_zone_instructions).visibility =
                View.VISIBLE
            dashboard.layoutParams = (dashboard.layoutParams as MarginLayoutParams).apply {
                topMargin = safeZone.top
                bottomMargin = safeZone.bottom
                leftMargin = safeZone.left
                rightMargin = safeZone.right
            }
        }
    }

    private fun setWidgetVisibility(view: View) {
        when (safeZone.top + safeZone.bottom + safeZone.left + safeZone.right) {
            0 -> {
                resetButton.visibility = View.INVISIBLE
                view.findViewById<View>(R.id.button_dashboard_safe_zone_instructions).visibility =
                    View.VISIBLE
            }
            else -> {
                resetButton.visibility = View.VISIBLE
                view.findViewById<View>(R.id.button_dashboard_safe_zone_instructions).visibility =
                    View.INVISIBLE
            }
        }
    }

    private fun setSafeZoneMarginsFromPinch(endRect: Rect, startRect: Rect) {
        safeZone.bottom = max(startRect.bottom - endRect.bottom, 0)
        safeZone.top = max(endRect.top - startRect.top, 0)
        safeZone.left = max(endRect.left - startRect.left, 0)
        safeZone.right = max(startRect.right - endRect.right, 0)

        dashboard.layoutParams = (dashboard.layoutParams as MarginLayoutParams).apply {
            Log.v(logTag, "UPDATING SAFE ZONE MARGINS")
            topMargin = safeZone.top
            bottomMargin = safeZone.bottom
            leftMargin = safeZone.left
            rightMargin = safeZone.right
        }

        view?.let { setWidgetVisibility(it) }

        Log.d(logTag, safeZone.toString())
        putSafeZoneMargins(requireContext(), safeZone)
    }

    fun getCoordRect(event: MotionEvent): Rect {
        val xArray = floatArrayOf(event.getX(0), event.getX(1));
        val yArray = floatArrayOf(event.getY(0), event.getY(1));

        return Rect(
            xArray.min().roundToInt(),
            yArray.min().roundToInt(),
            xArray.max().roundToInt(),
            yArray.max().roundToInt()
        )
    }

    var scaleReference: Rect = Rect(0, 0, 0, 0);
    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        if (event?.pointerCount == 2) {
            v?.let {
                v.findViewById<View>(R.id.button_dashboard_safe_zone_instructions).visibility =
                    View.INVISIBLE
            }
            Log.v(logTag, event.toString())
            when (event?.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN -> {
                    Log.d(logTag, "POINTER DOWN")
                    scaleReference = getCoordRect(event).apply {
                        top -= safeZone.top
                        left -= safeZone.left
                        bottom += safeZone.bottom
                        right += safeZone.right
                    }
                }
                MotionEvent.ACTION_MOVE -> setSafeZoneMarginsFromPinch(
                    getCoordRect(event),
                    scaleReference
                )
            }

        }
        return true
    }

}
