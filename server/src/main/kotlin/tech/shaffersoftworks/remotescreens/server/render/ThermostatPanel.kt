@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcColumnVerticalPositioning
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcPaintStyle
import androidx.compose.remote.creation.dsl.RcStrokeCap
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
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.Tile
import tech.shaffersoftworks.remotescreens.shared.VarValue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin


private const val DIAL_HEIGHT_SHARE = 0.55f
private const val DIAL_TINT_ALPHA = 0.3f
private const val ACCENT_BAR_WIDTH_DP = 100f
private const val DIAL_BASE_DP = 360f
private const val TARGET_SIZE_DIV = 7.5f
private const val ACCENT_BAR_HEIGHT_DP = 10f
private const val KNOB_DP = 12f
private const val ADJUST_CAPTION_SP = 15f
private const val FAN_LABEL_SP = 16f
private const val ACTION_SIZE_DIV = 18f
private const val PANEL_PAD_DP = 20f
private const val MODE_ICON_DP = 22f
private const val DIAL_SIDE_GAP_DP = 24f
private const val READING_CAPTION_SP = 25f
private const val TOP_PAD_DP = 28f
private const val MODE_ROW_MARGIN_DP = 40f
private const val SEG_HEIGHT_DP = 50f
private const val READING_VALUE_SP = 70f
private const val FAN_BUTTON_WIDTH_DP = 90f
private const val KNOB_RING_DP = 3f
private const val FAN_CORNER_DP = 8f

/** The size the RPI thermostat page was drawn for; the page scales from it. */
private const val BASE_W = 1280f
private const val BASE_H = 650f

/** Tick marks around the dial, as many as the RPI dial has steps. */
private const val TICKS = 100

/** Small labels stop shrinking here so they stay readable on 7" panels. */
private fun small(base: Float, layoutScale: Float) = maxOf(base * layoutScale, KNOB_DP)

/** The modes in the order the RPI page lists them (Auto, Heat, Cool, Off); others follow. */
private val MODE_ORDER = listOf("auto", "heat_cool", "heat", "cool", "off")

/**
 * The RPI thermostat page: the current temperature and humidity either side of a dial that shows the target, in
 * orange while heating and blue while cooling, the fan modes at the top right and a row of modes along the bottom.
 * Remote Compose cannot report a
 * drag, so the target is set with the keys beside the dial.
 */
fun DocContext.thermostatPanel(tile: Tile, box: TileBox) {
    val id = tile.entityId ?: return
    val entityState = states[id]
    val step = entityState?.number("target_temp_step") ?: if (homeAssistantConfig.temperatureUnit.contains("F")) 1.0 else 0.5
    val modes = entityState?.list("hvac_modes").orEmpty().sortedBy { MODE_ORDER.indexOf(it).let { i -> if (i < 0) MODE_ORDER.size else i } }.take(5)
    val humid = entityState?.number("current_humidity") != null
    val fans = entityState?.list("fan_modes").orEmpty().sortedBy { if (it == "auto") 0 else 1 }.take(4)
    structural(id) { s ->
        listOf(s?.list("hvac_modes")?.joinToString() ?: "", s?.list("fan_modes")?.joinToString() ?: "", (s?.number("current_humidity") != null).toString()).joinToString("|")
    }
    predict(ActionRef(tile.id, "temperature", "+$step"), id)
    predict(ActionRef(tile.id, "temperature", "-$step"), id)
    for (mode in modes) predict(ActionRef(tile.id, "mode", mode), id)
    for (mode in fans) predict(ActionRef(tile.id, "fan", mode), id)
    bindThermostat(tile.id, id, modes, fans)

    val layoutScale = minOf(box.w / BASE_W, box.h / BASE_H).coerceIn(0.4f, 1.5f)
    val ink = role(Role.INK)
    val accent = role(Role.ACCENT_DARK)

    scope.Column(Modifier.fillMaxSize().background(role(Role.PAGE))) {
        val doc = this@thermostatPanel
        Row(Modifier.fillMaxWidth().padding(doc.units.px(PANEL_PAD_DP * layoutScale), doc.units.px(TOP_PAD_DP * layoutScale), doc.units.px(PANEL_PAD_DP * layoutScale), 0f), vertical = RcVerticalPositioning.Top) {
            Column(Modifier.horizontalWeight(1f)) {
                Box(Modifier.size(doc.units.px(ACCENT_BAR_WIDTH_DP * layoutScale), doc.units.px(ACCENT_BAR_HEIGHT_DP * layoutScale)).background(accent))
                Spacer(Modifier.height(doc.units.px(ACCENT_BAR_HEIGHT_DP * layoutScale)))
                doc.label(doc.i18n.t("ui.adjust_temperature").uppercase(doc.i18n.locale), small(ADJUST_CAPTION_SP, layoutScale), ink)
            }
            if (fans.isNotEmpty()) doc.fanModes(tile.id, fans, layoutScale)
        }
        Row(Modifier.fillMaxWidth().verticalWeight(1f), vertical = RcVerticalPositioning.Center) {
            doc.reading(doc.i18n.t("ui.current_temp"), Vars.tile(tile.id, "current"), layoutScale, Modifier.horizontalWeight(1f))
            doc.key(ActionRef(tile.id, "temperature", "-$step"), icon = "minus", heightDp = 64f * layoutScale, widthDp = 64f * layoutScale, round = true)
            Spacer(Modifier.width(doc.units.px(DIAL_SIDE_GAP_DP * layoutScale)))
            doc.dial(tile.id, id, minOf(DIAL_BASE_DP * layoutScale, box.h * DIAL_HEIGHT_SHARE))
            Spacer(Modifier.width(doc.units.px(DIAL_SIDE_GAP_DP * layoutScale)))
            doc.key(ActionRef(tile.id, "temperature", "+$step"), icon = "plus", heightDp = 64f * layoutScale, widthDp = 64f * layoutScale, round = true)
            if (humid) doc.reading(doc.i18n.t("ui.current_humidity"), Vars.tile(tile.id, "humidity"), layoutScale, Modifier.horizontalWeight(1f))
            else Spacer(Modifier.horizontalWeight(1f))
        }
        if (modes.isNotEmpty()) {
            val segW = minOf(200f * layoutScale, (box.w - MODE_ROW_MARGIN_DP) / modes.size)
            val segH = SEG_HEIGHT_DP * layoutScale
            Box(Modifier.fillMaxWidth().padding(0f, 0f, 0f, doc.units.px(PANEL_PAD_DP * layoutScale)), horizontal = RcHorizontalPositioning.Center) {
                Row(Modifier.clip(doc.rounded(segH / 2f)).background(doc.role(Role.INK_DIM)).padding(doc.units.px(1f))) {
                    modes.forEachIndexed { i, mode ->
                        if (i > 0) Spacer(Modifier.width(doc.units.px(1f)))
                        val bg = doc.color(Vars.option(tile.id, "seg", i, Vars.KEY_BG))
                        Row(doc.tap(Modifier.size(doc.units.px(segW), doc.units.px(segH - 2f)).background(bg), ActionRef(tile.id, "mode", mode)),
                            horizontal = androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
                            doc.icon(MODE_ICONS[mode] ?: "thermostat", MODE_ICON_DP * layoutScale, ink)
                            Spacer(Modifier.width(doc.units.px(FAN_CORNER_DP * layoutScale)))
                            doc.label(doc.i18n.state("climate", mode), small(PANEL_PAD_DP, layoutScale), ink)
                        }
                    }
                }
            }
        }
    }
}

