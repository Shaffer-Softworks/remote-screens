@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.panels.lights

import tech.shaffersoftworks.remotescreens.server.data.lights.LightsRoom
import tech.shaffersoftworks.remotescreens.server.data.lights.LightsUiState
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Vars
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
import tech.shaffersoftworks.remotescreens.server.render.panels.lights.LightsMetrics as M

private const val KEY_LIGHTS_ON_PREFIX = "lightsOn."
private const val ALPHA_SHIFT = 24
private const val RGB_MASK = 0xFFFFFF

/** The page's scale and the width of its device column, in dp. */
internal class LightsLayout(val scale: Float, val pageW: Float, val pageH: Float) {
    fun dp(value: Float): Float = value * scale
    fun sp(value: Float): Float = maxOf(value * scale, M.SMALL_LABEL_MIN_SP)

    /** The room list's width, and what RPI's padded device column has left of the page. */
    val roomsW: Float get() = dp(M.ROOMS_W_DP)
    val devicesW: Float get() = pageW - roomsW - 2 * dp(M.DEVICES_PAD_DP)
}

/**
 * RPI's lights page (`LightsScreen`): every Home Assistant area in a list on the left, and the lights, switches and
 * covers of the chosen one on the right. A light that can do more than switch opens RPI's light dialog over both.
 */
fun DocContext.lightsPanel(tile: Tile, box: TileBox) {
    val ui = data.lightsUi[tile.id] ?: LightsUiState()
    val rooms = data.lightsRooms
    val room = rooms.firstOrNull { it.areaId == ui.selectedAreaId }
    val layout = LightsLayout(pageScale(box), box.w, box.h)
    scope.Row(Modifier.fillMaxSize().background(role(Role.PAGE))) {
        val doc = this@lightsPanel
        doc.roomsColumn(tile.id, rooms, room, layout)
        doc.devicesColumn(tile.id, room, layout)
    }
}

/**
 * RPI's light dialog over the whole screen, bars included, as Android draws a dialog window. The page draws it after
 * its nav bar, so nothing the page draws covers it; [screenW] and [screenH] are the screen in dp.
 */
fun DocContext.lightsDialogLayer(tile: Tile, box: TileBox, screenW: Float, screenH: Float) {
    val ui = data.lightsUi[tile.id] ?: return
    val room = data.lightsRooms.firstOrNull { it.areaId == ui.selectedAreaId }
    val detailEntityId = ui.detailEntityId?.takeIf { entityId ->
        room != null && entityId in room.entityIds && states[entityId]?.supportsLightDetail() == true
    }
    if (room != null && detailEntityId != null) {
        lightDetailDialog(tile.id, detailEntityId, room.name, ui, LightsLayout(pageScale(box), screenW, screenH))
    }
}

private fun pageScale(box: TileBox): Float =
    minOf(box.w / M.BASE_W, box.h / M.BASE_H).coerceIn(M.LAYOUT_SCALE_MIN, M.LAYOUT_SCALE_MAX)

/** The grey list: RPI's teal bar, "LIGHTING CONTROL", and a row per area, or a spinner until there are any. */
private fun DocContext.roomsColumn(
    tileId: String,
    rooms: List<LightsRoom>,
    selected: LightsRoom?,
    layout: LightsLayout,
) {
    scope.Column(
        Modifier.width(units.px(layout.roomsW)).fillMaxHeight().background(role(Role.STRIP))
            .padding(0f, units.px(layout.dp(M.COLUMN_TOP_DP)), 0f, 0f),
    ) {
        val doc = this@roomsColumn
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
            doc.i18n.t("ui.lights_title"),
            layout.sp(M.TITLE_SP),
            doc.role(Role.INK),
            modifier = Modifier.padding(doc.units.px(layout.dp(M.TITLE_START_DP)), 0f, 0f, 0f),
        )
        if (rooms.isEmpty()) {
            doc.roomsSpinner(layout)
        } else {
            Column(Modifier.fillMaxWidth().verticalWeight(1f).verticalScroll()) {
                rooms.forEachIndexed { index, room ->
                    if (index > 0) Spacer(Modifier.height(doc.units.px(layout.dp(M.ROOM_GAP_DP))))
                    doc.roomRow(tileId, room, room == selected, layout)
                }
            }
        }
    }
}

