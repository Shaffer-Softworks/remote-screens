package tech.shaffersoftworks.remotescreens.server.render

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
        val position = entityState?.number("media_position")
        val duration = entityState?.number("media_duration")
        val progress = if (position != null && duration != null && duration > 0) {
            (position / duration).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }
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

internal fun formatMusicTime(seconds: Double): String {
    val total = seconds.roundToInt().coerceAtLeast(0)
    val minutes = total / MusicMetrics.SECONDS_PER_MINUTE
    val secs = total % MusicMetrics.SECONDS_PER_MINUTE
    return "$minutes:${secs.toString().padStart(2, '0')}"
}
