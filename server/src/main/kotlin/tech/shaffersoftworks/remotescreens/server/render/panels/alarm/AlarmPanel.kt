@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.panels.alarm

import tech.shaffersoftworks.remotescreens.server.data.alarm.AlarmNotice
import tech.shaffersoftworks.remotescreens.server.data.alarm.AlarmUiState
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.theme.Rpi
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileBox

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcColor
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.verticalWeight
import androidx.compose.remote.creation.dsl.visibility
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.icon
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.rounded
import tech.shaffersoftworks.remotescreens.server.render.widgets.tap
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.Tile
import tech.shaffersoftworks.remotescreens.shared.VarValue
import tech.shaffersoftworks.remotescreens.server.render.panels.alarm.AlarmMetrics as M

/** An arm or disarm button under the zones: the catalogue verb it sends, its word, and its icon. */
private data class AlarmAction(val verb: String, val labelKey: String, val icon: String, val isDanger: Boolean = false)

private val ALARM_ACTIONS = listOf(
    AlarmAction("arm_home", "ui.alarm_arm_home", "shield-home"),
    AlarmAction("arm_away", "ui.alarm_arm_away", "shield-lock"),
    AlarmAction("arm_night", "ui.alarm_night_mode", "shield-moon"),
    AlarmAction("disarm", "ui.alarm_disarm", "shield-lock-open", isDanger = true),
)

private const val KEY_CLEAR = "clear"
private const val KEY_BACK = "back"
private const val BACKSPACE_GLYPH = "\u232B"
private val KEYPAD_ROWS = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf(KEY_CLEAR, "0", KEY_BACK),
)

/** The page's scale and the size of its two cards, in dp. */
private class AlarmLayout(val scale: Float, val alarmW: Float, val keypadW: Float, val cardH: Float) {
    fun dp(value: Float): Float = value * scale
    fun sp(value: Float): Float = maxOf(value * scale, M.SMALL_LABEL_MIN_SP)
}

/**
 * RPI's alarm page (`AlarmScreenV2`): the selected zone's status, its zones and the arm and disarm buttons on the
 * left, the code keypad on the right, and RPI's dialog over both when a pin or an open sensor stops an arm.
 */
fun DocContext.alarmPanel(tile: Tile, box: TileBox) {
    val entityId = tile.entityId ?: return
    val ui = data.alarmUi[tile.id] ?: AlarmUiState()
    val zones = tile.alarmZones()
    val zone = zones.selectedZone(ui.selectedZone) ?: return
    structural(zone.entityId) { zoneState ->
        listOf(zoneState?.state.orEmpty(), zoneState?.attr("arm_mode").orEmpty()).joinToString("|")
    }
    bind(entityId) { (0 until ALARM_PIN_LENGTH).map { VarValue.int(alarmDotVar(entityId, it), Vars.GONE) } }
    val scale = minOf(box.w / M.BASE_W, box.h / M.BASE_H).coerceIn(M.LAYOUT_SCALE_MIN, M.LAYOUT_SCALE_MAX)
    val panelsW = box.w - 2 * M.PAGE_PAD_H_DP * scale - M.PANEL_GAP_DP * scale
    val weights = M.ALARM_PANEL_WEIGHT + M.KEYPAD_PANEL_WEIGHT
    val layout = AlarmLayout(
        scale,
        alarmW = panelsW * M.ALARM_PANEL_WEIGHT / weights,
        keypadW = panelsW * M.KEYPAD_PANEL_WEIGHT / weights,
        cardH = box.h - 2 * M.PAGE_PAD_V_DP * scale,
    )
    scope.Box(Modifier.fillMaxSize().background(role(Role.PAGE))) {
        val doc = this@alarmPanel
        val padH = doc.units.px(layout.dp(M.PAGE_PAD_H_DP))
        val padV = doc.units.px(layout.dp(M.PAGE_PAD_V_DP))
        Row(Modifier.fillMaxSize().padding(padH, padV, padH, padV)) {
            doc.alarmCard(tile.id, zones, zone, layout)
            Spacer(Modifier.width(doc.units.px(layout.dp(M.PANEL_GAP_DP))))
            doc.keypadCard(tile.id, entityId, layout)
        }
        ui.notice?.let { notice -> doc.alarmDialog(tile.id, notice, layout) }
    }
}

