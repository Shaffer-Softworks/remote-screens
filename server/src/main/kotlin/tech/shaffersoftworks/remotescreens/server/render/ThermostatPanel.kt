@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcBorderShape
import androidx.compose.remote.creation.dsl.RcColumnVerticalPositioning
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcPaintStyle
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcStrokeCap
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.border
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.verticalWeight
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.Tile
import tech.shaffersoftworks.remotescreens.shared.VarValue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin


private const val DIAL_HEIGHT_SHARE = 0.55f
/** Inner fill alpha at the dial centre (RPI `primaryColor.copy(0.45f)`). */
private const val DIAL_INNER_ALPHA = 0.45f
/** Outer fill alpha at the dial rim (RPI `secondaryColor.copy(0.15f)`). */
private const val DIAL_OUTER_ALPHA = 0.15f
/** Tick marks outside the ring (RPI unlit tick alpha). */
private const val DIAL_TICK_ALPHA = 0.3f
/** Accent bar width on the RPI page (`HorizontalDivider` 120.dp). */
private const val ACCENT_BAR_WIDTH_DP = 120f
/** Dial diameter the RPI `CustomCircularProgressIndicator` is drawn at (500.dp). */
private const val DIAL_BASE_DP = 500f
private const val ACCENT_BAR_HEIGHT_DP = 10f
/** Knob radius as a share of the dial radius (RPI 20f on a 250f radius). */
private const val KNOB_RADIUS_SHARE = 20f / 250f
/** Knob ring stroke as a share of the dial radius (RPI 5f on a 250f radius). */
private const val KNOB_RING_SHARE = 5f / 250f
/** Ring thickness as a share of the dial diameter (RPI `width / 25`). */
private const val RING_THICKNESS_SHARE = 1f / 25f
private const val ADJUST_CAPTION_SP = 15f
private const val ACTION_LABEL_SP = 20f
private const val TARGET_LABEL_SP = 38f
private const val PANEL_PAD_DP = 20f
private const val MODE_ICON_DP = 22f
private const val MODE_LABEL_SP = 20f
private const val MODE_ICON_GAP_DP = 8f
private const val DIAL_SIDE_GAP_DP = 24f
private const val READING_CAPTION_SP = 25f
private const val READING_GAP_DP = 20f
private const val TOP_PAD_DP = 28f
private const val HEADER_GAP_DP = 10f
private const val MODE_ROW_MARGIN_DP = 40f
private const val MODE_ROW_GAP_DP = 12f
private const val SEG_HEIGHT_DP = 48f
private const val SEG_WIDTH_DP = 160f
private const val MODE_CORNER_DP = 10f
private const val MODE_BORDER_DP = 2f
private const val FAN_BUTTON_WIDTH_DP = 140f
private const val FAN_CAPTION_SP = 12f
private const val FAN_VALUE_SP = 16f
private const val FAN_ICON_DP = 28f
private const val FAN_PAD_DP = 12f
private const val READING_VALUE_SP = 70f
/** +/− key size; Remote Compose cannot drag the dial as the RPI page does. */
private const val ADJUST_KEY_DP = 64f
private const val SEG_EDGE_DP = 1f
/** RPI tick gap outside the ring (`gap = 15f` on a 500.dp dial). */
private const val TICK_GAP_SHARE = 15f / 500f
/** Both stops of the dial fill gradient are remote colour ids. */
private const val DIAL_GRADIENT_COLOR_MASK = 0b11
private const val OPAQUE_ALPHA = 0xFF
private const val ALPHA_SHIFT = 24
private const val RGB_MASK = 0x00FFFFFF

/** The size the RPI thermostat page was drawn for; the page scales from it. */
private const val BASE_W = 1280f
private const val BASE_H = 650f