/** "Fan" and a button per fan mode (Auto, On, …), the current one white with dark words as the RPI page drew it. */
private fun DocContext.fanModes(tileId: String, fans: List<String>, layoutScale: Float) {
    scope.Row(vertical = RcVerticalPositioning.Center) {
        val doc = this@fanModes
        doc.label(doc.i18n.t("ui.fan"), small(READING_CAPTION_SP, layoutScale), doc.role(Role.INK))
        fans.forEachIndexed { i, mode ->
            Spacer(Modifier.width(doc.units.px(ACCENT_BAR_HEIGHT_DP * layoutScale)))
            Box(doc.tap(Modifier.size(doc.units.px(FAN_BUTTON_WIDTH_DP * layoutScale), doc.units.px(SEG_HEIGHT_DP * layoutScale)).clip(doc.rounded(FAN_CORNER_DP * layoutScale)).background(doc.color(Vars.option(tileId, "fan", i, Vars.KEY_BG))), ActionRef(tileId, "fan", mode)),
                horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
                doc.label(doc.fanWord(mode), small(FAN_LABEL_SP, layoutScale), doc.color(Vars.option(tileId, "fan", i, Vars.KEY_INK)), Weight.MEDIUM)
            }
        }
    }
}

private fun DocContext.fanWord(mode: String): String =
    i18n.t("state.$mode").takeUnless { it == "state.$mode" } ?: mode.replace('_', ' ').replaceFirstChar { it.titlecase(i18n.locale) }

/** "CURRENT TEMP" in teal over the value, large. */
private fun DocContext.reading(caption: String, varName: String, layoutScale: Float, modifier: Modifier) {
    scope.Column(modifier, horizontal = RcHorizontalPositioning.Center) {
        val doc = this@reading
        doc.label(caption.uppercase(doc.i18n.locale), small(READING_CAPTION_SP, layoutScale), doc.role(Role.ACCENT_DARK), align = RcTextAlign.Center)
        Spacer(Modifier.height(doc.units.px(PANEL_PAD_DP * layoutScale)))
        doc.label(doc.text(varName), READING_VALUE_SP * layoutScale, doc.role(Role.INK), Weight.LIGHT, align = RcTextAlign.Center)
    }
}

/**
 * The RPI dial: a tinted disc in a white ring, an arc from the bottom round to the target with a knob at its end,
 * and tick marks outside; what the thermostat is doing and the target in the middle.
 */
