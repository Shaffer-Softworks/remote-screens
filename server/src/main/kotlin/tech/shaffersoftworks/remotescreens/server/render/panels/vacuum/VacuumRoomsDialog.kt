@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.panels.vacuum

import tech.shaffersoftworks.remotescreens.server.data.vacuum.VacuumRoom
import tech.shaffersoftworks.remotescreens.server.data.vacuum.VacuumUiState
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.theme.Rpi
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.gap
import tech.shaffersoftworks.remotescreens.server.render.widgets.icon
import tech.shaffersoftworks.remotescreens.server.render.widgets.key
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.rounded
import tech.shaffersoftworks.remotescreens.server.render.widgets.tap
import tech.shaffersoftworks.remotescreens.shared.ActionRef

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.render.keptVerticalScroll
import tech.shaffersoftworks.remotescreens.server.render.panels.vacuum.VacuumDialogMetrics as D

/** Metrics of the vacuum rooms dialog, aligned with the lights detail dialog chrome. */
internal object VacuumDialogMetrics {
    const val CARD_W_DP = 420f
    const val DIALOG_PAD_H_DP = 20f
    const val CARD_CORNER_DP = 28f
    const val CARD_PAD_DP = 20f
    const val CLOSE_DP = 40f
    const val TITLE_SP = 20f
    const val SUB_SP = 14f
    const val TITLE_SUB_GAP_DP = 8f
    const val ROW_H_DP = 48f
    const val CHECK_DP = 24f
    const val CHECK_ICON_RATIO = 0.65f
    const val ROW_GAP_DP = 4f
    const val ROW_ICON_GAP_DP = 14f
    const val FOOTER_GAP_DP = 16f
    const val KEY_GAP_DP = 10f
    const val KEY_H_DP = 52f
    const val CAPTION_GAP_DP = 8f
    const val CAPTION_SP = 12f
    const val LIST_MAX_H_DP = 320f
    const val HEADER_EXTRA_DP = 12f
}

/**
 * Select-rooms dialog over a scrim: checklist of [rooms], Cancel, and Clean N rooms.
 * [screenW] sizes the card; the scrim fills the document so it covers the bars like the lights dialog.
 */
fun DocContext.vacuumRoomsDialog(
    tileId: String,
    vacuumName: String,
    rooms: List<VacuumRoom>,
    ui: VacuumUiState,
    screenW: Float,
) {
    val dismiss = ActionRef(tileId, "vacuum_rooms_dismiss")
    val selected = ui.selectedRooms
    val cardW = minOf(D.CARD_W_DP, screenW - 2 * D.DIALOG_PAD_H_DP)
    val listH = minOf(D.LIST_MAX_H_DP, rooms.size * (D.ROW_H_DP + D.ROW_GAP_DP))
    val cardH = D.CARD_PAD_DP * 2 + D.CLOSE_DP + D.TITLE_SUB_GAP_DP + D.SUB_SP +
        D.HEADER_EXTRA_DP + listH + D.FOOTER_GAP_DP + D.KEY_H_DP + D.CAPTION_GAP_DP + D.CAPTION_SP
    scope.Box(
        tap(Modifier.fillMaxSize().background(role(Role.SCRIM)), dismiss),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@vacuumRoomsDialog
        val pad = doc.units.px(D.CARD_PAD_DP)
        Column(
            doc.tap(
                Modifier.size(doc.units.px(cardW), doc.units.px(cardH))
                    .clip(doc.rounded(D.CARD_CORNER_DP))
                    .background(doc.fixed(Rpi.LightDetailCard)),
                ActionRef("_", "noop"),
            ).padding(pad),
        ) {
            doc.roomsDialogHeader(vacuumName = vacuumName, dismiss = dismiss)
            doc.gap(D.TITLE_SUB_GAP_DP)
            Column(
                Modifier.fillMaxWidth()
                    .height(doc.units.px(listH))
                    .keptVerticalScroll("vacuum.$tileId.rooms"),
            ) {
                rooms.forEachIndexed { index, room ->
                    if (index > 0) Spacer(Modifier.height(doc.units.px(D.ROW_GAP_DP)))
                    doc.roomRow(
                        tileId = tileId,
                        room = room,
                        selected = room.id in selected,
                    )
                }
            }
            Spacer(Modifier.height(doc.units.px(D.FOOTER_GAP_DP)))
            doc.roomsDialogFooter(tileId = tileId, selectedCount = selected.size)
        }
    }
}

private fun DocContext.roomsDialogHeader(vacuumName: String, dismiss: ActionRef) {
    scope.Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
        val doc = this@roomsDialogHeader
        Column(Modifier.horizontalWeight(1f)) {
            doc.label(
                text = doc.i18n.t("ui.rooms_select_title"),
                sp = D.TITLE_SP,
                color = doc.role(Role.INK),
                weight = Weight.SEMI_BOLD,
            )
            doc.label(
                text = doc.i18n.t("ui.rooms_select_subtitle", "name" to vacuumName),
                sp = D.SUB_SP,
                color = doc.role(Role.INK_DIM),
            )
        }
        doc.key(
            ref = dismiss,
            icon = "close",
            heightDp = D.CLOSE_DP,
            widthDp = D.CLOSE_DP,
            round = true,
        )
    }
}

private fun DocContext.roomRow(tileId: String, room: VacuumRoom, selected: Boolean) {
    scope.Row(
        tap(
            Modifier.fillMaxWidth().height(units.px(D.ROW_H_DP)),
            ActionRef(tileId, "vacuum_rooms_toggle", room.id),
        ),
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@roomRow
        Box(
            Modifier.size(doc.units.px(D.CHECK_DP), doc.units.px(D.CHECK_DP))
                .clip(doc.rounded(D.CHECK_DP / 2f))
                .background(doc.role(if (selected) Role.ACCENT else Role.KEY)),
            horizontal = RcHorizontalPositioning.Center,
            vertical = RcVerticalPositioning.Center,
        ) {
            if (selected) {
                doc.icon("check", D.CHECK_DP * D.CHECK_ICON_RATIO, doc.role(Role.INK_DARK))
            }
        }
        Spacer(Modifier.width(doc.units.px(D.ROW_ICON_GAP_DP)))
        doc.label(room.name, Type.BODY, doc.role(Role.INK))
    }
}

private fun DocContext.roomsDialogFooter(tileId: String, selectedCount: Int) {
    val canClean = selectedCount > 0
    scope.Row(Modifier.fillMaxWidth()) {
        val doc = this@roomsDialogFooter
        doc.key(
            ref = ActionRef(tileId, "vacuum_rooms_dismiss"),
            text = doc.i18n.t("ui.cancel"),
            heightDp = D.KEY_H_DP,
            modifier = Modifier.horizontalWeight(1f),
        )
        Spacer(Modifier.width(doc.units.px(D.KEY_GAP_DP)))
        doc.key(
            ref = ActionRef(tileId, "vacuum_rooms_clean"),
            text = doc.i18n.t("ui.rooms_clean", "count" to selectedCount),
            heightDp = D.KEY_H_DP,
            bg = doc.role(if (canClean) Role.ACCENT else Role.KEY),
            ink = doc.role(if (canClean) Role.INK_DARK else Role.INK_DIM),
            modifier = Modifier.horizontalWeight(1f),
        )
    }
    gap(D.CAPTION_GAP_DP)
    label(
        text = i18n.t("ui.rooms_clears_after_start"),
        sp = D.CAPTION_SP,
        color = role(Role.INK_FAINT),
    )
}