private fun DocContext.alarmCard(tileId: String, zones: List<AlarmZone>, zone: AlarmZone, layout: AlarmLayout) {
    val pad = layout.dp(M.ALARM_PAD_DP)
    val rowW = layout.alarmW - 2 * pad
    scope.Column(
        Modifier.size(units.px(layout.alarmW), units.px(layout.cardH))
            .clip(rounded(layout.dp(M.CARD_CORNER_DP)))
            .background(role(Role.HEADER_DARK))
            .padding(units.px(pad)),
    ) {
        val doc = this@alarmCard
        doc.alarmStatus(states[zone.entityId], layout)
        Spacer(Modifier.height(doc.units.px(layout.dp(M.DIVIDER_TOP_GAP_DP))))
        Box(
            Modifier.fillMaxWidth()
                .height(doc.units.px(maxOf(layout.dp(M.DIVIDER_DP), M.DIVIDER_DP)))
                .background(doc.role(Role.DIVIDER)),
        )
        Spacer(Modifier.height(doc.units.px(layout.dp(M.DIVIDER_BOTTOM_GAP_DP))))
        doc.label(
            doc.i18n.t("ui.alarm_select_zone"),
            layout.sp(M.SELECT_ZONE_SP),
            doc.role(Role.INK),
            Weight.SEMI_BOLD,
        )
        Spacer(Modifier.height(doc.units.px(layout.dp(M.SELECT_ZONE_GAP_DP))))
        doc.zoneChips(tileId, zones, zone, rowW, layout)
        Spacer(Modifier.verticalWeight(1f))
        doc.alarmActions(tileId, rowW, layout)
    }
}

/** The lock in its ringed circle, the status and whether the system is secure, and the teal bar. */
private fun DocContext.alarmStatus(zoneState: EntityState?, layout: AlarmLayout) {
    val state = zoneState?.state
    scope.Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
        val doc = this@alarmStatus
        doc.statusCircle(if (state == "disarmed") "lock-open-variant-outline" else "lock", layout)
        Spacer(Modifier.width(doc.units.px(layout.dp(M.STATUS_TEXT_GAP_DP))))
        Column(Modifier.horizontalWeight(1f)) {
            doc.statusWord(state, layout)
            Spacer(Modifier.height(doc.units.px(layout.dp(M.SECURE_GAP_DP))))
            doc.label(
                doc.secureText(zoneState?.attr("arm_mode") ?: state),
                layout.sp(M.SECURE_SP),
                doc.role(Role.INK_SOFT),
            )
        }
        Box(
            Modifier.size(
                doc.units.px(layout.dp(M.ACCENT_BAR_W_DP)),
                doc.units.px(layout.dp(M.ACCENT_BAR_H_DP)),
            ).clip(doc.rounded(layout.dp(M.ACCENT_BAR_CORNER_DP))).background(doc.role(Role.ACCENT)),
        )
    }
}

/** Accent ring, then RPI's dark fill clipped to the inner radius so both edges stay round. */
private fun DocContext.statusCircle(icon: String, layout: AlarmLayout) {
    val sizeDp = layout.dp(M.STATUS_CIRCLE_DP)
    val ringDp = maxOf(layout.dp(M.STATUS_RING_DP), 1f)
    scope.Box(
        Modifier.size(units.px(sizeDp), units.px(sizeDp))
            .clip(rounded(sizeDp / 2f))
            .background(role(Role.ACCENT))
            .padding(units.px(ringDp)),
    ) {
        val doc = this@statusCircle
        Box(
            Modifier.fillMaxSize().clip(doc.rounded(sizeDp / 2f - ringDp)).background(doc.fixed(Rpi.AlarmCircle)),
            horizontal = RcHorizontalPositioning.Center,
            vertical = RcVerticalPositioning.Center,
        ) {
            doc.icon(icon, layout.dp(M.STATUS_ICON_DP), doc.fixed(Rpi.White))
        }
    }
}

