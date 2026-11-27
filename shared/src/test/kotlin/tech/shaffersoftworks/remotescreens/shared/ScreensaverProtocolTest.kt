package tech.shaffersoftworks.remotescreens.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreensaverProtocolTest {
    @Test
    fun photoWeatherAndShowingRoundTrip() {
        val photo = ServerMessage.ScreensaverPhoto(id = "slide-1", data = "qq")

        val weather = ServerMessage.ScreensaverWeather(
            condition = "partlycloudy",
            temperature = "72°",
            humidity = "40%",
            icon = "aa",
        )

        val showing = PlayerMessage.Screensaver(showing = true)

        assertEquals(photo, Protocol.decodeServer(Protocol.encode(photo)))
        assertEquals(weather, Protocol.decodeServer(Protocol.encode(weather)))
        assertEquals(showing, Protocol.decodePlayer(Protocol.encode(showing)))
    }

    @Test
    fun unknownFieldsAndAMissingHumidityStillDecode() {
        val photo = Protocol.decodeServer(
            """{"type":"screensaver_photo","id":"slide-1","data":"qq","album":"trips"}""",
        )

        val weather = Protocol.decodeServer(
            """{"type":"screensaver_weather","condition":"clear-night","temperature":"48°","extra":true}""",
        )

        assertEquals(ServerMessage.ScreensaverPhoto(id = "slide-1", data = "qq"), photo)
        assertTrue(weather is ServerMessage.ScreensaverWeather)
        assertEquals("", (weather as ServerMessage.ScreensaverWeather).humidity)
    }
}
