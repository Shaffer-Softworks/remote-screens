@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.panels.thermostat

import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.fill
import tech.shaffersoftworks.remotescreens.server.render.surfaceArgb
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileBox

import androidx.compose.remote.creation.dsl.Modifier
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
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.icon
import tech.shaffersoftworks.remotescreens.server.render.widgets.key
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.rounded
import tech.shaffersoftworks.remotescreens.server.render.widgets.tap
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.Tile
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HvacMode
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.Domain

private fun small(base: Float, layoutScale: Float) = maxOf(
    base * layoutScale,
    ThermostatMetrics.SMALL_LABEL_MIN_SP,
)

/**
 * Dual low/high setpoints only while HVAC is in Heat/Cool (or auto with a live range).
 * Supporting the range feature flag is not enough — Heat and Cool use a single `temperature`.
 */
internal fun EntityState?.usesDualSetpoint(): Boolean =
    when (this?.hvacMode) {
        HvacMode.HEAT_COOL -> true
        HvacMode.AUTO -> climate.hasTemperatureRange
        else -> false
    }

/**
 * Modes in HA more-info order (Heat/Cool, Heat, Cool, Off). Prefers `heat_cool`, else `auto`.
 */
private val HA_MODES = listOf(HvacMode.HEAT_COOL, HvacMode.HEAT, HvacMode.COOL, HvacMode.OFF).map { it.wire }

/** Icons as Home Assistant's climate mode row draws them. */
private val HA_MODE_ICONS = mapOf(
    HvacMode.AUTO.wire to "sun-snowflake-variant",
    HvacMode.HEAT_COOL.wire to "sun-snowflake-variant",
    HvacMode.HEAT.wire to "fire",
    HvacMode.COOL.wire to "snowflake",
    HvacMode.OFF.wire to "power",
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

    val step = entityState?.climate?.targetTempStep
        ?: if (homeAssistantConfig.temperatureUnit.contains("F")) {
            ThermostatMetrics.TEMP_STEP_F
        } else {
            ThermostatMetrics.TEMP_STEP_C
        }

    val dual = entityState.usesDualSetpoint()
    val modes = haModes(entityState?.climate?.hvacModes.orEmpty())
    val fans = entityState?.climate?.fanModes.orEmpty()

    structural(id) { s ->
        listOf(
            s?.state.orEmpty(),
            s?.climate?.hvacModes?.joinToString().orEmpty(),
            s?.climate?.fanModes?.joinToString().orEmpty(),
            s?.climate?.fanMode.orEmpty(),
            s.usesDualSetpoint().toString(),
            (s?.climate?.currentHumidity != null).toString(),
        ).joinToString("|")
    }

    if (dual) {
        predict(ActionRef(tile = tile.id, verb = "temperature_low", arg = "+$step"), id)
        predict(ActionRef(tile = tile.id, verb = "temperature_low", arg = "-$step"), id)
        predict(ActionRef(tile = tile.id, verb = "temperature_high", arg = "+$step"), id)
        predict(ActionRef(tile = tile.id, verb = "temperature_high", arg = "-$step"), id)
    } else {
        predict(ActionRef(tile = tile.id, verb = "temperature", arg = "+$step"), id)
        predict(ActionRef(tile = tile.id, verb = "temperature", arg = "-$step"), id)
    }

    for (mode in modes) predict(ActionRef(tile = tile.id, verb = "mode", arg = mode), id)

    for (mode in fans) predict(ActionRef(tile = tile.id, verb = "fan", arg = mode), id)

    bindThermostat(tileId = tile.id, entityId = id, modes = modes, dual = dual)

    val layoutScale = minOf(
        box.w / ThermostatMetrics.BASE_W,
        box.h / ThermostatMetrics.BASE_H,
    ).coerceIn(ThermostatMetrics.LAYOUT_SCALE_MIN, ThermostatMetrics.LAYOUT_SCALE_MAX)

    rcScope.Column(Modifier.fillMaxSize().background(fill(Role.PAGE))) {
        val doc = this@thermostatPanel
        doc.thermostatHeader(layoutScale)

        doc.thermostatBody(
            tileId = tile.id,
            step = step,
            dual = dual,
            layoutScale = layoutScale,
            boxH = box.h,
            showHumidity = entityState?.climate?.currentHumidity != null,
        )

        doc.thermostatModes(
            tileId = tile.id,
            entityId = id,
            modes = modes,
            fans = fans,
            layoutScale = layoutScale,
            boxW = box.w,
        )
    }
}

