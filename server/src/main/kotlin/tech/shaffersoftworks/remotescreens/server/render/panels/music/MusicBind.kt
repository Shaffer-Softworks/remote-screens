package tech.shaffersoftworks.remotescreens.server.render.panels.music

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Present
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.bindEntity
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.VarValue
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.roundToInt

/** Now-playing vars and transport predictions for a Music Assistant tile. */
internal fun DocContext.bindMusicNowPlaying(tileId: String, entityId: String) {
    bindEntity(entityId)
    bind(entityId) { states ->
        val entityState = states[entityId]
        val artist = entityState?.attr("media_artist").orEmpty()
        val album = entityState?.attr("media_album_name").orEmpty()
        val artistLine = artist.ifEmpty {
            i18n.state("media_player", entityState?.state ?: "off")
        }
        val playing = entityState?.state == "playing"
        val duration = entityState?.number("media_duration")
        val position = entityState.mediaPosition()
        val progress = mediaProgress(position, duration)
        listOf(
            VarValue.string(
                Vars.entity(entityId, "title"),
                entityState?.attr("media_title") ?: entityState?.name ?: "Nothing playing",
            ),
            VarValue.string(Vars.entity(entityId, "artist"), artistLine),
            VarValue.string(Vars.entity(entityId, "album"), album),
            VarValue.int(
                Vars.entity(entityId, "album_vis"),
                if (album.isNotBlank()) Vars.VISIBLE else Vars.GONE,
            ),
            VarValue.int(Vars.entity(entityId, "playing"), if (playing) Vars.VISIBLE else Vars.GONE),
            VarValue.int(
                Vars.entity(entityId, "notplaying"),
                if (playing) Vars.GONE else Vars.VISIBLE,
            ),
            VarValue.string(
                Vars.entity(entityId, "pos"),
                position?.let(::formatMusicTime) ?: "--:--",
            ),
            VarValue.string(
                Vars.entity(entityId, "dur"),
                duration?.let(::formatMusicTime) ?: "--:--",
            ),
            VarValue.float(Vars.entity(entityId, "progress"), progress),
            VarValue.float(
                Vars.entity(entityId, Vars.KEY_LEVEL),
                Present.view(entityState, i18n).level ?: 0f,
            ),
        )
    }
    predict(ActionRef(tileId, "play_pause"), entityId)
    predict(ActionRef(tileId, "volume"), entityId)
    predict(ActionRef(tileId, "seek"), entityId)
}

/**
 * RPI's `NowPlayingTrackTime` re-anchors on the last `media_position` snapshot and adds
 * elapsed time while the player is playing. Home Assistant does not publish that attribute
 * on a timer; it sends `media_position` plus `media_position_updated_at` on start, pause,
 * seek, or track change. A later live `state_changed` also refreshes the timestamp, so
 * elapsed since that stamp is ~0 and this does not double-count.
 */
internal fun EntityState?.mediaPosition(now: Instant = Instant.now()): Double? {
    val reported = this?.number("media_position") ?: this?.attr("media_position")?.toDoubleOrNull()
    val updatedAt = this?.mediaPositionUpdatedAt()
    val elapsedSeconds = if (this?.state == Playhead.PLAYING && reported != null && updatedAt != null) {
        (now.toEpochMilli() - updatedAt.toEpochMilli()).coerceAtLeast(0L) / Playhead.MS_PER_SECOND
    } else {
        0.0
    }
    val raw = reported?.plus(elapsedSeconds)
    val duration = this?.number("media_duration")
    return if (raw != null && duration != null && duration > 0.0) {
        minOf(raw, duration)
    } else {
        raw
    }
}

private fun EntityState.mediaPositionUpdatedAt(): Instant? {
    val stamp = attr("media_position_updated_at") ?: return null
    return runCatching { OffsetDateTime.parse(stamp).toInstant() }.getOrNull()
        ?: runCatching { Instant.parse(stamp) }.getOrNull()
}

internal fun mediaProgress(position: Double?, duration: Double?): Float {
    if (position == null || duration == null || duration <= 0.0) return 0f
    return (position / duration).toFloat().coerceIn(0f, 1f)
}

internal fun formatMusicTime(seconds: Double): String {
    val total = seconds.roundToInt().coerceAtLeast(0)
    val minutes = total / MusicMetrics.SECONDS_PER_MINUTE
    val secs = total % MusicMetrics.SECONDS_PER_MINUTE
    return "$minutes:${secs.toString().padStart(2, '0')}"
}

private object Playhead {
    const val PLAYING = "playing"
    const val MS_PER_SECOND = 1000.0
}
