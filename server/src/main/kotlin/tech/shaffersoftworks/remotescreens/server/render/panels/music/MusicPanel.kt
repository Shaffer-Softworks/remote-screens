@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.panels.music

import tech.shaffersoftworks.remotescreens.server.data.music.MusicListRow
import tech.shaffersoftworks.remotescreens.server.data.music.MusicUiState
import tech.shaffersoftworks.remotescreens.server.data.renderdata.PictureSlot
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.SearchBoundsPx
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.liveSearch
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileBox
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcColor
import androidx.compose.remote.creation.dsl.RcContentScale
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.componentId
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.modifiers.RectShape
import androidx.compose.remote.creation.dsl.height
import tech.shaffersoftworks.remotescreens.server.render.keptHorizontalScroll
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.requiredHeightIn
import androidx.compose.remote.creation.dsl.size
import tech.shaffersoftworks.remotescreens.server.render.keptVerticalScroll
import androidx.compose.remote.creation.dsl.verticalWeight
import androidx.compose.remote.creation.dsl.visibility
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.at
import tech.shaffersoftworks.remotescreens.server.render.widgets.gap
import tech.shaffersoftworks.remotescreens.server.render.widgets.icon
import tech.shaffersoftworks.remotescreens.server.render.widgets.key
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.levelBar
import tech.shaffersoftworks.remotescreens.server.render.widgets.rounded
import tech.shaffersoftworks.remotescreens.server.render.widgets.tap
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.Tile
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Wide Music Assistant page: now playing over Browse on the left, Search / Queue on the right.
 * Player picker is omitted — the tile already binds a Music Assistant player.
 */
fun DocContext.musicPanel(tile: Tile, box: TileBox) {
    val entityId = tile.entityId ?: return
    val ui = data.musicUi[tile.id] ?: MusicUiState()
    val browseRows = data.musicBrowse[tile.id].orEmpty().take(MusicMetrics.MAX_BROWSE_ROWS)
    val queue = data.musicQueue[tile.id]
    val queueRows = queue?.rows.orEmpty().take(MusicMetrics.MAX_QUEUE_ROWS)
    val browseHasMore = data.musicBrowseHasMore[tile.id] == true
    val configured = data.musicConfigEntry[tile.id]?.isNotBlank() == true
    val frame = musicFrame(box)
    bindMusicNowPlaying(tile.id, entityId)
    // Structure follows MusicUiStore / browse rows only. Do not key off media_title — that rebuilt the
    // whole page on every track metadata update and flickered the tablet on transport taps.
    structural(entityId) {
        listOf(
            ui.browseMediaType,
            ui.listenMode,
            ui.primarySection,
            browseRows.size.toString(),
            queueRows.size.toString(),
        ).joinToString("|")
    }
    val layout = MusicPanelLayout(
        tileId = tile.id,
        entityId = entityId,
        ui = ui,
        browseRows = browseRows,
        browseHasMore = browseHasMore,
        queueRows = queueRows,
        queueMore = queue?.moreCount ?: 0,
        configured = configured,
        progressWidthDp = frame.progressW,
        searchSlot = frame.search,
        browseListDp = frame.browseList,
        queueListDp = frame.queueList,
    )
    // A plain height is stretched to the page, so the cards meet the nav bar. requiredHeightIn
    // is applied after that stretch and keeps this surface the gap above the bar.
    val panelH = (box.h - MusicMetrics.BOTTOM_PAD_DP).coerceAtLeast(1f)
    scope.Box(
        Modifier.size(units.px(box.w), units.px(panelH))
            .requiredHeightIn(max = units.px(panelH))
            .clip(RectShape(0f, 0f, 0f, 0f))
            .background(role(Role.PAGE)),
    ) {
        this@musicPanel.musicPlaced(layout, frame)
    }
}

/**
 * Same rectangles as the wide Music Assistant page: now playing over Browse on the left,
 * Search / Queue filling the right. Sizes are explicit so the search field lands in that column.
 */
private data class MusicFrame(
    val pad: Float,
    val colW: Float,
    val innerH: Float,
    val sectionGap: Float,
    val columnGap: Float,
    val progressW: Float,
    val search: SearchSlot,
    val browseList: Float,
    val queueList: Float,
)

