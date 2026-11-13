@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.panels.blinds

import tech.shaffersoftworks.remotescreens.server.data.blinds.BlindsUiState
import tech.shaffersoftworks.remotescreens.server.data.lights.LightsRoom
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.keptVerticalScroll
import tech.shaffersoftworks.remotescreens.server.render.panels.lights.LightsLayout
import tech.shaffersoftworks.remotescreens.server.render.panels.lights.deviceRow
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.theme.Rpi
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileBox
import tech.shaffersoftworks.remotescreens.server.render.widgets.icon
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.tap
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.Tile
import tech.shaffersoftworks.remotescreens.shared.VarValue
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcColumnVerticalPositioning
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcPaintStyle
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxHeight
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.requiredHeightIn
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.width
import androidx.compose.remote.creation.modifiers.RectShape
import tech.shaffersoftworks.remotescreens.server.render.panels.lights.LightsMetrics as M

private const val KEY_BLINDS_OPEN_PREFIX = "blindsOpen."
private const val ALPHA_SHIFT = 24
private const val RGB_MASK = 0xFFFFFF

/**
 * Full blinds page: every Home Assistant area that has a cover on the left, and the covers of the chosen
 * room on the right (RPI BlindDeviceItem rows: tilt slider and open/close switch).
 */
fun DocContext.blindsPanel(tile: Tile, box: TileBox) {
    val ui = data.blindsUi[tile.id] ?: BlindsUiState()
    val rooms = data.blindsRooms
    val room = rooms.firstOrNull { it.areaId == ui.selectedAreaId }
    val layout = LightsLayout(pageScale(box), box.w, box.h)
    scope.Row(Modifier.fillMaxSize().background(role(Role.PAGE))) {
        val doc = this@blindsPanel
        doc.roomsColumn(tileId = tile.id, rooms = rooms, selected = room, layout = layout)
        doc.devicesColumn(tile.id, room, layout)
    }
}

private fun pageScale(box: TileBox): Float =
    minOf(box.w / M.BASE_W, box.h / M.BASE_H).coerceIn(M.LAYOUT_SCALE_MIN, M.LAYOUT_SCALE_MAX)

private fun DocContext.roomsColumn(
    tileId: String,
    rooms: List<LightsRoom>,
    selected: LightsRoom?,
    layout: LightsLayout,
) {
    val headerH = layout.roomsHeaderHeight()
    scope.Box(Modifier.width(units.px(layout.roomsW)).fillMaxHeight().background(role(Role.STRIP))) {
        val doc = this@roomsColumn
        if (rooms.isEmpty()) {
            doc.roomsSpinner(layout)
        } else {
            Column(doc.scrollingList("blinds.$tileId.rooms", layout.pageH)) {
                Spacer(Modifier.height(doc.units.px(headerH)))
                rooms.forEachIndexed { index, room ->
                    if (index > 0) Spacer(Modifier.height(doc.units.px(layout.dp(M.ROOM_GAP_DP))))
                    doc.roomRow(tileId = tileId, room = room, isSelected = room == selected, layout = layout)
                }
            }
        }
        doc.roomsHeader(layout, layout.roomsPlateHeight())
    }
}

private fun DocContext.roomsHeader(layout: LightsLayout, headerH: Float) {
    scope.Column(
        Modifier.fillMaxWidth().height(units.px(headerH)).background(role(Role.STRIP)),
    ) {
        val doc = this@roomsHeader
        Spacer(Modifier.height(doc.units.px(layout.dp(M.COLUMN_TOP_DP))))
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
            text = doc.i18n.t("ui.blinds_title"),
            sp = layout.sp(M.TITLE_SP),
            color = doc.role(Role.INK),
            modifier = Modifier.padding(
                start = doc.units.px(layout.dp(M.TITLE_START_DP)),
                top = 0f,
                end = 0f,
                bottom = 0f,
            )
                .height(doc.units.px(layout.line(M.TITLE_SP))),
        )
    }
}

private fun DocContext.roomRow(tileId: String, room: LightsRoom, isSelected: Boolean, layout: LightsLayout) {
    val countVar = Vars.tile(tileId, "$KEY_BLINDS_OPEN_PREFIX${room.areaId}")
    bindBlindsOpenCount(countVar, room)
    scope.Column(
        tap(
            Modifier.fillMaxWidth().height(units.px(layout.dp(M.ROOM_H_DP)))
                .padding(
                    start = units.px(layout.dp(M.ROOM_PAD_START_DP)),
                    top = 0f,
                    end = units.px(layout.dp(M.ROOM_PAD_END_DP)),
                    bottom = 0f,
                ),
            ActionRef(tileId, "blinds_room", room.areaId),
        ),
        vertical = RcColumnVerticalPositioning.Center,
    ) {
        val doc = this@roomRow
        doc.label(room.name, layout.sp(M.ROOM_SP), doc.role(if (isSelected) Role.ACCENT else Role.INK))
        Spacer(Modifier.height(doc.units.px(layout.dp(M.ROOM_SUB_GAP_DP))))
        doc.label(doc.text(countVar), layout.sp(M.ROOM_SUB_SP), doc.role(Role.INK_DIM))
    }
}