/** Tick marks around the dial (RPI default `maxValue - minValue` with 0…100). */
private const val TICKS = 100
/** Floor for scaled captions so they stay readable on 7" panels. */
private const val SMALL_LABEL_MIN_SP = 12f
private const val ARC_START_DEG = 90f
private const val FULL_CIRCLE_DEG = 360.0
private const val DEFAULT_MIN_TEMP_C = 7.0
private const val DEFAULT_MAX_TEMP_C = 35.0
private const val TEMP_DECIMALS_F = 0
private const val TEMP_DECIMALS_C = 1
/** Home Assistant `ClimateEntityFeature.TARGET_TEMPERATURE_RANGE`. */
private const val CLIMATE_TEMP_RANGE_FLAG = 2
/** Gap in degrees between the heat and cool arcs on a dual dial. */
private const val DUAL_ARC_GAP_DEG = 8f
private const val DUAL_TEMP_GAP_DP = 16f
private const val KEY_STACK_GAP_DP = 10f

/** Small labels stop shrinking here so they stay readable on 7" panels. */
private fun small(base: Float, layoutScale: Float) = maxOf(base * layoutScale, SMALL_LABEL_MIN_SP)

/** Dual low/high setpoints: range feature, `heat_cool`, or both target temps present. */
private fun isDual(entityState: EntityState?): Boolean =
    entityState?.supports(CLIMATE_TEMP_RANGE_FLAG) == true ||
        entityState?.state == "heat_cool" ||
        (entityState?.number("target_temp_high") != null && entityState.number("target_temp_low") != null)

/**
 * Modes in HA more-info order (Heat/Cool, Heat, Cool, Off). Prefers `heat_cool`, else `auto`.
 */
private val HA_MODES = listOf("heat_cool", "heat", "cool", "off")

/** Icons as Home Assistant's climate mode row draws them. */
private val HA_MODE_ICONS = mapOf(
    "auto" to "sun-snowflake-variant",
    "heat_cool" to "sun-snowflake-variant",
    "heat" to "fire",
    "cool" to "snowflake",
    "off" to "power",
)

/**
 * The RPI thermostat page with HA-style mode controls: current temperature and humidity either side of a dial,
 * then Heat/Cool / Heat / Cool / Off and a Fan mode button. Remote Compose cannot report a drag, so the target
 * is set with the keys beside the dial.
 */
@Suppress("LongMethod")
fun DocContext.thermostatPanel(tile: Tile, box: TileBox) {
    val id = tile.entityId ?: return
    val entityState = states[id]
    val step = entityState?.number("target_temp_step")
        ?: if (homeAssistantConfig.temperatureUnit.contains("F")) 1.0 else 0.5
    val dual = isDual(entityState)
    val modes = haModes(entityState?.list("hvac_modes").orEmpty())
    val fans = entityState?.list("fan_modes").orEmpty()
    structural(id) { s ->
        listOf(
            s?.list("hvac_modes")?.joinToString() ?: "",
            s?.list("fan_modes")?.joinToString() ?: "",
            s?.attr("fan_mode") ?: "",
            isDual(s).toString(),
            (s?.number("current_humidity") != null).toString(),
        ).joinToString("|")
    }
    if (dual) {
        predict(ActionRef(tile.id, "temperature_low", "+$step"), id)
        predict(ActionRef(tile.id, "temperature_low", "-$step"), id)
        predict(ActionRef(tile.id, "temperature_high", "+$step"), id)
        predict(ActionRef(tile.id, "temperature_high", "-$step"), id)
    } else {
        predict(ActionRef(tile.id, "temperature", "+$step"), id)
        predict(ActionRef(tile.id, "temperature", "-$step"), id)
    }
    for (mode in modes) predict(ActionRef(tile.id, "mode", mode), id)
    for (mode in fans) predict(ActionRef(tile.id, "fan", mode), id)
    bindThermostat(tile.id, id, modes, dual)

    val layoutScale = minOf(box.w / BASE_W, box.h / BASE_H).coerceIn(0.4f, 1.5f)
    scope.Column(Modifier.fillMaxSize().background(role(Role.PAGE))) {
        val doc = this@thermostatPanel
        doc.thermostatHeader(layoutScale)
        doc.thermostatBody(tile.id, id, step, dual, layoutScale, box.h)
        doc.thermostatModes(tile.id, id, modes, fans, layoutScale, box.w)
    }
}

