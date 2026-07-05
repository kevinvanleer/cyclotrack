package com.kvl.cyclotrack.preferences

import android.os.Bundle
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.work.WorkManager
import com.kvl.cyclotrack.OnboardSensorsMigrationWorker
import com.kvl.cyclotrack.OnboardSensorsRepository
import com.kvl.cyclotrack.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AdvancedPreferencesFragment : PreferenceFragmentCompat() {
    @Inject
    lateinit var onboardSensorsRepository: OnboardSensorsRepository

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.advanced_preferences, rootKey)

        val progressPref =
            findPreference<ProgressPreference>("onboard_sensors_migration_progress") ?: return
        WorkManager.getInstance(requireContext())
            .getWorkInfosForUniqueWorkLiveData(OnboardSensorsMigrationWorker.WORK_NAME)
            .observe(this) { refresh(progressPref) }
    }

    private fun refresh(progressPref: ProgressPreference) {
        lifecycleScope.launch {
            val (migrated, total) = onboardSensorsRepository.migrationProgress()
            findPreference<PreferenceCategory>("onboard_sensors_migration_category")
                ?.isVisible = total > 0
            progressPref.max = total
            progressPref.progress = migrated
            progressPref.summary =
                if (migrated < total) "$migrated of $total rides migrated"
                else "Migration complete — $total rides"
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().findViewById<Toolbar>(R.id.preferences_toolbar).title =
            "Settings: Advanced"
    }
}