private fun DocContext.bindBlindsOpenCount(countVar: String, room: LightsRoom) {
    val coverIds = room.entityIds.filter { it.startsWith("cover.") }
    for (entityId in coverIds) bind(entityId) { emptyList() }
    bind(null) { current ->
        val count = coverIds.count { current[it]?.state == "open" }
        val words = when (count) {
            0 -> i18n.t("ui.blinds_all_closed")
            1 -> i18n.t("ui.blinds_one_open")
            else -> i18n.t("ui.blinds_many_open", "count" to count)
        }
        listOf(VarValue.string(countVar, words))
    }
}

private fun DocContext.roomsSpinner(layout: LightsLayout) {
    val scrim = (M.SPINNER_SCRIM_ALPHA shl ALPHA_SHIFT) or (Rpi.Black and RGB_MASK)
    scope.Column(
        Modifier.fillMaxWidth().fillMaxHeight().background(fixed(scrim)),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcColumnVerticalPositioning.Center,
    ) {
        val doc = this@roomsSpinner
        val sizePx = doc.units.px(layout.dp(M.SPINNER_DP))
        val stroke = doc.units.px(layout.dp(M.SPINNER_STROKE_DP))
        Canvas(Modifier.size(sizePx, sizePx)) {
            val inset = stroke / 2f
            paint {
                clearShader()
                color(doc.fixed(Rpi.LightsSpinnerTrack))
                alpha(1f)
                strokeWidth(stroke)
                style(RcPaintStyle.Stroke)
            }
            drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - inset)
            paint { color(doc.fixed(Rpi.LightsSpinner)) }
            drawArc(
                left = inset,
                top = inset,
                right = sizePx - inset,
                bottom = sizePx - inset,
                startAngle = M.SPINNER_START_DEG,
                sweepAngle = M.SPINNER_SWEEP_DEG,
            )
        }
    }
}

private fun DocContext.devicesColumn(tileId: String, room: LightsRoom?, layout: LightsLayout) {
    val pad = units.px(layout.dp(M.DEVICES_PAD_DP))
    scope.Column(
        Modifier.horizontalWeight(1f).fillMaxHeight()
            .padding(start = pad, top = units.px(layout.dp(M.COLUMN_TOP_DP)), end = pad, bottom = pad),
    ) {
        val doc = this@devicesColumn
        if (room == null) {
            doc.selectRoomPrompt(layout)
            return@Column
        }
        val headerH = layout.devicesHeaderHeight()
        val devices = room.entityIds.filter { it in doc.states }
        Box(Modifier.fillMaxWidth().height(doc.units.px(layout.devicesBodyHeight()))) {
            Column(doc.scrollingList("blinds.$tileId.devices", layout.devicesBodyHeight())) {
                Spacer(Modifier.height(doc.units.px(headerH)))
                devices.forEachIndexed { index, entityId ->
                    if (index > 0) Spacer(Modifier.height(doc.units.px(layout.dp(M.DEVICE_GAP_DP))))
                    doc.deviceRow(tileId, entityId, layout)
                    Box(
                        Modifier.fillMaxWidth()
                            .height(doc.units.px(maxOf(layout.dp(M.DIVIDER_DP), M.DIVIDER_DP)))
                            .background(doc.role(Role.ACCENT_DARK)),
                    )
                }
            }
            Column(
                Modifier.fillMaxWidth()
                    .height(doc.units.px(layout.devicesPlateHeight()))
                    .background(doc.role(Role.PAGE)),
            ) {
                doc.label(
                    text = room.name,
                    sp = layout.sp(M.ROOM_TITLE_SP),
                    color = doc.role(Role.INK),
                    modifier = Modifier.padding(
                        start = doc.units.px(layout.dp(M.ROOM_TITLE_START_DP)),
                        top = 0f,
                        end = 0f,
                        bottom = 0f,
                    )
                        .height(doc.units.px(layout.line(M.ROOM_TITLE_SP))),
                )
            }
        }
    }
}

private fun DocContext.scrollingList(name: String, heightDp: Float): Modifier {
    val viewport = units.px(heightDp)
    return Modifier.fillMaxWidth()
        .height(viewport)
        .requiredHeightIn(max = viewport)
        .clip(RectShape(0f, 0f, 0f, 0f))
        .keptVerticalScroll(name)
}

private fun DocContext.selectRoomPrompt(layout: LightsLayout) {
    scope.Column(
        Modifier.fillMaxSize(),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcColumnVerticalPositioning.Center,
    ) {
        val doc = this@selectRoomPrompt
        doc.icon("gesture-tap", layout.dp(M.PROMPT_ICON_DP), doc.role(Role.INK_DIM))
        Spacer(Modifier.height(doc.units.px(layout.dp(M.PROMPT_GAP_DP))))
        doc.label(
            text = doc.i18n.t("ui.blinds_select_room"),
            sp = layout.sp(M.PROMPT_SP),
            color = doc.role(Role.INK_DIM),
            align = RcTextAlign.Center,
        )
    }
}
