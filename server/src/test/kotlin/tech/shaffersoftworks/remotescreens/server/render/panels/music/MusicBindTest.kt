package tech.shaffersoftworks.remotescreens.server.render.panels.music

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Home Assistant's `state_changed` carries `media_position` while the player is playing.
 * RPI's `MusicAssistantViewModel` copies that attribute; the add-on binds the same value.
 */
class MusicBindTest {
    @Test
    fun progressFollowsTheWebsocketPosition() {
        val entityState = mediaPlayer(
            position = REPORTED_POSITION,
            duration = TRACK_DURATION,
        )

        val position = entityState.mediaPosition()
        assertEquals(REPORTED_POSITION, position)
        assertEquals(REPORTED_PROGRESS, mediaProgress(position, TRACK_DURATION))
    }

    @Test
    fun positionCanArriveAsAStringAttribute() {
        val entityState = EntityState(
            "media_player.living_room",
            "playing",
            JsonObject(mapOf("media_position" to JsonPrimitive("42.0"))),
        )

        assertEquals(REPORTED_POSITION, entityState.mediaPosition())
    }

    @Test
    fun missingPositionOrDurationIsZeroProgress() {
        assertNull(null.mediaPosition())
        assertEquals(0f, mediaProgress(null, TRACK_DURATION))
        assertEquals(0f, mediaProgress(REPORTED_POSITION, null))
        assertEquals(0f, mediaProgress(REPORTED_POSITION, 0.0))
    }

    @Test
    fun formatMusicTimePadsSeconds() {
        assertEquals("0:42", formatMusicTime(REPORTED_POSITION))
        assertEquals("3:15", formatMusicTime(TRACK_DURATION))
    }

    private fun mediaPlayer(
        position: Double,
        duration: Double,
    ): EntityState {
        return EntityState(
            "media_player.living_room",
            "playing",
            JsonObject(
                mapOf(
                    "media_position" to JsonPrimitive(position),
                    "media_duration" to JsonPrimitive(duration),
                ),
            ),
        )
    }

    private companion object {
        const val REPORTED_POSITION = 42.0
        const val TRACK_DURATION = 195.0
        const val REPORTED_PROGRESS = 42f / 195f
    }
}