/** "Arming" fades out and back in orange, as RPI's `BlinkingText`; every other status is bold ink. */
private fun DocContext.statusWord(state: String?, layout: AlarmLayout) {
    val word = when (state) {
        "disarmed" -> i18n.t("ui.alarm_disarmed")
        "armed_home" -> i18n.t("ui.alarm_armed_home")
        "armed_away" -> i18n.t("ui.alarm_armed_away")
        "armed_night" -> i18n.t("ui.alarm_armed_night")
        "arming" -> i18n.t("ui.alarm_arming")
        null, "unknown", "unavailable" -> i18n.t("ui.alarm_disarmed")
        else -> i18n.state("alarm_control_panel", state)
    }
    val sizeSp = layout.sp(M.STATUS_SP)
    if (state == "arming") {
        // Steady, not RPI's blink: alpha21's player cannot read a graphicsLayer modifier.
        val orange = (M.ARMING_ALPHA shl M.ALPHA_SHIFT) or (Role.WARN.argb and M.RGB_MASK)
        label(word, sizeSp, fixed(orange))
    } else {
        label(word, sizeSp, role(Role.INK), Weight.BOLD)
    }
}

/** RPI reads Alarmo's `arm_mode`: secure in any armed mode, blank while arming, otherwise not secure. */
private fun DocContext.secureText(armMode: String?): String = when (armMode) {
    "arming" -> ""
    "armed_home", "armed_away", "armed_night" -> i18n.t("ui.alarm_secure")
    else -> i18n.t("ui.alarm_not_secure")
}

private fun DocContext.zoneChips(
    tileId: String,
    zones: List<AlarmZone>,
    selected: AlarmZone,
    rowW: Float,
    layout: AlarmLayout,
) {
    val gap = layout.dp(M.CHIP_GAP_DP)
    val chipW = minOf(layout.dp(M.CHIP_W_DP), (rowW - gap * (zones.size - 1)) / zones.size)
    scope.Row(Modifier.fillMaxWidth()) {
        val doc = this@zoneChips
        zones.forEachIndexed { index, zone ->
            if (index > 0) Spacer(Modifier.width(doc.units.px(gap)))
            doc.zoneChip(tileId, zone, index, zone == selected, chipW, layout)
        }
    }
}

/** A Material 3 filter chip as RPI styles it: outlined grey when off, teal on a dark teal plate when chosen. */
@Suppress("LongParameterList")
private fun DocContext.zoneChip(
    tileId: String,
    zone: AlarmZone,
    index: Int,
    isSelected: Boolean,
    chipW: Float,
    layout: AlarmLayout,
) {
    val ink = role(if (isSelected) Role.ACCENT else Role.INK)
    val fill = if (isSelected) fixed(Rpi.AlarmChipOn) else role(Role.KEY)
    val corner = layout.dp(M.CHIP_CORNER_DP)
    val border = if (isSelected) 0f else M.CHIP_BORDER_DP
    val label = zone.label.ifEmpty {
        if (index == 0) i18n.t("ui.alarm_all_zones") else states[zone.entityId]?.name ?: zone.entityId
    }
    val icon = when {
        index == 0 -> "apps"
        "garage" in label.lowercase() || "garage" in zone.entityId -> "garage"
        else -> "home-outline"
    }
    scope.Box(
        Modifier.size(units.px(chipW), units.px(layout.dp(M.CHIP_H_DP)))
            .clip(rounded(corner))
            .background(if (isSelected) fill else role(Role.DIVIDER))
            .padding(units.px(border)),
    ) {
        val doc = this@zoneChip
        Row(
            doc.tap(
                Modifier.fillMaxSize()
                    .clip(doc.rounded(corner - border))
                    .background(fill)
                    .padding(
                        doc.units.px(layout.dp(M.CHIP_PAD_START_DP)),
                        0f,
                        doc.units.px(layout.dp(M.CHIP_PAD_END_DP)),
                        0f,
                    ),
                ActionRef(tileId, "alarm_zone", zone.entityId),
            ),
            vertical = RcVerticalPositioning.Center,
        ) {
            doc.icon(icon, layout.dp(M.CHIP_ICON_DP), ink)
            Spacer(Modifier.width(doc.units.px(layout.dp(M.CHIP_ICON_GAP_DP))))
            doc.label(label, layout.sp(M.CHIP_LABEL_SP), ink)
        }
    }
}

