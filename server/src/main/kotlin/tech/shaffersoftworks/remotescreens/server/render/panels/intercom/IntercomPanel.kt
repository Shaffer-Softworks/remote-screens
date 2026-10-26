package tech.shaffersoftworks.remotescreens.server.render.panels.intercom

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcBorderShape
import androidx.compose.remote.creation.dsl.RcColumnVerticalPositioning
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.border
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxHeight
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.verticalScroll
import androidx.compose.remote.creation.dsl.verticalWeight
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.widgets.icon
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.rounded
import tech.shaffersoftworks.remotescreens.server.render.widgets.tap
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.IntercomStation
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileBox
import tech.shaffersoftworks.remotescreens.shared.Tile
import tech.shaffersoftworks.remotescreens.server.render.panels.intercom.IntercomMetrics as M

/**
 * RPI's `IntercomScreen`: a left column with the status and the Mute / Hang Up keys, and one card per station.
 * An older tablet still gets the page, with a status line asking for an app update and no call actions.
 */
fun DocContext.intercomPanel(tile: Tile, box: TileBox) {
    val view = intercom ?: IntercomView.from(null, supportsCalls = false, i18n)
    val layout = IntercomLayout(pageScale(box), box.w, box.h)
    scope.Row(Modifier.fillMaxSize().background(fixed(IntercomPaint.PAGE))) {
        val doc = this@intercomPanel
        doc.sideColumn(tile.id, view, layout)
        doc.stationList(tile.id, view, layout)
    }
}

private fun pageScale(box: TileBox): Float =
    minOf(box.w / M.BASE_W, box.h / M.BASE_H).coerceIn(M.LAYOUT_SCALE_MIN, M.LAYOUT_SCALE_MAX)

/** The grey column: the accent, "INTERCOM", then the status and the two keys centred under it. */
private fun DocContext.sideColumn(tileId: String, view: IntercomView, layout: IntercomLayout) {
    scope.Column(
        Modifier.width(units.px(layout.columnW)).fillMaxHeight().background(fixed(IntercomPaint.COLUMN))
            .padding(0f, units.px(layout.dp(M.COLUMN_TOP_DP)), 0f, 0f),
    ) {
        val doc = this@sideColumn
        doc.accentBar(layout)
        Spacer(Modifier.height(doc.units.px(layout.dp(M.TITLE_GAP_DP))))
        doc.label(
            doc.i18n.t("ui.intercom_title"),
            layout.sp(M.TITLE_SP),
            doc.fixed(IntercomPaint.INK),
            modifier = Modifier.padding(doc.units.px(layout.dp(M.TITLE_START_DP)), 0f, 0f, 0f),
        )
        doc.statusAndKeys(tileId, view, layout)
    }
}

private fun DocContext.statusAndKeys(tileId: String, view: IntercomView, layout: IntercomLayout) {
    scope.Column(
        Modifier.verticalWeight(1f).fillMaxWidth(),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcColumnVerticalPositioning.Center,
    ) {
        val doc = this@statusAndKeys
        doc.statusLine(view, layout)
        Spacer(Modifier.height(doc.units.px(layout.dp(M.BUTTON_TOP_DP))))
        scope.Row(vertical = RcVerticalPositioning.Center) {
            doc.muteKey(tileId, view, layout)
            Spacer(Modifier.width(doc.units.px(layout.dp(M.BUTTON_GAP_DP))))
            doc.hangupKey(tileId, view, layout)
        }
    }
}

private fun DocContext.accentBar(layout: IntercomLayout) {
    scope.Row {
        val doc = this@accentBar
        Spacer(Modifier.width(doc.units.px(layout.dp(M.ACCENT_START_DP))))
        Box(
            Modifier.size(
                doc.units.px(layout.dp(M.ACCENT_W_DP)),
                doc.units.px(layout.dp(M.ACCENT_H_DP)),
            ).background(doc.fixed(IntercomPaint.ACCENT)),
        )
    }
}

private fun DocContext.statusLine(view: IntercomView, layout: IntercomLayout) {
    val side = units.px(layout.dp(M.TITLE_START_DP))
    val modifier = Modifier.fillMaxWidth().padding(side, 0f, side, 0f)
    if (view.supportsCalls) {
        label(
            text(IntercomVars.STATUS),
            layout.sp(M.STATUS_SP),
            fixed(IntercomPaint.INK),
            align = RcTextAlign.Center,
            modifier = modifier,
        )
    } else {
        label(
            view.status,
            layout.sp(M.STATUS_SP),
            fixed(IntercomPaint.INK),
            lines = 3,
            align = RcTextAlign.Center,
            modifier = modifier,
        )
    }
}

private fun DocContext.stationList(tileId: String, view: IntercomView, layout: IntercomLayout) {
    val start = units.px(layout.dp(M.LIST_START_DP))
    val top = units.px(layout.dp(M.LIST_TOP_DP))
    val end = units.px(layout.dp(M.LIST_END_DP))
    val bottom = units.px(layout.dp(M.LIST_BOTTOM_DP))
    scope.Column(
        Modifier.horizontalWeight(1f).fillMaxHeight().verticalScroll().padding(start, top, end, bottom),
    ) {
        val doc = this@stationList
        view.stations.forEachIndexed { index, station ->
            if (index > 0) Spacer(Modifier.height(doc.units.px(layout.dp(M.ROW_GAP_DP))))
            doc.stationCard(tileId, view, station, index, layout)
        }
    }
}