private fun DocContext.dial(tileId: String, entityId: String, diameterDp: Float) {
    val ring = diameterDp / READING_CAPTION_SP
    val sizeDp = diameterDp + 2 * (ring + FAN_CORNER_DP)
    val px = units.px(sizeDp)
    val cx = px / 2f
    val r = units.px(diameterDp) / 2f
    val stroke = units.px(ring)
    val tint = color(Vars.tile(tileId, "dial"))
    val sweep = float(Vars.tile(tileId, "sweep"))
    val ink = role(Role.INK)
    scope.Box(Modifier.size(px, px), horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
        val doc = this@dial
        Canvas(Modifier.size(px, px)) {
            paint { color(tint); alpha(DIAL_TINT_ALPHA); style(RcPaintStyle.Fill) }
            drawCircle(cx, cx, r)
            paint { color(ink); alpha(1f); strokeWidth(stroke); style(RcPaintStyle.Stroke) }
            drawCircle(cx, cx, r)
            paint { color(tint); alpha(DIAL_TINT_ALPHA); strokeWidth(doc.units.px(1f)) }
            val inner = r + stroke / 2f + doc.units.px(4f)
            for (i in 0 until TICKS) {
                val a = i * 2 * PI / TICKS + PI / 2
                val (ca, sa) = cos(a).toFloat() to sin(a).toFloat()
                drawLine(cx + inner * ca, cx + inner * sa, cx + (inner + stroke) * ca, cx + (inner + stroke) * sa)
            }
            paint { color(tint); alpha(1f); strokeWidth(stroke); strokeCap(RcStrokeCap.Round); style(RcPaintStyle.Stroke) }
            drawArc((cx - r).rf, (cx - r).rf, (cx + r).rf, (cx + r).rf, 90f.rf, sweep)
            save {
                rotate(sweep, cx.rf, cx.rf)
                val knob = doc.units.px(KNOB_DP)
                paint { color(tint); style(RcPaintStyle.Fill) }
                drawCircle(cx, cx + r, knob)
                paint { color(ink); strokeWidth(doc.units.px(KNOB_RING_DP)); style(RcPaintStyle.Stroke) }
                drawCircle(cx, cx + r, knob)
            }
        }
        Column(Modifier.size(px, px), horizontal = RcHorizontalPositioning.Center, vertical = RcColumnVerticalPositioning.Center) {
            doc.label(doc.text(Vars.tile(tileId, "action")), diameterDp / ACTION_SIZE_DIV, ink, align = RcTextAlign.Center)
            Spacer(Modifier.height(doc.units.px(diameterDp / MODE_ROW_MARGIN_DP)))
            doc.label(doc.text(Vars.entity(entityId, Vars.KEY_VALUE)), diameterDp / TARGET_SIZE_DIV, ink, Weight.LIGHT, align = RcTextAlign.Center)
        }
    }
}

/** The current readings, the dial's colour and sweep, the action in its middle, and which mode is lit. */
private fun DocContext.bindThermostat(tileId: String, entityId: String, modes: List<String>, fans: List<String>) {
    bind(entityId) { states ->
        val entityState = states[entityId]
        val unit = if (homeAssistantConfig.temperatureUnit.contains("F")) 0 else 1
        val action = entityState?.attr("hvac_action")
        val tint = when (action) {
            "heating", "preheating" -> Role.WARN
            "cooling" -> Role.BLUE
            else -> Role.ACCENT_DARK
        }
        val minValue = entityState?.number("min_temp") ?: 7.0
        val maxValue = entityState?.number("max_temp") ?: 35.0
        val target = entityState?.number("temperature")
        val sweep = target?.let { (((it - minValue) / (maxValue - minValue).coerceAtLeast(1.0)).coerceIn(0.0, 1.0) * 360.0).toFloat() } ?: 0f
        listOf(
            VarValue.string(Vars.tile(tileId, "current"), entityState?.number("current_temperature")?.let { i18n.number(it, unit) + "°" } ?: "–"),
            VarValue.string(Vars.tile(tileId, "humidity"), entityState?.number("current_humidity")?.let { i18n.percent(it.toInt()) } ?: "–"),
            VarValue.string(Vars.tile(tileId, "action"), action?.let { actionWord(it) } ?: i18n.state("climate", entityState?.state ?: "off")),
            VarValue.color(Vars.tile(tileId, "dial"), tint.argb),
            VarValue.float(Vars.tile(tileId, "sweep"), sweep),
        ) + modes.mapIndexed { i, mode ->
            VarValue.color(Vars.option(tileId, "seg", i, Vars.KEY_BG), (if (entityState?.state == mode) Role.ACCENT_DARK else Role.PAGE).argb)
        } + fans.flatMapIndexed { i, mode ->
            val on = entityState?.attr("fan_mode") == mode
            listOf(
                VarValue.color(Vars.option(tileId, "fan", i, Vars.KEY_BG), (if (on) Role.INK else Role.KEY).argb),
                VarValue.color(Vars.option(tileId, "fan", i, Vars.KEY_INK), (if (on) Role.INK_DARK else Role.INK).argb),
            )
        }
    }
}

private fun DocContext.actionWord(action: String): String =
    i18n.t("state.$action").takeUnless { it == "state.$action" } ?: action.replace('_', ' ').replaceFirstChar { it.titlecase(i18n.locale) }
