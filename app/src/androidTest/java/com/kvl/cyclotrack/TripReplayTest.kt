package com.kvl.cyclotrack

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.SystemClock
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.rule.GrantPermissionRule
import com.opencsv.CSVReader
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.lang.Long.max
import javax.inject.Inject

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TripReplayTest() {
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

    @Inject
    lateinit var timeStateDao: TimeStateDao

    lateinit var locationManager: LocationManager

    private val testProviderName = "fakeLocationProvider"

    private fun getLocationTestData(csvResource: Int): List<Location> =
        CSVReader(
            getInstrumentation().targetContext.applicationContext
                ?.resources?.openRawResource(
                    csvResource
                )?.reader()
        ).let { reader ->
            reader.readNext().let { line ->
                reader.map {
                    Location(testProviderName).apply {
                        accuracy = it[line.indexOf("accuracy")].toFloat()
                        altitude = it[line.indexOf("altitude")].toDouble()
                        bearing = it[line.indexOf("bearing")].toFloat()
                        latitude = it[line.indexOf("latitude")].toDouble()
                        longitude = it[line.indexOf("longitude")].toDouble()
                        speed = it[line.indexOf("speed")].toFloat()
                        time = it[line.indexOf("time")].toLong()
                        verticalAccuracyMeters =
                            it[line.indexOf("verticalAccuracyMeters")].toFloat()
                        elapsedRealtimeNanos =
                                //it[line.indexOf("elapsedRealtimeNanos")].toLong()
                            it[line.indexOf("time")].toLong() * 1e6.toLong()
                        elapsedRealtimeUncertaintyNanos =
                            it[line.indexOf("elapsedRealtimeUncertaintyNanos")].toDouble()
                        speedAccuracyMetersPerSecond =
                            it[line.indexOf("speedAccuracyMetersPerSecond")].toFloat()

                    }
                }
            }
        }

    @Before
    fun init() {
        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() = runBlocking { tearDown() }
        })

        hiltRule.inject()

        val context = getInstrumentation().targetContext.applicationContext
        PreferenceManager.getDefaultSharedPreferences(getInstrumentation().targetContext.applicationContext)
            .edit {
                putBoolean(
                    context.getString(
                        R.string.preference_key_analytics_opt_in_presented
                    ), true
                )
                putBoolean(
                    context.getString(
                        R.string.preferences_key_enable_analytics
                    ), false
                )
                putBoolean(
                    context.getString(
                        R.string.preference_key_autopause_enable
                    ), false
                )
            }
        locationManager =
            (context.getSystemService(Context.LOCATION_SERVICE) as LocationManager)
        locationManager.addTestProvider(
            testProviderName,
            false,
            false,
            false,
            false,
            true,
            true,
            true,
            ProviderProperties.POWER_USAGE_LOW,
            ProviderProperties.ACCURACY_FINE
        )
        locationManager.setTestProviderEnabled(testProviderName, true)
    }

    @After
    fun tearDown() {
        locationManager.removeTestProvider(testProviderName)
    }

    @Test
    fun test04_fiveSecondTripTrip() {
        val testData =
            //getLocationTestData(R.raw.cyclotrack_000309_dutzow_to_cullum_branch)
            getLocationTestData(R.raw.cyclotrack_dev_000613_measurements_csv)

        //val tripsBefore = tripsDao.loadAll()
        onView(withId(R.id.fab)).check(matches(isDisplayed())).perform(click())
        //assertThat(navController.currentDestination?.id, IsEqual(R.id.TripInProgressFragment))
        val timeDelta = System.currentTimeMillis() - testData.first().time
        onView(withText("START")).check(matches(isDisplayed())).perform(click())

        //Wait for notification to disappear
        SystemClock.sleep(2000)

        var lastLocation = testData.first()
        var now = System.currentTimeMillis()
        testData.sortedBy { it.time }/*.slice(IntRange(0, 10))*/.forEach {
            if (it.isComplete) {
                SystemClock.sleep(
                    max(
                        0L,
                        it.time - lastLocation.time - (System.currentTimeMillis() - now)
                    )
                )
                now = System.currentTimeMillis()
                locationManager.setTestProviderLocation(
                    testProviderName,
                    Location(it).apply { time = it.time + timeDelta })
                lastLocation = it
            }
        }

        onView(withId(R.id.resume_button)).check(matches(not(isDisplayed())))
        onView(withId(R.id.stop_button)).check(matches(not(isDisplayed())))
        onView(withId(R.id.measurement_bottomRight)).check(matches(not("00:00")))
        onView(withId(R.id.measurement_bottomRight)).check(matches(not("--:--")))

        onView(withId(R.id.measurement_middleLeft)).perform(click())
        onView(withId(R.id.resume_button)).check(matches(not(isDisplayed())))
        onView(withId(R.id.stop_button)).check(matches(not(isDisplayed())))
        onView(withId(R.id.pause_button)).check(matches(isDisplayed())).perform(click())

        onView(withId(R.id.pause_button)).check(matches(not(isDisplayed())))
        onView(withId(R.id.stop_button)).check(matches(isDisplayed()))
        onView(withId(R.id.resume_button)).check(matches(isDisplayed())).perform(click())

        SystemClock.sleep(1000)

        onView(withId(R.id.pause_button)).check(matches(not(isDisplayed())))
        onView(withId(R.id.resume_button)).check(matches(not(isDisplayed())))
        onView(withId(R.id.stop_button)).check(matches(not(isDisplayed())))

        onView(withId(R.id.measurement_middleLeft)).perform(click())
        onView(withId(R.id.resume_button)).check(matches(not(isDisplayed())))
        onView(withId(R.id.stop_button)).check(matches(not(isDisplayed())))
        onView(withId(R.id.pause_button)).check(matches(isDisplayed())).perform(click())

        SystemClock.sleep(1000)

        onView(withId(R.id.pause_button)).check(matches(not(isDisplayed())))
        onView(withId(R.id.resume_button)).check(matches(isDisplayed()))
        onView(withId(R.id.stop_button)).check(matches(isDisplayed())).perform(click())

        //val tripsAfter = tripsDao.loadAll()

        //Assert.assertEquals(tripsBefore.size + 1, tripsAfter.size)
    }
}