private fun DocContext.stationCard(
    tileId: String,
    view: IntercomView,
    station: IntercomStation,
    index: Int,
    layout: IntercomLayout,
) {
    val corner = layout.dp(M.ROW_CORNER_DP)
    val pad = units.px(layout.dp(M.ROW_PAD_DP))
    scope.Box(Modifier.fillMaxWidth().height(units.px(layout.dp(M.ROW_H_DP))).padding(pad)) {
        val doc = this@stationCard
        Column(
            Modifier.fillMaxSize().clip(doc.rounded(corner)).background(doc.fixed(IntercomPaint.CARD)).border(
                doc.units.px(layout.dp(M.ROW_BORDER_DP)),
                doc.units.px(corner),
                doc.fixed(IntercomPaint.BLACK),
                RcBorderShape.RoundedRectangle,
            ),
        ) {
            doc.stationBody(tileId, view, station, index, layout)
            Box(
                Modifier.fillMaxWidth().height(doc.units.px(layout.dp(M.ROW_BAR_DP)))
                    .background(doc.fixed(IntercomPaint.ACCENT)),
            )
        }
    }
}

private fun DocContext.stationBody(
    tileId: String,
    view: IntercomView,
    station: IntercomStation,
    index: Int,
    layout: IntercomLayout,
) {
    val inset = units.px(layout.dp(M.ROW_INSET_DP))
    val canCall = view.supportsCalls && !view.isBusy && !station.inCall
    scope.Row(
        Modifier.verticalWeight(1f).fillMaxWidth().padding(inset),
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@stationBody
        doc.icon("account-tie-voice-outline", layout.dp(M.ROW_ICON_DP), doc.fixed(IntercomPaint.INK))
        Spacer(Modifier.width(doc.units.px(layout.dp(M.ROW_INSET_DP))))
        Column(Modifier.horizontalWeight(1f)) {
            doc.label(station.displayName, layout.sp(M.NAME_SP), doc.fixed(IntercomPaint.INK))
            doc.label(
                doc.text(IntercomVars.rowStatus(index)),
                layout.sp(M.ROW_STATUS_SP),
                doc.fixed(IntercomPaint.INK),
            )
        }
        doc.controlKey(
            doc.i18n.t("ui.intercom_call"),
            layout,
            M.CALL_H_DP,
            if (canCall) ActionRef(tileId, "intercom_call", station.clientId) else null,
        ) {
            doc.icon("phone-outline", layout.dp(M.BUTTON_ICON_DP), doc.fixed(IntercomPaint.INK))
        }
    }
}

private fun DocContext.muteKey(tileId: String, view: IntercomView, layout: IntercomLayout) {
    val action = if (view.supportsCalls) ActionRef(tileId, "intercom_mute") else null
    controlKey(i18n.t("ui.intercom_mute"), layout, M.BUTTON_H_DP, action) {
        muteGlyph(view, layout)
    }
}

private fun DocContext.hangupKey(tileId: String, view: IntercomView, layout: IntercomLayout) {
    val canHangUp = view.supportsCalls && (view.isBusy || view.connectedId != null)
    val action = if (canHangUp) ActionRef(tileId, "intercom_hangup") else null
    controlKey(i18n.t("ui.intercom_hangup"), layout, M.BUTTON_H_DP, action) {
        hangupGlyph(view, layout)
    }
}

private fun DocContext.muteGlyph(view: IntercomView, layout: IntercomLayout) {
    val size = layout.dp(M.BUTTON_ICON_DP)
    if (!view.supportsCalls) {
        icon("microphone-outline", size, fixed(IntercomPaint.MIC_LIVE))
        return
    }
    val edge = units.px(size)
    scope.Box(Modifier.size(edge, edge)) {
        icon(
            "microphone-outline",
            size,
            fixed(IntercomPaint.MIC_LIVE),
            int(IntercomVars.MIC_ON, if (view.isMuted) Vars.GONE else Vars.VISIBLE),
        )
        icon(
            "microphone-off",
            size,
            fixed(IntercomPaint.MIC_MUTED),
            int(IntercomVars.MIC_OFF, if (view.isMuted) Vars.VISIBLE else Vars.GONE),
        )
    }
}

private fun DocContext.hangupGlyph(view: IntercomView, layout: IntercomLayout) {
    val size = layout.dp(M.BUTTON_ICON_DP)
    if (!view.supportsCalls) {
        icon("phone-outline", size, fixed(IntercomPaint.INK))
        return
    }
    val live = view.connectedId != null
    val edge = units.px(size)
    scope.Box(Modifier.size(edge, edge)) {
        icon(
            "phone-hangup",
            size,
            fixed(IntercomPaint.INK),
            int(IntercomVars.HANGUP_LIVE, if (live) Vars.VISIBLE else Vars.GONE),
        )
        icon(
            "phone-outline",
            size,
            fixed(IntercomPaint.INK),
            int(IntercomVars.HANGUP_IDLE, if (live) Vars.GONE else Vars.VISIBLE),
        )
    }
}

private fun DocContext.controlKey(
    caption: String,
    layout: IntercomLayout,
    heightDp: Float,
    action: ActionRef?,
    glyph: () -> Unit,
) {
    val corner = layout.dp(M.BUTTON_CORNER_DP)
    val box = tap(
        Modifier.width(units.px(layout.dp(M.BUTTON_W_DP))).height(units.px(layout.dp(heightDp)))
            .clip(rounded(corner))
            .background(fixed(IntercomPaint.BUTTON))
            .border(
                units.px(layout.dp(M.BUTTON_BORDER_DP)),
                units.px(corner),
                fixed(IntercomPaint.BLACK),
                RcBorderShape.RoundedRectangle,
            ),
        action,
    )
    scope.Row(
        box,
        horizontal = RcRowHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        glyph()
        Spacer(Modifier.width(units.px(layout.dp(M.ICON_TEXT_GAP_DP))))
        label(caption, layout.sp(M.BUTTON_SP), fixed(IntercomPaint.INK))
    }
}
