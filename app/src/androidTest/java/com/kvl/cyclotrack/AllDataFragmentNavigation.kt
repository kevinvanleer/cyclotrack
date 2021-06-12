package com.kvl.cyclotrack

import androidx.lifecycle.ViewModelStore
import androidx.navigation.testing.TestNavHostController
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.hamcrest.CoreMatchers.not
import org.hamcrest.core.IsEqual
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
class AllDataFragmentNavigation {
    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    var workMgrRule = WorkManagerRule()

    @get:Rule(order = 2)
    var navControllerRule = NavControllerRule()

    @get:Rule(order = 3)
    var activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Rule
    @JvmField
    val locationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.ACCESS_FINE_LOCATION)

    private lateinit var navController: TestNavHostController

    @Inject
    lateinit var tripsDao: TripDao

    @Before
    fun init() {
        hiltRule.inject()
        tripsDao.wipe()
        navController = navControllerRule.navController

        getInstrumentation().runOnMainSync {
            navController.setViewModelStore(ViewModelStore())
            navController.setGraph(R.navigation.dashboard_nav_graph)
        }
        launchFragmentInHiltContainer<TripInProgressFragment>(navHostController = navController)
    }

    @Test
    fun test01_allDataViewBeforeStart() {
        assertThat(navController.currentDestination?.id, IsEqual(R.id.TripInProgressFragment))
        onView(withId(R.id.pause_button)).check(matches(isDisplayed()))
            .check(matches(withText("START")))
        onView(withId(R.id.resume_button)).check(matches(not(isDisplayed())))
        onView(withId(R.id.stop_button)).check(matches(not(isDisplayed())))
    }

    @Test
    fun test02_allDataViewStartRide() {
        assertThat(navController.currentDestination?.id, IsEqual(R.id.TripInProgressFragment))
        onView(withText("START")).check(matches(isDisplayed())).perform(click())

        assertThat(navController.currentDestination?.id, IsEqual(R.id.TripInProgressFragment))
        onView(withId(R.id.resume_button)).check(matches(not(isDisplayed())))
        onView(withId(R.id.stop_button)).check(matches(not(isDisplayed())))
        onView(withId(R.id.pause_button)).check(matches(not(isDisplayed())))

        onView(withId(R.id.textview_average_speed)).perform(click())
        onView(withId(R.id.resume_button)).check(matches(not(isDisplayed())))
        onView(withId(R.id.stop_button)).check(matches(not(isDisplayed())))
        onView(withId(R.id.pause_button)).check(matches(isDisplayed())).perform(click())

        onView(withId(R.id.pause_button)).check(matches(not(isDisplayed())))
        onView(withId(R.id.resume_button)).check(matches(isDisplayed()))
        onView(withId(R.id.stop_button)).check(matches(isDisplayed())).perform(click())
    }

    @Test
    fun test03_allDataViewPauseTrip() {
        assertThat(navController.currentDestination?.id, IsEqual(R.id.TripInProgressFragment))

        onView(withText("START")).check(matches(isDisplayed())).perform(click())
        onView(withId(R.id.resume_button)).check(matches(not(isDisplayed())))
        onView(withId(R.id.stop_button)).check(matches(not(isDisplayed())))

        onView(withId(R.id.textview_average_speed)).perform(click())
        onView(withId(R.id.resume_button)).check(matches(not(isDisplayed())))
        onView(withId(R.id.stop_button)).check(matches(not(isDisplayed())))
        onView(withId(R.id.pause_button)).check(matches(isDisplayed())).perform(click())

        onView(withId(R.id.pause_button)).check(matches(not(isDisplayed())))
        onView(withId(R.id.stop_button)).check(matches(isDisplayed()))
        onView(withId(R.id.resume_button)).check(matches(isDisplayed())).perform(click())

        onView(withId(R.id.pause_button)).check(matches(not(isDisplayed())))
        onView(withId(R.id.resume_button)).check(matches(not(isDisplayed())))
        onView(withId(R.id.stop_button)).check(matches(not(isDisplayed())))

        onView(withId(R.id.textview_average_speed)).perform(click())
        onView(withId(R.id.resume_button)).check(matches(not(isDisplayed())))
        onView(withId(R.id.stop_button)).check(matches(not(isDisplayed())))
        onView(withId(R.id.pause_button)).check(matches(isDisplayed())).perform(click())

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
    */
    /*
    @Test
    fun allDataViewStartRide() {
        onView(withId(R.id.textview_average_speed)).check(matches(isDisplayed())).perform(click())
        onView(withId(R.id.pause_button)).check(matches(isDisplayed())).perform(click())
        onView(withId(R.id.stop_button)).check(matches(isDisplayed()))
        onView(withId(R.id.resume_button)).check(matches(isDisplayed())).perform(click())
        onView(withId(R.id.stop_button)).check(matches(not(isDisplayed())))
        onView(withId(R.id.resume_button)).check(matches(not(isDisplayed())))
    }

    @Test
    fun allDataViewStarted() {
        onView(withId(R.id.pause_button)).check(matches(isDisplayed())).perform(click())
        assertThat(navController.currentDestination?.id, IsEqual(R.id.AllDataFragment))

        onView(withId(R.id.button_exit_all_data)).check(matches(isDisplayed())).perform(click())
        assertThat(navController.currentDestination?.id, IsEqual(R.id.TripInProgressFragment))
        onView(withId(R.id.resume_button)).check(matches(not(isDisplayed())))
        onView(withId(R.id.stop_button)).check(matches(not(isDisplayed())))
        onView(withId(R.id.pause_button)).check(matches(not(isDisplayed())))

        onView(withId(R.id.textview_average_speed)).check(matches(isDisplayed())).perform(click())
        onView(withId(R.id.pause_button)).check(matches(isDisplayed())).perform(click())
        onView(withId(R.id.stop_button)).check(matches(isDisplayed()))
        onView(withId(R.id.resume_button)).check(matches(isDisplayed())).perform(click())
        onView(withId(R.id.stop_button)).check(matches(not(isDisplayed())))
        onView(withId(R.id.resume_button)).check(matches(not(isDisplayed())))
    }
     */
}