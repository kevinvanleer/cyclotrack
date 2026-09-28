package com.kvl.cyclotrack

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert
import org.junit.Test

class WeatherResponseTest {
    private val adapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        .adapter(WeatherResponse::class.java)

    private fun parse(json: String) = adapter.fromJson(json)!!.toWeather(1)

    @Test
    fun fullResponse() {
        val weather = parse(
            """{"coord":{"lon":10.99,"lat":44.34},"weather":[{"id":501,"main":"Rain","description":"moderate rain","icon":"10d"}],"base":"stations","main":{"temp":298.48,"feels_like":298.74,"temp_min":297.56,"temp_max":300.05,"pressure":1015,"humidity":64,"sea_level":1015,"grnd_level":933},"visibility":10000,"wind":{"speed":0.62,"deg":349,"gust":1.18},"rain":{"1h":3.16},"clouds":{"all":100},"dt":1661870592,"sys":{"type":2,"id":2075663,"country":"IT","sunrise":1661834187,"sunset":1661882248},"timezone":7200,"id":3163858,"name":"Zocca","cod":200}"""
        )!!
        Assert.assertEquals(
            Weather(
                timestamp = 1661870592,
                sunrise = 1661834187,
                sunset = 1661882248,
                temperature = 298.48,
                feelsLike = 298.74,
                pressure = 1015,
                humidity = 64,
                dewPoint = null,
                uvIndex = null,
                clouds = 100,
                visibility = 10000,
                windSpeed = 0.62,
                windDirection = 349,
                tripId = 1,
            ),
            weather
        )
    }

    @Test
    fun onlyTimestampAndTemperature() {
        val weather = parse("""{"dt":1661870592,"main":{"temp":298.48}}""")!!
        Assert.assertEquals(298.48, weather.temperature, 0.0)
        Assert.assertNull(weather.visibility)
        Assert.assertNull(weather.windSpeed)
        Assert.assertNull(weather.windDirection)
        Assert.assertNull(weather.sunrise)
    }

    @Test
    fun missingTemperatureIsSkipped() {
        Assert.assertNull(parse("""{"dt":1661870592,"main":{"humidity":64}}"""))
        Assert.assertNull(parse("""{"dt":1661870592}"""))
    }

    @Test
    fun missingTimestampIsSkipped() {
        Assert.assertNull(parse("""{"main":{"temp":298.48}}"""))
    }

    @Test
    fun averageWindSkipsSamplesWithoutWind() {
        val calm = parse("""{"dt":1,"main":{"temp":280.0},"wind":{"speed":0.0}}""")!!
        val windy = parse("""{"dt":2,"main":{"temp":280.0},"wind":{"speed":4.0,"deg":90}}""")!!
        val wind = arrayOf(calm, windy).getAverageWind()!!
        val windyOnly = arrayOf(windy).getAverageWind()!!
        Assert.assertEquals(windyOnly.first, wind.first, 1e-9)
        Assert.assertEquals(windyOnly.second, wind.second, 1e-9)
        Assert.assertNull(arrayOf(calm).getAverageWind())
    }
}
