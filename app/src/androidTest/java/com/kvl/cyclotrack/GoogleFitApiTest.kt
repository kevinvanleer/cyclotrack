package com.kvl.cyclotrack

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class GoogleFitApiTest {
    @Test
    fun googleFitTest() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val mainActivity = launchActivity<MainActivity>()
        val logTag = "GOOGLE_FIT_TEST"
        val fitnessOptions = FitnessOptions.builder()
            //.addDataType(DataType.AGGREGATE_HEIGHT_SUMMARY,
            //    FitnessOptions.ACCESS_READ)
            //.addDataType(DataType.AGGREGATE_WEIGHT_SUMMARY,
            //    FitnessOptions.ACCESS_READ)
            //.addDataType(DataType.TYPE_ACTIVITY_SEGMENT, FitnessOptions.ACCESS_READ)
            //.addDataType(DataType.TYPE_ACTIVITY_SEGMENT,
            //    FitnessOptions.ACCESS_WRITE)
            .addDataType(DataType.AGGREGATE_ACTIVITY_SUMMARY,
                FitnessOptions.ACCESS_READ)
            .build()

        fun getGoogleAccount() = GoogleSignIn.getLastSignedInAccount(appContext)
        // Context of the app under test.
        if (getGoogleAccount() == null) {
            Log.d(logTag, "Google account is null")
        } else {
            Log.d(logTag, "Google account is valid")
        }

        if (!GoogleSignIn.hasPermissions(getGoogleAccount(), fitnessOptions)) {
            Log.d(logTag, "Syncing with Google Fit")
            GoogleSignIn.requestPermissions(this,
                1,
                getGoogleAccount(),
                fitnessOptions)
        } else {
            Log.d(logTag, "Already logged in to Google Fit")
            accessGoogleFit()
        }
    }
}