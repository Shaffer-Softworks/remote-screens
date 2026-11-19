@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards.media

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcContentScale
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.ControlVerb
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.MediaPlayerState
import tech.shaffersoftworks.remotescreens.server.data.renderdata.PictureSlot
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.cards.CardChrome
import tech.shaffersoftworks.remotescreens.server.render.cards.bar
import tech.shaffersoftworks.remotescreens.server.render.cards.flowKeys
import tech.shaffersoftworks.remotescreens.server.render.cards.ref
import tech.shaffersoftworks.remotescreens.server.render.cards.section
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.icon
import tech.shaffersoftworks.remotescreens.server.render.widgets.key
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.rounded
import tech.shaffersoftworks.remotescreens.server.render.widgets.spacer
import tech.shaffersoftworks.remotescreens.server.render.widgets.tap
import tech.shaffersoftworks.remotescreens.shared.VarValue

private const val MEDIA_ART_MAX_DP = 280f
internal const val PLAY_KEY_DP = 80f
private const val LIBRARY_TAKE = 20
private const val ART_REFRESH_S = 0

internal fun DocContext.mediaCard(entityId: String, entityState: EntityState?, width: Float) {
    val art = minOf(width, MEDIA_ART_MAX_DP)
    mediaArtwork(entityId, art)
    bindNowPlaying(entityId)
    predict(ref(entityId, "play_pause"), entityId)
    nowPlaying(entityId, art)
    section(i18n.t("ui.volume"))
    bar(entityId = entityId, verb = ControlVerb.VOLUME, width = width)
    mediaSources(entityId, entityState)
    mediaLibrary(entityId)
}

private fun DocContext.mediaArtwork(entityId: String, artDp: Float) {
    val px = units.px(artDp).toInt()

    picture(
        PictureSlot(
            name = "art.card.$entityId",
            source = "attr:$entityId:entity_picture",
            widthPx = px,
            heightPx = px,
            refreshSeconds = ART_REFRESH_S,
        ),
    )
}

private fun DocContext.bindNowPlaying(entityId: String) {
    bind(entityId) { states ->
        val current = states[entityId]
        val player = current?.mediaPlayer

        listOf(
            VarValue.string(Vars.entity(entityId, "title"), player?.mediaTitle.orEmpty()),
            VarValue.string(
                Vars.entity(entityId, "artist"),
                listOfNotNull(player?.mediaArtist, player?.mediaAlbumName).joinToString(" · "),
            ),
            VarValue.int(Vars.entity(entityId, "playing"), playbackVisibility(current, isPlaying = true)),
            VarValue.int(Vars.entity(entityId, "notplaying"), playbackVisibility(current, isPlaying = false)),
        )
    }
}

internal fun playbackVisibility(current: EntityState?, isPlaying: Boolean): Int {
    val playing = current?.mediaPlayerState == MediaPlayerState.PLAYING
    val shown = if (isPlaying) playing else !playing

    return if (shown) Vars.VISIBLE else Vars.GONE
}

private fun DocContext.nowPlaying(entityId: String, artDp: Float) {
    val px = units.px(artDp).toInt()

    rcScope.Column(Modifier.fillMaxWidth(), horizontal = RcHorizontalPositioning.Center) {
        val doc = this@nowPlaying

        Image(
            image = doc.image(name = "art.card.$entityId", width = px, height = px),
            modifier = Modifier.size(
                doc.units.px(artDp),
                doc.units.px(artDp)
            ).clip(doc.rounded(CardChrome.PANEL_PAD_DP)),
            contentScale = RcContentScale.Crop,
        )

        doc.spacer(CardChrome.HEADER_GAP_DP)

        doc.label(
            text = doc.text(Vars.entity(entityId, "title")),
            sp = Type.HEADLINE,
            color = doc.role(Role.INK),
            weight = Weight.SEMI_BOLD,
            align = RcTextAlign.Center,
        )

        doc.label(
            text = doc.text(Vars.entity(entityId, "artist")),
            sp = Type.BODY,
            color = doc.role(Role.INK_SOFT),
            align = RcTextAlign.Center,
        )

        doc.spacer(CardChrome.HEADER_GAP_DP)
        doc.transport(entityId)
    }
}

private fun DocContext.transport(entityId: String) {
    rcScope.Row(
        horizontal = RcRowHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@transport
        doc.mediaKey(entityId = entityId, verb = "previous", icon = "skip-previous")
        Spacer(Modifier.width(doc.units.px(CardChrome.SECTION_TITLE_GAP_DP)))
        doc.playPauseKey(entityId)
        Spacer(Modifier.width(doc.units.px(CardChrome.SECTION_TITLE_GAP_DP)))
        doc.mediaKey(entityId = entityId, verb = "next", icon = "skip-next")
    }
}

private fun DocContext.mediaKey(entityId: String, verb: String, icon: String) {
    key(
        ref = ref(entityId, verb),
        icon = icon,
        heightDp = CardChrome.MEDIA_CTRL_DP,
        widthDp = CardChrome.MEDIA_CTRL_DP,
        round = true,
    )
}

private fun DocContext.playPauseKey(entityId: String) {
    rcScope.Box(
        modifier = tap(
            Modifier.size(units.px(PLAY_KEY_DP), units.px(PLAY_KEY_DP))
                .clip(rounded(PLAY_KEY_DP / 2f))
                .background(role(Role.KEY_ON)),
            ref(entityId, "play_pause"),
        ),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@playPauseKey

        doc.icon(
            name = "pause",
            sizeDp = CardChrome.CLOSE_KEY_DP,
            color = doc.role(Role.KEY_ON_INK),
            visible = doc.int(Vars.entity(entityId, "playing")),
        )

        doc.icon(
            name = "play",
            sizeDp = CardChrome.CLOSE_KEY_DP,
            color = doc.role(Role.KEY_ON_INK),
            visible = doc.int(Vars.entity(entityId, "notplaying")),
        )
    }
}

private fun DocContext.mediaSources(entityId: String, entityState: EntityState?) {
    val sources = entityState?.mediaPlayer?.sourceList.orEmpty()

    if (sources.isEmpty()) return

    structural(entityId) { it?.mediaPlayer?.sourceList?.joinToString().orEmpty() }
    section(i18n.t("ui.sources"))

    flowKeys(
        entityId = entityId,
        verb = "source",
        choices = sources.take(CardChrome.SOURCES_TAKE).map { it to it },
    ) { it.mediaPlayer.source }
}

private fun DocContext.mediaLibrary(entityId: String) {
    val library = data.browse[entityId].orEmpty().filter { it.canPlay }.take(LIBRARY_TAKE)

    if (library.isEmpty()) return

    section(i18n.t("ui.library"))

    for ((title, contentId, contentType) in library) {
        key(
            ref = ref(entityId = entityId, verb = "play_media", arg = "$contentType|$contentId"),
            text = title,
            icon = "play-circle-outline",
            heightDp = CardChrome.FLOW_KEY_H_DP,
            modifier = Modifier.fillMaxWidth(),
        )

        spacer(CardChrome.ROW_GAP_DP)
    }
}
