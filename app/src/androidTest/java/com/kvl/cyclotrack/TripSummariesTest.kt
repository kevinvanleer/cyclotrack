package com.kvl.cyclotrack

import android.os.SystemClock
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.hamcrest.CoreMatchers
import org.hamcrest.Matchers.not
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import javax.inject.Inject

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TripSummariesTest {
    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    var workMgrRule = WorkManagerRule()

    @get:Rule(order = 2)
    var navControllerRule = NavControllerRule()

    @get:Rule(order = 3)
    var mainActivityRule = ActivityScenarioRule(MainActivity::class.java)

    @Rule
    @JvmField
    val locationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.ACCESS_FINE_LOCATION)

    @Rule
    @JvmField
    var repeatRule = RepeatRule()

    @Inject
    lateinit var tripsDao: TripDao

    @Before
    fun init() {
        hiltRule.inject()
        tripsDao.wipe()

        PreferenceManager.getDefaultSharedPreferences(getInstrumentation().targetContext.applicationContext)
            .edit {
                putBoolean(
                    getInstrumentation().targetContext.applicationContext.getString(
                        R.string.preference_key_analytics_opt_in_presented
                    ), true
                )
                putBoolean(
                    getInstrumentation().targetContext.applicationContext.getString(
                        R.string.preferences_key_enable_analytics
                    ), false
                )
            }
    }

    @Test
    fun test00_initialRunNoData() {
        onView(withId(R.id.trips_rollup)).check(matches(isDisplayed()))
        onView(withId(R.id.rollup_view_total_distance)).check(matches(withText("Welcome to")))
        onView(withId(R.id.rollup_view_total_duration)).check(matches(withText("Cyclotrack")))
        onView(withId(R.id.rollup_view_inspiring_message)).check(matches(withText(R.string.initial_inspiring_message)))
        onView(withId(R.id.fab)).check(matches(isDisplayed()))
    }

    @Test
    fun test01_allDataViewBeforeStart() {
        onView(withId(R.id.fab)).check(matches(isDisplayed())).perform(click())
        //assertThat(navController.currentDestination?.id, IsEqual(R.id.TripInProgressFragment))
        onView(withId(R.id.pause_button)).check(matches(isDisplayed()))
            .check(matches(withText("START")))
        onView(withId(R.id.resume_button)).check(matches(CoreMatchers.not(isDisplayed())))
        onView(withId(R.id.stop_button)).check(matches(CoreMatchers.not(isDisplayed())))
    }

    @Test
    @Repeat(10)
    fun test02_allDataViewStartRide() {
        onView(withId(R.id.fab)).check(matches(isDisplayed())).perform(click())
        //assertThat(navController.currentDestination?.id, IsEqual(R.id.TripInProgressFragment))
        onView(withText("START")).check(matches(isDisplayed())).perform(click())

        onView(withId(R.id.resume_button)).check(matches(CoreMatchers.not(isDisplayed())))
        onView(withId(R.id.stop_button)).check(matches(CoreMatchers.not(isDisplayed())))
        onView(withId(R.id.pause_button)).check(matches(CoreMatchers.not(isDisplayed())))

        onView(withId(R.id.textview_average_speed)).perform(click())
        onView(withId(R.id.resume_button)).check(matches(CoreMatchers.not(isDisplayed())))
        onView(withId(R.id.stop_button)).check(matches(CoreMatchers.not(isDisplayed())))
        onView(withId(R.id.pause_button)).check(matches(isDisplayed())).perform(click())

        onView(withId(R.id.pause_button)).check(matches(CoreMatchers.not(isDisplayed())))
        onView(withId(R.id.resume_button)).check(matches(isDisplayed()))
        onView(withId(R.id.stop_button)).check(matches(isDisplayed())).perform(click())
    }

    @Test
    fun test03_startPauseResumePauseStop() {
        onView(withId(R.id.fab)).check(matches(isDisplayed())).perform(click())
        //assertThat(navController.currentDestination?.id, IsEqual(R.id.TripInProgressFragment))

        onView(withText("START")).check(matches(isDisplayed())).perform(click())
        onView(withId(R.id.resume_button)).check(matches(CoreMatchers.not(isDisplayed())))
        onView(withId(R.id.stop_button)).check(matches(CoreMatchers.not(isDisplayed())))

        onView(withId(R.id.textview_average_speed)).perform(click())
        onView(withId(R.id.resume_button)).check(matches(CoreMatchers.not(isDisplayed())))
        onView(withId(R.id.stop_button)).check(matches(CoreMatchers.not(isDisplayed())))
        onView(withId(R.id.pause_button)).check(matches(isDisplayed())).perform(click())

        onView(withId(R.id.pause_button)).check(matches(CoreMatchers.not(isDisplayed())))
        onView(withId(R.id.stop_button)).check(matches(isDisplayed()))
        onView(withId(R.id.resume_button)).check(matches(isDisplayed()))
            .perform(click())

        onView(withId(R.id.pause_button)).check(matches(CoreMatchers.not(isDisplayed())))
        onView(withId(R.id.resume_button)).check(matches(CoreMatchers.not(isDisplayed())))
        onView(withId(R.id.stop_button)).check(matches(CoreMatchers.not(isDisplayed())))

        onView(withId(R.id.textview_average_speed)).perform(click())
        onView(withId(R.id.resume_button)).check(matches(CoreMatchers.not(isDisplayed())))
        onView(withId(R.id.stop_button)).check(matches(CoreMatchers.not(isDisplayed())))
        onView(withId(R.id.pause_button)).check(matches(isDisplayed())).perform(click())

        onView(withId(R.id.pause_button)).check(matches(CoreMatchers.not(isDisplayed())))
        onView(withId(R.id.resume_button)).check(matches(isDisplayed()))
        onView(withId(R.id.stop_button)).check(matches(isDisplayed())).perform(click())
        onView(withId(R.id.trip_details_map_view)).check(matches(isDisplayed()))
    }

    @Test
    fun test04_fiveSecondTripTrip() {
        onView(withId(R.id.fab)).check(matches(isDisplayed())).perform(click())
        //assertThat(navController.currentDestination?.id, IsEqual(R.id.TripInProgressFragment))

        openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext)
        //onView(withId(R.id.menu_item_show_details)).check(matches(isDisplayed())).perform(click())
        onView(withText("Debug")).check(matches(isDisplayed())).perform(click())
        //assertThat(navController.currentDestination?.id, IsEqual(R.id.AllDataFragment))
        //SystemClock.sleep(1000)
        onView(withText("DASHBOARD")).check(matches(isDisplayed())).perform(click())

        onView(withText("START")).check(matches(isDisplayed())).perform(click())
        //Wait for notification to disappear
        SystemClock.sleep(5000)
        onView(withId(R.id.resume_button)).check(matches(not(isDisplayed())))
        onView(withId(R.id.stop_button)).check(matches(not(isDisplayed())))
        onView(withId(R.id.textview_duration)).check(matches(not("00:00")))
        onView(withId(R.id.textview_duration)).check(matches(not("--:--")))

        onView(withId(R.id.textview_average_speed)).perform(click())
        onView(withId(R.id.resume_button)).check(matches(not(isDisplayed())))
        onView(withId(R.id.stop_button)).check(matches(not(isDisplayed())))
        onView(withId(R.id.pause_button)).check(matches(isDisplayed())).perform(click())

        //SystemClock.sleep(1000)
        openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext)
        //SystemClock.sleep(1000)
        onView(withText("Debug")).check(matches(isDisplayed())).perform(click())
        //SystemClock.sleep(1000)
        //assertThat(navController.currentDestination?.id, IsEqual(R.id.AllDataFragment))
        onView(withText("DASHBOARD")).check(matches(isDisplayed())).perform(click())

        onView(withId(R.id.pause_button)).check(matches(not(isDisplayed())))
        onView(withId(R.id.stop_button)).check(matches(isDisplayed()))
        onView(withId(R.id.resume_button)).check(matches(isDisplayed())).perform(click())

        openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext)
        onView(withText("Debug")).check(matches(isDisplayed())).perform(click())
        SystemClock.sleep(1000)
        //assertThat(navController.currentDestination?.id, IsEqual(R.id.AllDataFragment))
        onView(withText("DASHBOARD")).check(matches(isDisplayed())).perform(click())

        onView(withId(R.id.pause_button)).check(matches(not(isDisplayed())))
        onView(withId(R.id.resume_button)).check(matches(not(isDisplayed())))
        onView(withId(R.id.stop_button)).check(matches(not(isDisplayed())))

        onView(withId(R.id.textview_average_speed)).perform(click())
        onView(withId(R.id.resume_button)).check(matches(not(isDisplayed())))
        onView(withId(R.id.stop_button)).check(matches(not(isDisplayed())))
        onView(withId(R.id.pause_button)).check(matches(isDisplayed())).perform(click())

        openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext)
        onView(withText("Debug")).check(matches(isDisplayed())).perform(click())
        SystemClock.sleep(1000)
        //assertThat(navController.currentDestination?.id, IsEqual(R.id.AllDataFragment))
        onView(withText("DASHBOARD")).check(matches(isDisplayed())).perform(click())

        onView(withId(R.id.pause_button)).check(matches(not(isDisplayed())))
        onView(withId(R.id.resume_button)).check(matches(isDisplayed()))
        onView(withId(R.id.stop_button)).check(matches(isDisplayed())).perform(click())
    }