private fun musicFrame(box: TileBox): MusicFrame {
    val pad = MusicMetrics.PAD_DP
    val bottom = MusicMetrics.BOTTOM_PAD_DP
    val columnGap = MusicMetrics.COLUMN_GAP_DP
    val sectionGap = MusicMetrics.SECTION_GAP_DP
    val cardPad = MusicMetrics.CARD_PAD_DP
    val innerW = (box.w - pad * 2f).coerceAtLeast(1f)
    val innerH = (box.h - pad - bottom).coerceAtLeast(1f)
    val colW = ((innerW - columnGap) / 2f).coerceAtLeast(1f)
    val progressW = (
        colW - cardPad * 2f - MusicMetrics.ART_DP - columnGap
        ).coerceAtLeast(MusicMetrics.PROGRESS_BAR_MIN_W_DP)
    val searchChrome = cardPad * 2f + MusicMetrics.SEGMENT_H_DP + MusicMetrics.LISTEN_SEGMENT_GAP_DP
    val line = MusicMetrics.TEXT_LINE_FACTOR
    val nowMeta = Type.TITLE * line +
        Type.BODY * line +
        Type.CAPTION * line +
        MusicMetrics.CHIP_GAP_DP +
        Type.CAPTION * line +
        MusicMetrics.PROGRESS_TIME_GAP_DP +
        MusicMetrics.PROGRESS_H_DP +
        MusicMetrics.CHIP_GAP_DP +
        MusicMetrics.TRANSPORT_DP
    val nowCard = max(MusicMetrics.ART_DP, nowMeta) + cardPad * 2f
    val browseHeader = Type.TITLE * line +
        MusicMetrics.BROWSE_TITLE_GAP_DP +
        MusicMetrics.CHIP_H_DP +
        MusicMetrics.CHIP_GAP_DP
    val browseList = (
        innerH - nowCard - sectionGap - cardPad * 2f - browseHeader - MusicMetrics.LIST_SHORTFALL_DP
        ).coerceAtLeast(MusicMetrics.ROW_H_DP)
    val queueList = (
        innerH - cardPad * 2f -
            MusicMetrics.SEGMENT_H_DP - MusicMetrics.LISTEN_SEGMENT_GAP_DP -
            MusicMetrics.CHIP_H_DP - MusicMetrics.CHIP_GAP_DP -
            MusicMetrics.LIST_SHORTFALL_DP
        ).coerceAtLeast(MusicMetrics.ROW_H_DP)
    val search = SearchSlot(
        xDp = box.x + pad + colW + columnGap + cardPad,
        yDp = box.y + pad + cardPad + MusicMetrics.SEGMENT_H_DP + MusicMetrics.LISTEN_SEGMENT_GAP_DP,
        widthDp = (colW - cardPad * 2f).coerceAtLeast(1f),
        heightDp = (innerH - searchChrome).coerceAtLeast(1f),
    )
    return MusicFrame(
        pad = pad,
        colW = colW,
        innerH = innerH,
        sectionGap = sectionGap,
        columnGap = columnGap,
        progressW = progressW,
        search = search,
        browseList = browseList,
        queueList = queueList,
    )
}

private data class SearchSlot(
    val xDp: Float,
    val yDp: Float,
    val widthDp: Float,
    val heightDp: Float,
)

private data class MusicPanelLayout(
    val tileId: String,
    val entityId: String,
    val ui: MusicUiState,
    val browseRows: List<MusicListRow>,
    val browseHasMore: Boolean,
    val queueRows: List<MusicListRow>,
    val queueMore: Int,
    val configured: Boolean,
    val progressWidthDp: Float,
    val searchSlot: SearchSlot,
    val browseListDp: Float,
    val queueListDp: Float,
)

private fun DocContext.musicPlaced(layout: MusicPanelLayout, frame: MusicFrame) {
    val doc = this
    // Now playing hugs its content so the space under the transport buttons is only the
    // card padding. Browse takes whatever height is left in the column.
    at(frame.pad, frame.pad) {
        doc.scope.Column(
            Modifier.size(doc.units.px(frame.colW), doc.units.px(frame.innerH))
                .requiredHeightIn(max = doc.units.px(frame.innerH)),
        ) {
            doc.nowPlayingCard(layout, Modifier.fillMaxWidth())
            Spacer(Modifier.height(doc.units.px(frame.sectionGap)))
            doc.browseCard(layout, Modifier.fillMaxWidth().verticalWeight(1f))
        }
    }
    at(frame.pad + frame.colW + frame.columnGap, frame.pad) {
        doc.listenCard(
            layout,
            Modifier.size(doc.units.px(frame.colW), doc.units.px(frame.innerH))
                .requiredHeightIn(max = doc.units.px(frame.innerH)),
        )
    }
}

