@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.panels.thermostat

import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.shared.VarValue
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HvacMode
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.Domain

/** Readings, dial colours/sweeps, action, dual setpoints, fan word, and which mode is lit. */
internal fun DocContext.bindThermostat(tileId: String, entityId: String, modes: List<String>, dual: Boolean) {
    bind(entityId) { states ->
        thermostatValues(tileId = tileId, modes = modes, dual = dual, entityState = states[entityId])
    }
}

private fun DocContext.thermostatValues(
    tileId: String,
    modes: List<String>,
    dual: Boolean,
    entityState: EntityState?,
): List<VarValue> {

    val unit = if (homeAssistantConfig.temperatureUnit.contains(
            "F",
        )) {
        ThermostatMetrics.TEMP_DECIMALS_F
    } else {
        ThermostatMetrics.TEMP_DECIMALS_C
    }

    val unitSuffix = if (homeAssistantConfig.temperatureUnit.contains("F")) "°F" else "°"
    val action = entityState?.climate?.hvacAction

    val tintArgb = dialTint(action)
    val minValue = entityState?.climate?.minTemp ?: ThermostatMetrics.DEFAULT_MIN_TEMP_C
    val maxValue = entityState?.climate?.maxTemp ?: ThermostatMetrics.DEFAULT_MAX_TEMP_C
    val span = (maxValue - minValue).coerceAtLeast(1.0)

    val lowSweep = sweepAmount(
        value = entityState?.climate?.targetTempLow,
        minValue = minValue,
        span = span,
    )

    val highSweep = sweepAmount(
        value = entityState?.climate?.targetTempHigh,
        minValue = minValue,
        span = span,
    )

    val coolStart = ThermostatMetrics.ARC_START_DEG + lowSweep + ThermostatMetrics.DUAL_ARC_GAP_DEG

    val coolSweep = (highSweep - lowSweep - ThermostatMetrics.DUAL_ARC_GAP_DEG).coerceAtLeast(
        0f,
    )

    val arc = ThermostatArc(
        minValue = minValue,
        span = span,
        lowSweep = lowSweep,
        highSweep = highSweep,
        coolStart = coolStart,
        coolSweep = coolSweep
    )

    return currentValues(
        tileId = tileId,
        entityState = entityState,
        unit = unit,
        action = action,
        tintArgb = tintArgb
    ) +
        targetValues(tileId = tileId, entityState = entityState, unitSuffix = unitSuffix, dual = dual, arc = arc) +
        modeValues(tileId = tileId, state = entityState?.state, modes = modes)
}

private data class ThermostatArc(
    val minValue: Double,
    val span: Double,
    val lowSweep: Float,
    val highSweep: Float,
    val coolStart: Float,
    val coolSweep: Float,
)

private fun DocContext.currentValues(
    tileId: String,
    entityState: EntityState?,
    unit: Int,
    action: String?,
    tintArgb: Int,
): List<VarValue> {
    val state = entityState?.state

    return listOf(
        VarValue.string(
            Vars.tile(tileId, "current"),
            entityState?.climate?.currentTemperature?.let { i18n.number(it, unit) + "°" } ?: "–",
        ),
        VarValue.string(
            Vars.tile(tileId, "humidity"),
            entityState?.climate?.currentHumidity?.let { i18n.percent(it.toInt()) } ?: "–",
        ),
        VarValue.string(
            Vars.tile(tileId, "action"),
            action?.let { actionWord(it) } ?: i18n.state(Domain.CLIMATE.value, state ?: HvacMode.OFF.wire),
        ),
        VarValue.color(Vars.tile(tileId, "dial"), tintArgb),
        VarValue.color(
            Vars.tile(tileId, "dialInner"),
            withAlpha(tintArgb, ThermostatMetrics.DIAL_INNER_ALPHA),
        ),
        VarValue.color(
            Vars.tile(tileId, "dialOuter"),
            withAlpha(Role.INK.argb, ThermostatMetrics.DIAL_OUTER_ALPHA),
        ),
    )
}

private fun DocContext.targetValues(
    tileId: String,
    entityState: EntityState?,
    unitSuffix: String,
    dual: Boolean,
    arc: ThermostatArc,
): List<VarValue> = listOf(
    VarValue.float(
        Vars.tile(tileId, "sweep"),
        sweepAmount(
            value = entityState?.climate?.temperature,
            minValue = arc.minValue,
            span = arc.span,
        ),
    ),
    VarValue.float(Vars.tile(tileId, "sweepLow"), arc.lowSweep),
    VarValue.float(Vars.tile(tileId, "sweepHigh"), arc.highSweep),
    VarValue.float(Vars.tile(tileId, "coolStart"), arc.coolStart),
    VarValue.float(Vars.tile(tileId, "coolSweep"), arc.coolSweep),
    VarValue.string(
        Vars.tile(tileId, "target"),
        if (dual) "" else tempLabel(entityState?.climate?.temperature, unitSuffix),
    ),
    VarValue.string(
        Vars.tile(tileId, "low"),
        if (dual) tempLabel(entityState?.climate?.targetTempLow, unitSuffix) else "",
    ),
    VarValue.string(
        Vars.tile(tileId, "high"),
        if (dual) tempLabel(entityState?.climate?.targetTempHigh, unitSuffix) else "",
    ),
    VarValue.string(
        Vars.tile(tileId, "fan"),
        entityState?.climate?.fanMode?.let { fanWord(it) } ?: "–",
    ),
)

private fun DocContext.modeValues(tileId: String, state: String?, modes: List<String>): List<VarValue> =
    modes.mapIndexed { i, mode ->
        VarValue.color(
            Vars.option(scope = tileId, group = "seg", index = i, key = Vars.KEY_BG),
            (if (modeSelected(state, mode)) Role.ACCENT_DARK else Role.PAGE).argb,
        )
    }

private fun DocContext.dialTint(action: String?): Int = when (action) {
    "heating", "preheating" -> Role.WARN
    "cooling" -> Role.BLUE
    else -> Role.ACCENT_DARK
}.argb

private fun sweepAmount(value: Double?, minValue: Double, span: Double): Float {
    val fraction = value?.let { ((it - minValue) / span).coerceIn(0.0, 1.0) } ?: 0.0

    return (fraction * ThermostatMetrics.FULL_CIRCLE_DEG).toFloat()
}

private fun DocContext.tempLabel(temperature: Double?, suffix: String): String {
    val shown = temperature ?: return "–"
    val places = if (shown % 1.0 == 0.0) 0 else 1

    return i18n.number(shown, places) + suffix
}

private fun DocContext.fanWord(mode: String): String =
    i18n.t("state.$mode").takeUnless { it == "state.$mode" }
        ?: mode.replace('_', ' ').replaceFirstChar { it.titlecase(i18n.locale) }

/** Whether [state] lights the RPI mode button for [mode] (`auto` ↔ `heat_cool`). */
private fun modeSelected(state: String?, mode: String): Boolean = hvacModeSelected(state, mode)

private fun withAlpha(argb: Int, alpha: Float): Int {
    val a = (alpha.coerceIn(0f, 1f) * ThermostatMetrics.OPAQUE_ALPHA).toInt()

    return (a shl ThermostatMetrics.ALPHA_SHIFT) or (argb and ThermostatMetrics.RGB_MASK)
}

private fun DocContext.actionWord(action: String): String =
    i18n.t("state.$action").takeUnless { it == "state.$action" }
        ?: action.replace('_', ' ').replaceFirstChar { it.titlecase(i18n.locale) }
