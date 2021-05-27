package com.kvl.cyclotrack

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripSummariesTest {
    @get:Rule
    var activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Rule
    @JvmField
    val locationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.ACCESS_FINE_LOCATION)

    @Test
    fun initialRunNoData() {
        onView(withId(R.id.trips_rollup)).check(matches(isDisplayed()))
        onView(withId(R.id.rollup_view_total_distance)).check(matches(withText("Welcome to")))
        onView(withId(R.id.rollup_view_total_duration)).check(matches(withText("Cyclotrack")))
        onView(withId(R.id.rollup_view_inspiring_message)).check(matches(withText(R.string.initial_inspiring_message)))
        onView(withId(R.id.fab)).check(matches(isDisplayed()))
    }

    @Test
    fun locationPermissionDeny() {
        onView(withId(R.id.fab)).check(matches(isDisplayed())).perform(ViewActions.click())
        onView(withText("New rides disabled")).inRoot(RootMatchers.isDialog())
            .check(matches(isDisplayed()))
        onView(withText("OK")).perform(ViewActions.click())
        onView(withId(R.id.fab)).check(matches(isDisplayed())).perform(ViewActions.click())
        onView(withText("Grant Location Access")).inRoot(RootMatchers.isDialog())
            .check(matches(isDisplayed()))
        onView(withText("DENY")).check(matches(isDisplayed())).perform(ViewActions.click())
        onView(withId(R.id.fab)).check(matches(isDisplayed())).perform(ViewActions.click())
        onView(withText("Grant Location Access")).inRoot(RootMatchers.isDialog())
            .check(matches(isDisplayed()))
        onView(withText("PROCEED")).check(matches(isDisplayed())).perform(ViewActions.click())
        onView(withText("Allow Cyclotrack to access this device's location")).inRoot(
            RootMatchers.isDialog()).check(matches(isDisplayed()))
        onView(withText("ALLOW ONLY WHILE USING THE APP")).check(matches(isDisplayed()))
            .perform(ViewActions.click())
        onView(withId(R.id.TripInProgressFragment)).check(matches(isDisplayed()))
    }
}