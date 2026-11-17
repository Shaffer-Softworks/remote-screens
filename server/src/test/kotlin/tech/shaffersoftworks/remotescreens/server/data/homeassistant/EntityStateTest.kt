package tech.shaffersoftworks.remotescreens.server.data.homeassistant

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EntityStateTest {
    private fun entity(entityId: String, state: String, attributes: String): EntityState =
        EntityState(entityId, state, Json.parseToJsonElement(attributes).jsonObject)

    @Test
    fun typedViewsReadHomeAssistantAttributes() {
        val light = entity(
            entityId = "light.kitchen",
            state = "on",
            attributes = """{"friendly_name":"Kitchen","brightness":128,"rgb_color":[255,0,10],
                "supported_color_modes":["hs","color_temp"],"supported_features":44}""",
        )

        assertEquals(Domain.LIGHT, light.domain)
        assertEquals("Kitchen", light.name)
        assertEquals(128.0, light.light.brightness)
        assertEquals(listOf("hs", "color_temp"), light.light.supportedColorModes)
        assertEquals(44, light.features())
        assertEquals("#FF000A", light.readRgbHex())
        assertTrue(light.isOn)
    }

    @Test
    fun looseAttributesStillDecode() {
        val player = entity(
            entityId = "media_player.den",
            state = "playing",
            attributes = """{"media_position":"42.5","source_list":null,"volume_level":"oops",
                "media_title":"Song"}""",
        )

        assertEquals(42.5, player.mediaPlayer.mediaPosition)
        assertEquals(emptyList(), player.mediaPlayer.sourceList)
        assertNull(player.mediaPlayer.volumeLevel)
        assertEquals("Song", player.mediaPlayer.mediaTitle)
        assertEquals(MediaPlayerState.PLAYING, player.mediaPlayerState)
    }

    @Test
    fun aMissingFriendlyNameFallsBackToTheObjectId() {
        assertEquals("Living room", entity("light.living_room", "off", "{}").name)
    }

    @Test
    fun statesOutsideTheEnumsAreOther() {
        val climate = entity("climate.den", "eco_boost", """{"target_temp_low":18,"target_temp_high":24}""")

        assertEquals(HvacMode.OTHER, climate.hvacMode)
        assertTrue(climate.climate.hasTemperatureRange)
        assertEquals(Availability.UNAVAILABLE, entity("lock.door", "unavailable", "{}").availability)
        assertFalse(entity("lock.door", "unknown", "{}").isAvailable)
        assertTrue(entity("alarm_control_panel.house", "pending", "{}").alarmState.isAlerting)
    }

    @Test
    fun alarmoOpenSensorsAreAMapOrAList() {
        val open = entity("alarm_control_panel.garage", "disarmed", """{"open_sensors":{"binary_sensor.door":"on"}}""")
        val closed = entity("alarm_control_panel.garage", "disarmed", """{"open_sensors":null}""")
        val listed = entity("alarm_control_panel.garage", "disarmed", """{"open_sensors":["binary_sensor.door"]}""")

        assertTrue(open.alarm.hasOpenSensors)
        assertFalse(closed.alarm.hasOpenSensors)
        assertTrue(listed.alarm.hasOpenSensors)
    }

    @Test
    fun layoutNamedAttributesStayReadableByName() {
        val sensor = entity("sensor.power", "432", """{"phase":"L1","peak":5.5,"zones":["a","b"]}""")

        assertEquals("L1", sensor.attributeText("phase"))
        assertEquals(5.5, sensor.attributeNumber("peak"))
        assertEquals(listOf("a", "b"), sensor.attributeList("zones"))
    }

    @Test
    fun serviceRefsParseDomainAndService() {
        assertEquals(ServiceRef(Domain.LIGHT, "turn_on"), ServiceRef.parse("light.turn_on"))
        assertEquals("climate.set_temperature", ServiceRef(Domain.CLIMATE, "set_temperature").toString())
        assertNull(ServiceRef.parse("light"))
        assertNull(ServiceRef.parse(".turn_on"))
    }
}