/*
@Test
fun test04_allDataViewPauseTrip() {
    assertThat(navController.currentDestination?.id, IsEqual(R.id.TripInProgressFragment))

    Espresso.openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext)
    onView(withText("Debug")).check(matches(isDisplayed())).perform(click())
    assertThat(navController.currentDestination?.id, IsEqual(R.id.AllDataFragment))
    SystemClock.sleep(1000)
    onView(withText("DASHBOARD")).check(matches(isDisplayed())).perform(click())

    onView(withText("START")).check(matches(isDisplayed())).perform(click())
    onView(withId(R.id.resume_button)).check(matches(not(isDisplayed())))
    onView(withId(R.id.stop_button)).check(matches(not(isDisplayed())))

    onView(withId(R.id.textview_average_speed)).perform(click())
    onView(withId(R.id.resume_button)).check(matches(not(isDisplayed())))
    onView(withId(R.id.stop_button)).check(matches(not(isDisplayed())))
    onView(withId(R.id.pause_button)).check(matches(isDisplayed())).perform(click())

    Espresso.openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext)
    onView(withText("Debug")).check(matches(isDisplayed())).perform(click())
    assertThat(navController.currentDestination?.id, IsEqual(R.id.AllDataFragment))
    onView(withText("DASHBOARD")).check(matches(isDisplayed())).perform(click())

    onView(withId(R.id.pause_button)).check(matches(not(isDisplayed())))
    onView(withId(R.id.stop_button)).check(matches(isDisplayed()))
    onView(withId(R.id.resume_button)).check(matches(isDisplayed())).perform(click())

    Espresso.openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext)
    onView(withText("Debug")).check(matches(isDisplayed())).perform(click())
    assertThat(navController.currentDestination?.id, IsEqual(R.id.AllDataFragment))
    onView(withText("DASHBOARD")).check(matches(isDisplayed())).perform(click())

    onView(withId(R.id.pause_button)).check(matches(not(isDisplayed())))
    onView(withId(R.id.resume_button)).check(matches(not(isDisplayed())))
    onView(withId(R.id.stop_button)).check(matches(not(isDisplayed())))

    onView(withId(R.id.textview_average_speed)).perform(click())
    onView(withId(R.id.resume_button)).check(matches(not(isDisplayed())))
    onView(withId(R.id.stop_button)).check(matches(not(isDisplayed())))
    onView(withId(R.id.pause_button)).check(matches(isDisplayed())).perform(click())

    Espresso.openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext)
    onView(withText("Debug")).check(matches(isDisplayed())).perform(click())
    assertThat(navController.currentDestination?.id, IsEqual(R.id.AllDataFragment))
    onView(withText("DASHBOARD")).check(matches(isDisplayed())).perform(click())


    onView(withId(R.id.pause_button)).check(matches(not(isDisplayed())))
    onView(withId(R.id.resume_button)).check(matches(isDisplayed()))
    onView(withText(R.id.stop_button)).check(matches(isDisplayed()))
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
 */
}