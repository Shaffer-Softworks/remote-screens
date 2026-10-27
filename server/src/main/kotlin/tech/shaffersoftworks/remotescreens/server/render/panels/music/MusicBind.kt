package tech.shaffersoftworks.remotescreens.server.render.panels.music

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Present
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.bindEntity
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.VarValue
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
 * Home Assistant's `state_changed` carries `media_position` while the player is playing.
 * RPI's `MusicAssistantViewModel` copies that attribute from `newState`; we do the same.
 */
internal fun EntityState?.mediaPosition(): Double? =
    this?.number("media_position") ?: this?.attr("media_position")?.toDoubleOrNull()

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