/** Teal bar + "ADJUST THE TEMPERATURE" (RPI has no fan row). */
private fun DocContext.thermostatHeader(layoutScale: Float) {
    val ink = role(Role.INK)
    val accent = role(Role.ACCENT_DARK)
    val pad = units.px(PANEL_PAD_DP * layoutScale)
    val top = units.px(TOP_PAD_DP * layoutScale)
    scope.Column(Modifier.fillMaxWidth().padding(pad, top, pad, 0f)) {
        Box(
            Modifier.size(
                this@thermostatHeader.units.px(ACCENT_BAR_WIDTH_DP * layoutScale),
                this@thermostatHeader.units.px(ACCENT_BAR_HEIGHT_DP * layoutScale),
            ).background(accent),
        )
        Spacer(Modifier.height(this@thermostatHeader.units.px(HEADER_GAP_DP * layoutScale)))
        this@thermostatHeader.label(
            this@thermostatHeader.i18n.t("ui.adjust_temperature").uppercase(this@thermostatHeader.i18n.locale),
            small(ADJUST_CAPTION_SP, layoutScale),
            ink,
        )
    }
}

/**
 * Current temp, +/− keys, dial, humidity. In dual (Heat/Cool) mode the left keys move the low setpoint and the
 * right keys the high — Remote Compose cannot drag the two knobs.
 */
private fun DocContext.thermostatBody(
    tileId: String,
    entityId: String,
    step: Double,
    dual: Boolean,
    layoutScale: Float,
    boxH: Float,
) {
    val keyDp = ADJUST_KEY_DP * layoutScale
    val dialDp = minOf(DIAL_BASE_DP * layoutScale, boxH * DIAL_HEIGHT_SHARE)
    scope.Row(Modifier.fillMaxWidth().verticalWeight(1f), vertical = RcVerticalPositioning.Center) {
        val doc = this@thermostatBody
        doc.reading(
            doc.i18n.t("ui.current_temp"),
            Vars.tile(tileId, "current"),
            layoutScale,
            Modifier.horizontalWeight(1f),
        )
        if (dual) {
            doc.adjustKeys(tileId, "temperature_low", step, keyDp, layoutScale)
        } else {
            doc.key(
                ActionRef(tileId, "temperature", "-$step"),
                icon = "minus", heightDp = keyDp, widthDp = keyDp, round = true,
            )
        }
        Spacer(Modifier.width(doc.units.px(DIAL_SIDE_GAP_DP * layoutScale)))
        doc.dial(tileId, entityId, dialDp, dual)
        Spacer(Modifier.width(doc.units.px(DIAL_SIDE_GAP_DP * layoutScale)))
        if (dual) {
            doc.adjustKeys(tileId, "temperature_high", step, keyDp, layoutScale)
        } else {
            doc.key(
                ActionRef(tileId, "temperature", "+$step"),
                icon = "plus", heightDp = keyDp, widthDp = keyDp, round = true,
            )
        }
        doc.reading(
            doc.i18n.t("ui.current_humidity"),
            Vars.tile(tileId, "humidity"),
            layoutScale,
            Modifier.horizontalWeight(1f),
        )
    }
}

/** Minus above plus for one setpoint (low or high). */
private fun DocContext.adjustKeys(tileId: String, verb: String, step: Double, keyDp: Float, layoutScale: Float) {
    scope.Column(horizontal = RcHorizontalPositioning.Center) {
        val doc = this@adjustKeys
        doc.key(
            ActionRef(tileId, verb, "+$step"),
            icon = "plus", heightDp = keyDp, widthDp = keyDp, round = true,
        )
        Spacer(Modifier.height(doc.units.px(KEY_STACK_GAP_DP * layoutScale)))
        doc.key(
            ActionRef(tileId, verb, "-$step"),
            icon = "minus", heightDp = keyDp, widthDp = keyDp, round = true,
        )
    }
}

/**
 * HA-style mode bar: teal-bordered Heat/Cool · Heat · Cool · Off, and a Fan mode button that cycles the fan.
 */
