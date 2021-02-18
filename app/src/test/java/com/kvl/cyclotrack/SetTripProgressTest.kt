package com.kvl.cyclotrack

/*
import android.content.SharedPreferences
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.nhaarman.mockitokotlin2.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.test.setMain
import org.junit.*
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.isAccessible
fun getPrivateMethod(
    myClass: KClass<*>,
    methodName: String,
    vararg args: KClass<*>,
): Method {
    val javaClasses = args.map { arg -> arg.java }
    val privateMethod =
        myClass.java.getDeclaredMethod(methodName,
            *javaClasses.toTypedArray())
    privateMethod.isAccessible = true
    return privateMethod
}

fun getPrivateField(myClass: KClass<*>, fieldName: String): Field {
    val privateField = myClass.java.getDeclaredField(fieldName)
    privateField.isAccessible = true
    return privateField
}

@SafeVarargs
inline fun <reified T> T.callPrivateFun(name: String, vararg varargs: Any?): Any? =
    T::class.declaredMemberFunctions.firstOrNull { it.name == name }?.apply { isAccessible = true }
        ?.call(this, *varargs)

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class SetTripProgressTest {
    @Rule
    @JvmField
    var instantTaskExecutorRule = InstantTaskExecutorRule()
    private val testDispatcher = TestCoroutineDispatcher()

    private lateinit var viewModel: TripInProgressViewModel
    private val mockTripsRepository = mock<TripsRepository> {

    }
    private val mockMeasurementsRepository = mock<MeasurementsRepository> {

    }
    private val mockTimeStateRepository = mock<TimeStateRepository> {

    }
    private val mockSplitRepository = mock<SplitRepository> {

    }
    private val mockGpsService = mock<GpsService> {

    }
    private val mockBleService = mock<BleService> {
        //onGeneric { speedSensor } doReturn (MutableLiveData(SpeedData(74, 335, 4322, 95f)))
        //onGeneric { cadenceSensor } doReturn (MutableLiveData(CadenceData(64, 135, 1322, 155f)))
    }
    private val mockSharedPreferences = mock<SharedPreferences> {

    }

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

        val accessSetTripProgress = getPrivateMethod(TripInProgressViewModel::class,
            "setTripProgress",
            Measurements::class)

        runBlockingTest {
            accessSetTripProgress.invoke(viewModel, Measurements(555,
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
                SpeedData(75, 321, 1234, 150f)))
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
            SpeedData(75, 320, 1234, 150f))

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
        val accessSetTripProgress = getPrivateMethod(TripInProgressViewModel::class,
            "setTripProgress",
            Measurements::class)
        runBlockingTest {
            accessSetTripProgress.invoke(viewModelSpy, testMeasurements)
            Assert.assertEquals(320, initCircRevs.getInt(viewModelSpy))
            Assert.assertEquals(0.0, initCircDist.getDouble(viewModelSpy), 1e-3)
            Assert.assertEquals(null, viewModelSpy.circumference)

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

            //doReturn(101.0).whenever(viewModelSpy).getDistanceDelta(any(), any())
            accessSetTripProgress.invoke(viewModelSpy, Measurements(555,
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
            Assert.assertEquals(320, initCircRevs.getInt(viewModelSpy))
            Assert.assertEquals(0.0, initCircDist.getDouble(viewModelSpy), 1e-3)
            //Assert.assertEquals(expectedTripProgress, viewModelSpy.currentProgress.value)

            accessSetTripProgress.invoke(viewModelSpy, Measurements(555,
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
            Assert.assertEquals(0.6516, viewModelSpy.circumference!!, 1e-3)
            Assert.assertEquals(202.0, viewModelSpy.currentProgress.value?.distance)
            Assert.assertEquals(1.629f, viewModelSpy.currentProgress.value?.speed ?: 0f, 1e-3f)
        }
    }
}
*/