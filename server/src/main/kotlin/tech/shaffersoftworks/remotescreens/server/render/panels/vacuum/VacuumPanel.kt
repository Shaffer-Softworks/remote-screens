@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.panels.vacuum

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.data.vacuum.VacuumUiState
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.cards.vacuumCard
import tech.shaffersoftworks.remotescreens.server.render.keptVerticalScroll
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileBox
import tech.shaffersoftworks.remotescreens.server.render.widgets.icon
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.tap
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.Tile
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcColumnVerticalPositioning
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.fillMaxHeight
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.verticalWeight
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.render.panels.vacuum.VacuumMetrics as M

/** The page's scale and the width of its vacuum list, in dp. */
private class VacuumLayout(val scale: Float, val pageW: Float, val pageH: Float) {
    fun dp(value: Float): Float = value * scale
    fun sp(value: Float): Float = maxOf(value * scale, M.SMALL_LABEL_MIN_SP)
    val listW: Float get() = dp(M.LIST_W_DP)
}

/**
 * Full vacuum page: every `vacuum.*` in a list on the left, and start / pause / dock / locate plus fan speeds of
 * the chosen one on the right.
 */
fun DocContext.vacuumPanel(tile: Tile, box: TileBox) {
    val ui = data.vacuumUi[tile.id] ?: VacuumUiState()
    val vacuums = vacuumEntityIds(tile.entityId)
    val selectedId = ui.selectedVacuum?.takeIf { it in vacuums }
        ?: tile.entityId?.takeIf { it in vacuums }
        ?: vacuums.singleOrNull()
    val selected = selectedId?.let { states[it] }
    val layout = VacuumLayout(pageScale(box), box.w, box.h)
    scope.Row(Modifier.fillMaxSize().background(role(Role.PAGE))) {
        val doc = this@vacuumPanel
        doc.vacuumList(tileId = tile.id, vacuums = vacuums, selectedId = selectedId, layout = layout)
        doc.vacuumDetail(selected = selected, layout = layout)
    }
}

private fun pageScale(box: TileBox): Float =
    minOf(box.w / M.BASE_W, box.h / M.BASE_H).coerceIn(M.LAYOUT_SCALE_MIN, M.LAYOUT_SCALE_MAX)

/** All vacuums, with the tile's entity first when it is one. */
private fun DocContext.vacuumEntityIds(preferred: String?): List<String> {
    val all = states.keys.filter { it.startsWith("vacuum.") }.sorted()
    if (preferred == null || preferred !in all) return all
    return listOf(preferred) + all.filter { it != preferred }
}

private fun DocContext.vacuumList(
    tileId: String,
    vacuums: List<String>,
    selectedId: String?,
    layout: VacuumLayout,
) {
    scope.Column(
        Modifier.width(units.px(layout.listW)).fillMaxHeight().background(role(Role.STRIP))
            .padding(start = 0f, top = units.px(layout.dp(M.LIST_TOP_DP)), end = 0f, bottom = 0f),
    ) {
        val doc = this@vacuumList
        Row {
            Spacer(Modifier.width(doc.units.px(layout.dp(M.ACCENT_BAR_START_DP))))
            Box(
                Modifier.size(
                    doc.units.px(layout.dp(M.ACCENT_BAR_W_DP - M.ACCENT_BAR_START_DP)),
                    doc.units.px(layout.dp(M.ACCENT_BAR_H_DP)),
                ).background(doc.role(Role.ACCENT_DARK)),
            )
        }
        Spacer(Modifier.height(doc.units.px(layout.dp(M.TITLE_GAP_DP))))
        doc.label(
            text = doc.i18n.t("ui.vacuum_title"),
            sp = layout.sp(M.TITLE_SP),
            color = doc.role(Role.INK),
            modifier = Modifier.padding(
                start = doc.units.px(layout.dp(M.TITLE_START_DP)),
                top = 0f,
                end = 0f,
                bottom = 0f,
            ),
        )
        val pad = doc.units.px(layout.dp(M.LIST_PAD_DP))
        Column(
            Modifier.fillMaxWidth()
                .verticalWeight(1f)
                .keptVerticalScroll("vacuum.$tileId")
                .padding(start = pad, top = pad, end = pad, bottom = pad),
        ) {
            vacuums.forEachIndexed { index, entityId ->
                if (index > 0) Spacer(Modifier.height(doc.units.px(layout.dp(M.ITEM_GAP_DP))))
                doc.vacuumRow(
                    tileId = tileId,
                    entityId = entityId,
                    isSelected = entityId == selectedId,
                    layout = layout,
                )
            }
        }
    }
}

