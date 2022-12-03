package com.kvl.cyclotrack.preferences

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.kvl.cyclotrack.R

class DashboardSafeZonePreferenceFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_trip_in_progress, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val pauseButton: Button = view.findViewById(R.id.pause_button)
        val resumeButton: Button = view.findViewById(R.id.resume_button)
        val autoPauseChip: Button = view.findViewById(R.id.autopause_button)
        val stopButton: Button = view.findViewById(R.id.stop_button)
        pauseButton.visibility = View.GONE
        resumeButton.visibility = View.GONE
        autoPauseChip.visibility = View.GONE
        stopButton.visibility = View.GONE
    }
}