private fun DocContext.nowPlayingCard(layout: MusicPanelLayout, modifier: Modifier) {
    val cardBg = fixed(withAlpha(roleArgb(Role.CARD), MusicMetrics.NOW_PLAYING_FILL_ALPHA))
    scope.Column(
        modifier
            .clip(rounded(MusicMetrics.CARD_CORNER_DP))
            .background(cardBg)
            .padding(units.px(MusicMetrics.CARD_PAD_DP)),
    ) {
        val doc = this@nowPlayingCard
        doc.nowPlayingSection(layout)
    }
}

private fun DocContext.nowPlayingSection(layout: MusicPanelLayout) {
    scope.Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
        val doc = this@nowPlayingSection
        doc.nowPlayingArt(layout.tileId, layout.entityId)
        Spacer(Modifier.width(doc.units.px(MusicMetrics.COLUMN_GAP_DP)))
        Column(Modifier.horizontalWeight(1f)) {
            doc.nowPlayingMeta(layout)
        }
    }
}

private fun DocContext.nowPlayingArt(tileId: String, entityId: String) {
    val art = MusicMetrics.ART_DP
    val px = units.px(art).toInt()
    picture(
        PictureSlot(
            name = "art.$entityId.$tileId",
            source = "attr:$entityId:entity_picture",
            widthPx = px,
            heightPx = px,
            refreshSeconds = 0,
        ),
    )
    scope.Image(
        image("art.$entityId.$tileId", px, px),
        Modifier.size(units.px(art), units.px(art))
            .clip(rounded(MusicMetrics.ART_CORNER_DP))
            .background(role(Role.TRACK)),
        contentScale = RcContentScale.Crop,
    )
}

private fun DocContext.nowPlayingMeta(layout: MusicPanelLayout) {
    val entityId = layout.entityId
    val ink = role(Role.INK)
    val soft = role(Role.INK_SOFT)
    label(
        text = text(Vars.entity(entityId, "title")),
        sp = Type.TITLE,
        color = ink,
        weight = Weight.SEMI_BOLD,
        lines = 2,
    )
    label(text = text(Vars.entity(entityId, "artist")), sp = Type.BODY, color = soft, lines = 1)
    label(
        text = text(Vars.entity(entityId, "album")),
        sp = Type.CAPTION,
        color = soft,
        lines = 1,
        modifier = Modifier.visibility(int(Vars.entity(entityId, "album_vis"))),
    )
    gap(MusicMetrics.CHIP_GAP_DP)
    nowPlayingProgress(
        tileId = layout.tileId,
        entityId = entityId,
        progressWidthDp = layout.progressWidthDp,
        soft = soft,
    )
    gap(MusicMetrics.CHIP_GAP_DP)
    nowPlayingTransport(layout.tileId, entityId)
}

/** RPI NowPlayingTrackTime: elapsed | duration on one row, full-width bar under. */
private fun DocContext.nowPlayingProgress(
    tileId: String,
    entityId: String,
    progressWidthDp: Float,
    soft: RcColor,
) {
    scope.Column(Modifier.fillMaxWidth()) {
        val doc = this@nowPlayingProgress
        Row(
            Modifier.fillMaxWidth(),
            horizontal = RcRowHorizontalPositioning.SpaceBetween,
            vertical = RcVerticalPositioning.Center,
        ) {
            doc.label(doc.text(Vars.entity(entityId, "pos")), Type.CAPTION, soft)
            doc.label(doc.text(Vars.entity(entityId, "dur")), Type.CAPTION, soft)
        }
        doc.gap(MusicMetrics.PROGRESS_TIME_GAP_DP)
        doc.levelBar(
            level = doc.float(Vars.entity(entityId, "progress")),
            widthDp = progressWidthDp,
            heightDp = MusicMetrics.PROGRESS_H_DP,
            fill = doc.role(Role.ACCENT),
            track = doc.role(Role.TRACK),
            steps = MusicMetrics.PROGRESS_STEPS,
        ) { step ->
            ActionRef(
                tileId,
                "seek",
                (
                    (doc.states[entityId]?.number("media_duration") ?: 0.0) *
                        step / MusicMetrics.PROGRESS_STEPS
                    ).roundToInt().toString(),
            )
        }
    }
}