@Suppress("LongMethod")
private fun DocContext.thermostatModes(
    tileId: String,
    entityId: String,
    modes: List<String>,
    fans: List<String>,
    layoutScale: Float,
    boxW: Float,
) {
    if (modes.isEmpty() && fans.isEmpty()) return
    val ink = role(Role.INK)
    val accent = role(Role.ACCENT_DARK)
    val corner = MODE_CORNER_DP * layoutScale
    val border = MODE_BORDER_DP * layoutScale
    val segH = SEG_HEIGHT_DP * layoutScale
    val fanW = if (fans.isEmpty()) 0f else FAN_BUTTON_WIDTH_DP * layoutScale + MODE_ROW_GAP_DP * layoutScale
    val segW = if (modes.isEmpty()) {
        0f
    } else {
        minOf(SEG_WIDTH_DP * layoutScale, (boxW - MODE_ROW_MARGIN_DP - fanW) / modes.size)
    }
    val nextFan = nextFanMode(states[entityId]?.attr("fan_mode"), fans)
    scope.Box(
        Modifier.fillMaxWidth().padding(0f, 0f, 0f, units.px(PANEL_PAD_DP * layoutScale)),
        horizontal = RcHorizontalPositioning.Center,
    ) {
        val doc = this@thermostatModes
        Row(vertical = RcVerticalPositioning.Center) {
            if (modes.isNotEmpty()) {
                Row(
                    Modifier.clip(doc.rounded(corner))
                        .border(
                            doc.units.px(border),
                            doc.units.px(corner),
                            accent,
                            RcBorderShape.RoundedRectangle,
                        )
                        .background(doc.role(Role.PAGE)),
                ) {
                    modes.forEachIndexed { i, mode ->
                        if (i > 0) {
                            Box(
                                Modifier.size(doc.units.px(SEG_EDGE_DP), doc.units.px(segH))
                                    .background(accent),
                            )
                        }
                        val bg = doc.color(Vars.option(tileId, "seg", i, Vars.KEY_BG))
                        Row(
                            doc.tap(
                                Modifier.size(doc.units.px(segW), doc.units.px(segH)).background(bg),
                                ActionRef(tileId, "mode", mode),
                            ),
                            horizontal = RcRowHorizontalPositioning.Center,
                            vertical = RcVerticalPositioning.Center,
                        ) {
                            doc.icon(HA_MODE_ICONS[mode] ?: "thermostat", MODE_ICON_DP * layoutScale, ink)
                            Spacer(Modifier.width(doc.units.px(MODE_ICON_GAP_DP * layoutScale)))
                            doc.label(doc.i18n.state("climate", mode), small(MODE_LABEL_SP, layoutScale), ink)
                        }
                    }
                }
            }
            if (fans.isNotEmpty() && nextFan != null) {
                Spacer(Modifier.width(doc.units.px(MODE_ROW_GAP_DP * layoutScale)))
                Row(
                    doc.tap(
                        Modifier.size(doc.units.px(FAN_BUTTON_WIDTH_DP * layoutScale), doc.units.px(segH))
                            .clip(doc.rounded(corner))
                            .border(
                                doc.units.px(border),
                                doc.units.px(corner),
                                accent,
                                RcBorderShape.RoundedRectangle,
                            )
                            .background(doc.role(Role.PAGE))
                            .padding(doc.units.px(FAN_PAD_DP * layoutScale), 0f),
                        ActionRef(tileId, "fan", nextFan),
                    ),
                    vertical = RcVerticalPositioning.Center,
                ) {
                    doc.icon("fan", FAN_ICON_DP * layoutScale, ink)
                    Spacer(Modifier.width(doc.units.px(MODE_ICON_GAP_DP * layoutScale)))
                    Column {
                        doc.label(doc.i18n.t("ui.fan_mode"), small(FAN_CAPTION_SP, layoutScale), ink)
                        doc.label(
                            doc.text(Vars.tile(tileId, "fan")),
                            small(FAN_VALUE_SP, layoutScale),
                            ink,
                            Weight.MEDIUM,
                        )
                    }
                }
            }
        }
    }
}

/** Heat/Cool · Heat · Cool · Off from the entity's modes (`auto` stands in when there is no `heat_cool`). */
private fun haModes(available: List<String>): List<String> {
    if (available.isEmpty()) return listOf("heat_cool", "heat", "cool", "off")
    val autoMode = when {
        "heat_cool" in available -> "heat_cool"
        "auto" in available -> "auto"
        else -> null
    }
    return buildList {
        if (autoMode != null) add(autoMode)
        for (mode in listOf("heat", "cool", "off")) if (mode in available) add(mode)
    }.ifEmpty { available.take(HA_MODES.size) }
}

