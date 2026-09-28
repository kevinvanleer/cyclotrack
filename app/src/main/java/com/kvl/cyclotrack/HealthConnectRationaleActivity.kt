package com.kvl.cyclotrack

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.core.net.toUri

// ponytail: the privacy policy is the rationale. A dedicated screen restating it
// would be a second thing to keep in sync.
class HealthConnectRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            startActivity(Intent(Intent.ACTION_VIEW, privacyPolicyUri))
        } catch (e: ActivityNotFoundException) {
            Log.e("HcRationaleActivity", "No browser to show the privacy policy", e)
        }
        finish()
    }

    companion object {
        val privacyPolicyUri: Uri =
            "https://www.kevinvanleer.com/cyclotrack/#/privacy".toUri()
    }
}