private fun DocContext.nowPlayingTransport(tileId: String, entityId: String) {
    val hit = MusicMetrics.TRANSPORT_DP
    val glyph = MusicMetrics.TRANSPORT_ICON_DP
    scope.Row(
        Modifier.fillMaxWidth().keptHorizontalScroll("music.$tileId.transport"),
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@nowPlayingTransport
        doc.transportIcon(ref = ActionRef(tileId, "previous"), name = "skip-previous", hitDp = hit, iconDp = glyph)
        Box(
            doc.tap(
                Modifier.size(doc.units.px(hit), doc.units.px(hit)),
                ActionRef(tileId, "play_pause"),
            ),
            horizontal = RcHorizontalPositioning.Center,
            vertical = RcVerticalPositioning.Center,
        ) {
            doc.icon(
                name = "pause",
                sizeDp = glyph,
                color = doc.role(Role.INK),
                visible = doc.int(Vars.entity(entityId, "playing")),
            )
            doc.icon(
                name = "play",
                sizeDp = glyph,
                color = doc.role(Role.INK),
                visible = doc.int(Vars.entity(entityId, "notplaying")),
            )
        }
        doc.transportIcon(ref = ActionRef(tileId, "next"), name = "skip-next", hitDp = hit, iconDp = glyph)
        Spacer(Modifier.width(doc.units.px(MusicMetrics.TIME_GAP_DP)))
        doc.transportIcon(
            ref = ActionRef(tileId, "volume_down"),
            name = "volume-minus",
            hitDp = hit,
            iconDp = glyph,
        )
        doc.transportIcon(
            ref = ActionRef(tileId, "volume_up"),
            name = "volume-plus",
            hitDp = hit,
            iconDp = glyph,
        )
    }
}

private fun DocContext.transportIcon(ref: ActionRef, name: String, hitDp: Float, iconDp: Float) {
    scope.Box(
        tap(Modifier.size(units.px(hitDp), units.px(hitDp)), ref),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        this@transportIcon.icon(name, iconDp, role(Role.INK))
    }
}

private fun DocContext.browseCard(layout: MusicPanelLayout, modifier: Modifier) {
    val cardBg = fixed(withAlpha(roleArgb(Role.CARD), MusicMetrics.CARD_FILL_ALPHA))
    val pad = units.px(MusicMetrics.CARD_PAD_DP)
    scope.Column(
        modifier.clip(rounded(MusicMetrics.CARD_CORNER_DP))
            .background(cardBg)
            .padding(pad),
    ) {
        val doc = this@browseCard
        val tileId = layout.tileId
        val ui = layout.ui
        doc.label(text = "Browse", sp = Type.TITLE, color = doc.role(Role.INK), weight = Weight.SEMI_BOLD)
        doc.gap(MusicMetrics.BROWSE_TITLE_GAP_DP)
        Row(
            Modifier.fillMaxWidth().keptHorizontalScroll("music.${layout.tileId}.types"),
            vertical = RcVerticalPositioning.Center,
        ) {
            MusicUiState.BROWSE_TYPES.forEachIndexed { index, type ->
                if (index > 0) Spacer(Modifier.width(doc.units.px(MusicMetrics.CHIP_GAP_DP)))
                val selected = ui.browseMediaType == type
                doc.key(
                    ref = ActionRef(tileId, "browse_type", type),
                    text = type.replaceFirstChar { it.uppercase() },
                    bg = if (selected) doc.role(Role.KEY_ON) else doc.role(Role.KEY),
                    ink = if (selected) doc.role(Role.KEY_ON_INK) else doc.role(Role.INK),
                    heightDp = MusicMetrics.CHIP_H_DP,
                )
            }
        }
        doc.gap(MusicMetrics.CHIP_GAP_DP)
        // Weight on a scroller is ignored and the rows paint to the card edge. An exact
        // height is the viewport, short of the card padding, and the rect clip holds it.
        Column(
            Modifier.fillMaxWidth()
                .height(doc.units.px(layout.browseListDp))
                .requiredHeightIn(max = doc.units.px(layout.browseListDp))
                .clip(RectShape(0f, 0f, 0f, 0f))
                .keptVerticalScroll("music.${layout.tileId}.browse"),
        ) {
            if (!layout.configured) {
                doc.label("Bind a Music Assistant player on this tile.", Type.BODY, doc.role(Role.INK_SOFT))
            } else if (layout.browseRows.isEmpty()) {
                doc.label("No items", Type.BODY, doc.role(Role.INK_SOFT))
            } else {
                layout.browseRows.forEachIndexed { index, row ->
                    if (index > 0) doc.gap(MusicMetrics.ROW_GAP_DP)
                    doc.musicRow(tileId, row, showEnqueue = true)
                }
                if (layout.browseHasMore) {
                    doc.gap(MusicMetrics.CHIP_GAP_DP)
                    Box(
                        doc.tap(Modifier.fillMaxWidth(), ActionRef(tileId, "load_more")),
                        vertical = RcVerticalPositioning.Center,
                    ) {
                        doc.label("Load more", Type.BODY, doc.role(Role.ACCENT))
                    }
                }
            }
        }
    }
}