private fun DocContext.vacuumRow(
    tileId: String,
    entityId: String,
    isSelected: Boolean,
    layout: VacuumLayout,
) {
    val entityState = states[entityId]
    val name = entityState?.name.orEmpty().ifBlank { entityId }
    val status = entityState?.state.orEmpty()
    scope.Row(
        tap(
            Modifier.fillMaxWidth().height(units.px(layout.dp(M.ITEM_H_DP))),
            ActionRef(tileId, "vacuum_select", entityId),
        ),
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@vacuumRow
        doc.icon("robot-vacuum", layout.dp(M.ITEM_ICON_DP), doc.role(if (isSelected) Role.ACCENT else Role.INK))
        Spacer(Modifier.width(doc.units.px(layout.dp(M.ITEM_ICON_GAP_DP))))
        Column(Modifier.horizontalWeight(1f)) {
            doc.label(name, layout.sp(M.ITEM_LABEL_SP), doc.role(if (isSelected) Role.ACCENT else Role.INK))
            Spacer(Modifier.height(doc.units.px(layout.dp(M.ITEM_SUB_GAP_DP))))
            doc.label(status, layout.sp(M.ITEM_SUB_SP), doc.role(Role.INK_DIM))
        }
    }
}

private fun DocContext.vacuumDetail(selected: EntityState?, layout: VacuumLayout) {
    val pad = units.px(layout.dp(M.DETAIL_PAD_DP))
    scope.Column(
        Modifier.horizontalWeight(1f).fillMaxHeight().padding(start = pad, top = pad, end = pad, bottom = pad),
    ) {
        val doc = this@vacuumDetail
        if (selected == null) {
            doc.selectVacuumPrompt(layout)
            return@Column
        }
        doc.label(
            text = selected.name.ifBlank { selected.entityId },
            sp = layout.sp(M.DETAIL_NAME_SP),
            color = doc.role(Role.INK),
            modifier = Modifier.padding(
                start = doc.units.px(layout.dp(M.DETAIL_NAME_START_DP)),
                top = 0f,
                end = 0f,
                bottom = 0f,
            ),
        )
        Spacer(Modifier.height(doc.units.px(layout.dp(M.BATTERY_GAP_DP))))
        val battery = selected.number("battery_level")?.toInt()
        val statusLine = if (battery != null) {
            "${selected.state} · $battery%"
        } else {
            selected.state
        }
        doc.label(statusLine, layout.sp(M.DETAIL_STATUS_SP), doc.role(Role.INK_DIM))
        Spacer(Modifier.height(doc.units.px(layout.dp(M.DETAIL_GAP_DP))))
        doc.vacuumCard(selected.entityId, selected)
    }
}

private fun DocContext.selectVacuumPrompt(layout: VacuumLayout) {
    scope.Column(
        Modifier.fillMaxSize(),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcColumnVerticalPositioning.Center,
    ) {
        val doc = this@selectVacuumPrompt
        doc.icon("gesture-tap", layout.dp(M.PROMPT_ICON_DP), doc.role(Role.INK_DIM))
        Spacer(Modifier.height(doc.units.px(layout.dp(M.PROMPT_GAP_DP))))
        doc.label(
            text = doc.i18n.t("ui.vacuum_select"),
            sp = layout.sp(M.PROMPT_SP),
            color = doc.role(Role.INK_DIM),
            align = RcTextAlign.Center,
        )
    }
}
