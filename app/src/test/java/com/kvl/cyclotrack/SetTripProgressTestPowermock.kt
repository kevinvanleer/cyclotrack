package com.kvl.cyclotrack

import android.content.SharedPreferences
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.given
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.test.setMain
import org.junit.*
import org.junit.runner.RunWith
import org.powermock.api.mockito.PowerMockito.*
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import org.powermock.reflect.Whitebox
import java.lang.reflect.Field
import kotlin.reflect.KClass

fun getPrivateField(myClass: KClass<*>, fieldName: String): Field {
    val privateField = myClass.java.getDeclaredField(fieldName)
    privateField.isAccessible = true
    return privateField
}

@ExperimentalCoroutinesApi
@RunWith(PowerMockRunner::class)
@PrepareForTest(TripInProgressViewModel::class)
class SetTripProgressTestPowermock {
    @Rule
    @JvmField
    var instantTaskExecutorRule = InstantTaskExecutorRule()
    private val testDispatcher = TestCoroutineDispatcher()

    private lateinit var viewModel: TripInProgressViewModel

    private val mockTripsRepository = mock(TripsRepository::class.java)
    private val mockMeasurementsRepository = mock(MeasurementsRepository::class.java)
    private val mockTimeStateRepository = mock(TimeStateRepository::class.java)
    private val mockSplitRepository = mock(SplitRepository::class.java)
    private val mockGpsService = mock(GpsService::class.java)
    private val mockBleService = mock(BleService::class.java)
    private val mockSharedPreferences = mock(SharedPreferences::class.java)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = TripInProgressViewModel(TestScope(testDispatcher),
            mockTripsRepository,
            mockMeasurementsRepository,
            mockTimeStateRepository,
            mockSplitRepository,
            mockGpsService,
            mockBleService,
            mockSharedPreferences
        )