private fun DocContext.listenCard(layout: MusicPanelLayout, modifier: Modifier) {
    val cardBg = fixed(withAlpha(roleArgb(Role.CARD), MusicMetrics.CARD_FILL_ALPHA))
    val pad = units.px(MusicMetrics.CARD_PAD_DP)
    scope.Column(
        modifier.clip(rounded(MusicMetrics.CARD_CORNER_DP))
            .background(cardBg)
            .padding(pad),
    ) {
        val doc = this@listenCard
        doc.listenModeStrip(layout.tileId, layout.ui)
        doc.gap(MusicMetrics.LISTEN_SEGMENT_GAP_DP)
        when (layout.ui.listenMode) {
            MusicUiState.LISTEN_SEARCH -> doc.searchHost(
                tileId = layout.tileId,
                entityId = layout.entityId,
                configured = layout.configured,
                slot = layout.searchSlot,
            )
            else -> doc.queueSection(
                tileId = layout.tileId,
                rows = layout.queueRows,
                moreCount = layout.queueMore,
                configured = layout.configured,
                listHeightDp = layout.queueListDp,
                modifier = Modifier.verticalWeight(1f).fillMaxWidth(),
            )
        }
    }
}

private fun DocContext.listenModeStrip(tileId: String, ui: MusicUiState) {
    scope.Row(Modifier.fillMaxWidth()) {
        val doc = this@listenModeStrip
        listOf(
            MusicUiState.LISTEN_SEARCH to "Search",
            MusicUiState.LISTEN_QUEUE to "Queue",
        ).forEachIndexed { index, (mode, label) ->
            if (index > 0) Spacer(Modifier.width(doc.units.px(MusicMetrics.CHIP_GAP_DP)))
            val selected = ui.listenMode == mode
            doc.key(
                ref = ActionRef(tileId, "listen_mode", mode),
                text = label,
                icon = if (selected) "check" else null,
                bg = if (selected) doc.role(Role.KEY_ON) else doc.role(Role.KEY),
                ink = if (selected) doc.role(Role.KEY_ON_INK) else doc.role(Role.INK),
                heightDp = MusicMetrics.SEGMENT_H_DP,
                modifier = Modifier.horizontalWeight(1f),
            )
        }
    }
}

private fun DocContext.searchHost(
    tileId: String,
    entityId: String,
    configured: Boolean,
    slot: SearchSlot,
) {
    val bounds = SearchBoundsPx(
        x = units.px(slot.xDp).toInt(),
        y = units.px(slot.yDp).toInt(),
        width = units.px(slot.widthDp).toInt(),
        height = units.px(slot.heightDp).toInt(),
    )
    val componentId = liveSearch(tileId, entityId, bounds)
    scope.Box(
        Modifier.size(units.px(slot.widthDp), units.px(slot.heightDp))
            .componentId(componentId),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@searchHost
        if (!configured) {
            doc.label("Configure the Music Assistant player.", Type.BODY, doc.role(Role.INK_SOFT))
        } else {
            doc.label("Search opens on capable tablets.", Type.BODY, doc.role(Role.INK_SOFT))
        }
    }
}

private fun DocContext.queueSection(
    tileId: String,
    rows: List<MusicListRow>,
    moreCount: Int,
    configured: Boolean,
    listHeightDp: Float,
    modifier: Modifier,
) {
    scope.Column(modifier) {
        val doc = this@queueSection
        Row(Modifier.fillMaxWidth(), horizontal = RcRowHorizontalPositioning.End) {
            doc.key(
                ActionRef(tileId, "clear_queue"),
                text = "Clear",
                heightDp = MusicMetrics.CHIP_H_DP,
            )
        }
        doc.gap(MusicMetrics.CHIP_GAP_DP)
        Column(
            Modifier.fillMaxWidth()
                .height(doc.units.px(listHeightDp))
                .requiredHeightIn(max = doc.units.px(listHeightDp))
                .clip(RectShape(0f, 0f, 0f, 0f))
                .keptVerticalScroll("music.$tileId.queue"),
        ) {
            when {
                !configured -> doc.label(
                    "Bind a Music Assistant player.",
                    Type.BODY,
                    doc.role(Role.INK_SOFT),
                )
                rows.isEmpty() -> doc.label("Queue is empty", Type.BODY, doc.role(Role.INK_SOFT))
                else -> {
                    rows.forEachIndexed { index, row ->
                        if (index > 0) doc.gap(MusicMetrics.ROW_GAP_DP)
                        doc.musicRow(tileId, row, showEnqueue = false)
                    }
                    if (moreCount > 0) {
                        doc.gap(MusicMetrics.CHIP_GAP_DP)
                        doc.label(
                            "+$moreCount more",
                            Type.CAPTION,
                            doc.role(Role.INK_SOFT),
                        )
                    }
                }
            }
        }
    }
}

