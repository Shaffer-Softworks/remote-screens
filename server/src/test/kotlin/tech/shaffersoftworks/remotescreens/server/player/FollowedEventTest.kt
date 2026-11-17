package tech.shaffersoftworks.remotescreens.server.player

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistantEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class FollowedEventTest {
    private fun of(type: String, data: JsonObject = JsonObject(emptyMap())) =
        FollowedEvent.of(HomeAssistantEvent(type, data))

    @Test
    fun targetsComeFromTargetsOrScreens() {
        val open = of(
            FollowedEvent.OPEN_PAGE,
            buildJsonObject {
                put("page", "Cameras")
                put("targets", buildJsonArray { add(JsonPrimitive("hall")) })
            },
        )
        assertEquals(FollowedEvent.OpenPage(listOf("hall"), "Cameras"), open)
        assertEquals(
            FollowedEvent.Wake(listOf("kitchen")),
            of(FollowedEvent.WAKE, buildJsonObject { put("screens", "kitchen") }),
        )
        assertEquals(FollowedEvent.Close(emptyList()), of(FollowedEvent.CLOSE))
    }

    @Test
    fun anAlertIsDecodedAndAPageWithoutANameIsUnreadable() {
        val alert = assertIs<FollowedEvent.ShowAlert>(
            of(FollowedEvent.ALERT, buildJsonObject { put("title", "Doorbell") }),
        )
        assertEquals("Doorbell", alert.alert.title)
        assertIs<FollowedEvent.Unreadable>(of(FollowedEvent.ALERT, buildJsonObject { put("message", "No title") }))
        assertIs<FollowedEvent.Unreadable>(of(FollowedEvent.OPEN_PAGE))
        assertNull(of("state_changed"))
    }
}
