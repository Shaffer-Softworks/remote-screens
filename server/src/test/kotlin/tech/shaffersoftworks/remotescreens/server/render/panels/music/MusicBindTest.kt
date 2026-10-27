package tech.shaffersoftworks.remotescreens.server.render.panels.music

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Home Assistant snapshots `media_position` with `media_position_updated_at`.
 * RPI's `NowPlayingTrackTime` adds elapsed time to that snapshot while playing.
 */
class MusicBindTest {
    @Test
    fun snapshotWithoutATimestampStaysPut() {
        val entityState = mediaPlayer(
            position = REPORTED_POSITION,
            duration = TRACK_DURATION,
        )

        val position = entityState.mediaPosition(NOW)
        assertEquals(REPORTED_POSITION, position)
        assertEquals(REPORTED_PROGRESS, mediaProgress(position, TRACK_DURATION))
    }

    @Test
    fun playingAddsElapsedSinceThePositionTimestamp() {
        val entityState = mediaPlayer(
            position = REPORTED_POSITION,
            duration = TRACK_DURATION,
            updatedAt = UPDATED_AT,
        )

        assertEquals(REPORTED_POSITION + ELAPSED_SECONDS, entityState.mediaPosition(NOW))
        assertEquals(
            (REPORTED_POSITION + ELAPSED_SECONDS).toFloat() / TRACK_DURATION.toFloat(),
            mediaProgress(entityState.mediaPosition(NOW), TRACK_DURATION),
        )
    }

    @Test
    fun aFreshWebsocketPositionDoesNotAddElapsed() {
        val entityState = mediaPlayer(
            position = REPORTED_POSITION,
            duration = TRACK_DURATION,
            updatedAt = NOW_STAMP,
        )

        assertEquals(REPORTED_POSITION, entityState.mediaPosition(NOW))
    }

    @Test
    fun pausedStaysOnTheReportedPosition() {
        val entityState = mediaPlayer(
            state = "paused",
            position = REPORTED_POSITION,
            duration = TRACK_DURATION,
            updatedAt = UPDATED_AT,
        )

        assertEquals(REPORTED_POSITION, entityState.mediaPosition(NOW))
    }

    @Test
    fun playingCapsAtTheTrackDuration() {
        val entityState = mediaPlayer(
            position = NEAR_END_POSITION,
            duration = TRACK_DURATION,
            updatedAt = UPDATED_AT,
        )

        assertEquals(TRACK_DURATION, entityState.mediaPosition(NOW))
    }

    @Test
    fun positionTimestampCanUseAnOffset() {
        val entityState = mediaPlayer(
            position = REPORTED_POSITION,
            duration = TRACK_DURATION,
            updatedAt = UPDATED_AT_OFFSET,
        )

        assertEquals(REPORTED_POSITION + ELAPSED_SECONDS, entityState.mediaPosition(NOW))
    }

    @Test
    fun positionCanArriveAsAStringAttribute() {
        val entityState = EntityState(
            "media_player.living_room",
            "playing",
            JsonObject(mapOf("media_position" to JsonPrimitive("42.0"))),
        )

        assertEquals(REPORTED_POSITION, entityState.mediaPosition(NOW))
    }

    @Test
    fun missingPositionOrDurationIsZeroProgress() {
        assertNull(null.mediaPosition(NOW))
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
        state: String = "playing",
        position: Double,
        duration: Double,
        updatedAt: String? = null,
    ): EntityState {
        val attributes = buildMap {
            put("media_position", JsonPrimitive(position))
            put("media_duration", JsonPrimitive(duration))
            if (updatedAt != null) {
                put("media_position_updated_at", JsonPrimitive(updatedAt))
            }
        }
        return EntityState(
            "media_player.living_room",
            state,
            JsonObject(attributes),
        )
    }

    private companion object {
        const val REPORTED_POSITION = 42.0
        const val NEAR_END_POSITION = 190.0
        const val TRACK_DURATION = 195.0
        const val ELAPSED_SECONDS = 10.0
        const val REPORTED_PROGRESS = 42f / 195f
        const val NOW_STAMP = "2026-10-27T12:00:10Z"
        const val UPDATED_AT = "2026-10-27T12:00:00Z"
        const val UPDATED_AT_OFFSET = "2026-10-27T12:00:00.000000+00:00"
        val NOW: Instant = Instant.parse(NOW_STAMP)
    }
}