private fun DocContext.musicRow(tileId: String, row: MusicListRow, showEnqueue: Boolean) {
    val bgAlpha = if (row.isCurrentPlayback) {
        MusicMetrics.ROW_CURRENT_ALPHA
    } else {
        MusicMetrics.ROW_OTHER_ALPHA
    }
    val rowBg = fixed(withAlpha(roleArgb(Role.CARD), bgAlpha))
    val thumb = MusicMetrics.THUMB_DP
    scope.Row(
        Modifier.fillMaxWidth()
            .clip(rounded(MusicMetrics.ROW_CORNER_DP))
            .background(rowBg)
            .padding(
                start = units.px(MusicMetrics.ROW_PAD_H_DP),
                top = units.px(MusicMetrics.ROW_PAD_V_DP),
                end = units.px(MusicMetrics.ROW_PAD_H_DP),
                bottom = units.px(MusicMetrics.ROW_PAD_V_DP),
            ),
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@musicRow
        Row(
            doc.tap(Modifier.horizontalWeight(1f), ActionRef(tileId, "play_uri", row.uri)),
            vertical = RcVerticalPositioning.Center,
        ) {
            doc.musicRowThumb(tileId, row, thumb)
            Spacer(Modifier.width(doc.units.px(MusicMetrics.ROW_THUMB_GAP_DP)))
            Column(Modifier.horizontalWeight(1f)) {
                doc.label(text = row.title, sp = Type.BODY, color = doc.role(Role.INK), lines = 1)
                row.subtitle?.let {
                    doc.label(text = it, sp = Type.CAPTION, color = doc.role(Role.INK_SOFT), lines = 1)
                }
            }
        }
        if (showEnqueue) {
            doc.transportIcon(
                ref = ActionRef(tileId, "enqueue_uri", row.uri),
                name = "dots-vertical",
                hitDp = MusicMetrics.TRANSPORT_DP,
                iconDp = MusicMetrics.TRANSPORT_ICON_DP,
            )
        }
    }
}

private fun DocContext.musicRowThumb(tileId: String, row: MusicListRow, thumb: Float) {
    val px = units.px(thumb).toInt()
    val pictureName = "ma.$tileId.${row.uri.hashCode()}"
    if (!row.imagePathOrUrl.isNullOrBlank()) {
        picture(
            PictureSlot(
                name = pictureName,
                source = row.imagePathOrUrl,
                widthPx = px,
                heightPx = px,
                refreshSeconds = 0,
            ),
        )
        scope.Image(
            image(pictureName, px, px),
            Modifier.size(units.px(thumb), units.px(thumb))
                .clip(rounded(MusicMetrics.THUMB_CORNER_DP)),
            contentScale = RcContentScale.Crop,
        )
    } else {
        scope.Box(
            Modifier.size(units.px(thumb), units.px(thumb))
                .clip(rounded(MusicMetrics.THUMB_CORNER_DP))
                .background(role(Role.ACCENT_SOFT)),
            horizontal = RcHorizontalPositioning.Center,
            vertical = RcVerticalPositioning.Center,
        ) {
            val doc = this@musicRowThumb
            doc.label(
                if (row.isCurrentPlayback) "♫" else "♪",
                Type.BODY,
                doc.role(Role.ACCENT),
            )
        }
    }
}

/** ARGB from a theme role, for alpha fills. */
private fun DocContext.roleArgb(role: Role): Int = palette[role]

private fun withAlpha(argb: Int, alpha: Float): Int {
    val a = (alpha.coerceIn(0f, 1f) * MusicMetrics.ALPHA_BYTE_MAX).toInt()
    return (argb and MusicMetrics.RGB_MASK) or (a shl MusicMetrics.ALPHA_SHIFT)
}