/** The next fan mode after [current], for cycling the Fan mode button. */
private fun nextFanMode(current: String?, fans: List<String>): String? {
    if (fans.isEmpty()) return null
    val index = fans.indexOf(current).takeIf { it >= 0 } ?: -1
    return fans[(index + 1) % fans.size]
}

/** "CURRENT TEMP" in teal over the value, large. */
private fun DocContext.reading(caption: String, varName: String, layoutScale: Float, modifier: Modifier) {
    scope.Column(modifier, horizontal = RcHorizontalPositioning.Center) {
        val doc = this@reading
        doc.label(
            caption.uppercase(doc.i18n.locale),
            small(READING_CAPTION_SP, layoutScale),
            doc.role(Role.ACCENT_DARK),
            align = RcTextAlign.Center,
        )
        Spacer(Modifier.height(doc.units.px(READING_GAP_DP * layoutScale)))
        doc.label(
            doc.text(varName),
            READING_VALUE_SP * layoutScale,
            doc.role(Role.INK),
            Weight.LIGHT,
            align = RcTextAlign.Center,
        )
    }
}

/**
 * The dial: single target (one tinted arc) or dual Heat/Cool (orange low arc + blue high arc with a white gap),
 * knobs at each end, action and target(s) in the middle.
 */
@Suppress("LongMethod")
private fun DocContext.dial(tileId: String, entityId: String, diameterDp: Float, dual: Boolean) {
    val strokeDp = diameterDp * RING_THICKNESS_SHARE
    val knobDp = diameterDp / 2f * KNOB_RADIUS_SHARE
    val knobRingDp = diameterDp / 2f * KNOB_RING_SHARE
    val tickGapDp = diameterDp * TICK_GAP_SHARE
    val sizeDp = diameterDp + 2 * (strokeDp + tickGapDp + knobDp)
    val px = units.px(sizeDp)
    val cx = px / 2f
    val r = units.px(diameterDp) / 2f
    val stroke = units.px(strokeDp)
    val inner = color(Vars.tile(tileId, "dialInner"))
    val outer = color(Vars.tile(tileId, "dialOuter"))
    val tint = color(Vars.tile(tileId, "dial"))
    val heat = role(Role.WARN)
    val cool = role(Role.BLUE)
    val sweep = float(Vars.tile(tileId, "sweep"))
    val sweepLow = float(Vars.tile(tileId, "sweepLow"))
    val sweepHigh = float(Vars.tile(tileId, "sweepHigh"))
    val coolStart = float(Vars.tile(tileId, "coolStart"))
    val coolSweep = float(Vars.tile(tileId, "coolSweep"))
    val ink = role(Role.INK)
    val layoutScale = (diameterDp / DIAL_BASE_DP).coerceIn(0.4f, 1.5f)
    scope.Box(
        Modifier.size(px, px),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@dial
        Canvas(Modifier.size(px, px)) {
            paint {
                style(RcPaintStyle.Fill)
                radialGradient(cx, cx, r, intArrayOf(inner.id, outer.id), colorMask = DIAL_GRADIENT_COLOR_MASK)
            }
            drawCircle(cx, cx, r)
            paint { color(ink); alpha(1f); strokeWidth(stroke); style(RcPaintStyle.Stroke) }
            drawCircle(cx, cx, r)
            paint {
                color(tint); alpha(DIAL_TICK_ALPHA); strokeWidth(doc.units.px(1f)); style(RcPaintStyle.Stroke)
            }
            val tickInner = r + stroke / 2f + doc.units.px(tickGapDp)
            val tickOuter = tickInner + stroke
            for (i in 0 until TICKS) {
                val a = i * 2 * PI / TICKS + PI / 2
                val ca = cos(a).toFloat()
                val sa = sin(a).toFloat()
                drawLine(cx + tickInner * ca, cx + tickInner * sa, cx + tickOuter * ca, cx + tickOuter * sa)
            }
            val left = (cx - r).rf
            val top = (cx - r).rf
            val right = (cx + r).rf
            val bottom = (cx + r).rf
            if (dual) {
                paint {
                    color(heat)
                    alpha(1f)
                    strokeWidth(stroke)
                    strokeCap(RcStrokeCap.Round)
                    style(RcPaintStyle.Stroke)
                }
                drawArc(left, top, right, bottom, ARC_START_DEG.rf, sweepLow)
                paint {
                    color(cool)
                    alpha(1f)
                    strokeWidth(stroke)
                    strokeCap(RcStrokeCap.Round)
                    style(RcPaintStyle.Stroke)
                }
                drawArc(left, top, right, bottom, coolStart, coolSweep)
                val knob = doc.units.px(knobDp)
                save {
                    rotate(sweepLow, cx.rf, cx.rf)
                    paint { color(heat); style(RcPaintStyle.Fill) }
                    drawCircle(cx, cx + r, knob)
                    paint { color(ink); strokeWidth(doc.units.px(knobRingDp)); style(RcPaintStyle.Stroke) }
                    drawCircle(cx, cx + r, knob)
                }
                save {
                    rotate(sweepHigh, cx.rf, cx.rf)
                    paint { color(cool); style(RcPaintStyle.Fill) }
                    drawCircle(cx, cx + r, knob)
                    paint { color(ink); strokeWidth(doc.units.px(knobRingDp)); style(RcPaintStyle.Stroke) }
                    drawCircle(cx, cx + r, knob)
                }
            } else {
                paint {
                    color(tint)
                    alpha(1f)
                    strokeWidth(stroke)
                    strokeCap(RcStrokeCap.Round)
                    style(RcPaintStyle.Stroke)
                }
                drawArc(left, top, right, bottom, ARC_START_DEG.rf, sweep)
                save {
                    rotate(sweep, cx.rf, cx.rf)
                    val knob = doc.units.px(knobDp)
                    paint { color(tint); style(RcPaintStyle.Fill) }
                    drawCircle(cx, cx + r, knob)
                    paint { color(ink); strokeWidth(doc.units.px(knobRingDp)); style(RcPaintStyle.Stroke) }
                    drawCircle(cx, cx + r, knob)
                }
            }
        }
        Column(
            Modifier.size(px, px),
            horizontal = RcHorizontalPositioning.Center,
            vertical = RcColumnVerticalPositioning.Center,
        ) {
            doc.label(
                doc.text(Vars.tile(tileId, "action")),
                ACTION_LABEL_SP * layoutScale,
                ink,
                align = RcTextAlign.Center,
            )
            Spacer(Modifier.height(doc.units.px(HEADER_GAP_DP * layoutScale)))
            if (dual) {
                Row(vertical = RcVerticalPositioning.Center) {
                    doc.label(
                        doc.text(Vars.tile(tileId, "low")),
                        TARGET_LABEL_SP * layoutScale,
                        heat,
                        Weight.LIGHT,
                        align = RcTextAlign.Center,
                    )
                    Spacer(Modifier.width(doc.units.px(DUAL_TEMP_GAP_DP * layoutScale)))
                    doc.label(
                        doc.text(Vars.tile(tileId, "high")),
                        TARGET_LABEL_SP * layoutScale,
                        cool,
                        Weight.LIGHT,
                        align = RcTextAlign.Center,
                    )
                }
            } else {
                doc.label(
                    doc.text(Vars.entity(entityId, Vars.KEY_VALUE)),
                    TARGET_LABEL_SP * layoutScale,
                    ink,
                    Weight.LIGHT,
                    align = RcTextAlign.Center,
                )
            }
        }
    }
}

