@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render

import tech.shaffersoftworks.remotescreens.server.render.tiles.TileBox

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcBorderShape
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
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

private fun small(base: Float, layoutScale: Float) = maxOf(
    base * layoutScale,
    ThermostatMetrics.SMALL_LABEL_MIN_SP,
)

/** Dual low/high setpoints: range feature, `heat_cool`, or both target temps present. */
private fun isDual(entityState: EntityState?): Boolean =
    entityState?.supports(ThermostatMetrics.CLIMATE_TEMP_RANGE_FLAG) == true ||
        entityState?.state == "heat_cool" ||
        (entityState?.number(
            "target_temp_high",
        ) != null && entityState.number("target_temp_low") != null)

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
        ?: if (homeAssistantConfig.temperatureUnit.contains("F")) {
            ThermostatMetrics.TEMP_STEP_F
        } else {
            ThermostatMetrics.TEMP_STEP_C
        }
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

    val layoutScale = minOf(
        box.w / ThermostatMetrics.BASE_W,
        box.h / ThermostatMetrics.BASE_H,
    ).coerceIn(ThermostatMetrics.LAYOUT_SCALE_MIN, ThermostatMetrics.LAYOUT_SCALE_MAX)
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
    val pad = units.px(ThermostatMetrics.PANEL_PAD_DP * layoutScale)
    val top = units.px(ThermostatMetrics.TOP_PAD_DP * layoutScale)
    scope.Column(Modifier.fillMaxWidth().padding(pad, top, pad, 0f)) {
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
            this@thermostatHeader.i18n.t(
                "ui.adjust_temperature",
            ).uppercase(this@thermostatHeader.i18n.locale),
            small(ThermostatMetrics.ADJUST_CAPTION_SP, layoutScale),
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
    val keyDp = ThermostatMetrics.ADJUST_KEY_DP * layoutScale
    val dialDp = minOf(
        ThermostatMetrics.DIAL_BASE_DP * layoutScale,
        boxH * ThermostatMetrics.DIAL_HEIGHT_SHARE,
    )
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
                icon = "minus",
                heightDp = keyDp,
                widthDp = keyDp,
                round = true,
            )
        }
        Spacer(Modifier.width(doc.units.px(ThermostatMetrics.DIAL_SIDE_GAP_DP * layoutScale)))
        doc.thermostatDial(tileId, entityId, dialDp, dual)
        Spacer(Modifier.width(doc.units.px(ThermostatMetrics.DIAL_SIDE_GAP_DP * layoutScale)))
        if (dual) {
            doc.adjustKeys(tileId, "temperature_high", step, keyDp, layoutScale)
        } else {
            doc.key(
                ActionRef(tileId, "temperature", "+$step"),
                icon = "plus",
                heightDp = keyDp,
                widthDp = keyDp,
                round = true,
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
            icon = "plus",
            heightDp = keyDp,
            widthDp = keyDp,
            round = true,
        )
        Spacer(Modifier.height(doc.units.px(ThermostatMetrics.KEY_STACK_GAP_DP * layoutScale)))
        doc.key(
            ActionRef(tileId, verb, "-$step"),
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
    val accent = role(Role.ACCENT_DARK)
    val corner = ThermostatMetrics.MODE_CORNER_DP * layoutScale
    val border = ThermostatMetrics.MODE_BORDER_DP * layoutScale
    val segH = ThermostatMetrics.SEG_HEIGHT_DP * layoutScale
    val fanW = if (fans.isEmpty()) 0f else ThermostatMetrics.FAN_BUTTON_WIDTH_DP * layoutScale + ThermostatMetrics.MODE_ROW_GAP_DP * layoutScale
    val segW = if (modes.isEmpty()) {
        0f
    } else {
        minOf(
            ThermostatMetrics.SEG_WIDTH_DP * layoutScale,
            (boxW - ThermostatMetrics.MODE_ROW_MARGIN_DP - fanW) / modes.size,
        )
    }
    val nextFan = nextFanMode(states[entityId]?.attr("fan_mode"), fans)
    scope.Box(
        Modifier.fillMaxWidth().padding(
            0f,
            0f,
            0f,
            units.px(ThermostatMetrics.PANEL_PAD_DP * layoutScale),
        ),
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
                                Modifier.size(
                                    doc.units.px(ThermostatMetrics.SEG_EDGE_DP),
                                    doc.units.px(segH),
                                )
                                    .background(accent),
                            )
                        }
                        val bg = doc.color(Vars.option(tileId, "seg", i, Vars.KEY_BG))
                        Row(
                            doc.tap(
                                Modifier.size(
                                    doc.units.px(segW),
                                    doc.units.px(segH),
                                ).background(bg),
                                ActionRef(tileId, "mode", mode),
                            ),
                            horizontal = RcRowHorizontalPositioning.Center,
                            vertical = RcVerticalPositioning.Center,
                        ) {
                            doc.icon(
                                HA_MODE_ICONS[mode] ?: "thermostat",
                                ThermostatMetrics.MODE_ICON_DP * layoutScale,
                                ink,
                            )
                            Spacer(
                                Modifier.width(
                                    doc.units.px(ThermostatMetrics.MODE_ICON_GAP_DP * layoutScale),
                                ),
                            )
                            doc.label(
                                doc.i18n.state("climate", mode),
                                small(ThermostatMetrics.MODE_LABEL_SP, layoutScale),
                                ink,
                            )
                        }
                    }
                }
            }
            if (fans.isNotEmpty() && nextFan != null) {
                Spacer(
                    Modifier.width(doc.units.px(ThermostatMetrics.MODE_ROW_GAP_DP * layoutScale)),
                )
                Row(
                    doc.tap(
                        Modifier.size(
                            doc.units.px(ThermostatMetrics.FAN_BUTTON_WIDTH_DP * layoutScale),
                            doc.units.px(segH),
                        )
                            .clip(doc.rounded(corner))
                            .border(
                                doc.units.px(border),
                                doc.units.px(corner),
                                accent,
                                RcBorderShape.RoundedRectangle,
                            )
                            .background(doc.role(Role.PAGE))
                            .padding(doc.units.px(ThermostatMetrics.FAN_PAD_DP * layoutScale), 0f),
                        ActionRef(tileId, "fan", nextFan),
                    ),
                    vertical = RcVerticalPositioning.Center,
                ) {
                    doc.icon("fan", ThermostatMetrics.FAN_ICON_DP * layoutScale, ink)
                    Spacer(
                        Modifier.width(
                            doc.units.px(ThermostatMetrics.MODE_ICON_GAP_DP * layoutScale),
                        ),
                    )
                    Column {
                        doc.label(
                            doc.i18n.t("ui.fan_mode"),
                            small(ThermostatMetrics.FAN_CAPTION_SP, layoutScale),
                            ink,
                        )
                        doc.label(
                            doc.text(Vars.tile(tileId, "fan")),
                            small(ThermostatMetrics.FAN_VALUE_SP, layoutScale),
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
            small(ThermostatMetrics.READING_CAPTION_SP, layoutScale),
            doc.role(Role.ACCENT_DARK),
            align = RcTextAlign.Center,
        )
        Spacer(Modifier.height(doc.units.px(ThermostatMetrics.READING_GAP_DP * layoutScale)))
        doc.label(
            doc.text(varName),
            ThermostatMetrics.READING_VALUE_SP * layoutScale,
            doc.role(Role.INK),
            Weight.LIGHT,
            align = RcTextAlign.Center,
        )
    }
}