/** Arm Home, Arm Away, Night Mode and the red Disarm, sharing the row. */
private fun DocContext.alarmActions(tileId: String, rowW: Float, layout: AlarmLayout) {
    val gap = layout.dp(M.ACTION_GAP_DP)
    val buttonW = (rowW - gap * (ALARM_ACTIONS.size - 1)) / ALARM_ACTIONS.size
    val padH = units.px(layout.dp(M.ACTION_PAD_H_DP))
    scope.Row(Modifier.fillMaxWidth()) {
        val doc = this@alarmActions
        ALARM_ACTIONS.forEachIndexed { index, action ->
            if (index > 0) Spacer(Modifier.width(doc.units.px(gap)))
            val ink = if (action.isDanger) doc.fixed(Rpi.White) else doc.role(Role.INK)
            Row(
                doc.tap(
                    Modifier.size(doc.units.px(buttonW), doc.units.px(layout.dp(M.ACTION_H_DP)))
                        .clip(doc.rounded(layout.dp(M.ACTION_CORNER_DP)))
                        .background(doc.role(if (action.isDanger) Role.DANGER else Role.KEY))
                        .padding(padH, 0f, padH, 0f),
                    ActionRef(tileId, "alarm_arm", action.verb),
                ),
                horizontal = RcRowHorizontalPositioning.Center,
                vertical = RcVerticalPositioning.Center,
            ) {
                doc.icon(action.icon, layout.dp(M.ACTION_ICON_DP), ink)
                Spacer(Modifier.width(doc.units.px(layout.dp(M.ACTION_ICON_GAP_DP))))
                doc.label(doc.i18n.t(action.labelKey), layout.sp(M.ACTION_SP), ink)
            }
        }
    }
}

/** RPI's `KeypadPanel`: "Enter code", a dot per pin digit, and 1–9, Clear, 0 and backspace. */
private fun DocContext.keypadCard(tileId: String, entityId: String, layout: AlarmLayout) {
    val pad = layout.dp(M.KEYPAD_PAD_DP)
    val colGap = layout.dp(M.KEY_COL_GAP_DP)
    val rowGap = layout.dp(M.KEY_ROW_GAP_DP)
    val headerH = layout.sp(M.ENTER_CODE_SP) * M.LINE_HEIGHT_RATIO +
        layout.dp(M.ENTER_CODE_GAP_DP + M.DOT_DP + M.DOTS_GAP_DP)
    val keyW = (layout.keypadW - 2 * pad - colGap * (M.KEYPAD_COLS - 1)) / M.KEYPAD_COLS
    val keyH = minOf(
        keyW / M.KEY_ASPECT,
        (layout.cardH - 2 * pad - headerH - rowGap * (M.KEYPAD_ROWS - 1)) / M.KEYPAD_ROWS,
    ).coerceAtLeast(0f)
    scope.Column(
        Modifier.size(units.px(layout.keypadW), units.px(layout.cardH))
            .clip(rounded(layout.dp(M.CARD_CORNER_DP)))
            .background(role(Role.HEADER_DARK))
            .padding(units.px(pad)),
        horizontal = RcHorizontalPositioning.Center,
    ) {
        val doc = this@keypadCard
        doc.label(doc.i18n.t("ui.alarm_enter_code"), layout.sp(M.ENTER_CODE_SP), doc.role(Role.INK), Weight.MEDIUM)
        Spacer(Modifier.height(doc.units.px(layout.dp(M.ENTER_CODE_GAP_DP))))
        doc.pinDots(entityId, layout)
        Spacer(Modifier.height(doc.units.px(layout.dp(M.DOTS_GAP_DP))))
        KEYPAD_ROWS.forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) Spacer(Modifier.height(doc.units.px(rowGap)))
            Row {
                row.forEachIndexed { index, key ->
                    if (index > 0) Spacer(Modifier.width(doc.units.px(colGap)))
                    doc.keypadKey(tileId, key, keyW, keyH, layout)
                }
            }
        }
    }
}