/** Teal bar + "ADJUST THE TEMPERATURE" (RPI has no fan row). */
private fun DocContext.thermostatHeader(layoutScale: Float) {
    val ink = role(Role.INK)
    val accent = fill(Role.ACCENT_DARK)
    val pad = units.px(ThermostatMetrics.PANEL_PAD_DP * layoutScale)
    val top = units.px(ThermostatMetrics.TOP_PAD_DP * layoutScale)

    rcScope.Column(Modifier.fillMaxWidth().padding(start = pad, top = top, end = pad, bottom = 0f)) {
        Box(
            Modifier.size(
                this@thermostatHeader.units.px(ThermostatMetrics.ACCENT_BAR_WIDTH_DP * layoutScale),
                this@thermostatHeader.units.px(
                    ThermostatMetrics.ACCENT_BAR_HEIGHT_DP * layoutScale,
                ),
            ).background(accent),
        )

        Spacer(
            Modifier.height(
                this@thermostatHeader.units.px(ThermostatMetrics.HEADER_GAP_DP * layoutScale),
            ),
        )

        this@thermostatHeader.label(
            text = this@thermostatHeader.i18n.t(
                "ui.adjust_temperature",
            ).uppercase(this@thermostatHeader.i18n.locale),
            sp = small(ThermostatMetrics.ADJUST_CAPTION_SP, layoutScale),
            color = ink,
        )
    }
}

/**
 * Current temp, +/− keys, dial, humidity. In dual (Heat/Cool) mode the left keys move the low setpoint and the
 * right keys the high — Remote Compose cannot drag the two knobs.
 */
private fun DocContext.thermostatBody(
    tileId: String,
    step: Double,
    dual: Boolean,
    layoutScale: Float,
    boxH: Float,
    showHumidity: Boolean,
) {
    val keyDp = ThermostatMetrics.ADJUST_KEY_DP * layoutScale

    val dialDp = minOf(
        ThermostatMetrics.DIAL_BASE_DP * layoutScale,
        boxH * ThermostatMetrics.DIAL_HEIGHT_SHARE,
    )

    rcScope.Row(Modifier.fillMaxWidth().verticalWeight(1f), vertical = RcVerticalPositioning.Center) {
        val doc = this@thermostatBody

        doc.reading(
            caption = doc.i18n.t("ui.current_temp"),
            varName = Vars.tile(tileId, "current"),
            layoutScale = layoutScale,
            modifier = Modifier.horizontalWeight(1f),
        )

        if (dual) {
            doc.adjustKeys(
                tileId = tileId,
                verb = "temperature_low",
                step = step,
                keyDp = keyDp,
                layoutScale = layoutScale,
            )
        } else {
            doc.key(
                ref = ActionRef(tile = tileId, verb = "temperature", arg = "-$step"),
                icon = "minus",
                heightDp = keyDp,
                widthDp = keyDp,
                round = true,
            )
        }

        Spacer(Modifier.width(doc.units.px(ThermostatMetrics.DIAL_SIDE_GAP_DP * layoutScale)))
        doc.thermostatDial(tileId = tileId, diameterDp = dialDp, dual = dual)
        Spacer(Modifier.width(doc.units.px(ThermostatMetrics.DIAL_SIDE_GAP_DP * layoutScale)))

        if (dual) {
            doc.adjustKeys(
                tileId = tileId,
                verb = "temperature_high",
                step = step,
                keyDp = keyDp,
                layoutScale = layoutScale,
            )
        } else {
            doc.key(
                ref = ActionRef(tile = tileId, verb = "temperature", arg = "+$step"),
                icon = "plus",
                heightDp = keyDp,
                widthDp = keyDp,
                round = true,
            )
        }

        doc.humidityReading(tileId = tileId, layoutScale = layoutScale, visible = showHumidity)
    }
}

private fun DocContext.humidityReading(tileId: String, layoutScale: Float, visible: Boolean) {
    if (visible) {
        reading(
            caption = i18n.t("ui.current_humidity"),
            varName = Vars.tile(tileId, "humidity"),
            layoutScale = layoutScale,
            modifier = Modifier.horizontalWeight(1f),
        )
    } else {
        // Keep the dial centered when there is no humidity reading.
        rcScope.Spacer(Modifier.horizontalWeight(1f))
    }
}