/** RPI's `LightsRoomItemView`: the name, teal when chosen, over how many of the room's lights are on. */
private fun DocContext.roomRow(tileId: String, room: LightsRoom, isSelected: Boolean, layout: LightsLayout) {
    val countVar = Vars.tile(tileId, "$KEY_LIGHTS_ON_PREFIX${room.areaId}")
    bindLightsOnCount(countVar, room)
    scope.Column(
        tap(
            Modifier.fillMaxWidth().height(units.px(layout.dp(M.ROOM_H_DP)))
                .padding(units.px(layout.dp(M.ROOM_PAD_START_DP)), 0f, units.px(layout.dp(M.ROOM_PAD_END_DP)), 0f),
            ActionRef(tileId, "lights_room", room.areaId),
        ),
        vertical = RcColumnVerticalPositioning.Center,
    ) {
        val doc = this@roomRow
        doc.label(room.name, layout.sp(M.ROOM_SP), doc.role(if (isSelected) Role.ACCENT else Role.INK))
        Spacer(Modifier.height(doc.units.px(layout.dp(M.ROOM_SUB_GAP_DP))))
        doc.label(doc.text(countVar), layout.sp(M.ROOM_SUB_SP), doc.role(Role.INK_DIM))
    }
}

/**
 * The count RPI's `getNumberOfLightsOnByRoom` template gives (the area's `light.*` entities that are on), worded as
 * its `LightsScreen`. The room's lights are watched so the line follows them without a rebuild.
 */
private fun DocContext.bindLightsOnCount(countVar: String, room: LightsRoom) {
    val lightIds = room.entityIds.filter { it.startsWith("light.") }
    for (entityId in lightIds) bind(entityId) { emptyList() }
    bind(null) { current ->
        val count = lightIds.count { current[it]?.state == "on" }
        val words = when (count) {
            0 -> i18n.t("ui.lights_all_off")
            1 -> i18n.t("ui.lights_one_on")
            else -> i18n.t("ui.lights_many_on", "count" to count)
        }
        listOf(VarValue.string(countVar, words))
    }
}

/** RPI's half-black cover with Material's progress ring while the areas load. */
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
            drawArc(inset, inset, sizePx - inset, sizePx - inset, M.SPINNER_START_DEG, M.SPINNER_SWEEP_DEG)
        }
    }
}

/** The chosen room's name and its devices, each with a teal line under it; a prompt until a room is chosen. */
private fun DocContext.devicesColumn(tileId: String, room: LightsRoom?, layout: LightsLayout) {
    val pad = units.px(layout.dp(M.DEVICES_PAD_DP))
    scope.Column(
        Modifier.horizontalWeight(1f).fillMaxHeight()
            .padding(pad, units.px(layout.dp(M.COLUMN_TOP_DP)), pad, pad),
    ) {
        val doc = this@devicesColumn
        if (room == null) {
            doc.selectRoomPrompt(layout)
            return@Column
        }
        doc.label(
            room.name,
            layout.sp(M.ROOM_TITLE_SP),
            doc.role(Role.INK),
            modifier = Modifier.padding(doc.units.px(layout.dp(M.ROOM_TITLE_START_DP)), 0f, 0f, 0f),
        )
        Spacer(Modifier.height(doc.units.px(layout.dp(M.TITLE_GAP_DP))))
        val devices = room.entityIds.filter { it in doc.states }
        Column(Modifier.fillMaxWidth().verticalWeight(1f).verticalScroll()) {
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
    }
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
            doc.i18n.t("ui.lights_select_room"),
            layout.sp(M.PROMPT_SP),
            doc.role(Role.INK_DIM),
            align = RcTextAlign.Center,
        )
    }
}