/** Grey dots that light in the nav teal as digits are typed; the add-on flips each one's visibility. */
private fun DocContext.pinDots(entityId: String, layout: AlarmLayout) {
    val dotDp = layout.dp(M.DOT_DP)
    scope.Row {
        val doc = this@pinDots
        repeat(ALARM_PIN_LENGTH) { index ->
            if (index > 0) Spacer(Modifier.width(doc.units.px(layout.dp(M.DOT_GAP_DP))))
            Box(
                Modifier.size(doc.units.px(dotDp), doc.units.px(dotDp))
                    .clip(doc.rounded(dotDp / 2f))
                    .background(doc.role(Role.INK_DIM)),
            ) {
                Box(
                    Modifier.fillMaxSize()
                        .visibility(doc.int(alarmDotVar(entityId, index), Vars.GONE))
                        .clip(doc.rounded(dotDp / 2f))
                        .background(doc.role(Role.NAV_ON)),
                )
            }
        }
    }
}

private fun DocContext.keypadKey(tileId: String, key: String, keyW: Float, keyH: Float, layout: AlarmLayout) {
    val (ref, text) = when (key) {
        KEY_CLEAR -> ActionRef(tileId, "code_clear") to i18n.t("ui.clear")
        KEY_BACK -> ActionRef(tileId, "code_back") to BACKSPACE_GLYPH
        else -> ActionRef(tileId, "code_digit", key) to key
    }
    scope.Box(
        tap(
            Modifier.size(units.px(keyW), units.px(keyH))
                .clip(rounded(layout.dp(M.KEY_CORNER_DP)))
                .background(role(Role.KEY)),
            ref,
        ),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        this@keypadKey.label(text, layout.sp(M.KEY_SP), this@keypadKey.role(Role.INK), Weight.MEDIUM)
    }
}

/** RPI's Material 3 `AlertDialog`: icon, title, text, and Dismiss / Confirm, which both close it. */
private fun DocContext.alarmDialog(tileId: String, notice: AlarmNotice, layout: AlarmLayout) {
    val dismiss = ActionRef(tileId, "alarm_dismiss")
    scope.Box(
        tap(Modifier.fillMaxSize().background(role(Role.SCRIM)), dismiss),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@alarmDialog
        Column(
            doc.tap(
                Modifier.width(doc.units.px(layout.dp(M.DIALOG_W_DP)))
                    .clip(doc.rounded(layout.dp(M.DIALOG_CORNER_DP)))
                    .background(doc.role(Role.CARD)),
                ActionRef("_", "noop"),
            ).padding(doc.units.px(layout.dp(M.DIALOG_PAD_DP))),
            horizontal = RcHorizontalPositioning.Center,
        ) {
            val gap = doc.units.px(layout.dp(M.DIALOG_GAP_DP))
            doc.icon("alert-plus", layout.dp(M.DIALOG_ICON_DP), doc.role(Role.INK))
            Spacer(Modifier.height(gap))
            doc.label(notice.title, layout.sp(M.DIALOG_TITLE_SP), doc.role(Role.INK), align = RcTextAlign.Center)
            Spacer(Modifier.height(gap))
            doc.label(
                notice.message,
                layout.sp(M.DIALOG_TEXT_SP),
                doc.role(Role.INK_SOFT),
                lines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(gap))
            Row(Modifier.fillMaxWidth(), horizontal = RcRowHorizontalPositioning.End) {
                doc.dialogButton(doc.i18n.t("ui.alarm_dismiss"), dismiss, layout)
                Spacer(Modifier.width(doc.units.px(layout.dp(M.DIALOG_BUTTON_GAP_DP))))
                doc.dialogButton(doc.i18n.t("ui.alarm_confirm"), dismiss, layout)
            }
        }
    }
}

private fun DocContext.dialogButton(text: String, ref: ActionRef, layout: AlarmLayout) {
    val padH = units.px(layout.dp(M.DIALOG_GAP_DP))
    scope.Box(
        tap(
            Modifier.height(units.px(layout.dp(M.DIALOG_BUTTON_H_DP))).padding(padH, 0f, padH, 0f),
            ref,
        ),
        vertical = RcVerticalPositioning.Center,
    ) {
        val ink: RcColor = this@dialogButton.role(Role.ACCENT)
        this@dialogButton.label(text, layout.sp(M.DIALOG_BUTTON_SP), ink, Weight.MEDIUM)
    }
}
