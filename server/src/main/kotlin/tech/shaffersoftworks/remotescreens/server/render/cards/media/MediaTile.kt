@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards.media

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcColor
import androidx.compose.remote.creation.dsl.RcContentScale
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.verticalWeight
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.Domain
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.MediaPlayerState
import tech.shaffersoftworks.remotescreens.server.data.renderdata.PictureSlot
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileBox
import tech.shaffersoftworks.remotescreens.server.render.tiles.tileIcon
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileChrome as C
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.icon
import tech.shaffersoftworks.remotescreens.server.render.widgets.key
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.rounded
import tech.shaffersoftworks.remotescreens.server.render.widgets.spacer
import tech.shaffersoftworks.remotescreens.server.render.widgets.tap
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.Catalogue
import tech.shaffersoftworks.remotescreens.shared.Tile
import tech.shaffersoftworks.remotescreens.shared.VarValue

private const val ART_REFRESH_S = 0

internal fun DocContext.mediaTile(tile: Tile, box: TileBox, entityState: EntityState?) {
    val entityId = tile.entityId ?: return
    val ink = color(Vars.tile(tile.id, Vars.KEY_INK))
    val ink2 = color(Vars.tile(tile.id, Vars.KEY_INK2))
    val showsArtwork = box.w >= C.HEADER_TALL_MIN_DP && box.h >= C.TALL_HEIGHT_DP
    val showsTransport = box.w >= C.HEADER_TALL_MIN_DP || box.h >= C.TALL_HEIGHT_DP
    bindMediaTile(entityId)
    predict(ActionRef(tile.id, "play_pause"), entityId)

    rcScope.Box(Modifier.fillMaxSize()) {
        val doc = this@mediaTile

        if (showsArtwork) doc.mediaArtwork(entityId, box)

        Column(Modifier.fillMaxSize().padding(doc.units.px(C.PAD_LG_DP))) {
            doc.tileIcon(
                tile = tile,
                spec = Catalogue.of(Domain.MEDIA_PLAYER.value),
                entityState = entityState,
                sizeDp = C.LEVEL_BAR_HEIGHT_DP,
            )

            Spacer(Modifier.verticalWeight(1f))
            doc.mediaCaption(entityId = entityId, ink = ink, ink2 = ink2, showsArtwork = showsArtwork)

            if (showsTransport) {
                doc.spacer(C.PAD_SM_DP)
                doc.mediaTransport(tile = tile, entityId = entityId, ink = ink)
            }
        }
    }
}

private fun DocContext.bindMediaTile(entityId: String) {
    bind(entityId) { states ->
        val current = states[entityId]
        val player = current?.mediaPlayer
        val artist = listOfNotNull(player?.mediaArtist, player?.mediaAlbumName).joinToString(" · ")

        listOf(
            VarValue.string(Vars.entity(entityId, "title"), player?.mediaTitle ?: current?.name.orEmpty()),
            VarValue.string(
                Vars.entity(entityId, "artist"),
                artist.ifEmpty { i18n.state(Domain.MEDIA_PLAYER.value, current?.state ?: MediaPlayerState.OFF.wire) },
            ),
            VarValue.int(Vars.entity(entityId, "playing"), playbackVisibility(current, isPlaying = true)),
            VarValue.int(Vars.entity(entityId, "notplaying"), playbackVisibility(current, isPlaying = false)),
        )
    }
}

private fun DocContext.mediaArtwork(entityId: String, box: TileBox) {
    val widthPx = units.px(box.w).toInt()
    val heightPx = units.px(box.h).toInt()

    picture(
        PictureSlot(
            name = "art.$entityId",
            source = "attr:$entityId:entity_picture",
            widthPx = widthPx,
            heightPx = heightPx,
            refreshSeconds = ART_REFRESH_S,
        ),
    )

    rcScope.Image(
        image = image(name = "art.$entityId", width = widthPx, height = heightPx),
        modifier = Modifier.fillMaxSize(),
        contentScale = RcContentScale.Crop,
        alpha = C.PERSON_PHOTO_W_SHARE,
    )
}

private fun DocContext.mediaCaption(entityId: String, ink: RcColor, ink2: RcColor, showsArtwork: Boolean) {
    label(
        text = text(Vars.entity(entityId, "title")),
        sp = if (showsArtwork) Type.TITLE + C.MEDIA_TITLE_BUMP else Type.BODY,
        color = ink,
        weight = Weight.SEMI_BOLD,
        lines = if (showsArtwork) 2 else 1,
    )

    label(
        text = text(Vars.entity(entityId, "artist")),
        sp = Type.CAPTION + 1f,
        color = ink2,
    )
}

private fun DocContext.mediaTransport(tile: Tile, entityId: String, ink: RcColor) {
    rcScope.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontal = RcRowHorizontalPositioning.SpaceBetween,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@mediaTransport
        doc.skipKey(tile = tile, verb = "previous", iconName = "skip-previous", ink = ink)
        doc.playPause(tile = tile, entityId = entityId, ink = ink)
        doc.skipKey(tile = tile, verb = "next", iconName = "skip-next", ink = ink)
    }
}

private fun DocContext.skipKey(tile: Tile, verb: String, iconName: String, ink: RcColor) {
    key(
        ref = ActionRef(tile.id, verb),
        icon = iconName,
        heightDp = C.HEADER_TALL_DP,
        widthDp = C.HEADER_TALL_DP,
        round = true,
        bg = color(Vars.tile(tile.id, Vars.KEY_FILL)),
        ink = ink,
    )
}

private fun DocContext.playPause(tile: Tile, entityId: String, ink: RcColor) {
    val size = units.px(C.WEATHER_ICON_TALL_DP)

    rcScope.Box(
        modifier = tap(
            Modifier.size(size, size).clip(rounded(C.WEATHER_ICON_TALL_DP / 2f)).background(ink),
            ActionRef(tile.id, "play_pause"),
        ),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@playPause
        val onTile = doc.color(Vars.tile(tile.id, Vars.KEY_BG))

        doc.icon(
            name = "pause",
            sizeDp = C.LEVEL_BAR_HEIGHT_DP,
            color = onTile,
            visible = doc.int(Vars.entity(entityId, "playing")),
        )

        doc.icon(
            name = "play",
            sizeDp = C.LEVEL_BAR_HEIGHT_DP,
            color = onTile,
            visible = doc.int(Vars.entity(entityId, "notplaying")),
        )
    }
}