        given(mockSharedPreferences.edit()).willReturn(object : SharedPreferences.Editor {
            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                TODO("Not yet implemented")
            }

            override fun putStringSet(
                key: String?,
                values: MutableSet<String>?,
            ): SharedPreferences.Editor {
                TODO("Not yet implemented")
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                TODO("Not yet implemented")
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                TODO("Not yet implemented")
            }

            override fun putFloat(name: String, value: Float): SharedPreferences.Editor {
                return this
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                TODO("Not yet implemented")
            }

            override fun remove(key: String?): SharedPreferences.Editor {
                TODO("Not yet implemented")
            }

            override fun clear(): SharedPreferences.Editor {
                TODO("Not yet implemented")
            }

            override fun commit(): Boolean {
                TODO("Not yet implemented")
            }

            override fun apply() {
            }
        })
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testDispatcher.cleanupTestCoroutines()
    }

    @Test
    fun setTripProgress_uninitialized() {
        val testLocationData = LocationData(3.22f,
            500.0,
            10f,
            1000000000,
            15.0,
            16.0,
            5f,
            123456789,
            null,
            null,
            null,
            null)
        val testMeasurements = Measurements(555,
            testLocationData,
            55,
            CadenceData(65, 123, 4321, 90F),
            SpeedData(75, 321, 1234, 150f))
        val expectedTripProgress = TripProgress(testMeasurements,
            testLocationData.accuracy,
            testLocationData.speed,
            0f,
            testLocationData.speed,
            0f,
            0f,
            0.0,
            0.0,
            0.0,
            true)

        runBlockingTest {
            val measurementsArg = Measurements(555,
                LocationData(3.22f,
                    500.0,
                    10f,
                    1000000000,
                    15.0,
                    16.0,
                    5f,
                    123456789,
                    null,
                    null,
                    null,
                    null),
                55,
                CadenceData(65, 123, 4321, 90f),
                SpeedData(75, 321, 1234, 150f))
            Whitebox.invokeMethod<Void>(viewModel,
                "setTripProgress",
                measurementsArg)
            Assert.assertEquals(expectedTripProgress, viewModel.currentProgress.value)
        }
    }

    @Test
    fun setTripProgress_twoUpdates() {
        var testLocationData = LocationData(3.22f,
            500.0,
            0f,
            1000000000,
            15.0,
            16.0,
            5f,
            123456789,
            null,
            null,
            null,
            null)
        var testMeasurements = Measurements(555,
            testLocationData,
            55,
            CadenceData(65, 123, 4321, 90F),
            SpeedData(75, 1, 1234, 150f))

        val initCircRevs =
            TripInProgressViewModel::class.java.getDeclaredField("initialMeasureCircRevs")
        initCircRevs.isAccessible = true
        val initCircDist =
            TripInProgressViewModel::class.java.getDeclaredField("initialMeasureCircDistance")
        initCircDist.isAccessible = true

        val privateTripId = TripInProgressViewModel::class.java.getDeclaredField("tripId")
        privateTripId.isAccessible = true
        privateTripId.set(viewModel, 1.toLong())
        val privateStartTime = getPrivateField(TripInProgressViewModel::class, "startTime")
        privateStartTime.setDouble(viewModel, System.currentTimeMillis() / 1e3 - 60)
        val privateCurrentState = getPrivateField(TripInProgressViewModel::class, "currentState")
        privateCurrentState.set(viewModel, TimeStateEnum.START)


        val viewModelSpy = spy(viewModel)
        runBlockingTest {
            Whitebox.invokeMethod<Void>(viewModelSpy,
                "setTripProgress",
                testMeasurements)
            Assert.assertEquals(1, initCircRevs.getInt(viewModelSpy))
            Assert.assertEquals(0.0, initCircDist.getDouble(viewModelSpy), 1e-3)
            Assert.assertEquals(null, viewModelSpy.autoCircumference)

            val expectedMeasurements =
                testMeasurements.copy(latitude = 15.1,
                    speedRevolutions = 475,
                    elapsedRealtimeNanos = 2000000000)
            var expectedTripProgress = TripProgress(expectedMeasurements,
                testLocationData.accuracy,
                testLocationData.speed,
                1.682f,
                testLocationData.speed,
                0f,
                0f,
                101.0,
                0.0,
                60.0,
                true)

            doReturn(1001.0).`when`(viewModelSpy, "getDistanceDelta", any(), any())
            Whitebox.invokeMethod<Void>(viewModelSpy,
                "setTripProgress", Measurements(555,
                    LocationData(3.22f,
                        500.0,
                        0f,
                        2000000000,
                        15.1,
                        16.0,
                        5f,
                        123456789,
                        null,
                        null,
                        null,
                        null),
                    55,
                    CadenceData(65, 123, 4321, 90f),
                    SpeedData(75, 475, 1234, 150f)))
            Assert.assertEquals(1, initCircRevs.getInt(viewModelSpy))
            Assert.assertEquals(0.0, initCircDist.getDouble(viewModelSpy), 1e-3)
            //Assert.assertEquals(expectedTripProgress, viewModelSpy.currentProgress.value)

            Whitebox.invokeMethod<Void>(viewModelSpy,
                "setTripProgress", Measurements(555,
                    LocationData(3.22f,
                        500.0,
                        0f,
                        2000000000,
                        15.1,
                        16.0,
                        5f,
                        123456789,
                        null,
                        null,
                        null,
                        null),
                    55,
                    CadenceData(65, 123, 4321, 90f),
                    SpeedData(75, 475, 1234, 150f)))
            Assert.assertEquals(2.112f, viewModelSpy.autoCircumference!!, 1e-3f)
            Assert.assertEquals(2.112f, viewModelSpy.circumference!!, 1e-3f)
            Assert.assertEquals(2002.0, viewModelSpy.currentProgress.value?.distance)
            Assert.assertEquals(5.28f, viewModelSpy.currentProgress.value?.speed ?: 0f, 1e-3f)
        }
    }
}