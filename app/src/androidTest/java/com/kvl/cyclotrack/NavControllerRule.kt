package com.kvl.cyclotrack

import android.util.Log
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class NavControllerRule : TestRule {
    lateinit var navController: TestNavHostController
    override fun apply(base: Statement?, description: Description?): Statement {
        return object : Statement() {
            override fun evaluate() {
                navController = TestNavHostController(ApplicationProvider.getApplicationContext())
                try {
                    base?.evaluate()
                } finally {
                    Log.d("NavControllerRule", "Do some teardown")
                }
            }

        }
    }
}