/** Minus above plus for one setpoint (low or high). */
private fun DocContext.adjustKeys(tileId: String, verb: String, step: Double, keyDp: Float, layoutScale: Float) {
    rcScope.Column(horizontal = RcHorizontalPositioning.Center) {
        val doc = this@adjustKeys

        doc.key(
            ref = ActionRef(tile = tileId, verb = verb, arg = "+$step"),
            icon = "plus",
            heightDp = keyDp,
            widthDp = keyDp,
            round = true,
        )

        Spacer(Modifier.height(doc.units.px(ThermostatMetrics.KEY_STACK_GAP_DP * layoutScale)))

        doc.key(
            ref = ActionRef(tile = tileId, verb = verb, arg = "-$step"),
            icon = "minus",
            heightDp = keyDp,
            widthDp = keyDp,
            round = true,
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
    val accent = fill(Role.ACCENT_DARK)
    val corner = ThermostatMetrics.MODE_CORNER_DP * layoutScale
    val border = ThermostatMetrics.MODE_BORDER_DP * layoutScale
    val segH = ThermostatMetrics.SEG_HEIGHT_DP * layoutScale
    val fanSpan = ThermostatMetrics.FAN_BUTTON_WIDTH_DP + ThermostatMetrics.MODE_ROW_GAP_DP
    val fanW = if (fans.isEmpty()) 0f else fanSpan * layoutScale

    val segW = if (modes.isEmpty()) {
        0f
    } else {
        minOf(
            ThermostatMetrics.SEG_WIDTH_DP * layoutScale,
            (boxW - ThermostatMetrics.MODE_ROW_MARGIN_DP - fanW) / modes.size,
        )
    }

    val nextFan = nextFanMode(states[entityId]?.climate?.fanMode, fans)

    rcScope.Box(
        Modifier.fillMaxWidth().padding(
            start = 0f,
            top = 0f,
            end = 0f,
            bottom = units.px(ThermostatMetrics.PANEL_PAD_DP * layoutScale),
        ),
        horizontal = RcHorizontalPositioning.Center,
    ) {
        val doc = this@thermostatModes

        Row(vertical = RcVerticalPositioning.Center) {
            // Ring = accent fill + padding (no stroke). A stroke on top of the fill draws a
            // second edge; square inner cells then kink the corner. Clip the plate to the
            // inner radius so both edges stay concentric (editor: border-radius + overflow).
            val borderPx = doc.units.px(border)
            val innerCorner = (corner - border).coerceAtLeast(0f)

            if (modes.isNotEmpty()) {
                val innerW = modes.size * segW + (modes.size - 1) * border
                val frameW = innerW + border * 2

                Box(
                    Modifier.size(
                        doc.units.px(frameW),
                        doc.units.px(segH + border * 2),
                    )
                        .clip(doc.rounded(corner))
                        .background(accent)
                        .padding(borderPx),
                ) {
                    Row(
                        Modifier.size(doc.units.px(innerW), doc.units.px(segH))
                            .clip(doc.rounded(innerCorner)),
                    ) {
                        val selected = states[entityId]?.state

                        modes.forEachIndexed { i, mode ->
                            if (i > 0) {
                                Box(
                                    Modifier.size(
                                        doc.units.px(border),
                                        doc.units.px(segH),
                                    ).background(accent),
                                )
                            }

                            val on = modeSelected(selected, mode)

                            val bg = doc.color(
                                Vars.option(scope = tileId, group = "seg", index = i, key = Vars.KEY_BG),
                                if (on) surfaceArgb(Role.ACCENT_DARK) else surfaceArgb(Role.PAGE),
                            )

                            Row(
                                modifier = doc.tap(
                                    Modifier.size(
                                        doc.units.px(segW),
                                        doc.units.px(segH),
                                    ).background(bg),
                                    ActionRef(tile = tileId, verb = "mode", arg = mode),
                                ),
                                horizontal = RcRowHorizontalPositioning.Center,
                                vertical = RcVerticalPositioning.Center,
                            ) {
                                doc.icon(
                                    name = HA_MODE_ICONS[mode] ?: "thermostat",
                                    sizeDp = ThermostatMetrics.MODE_ICON_DP * layoutScale,
                                    color = ink,
                                )

                                Spacer(
                                    Modifier.width(
                                        doc.units.px(
                                            ThermostatMetrics.MODE_ICON_GAP_DP * layoutScale,
                                        ),
                                    ),
                                )

                                doc.label(
                                    text = doc.i18n.state(Domain.CLIMATE.value, mode),
                                    sp = small(ThermostatMetrics.MODE_LABEL_SP, layoutScale),
                                    color = ink,
                                )
                            }
                        }
                    }
                }
            }

            if (fans.isNotEmpty() && nextFan != null) {
                Spacer(
                    Modifier.width(doc.units.px(ThermostatMetrics.MODE_ROW_GAP_DP * layoutScale)),
                )

                val fanPad = doc.units.px(ThermostatMetrics.FAN_PAD_DP * layoutScale)
                val fanInnerW = ThermostatMetrics.FAN_BUTTON_WIDTH_DP * layoutScale

                Box(
                    Modifier.size(
                        doc.units.px(fanInnerW + border * 2),
                        doc.units.px(segH + border * 2),
                    )
                        .clip(doc.rounded(corner))
                        .background(accent)
                        .padding(borderPx),
                ) {
                    Row(
                        doc.tap(
                            Modifier.size(doc.units.px(fanInnerW), doc.units.px(segH))
                                .clip(doc.rounded(innerCorner))
                                .background(doc.fill(Role.PAGE))
                                .padding(start = fanPad, top = 0f, end = fanPad, bottom = 0f),
                            ActionRef(tile = tileId, verb = "fan", arg = nextFan),
                        ),
                        vertical = RcVerticalPositioning.Center,
                    ) {
                        doc.icon(name = "fan", sizeDp = ThermostatMetrics.FAN_ICON_DP * layoutScale, color = ink)

                        Spacer(
                            Modifier.width(
                                doc.units.px(ThermostatMetrics.MODE_ICON_GAP_DP * layoutScale),
                            ),
                        )

                        Column {
                            doc.label(
                                text = doc.i18n.t("ui.fan_mode"),
                                sp = small(ThermostatMetrics.FAN_CAPTION_SP, layoutScale),
                                color = ink,
                            )

                            doc.label(
                                text = doc.text(Vars.tile(tileId, "fan")),
                                sp = small(ThermostatMetrics.FAN_VALUE_SP, layoutScale),
                                color = ink,
                                weight = Weight.MEDIUM,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Whether [state] lights the mode cell for [mode] (`auto` ↔ `heat_cool`). */
private fun modeSelected(state: String?, mode: String): Boolean = hvacModeSelected(state, mode)

/** Whether a climate in [state] lights the control for [mode]; `auto` and `heat_cool` stand in for each other. */
internal fun hvacModeSelected(state: String?, mode: String): Boolean =
    state == mode || setOf(HvacMode.of(state.orEmpty()), HvacMode.of(mode)) == RANGE_MODES

private val RANGE_MODES = setOf(HvacMode.HEAT_COOL, HvacMode.AUTO)

/** Heat/Cool · Heat · Cool · Off from the entity's modes (`auto` stands in when there is no `heat_cool`). */
private fun haModes(available: List<String>): List<String> {
    if (available.isEmpty()) return HA_MODES

    val autoMode = listOf(HvacMode.HEAT_COOL.wire, HvacMode.AUTO.wire).firstOrNull { it in available }

    return buildList {
        if (autoMode != null) add(autoMode)

        for (mode in HA_MODES.drop(1)) if (mode in available) add(mode)
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
    rcScope.Column(modifier, horizontal = RcHorizontalPositioning.Center) {
        val doc = this@reading

        doc.label(
            text = caption.uppercase(doc.i18n.locale),
            sp = small(ThermostatMetrics.READING_CAPTION_SP, layoutScale),
            color = doc.role(Role.ACCENT_DARK),
            align = RcTextAlign.Center,
        )

        Spacer(Modifier.height(doc.units.px(ThermostatMetrics.READING_GAP_DP * layoutScale)))

        doc.label(
            text = doc.text(varName),
            sp = ThermostatMetrics.READING_VALUE_SP * layoutScale,
            color = doc.role(Role.INK),
            weight = Weight.LIGHT,
            align = RcTextAlign.Center,
        )
    }
}