/** Readings, dial colours/sweeps, action, dual setpoints, fan word, and which mode is lit. */
private fun DocContext.bindThermostat(tileId: String, entityId: String, modes: List<String>, dual: Boolean) {
    bind(entityId) { states ->
        val entityState = states[entityId]
        val unit = if (homeAssistantConfig.temperatureUnit.contains("F")) TEMP_DECIMALS_F else TEMP_DECIMALS_C
        val unitSuffix = if (homeAssistantConfig.temperatureUnit.contains("F")) "°F" else "°"
        val action = entityState?.attr("hvac_action")
        val tintArgb = when (action) {
            "heating", "preheating" -> Role.WARN
            "cooling" -> Role.BLUE
            else -> Role.ACCENT_DARK
        }.argb
        val minValue = entityState?.number("min_temp") ?: DEFAULT_MIN_TEMP_C
        val maxValue = entityState?.number("max_temp") ?: DEFAULT_MAX_TEMP_C
        val span = (maxValue - minValue).coerceAtLeast(1.0)
        fun sweepOf(value: Double?) = value?.let {
            (((it - minValue) / span).coerceIn(0.0, 1.0) * FULL_CIRCLE_DEG).toFloat()
        } ?: 0f
        fun tempLabel(attr: String) =
            entityState?.number(attr)?.let { i18n.number(it, if (it % 1.0 == 0.0) 0 else 1) + unitSuffix } ?: "–"
        val lowSweep = sweepOf(entityState?.number("target_temp_low"))
        val highSweep = sweepOf(entityState?.number("target_temp_high"))
        val coolStart = ARC_START_DEG + lowSweep + DUAL_ARC_GAP_DEG
        val coolSweep = (highSweep - lowSweep - DUAL_ARC_GAP_DEG).coerceAtLeast(0f)
        val state = entityState?.state
        listOf(
            VarValue.string(
                Vars.tile(tileId, "current"),
                entityState?.number("current_temperature")?.let { i18n.number(it, unit) + "°" } ?: "–",
            ),
            VarValue.string(
                Vars.tile(tileId, "humidity"),
                entityState?.number("current_humidity")?.let { i18n.percent(it.toInt()) } ?: "–",
            ),
            VarValue.string(
                Vars.tile(tileId, "action"),
                action?.let { actionWord(it) } ?: i18n.state("climate", state ?: "off"),
            ),
            VarValue.color(Vars.tile(tileId, "dial"), tintArgb),
            VarValue.color(Vars.tile(tileId, "dialInner"), withAlpha(tintArgb, DIAL_INNER_ALPHA)),
            VarValue.color(Vars.tile(tileId, "dialOuter"), withAlpha(Role.INK.argb, DIAL_OUTER_ALPHA)),
            VarValue.float(Vars.tile(tileId, "sweep"), sweepOf(entityState?.number("temperature"))),
            VarValue.float(Vars.tile(tileId, "sweepLow"), lowSweep),
            VarValue.float(Vars.tile(tileId, "sweepHigh"), highSweep),
            VarValue.float(Vars.tile(tileId, "coolStart"), coolStart),
            VarValue.float(Vars.tile(tileId, "coolSweep"), coolSweep),
            VarValue.string(Vars.tile(tileId, "low"), if (dual) tempLabel("target_temp_low") else ""),
            VarValue.string(Vars.tile(tileId, "high"), if (dual) tempLabel("target_temp_high") else ""),
            VarValue.string(
                Vars.tile(tileId, "fan"),
                entityState?.attr("fan_mode")?.let { fanWord(it) } ?: "–",
            ),
        ) + modes.mapIndexed { i, mode ->
            VarValue.color(
                Vars.option(tileId, "seg", i, Vars.KEY_BG),
                (if (modeSelected(state, mode)) Role.ACCENT_DARK else Role.PAGE).argb,
            )
        }
    }
}

private fun DocContext.fanWord(mode: String): String =
    i18n.t("state.$mode").takeUnless { it == "state.$mode" }
        ?: mode.replace('_', ' ').replaceFirstChar { it.titlecase(i18n.locale) }

/** Whether [state] lights the RPI mode button for [mode] (`auto` ↔ `heat_cool`). */
private fun modeSelected(state: String?, mode: String): Boolean =
    state == mode ||
        (mode == "heat_cool" && state == "auto") ||
        (mode == "auto" && state == "heat_cool")

private fun withAlpha(argb: Int, alpha: Float): Int {
    val a = (alpha.coerceIn(0f, 1f) * OPAQUE_ALPHA).toInt()
    return (a shl ALPHA_SHIFT) or (argb and RGB_MASK)
}

private fun DocContext.actionWord(action: String): String =
    i18n.t("state.$action").takeUnless { it == "state.$action" }
        ?: action.replace('_', ' ').replaceFirstChar { it.titlecase(i18n.locale) }
