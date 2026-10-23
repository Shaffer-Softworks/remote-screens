@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render

import tech.shaffersoftworks.remotescreens.shared.VarValue

/** Readings, dial colours/sweeps, action, dual setpoints, fan word, and which mode is lit. */
internal fun DocContext.bindThermostat(tileId: String, entityId: String, modes: List<String>, dual: Boolean) {
    bind(entityId) { states ->
        val entityState = states[entityId]
        val unit = if (homeAssistantConfig.temperatureUnit.contains(
                "F",
            )) {
            ThermostatMetrics.TEMP_DECIMALS_F
        } else {
            ThermostatMetrics.TEMP_DECIMALS_C
        }
        val unitSuffix = if (homeAssistantConfig.temperatureUnit.contains("F")) "°F" else "°"
        val action = entityState?.attr("hvac_action")
        val tintArgb = when (action) {
            "heating", "preheating" -> Role.WARN
            "cooling" -> Role.BLUE
            else -> Role.ACCENT_DARK
        }.argb
        val minValue = entityState?.number("min_temp") ?: ThermostatMetrics.DEFAULT_MIN_TEMP_C
        val maxValue = entityState?.number("max_temp") ?: ThermostatMetrics.DEFAULT_MAX_TEMP_C
        val span = (maxValue - minValue).coerceAtLeast(1.0)
        fun sweepOf(value: Double?) = value?.let {
            (((it - minValue) / span).coerceIn(
                0.0,
                1.0,
            ) * ThermostatMetrics.FULL_CIRCLE_DEG).toFloat()
        } ?: 0f
        fun tempLabel(attr: String) =
            entityState?.number(
                attr,
            )?.let { i18n.number(it, if (it % 1.0 == 0.0) 0 else 1) + unitSuffix } ?: "–"
        val lowSweep = sweepOf(entityState?.number("target_temp_low"))
        val highSweep = sweepOf(entityState?.number("target_temp_high"))
        val coolStart = ThermostatMetrics.ARC_START_DEG + lowSweep + ThermostatMetrics.DUAL_ARC_GAP_DEG
        val coolSweep = (highSweep - lowSweep - ThermostatMetrics.DUAL_ARC_GAP_DEG).coerceAtLeast(
            0f,
        )
        val state = entityState?.state
        listOf(
            VarValue.string(
                Vars.tile(tileId, "current"),
                entityState?.number(
                    "current_temperature",
                )?.let { i18n.number(it, unit) + "°" } ?: "–",
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
            VarValue.color(
                Vars.tile(tileId, "dialInner"),
                withAlpha(tintArgb, ThermostatMetrics.DIAL_INNER_ALPHA),
            ),
            VarValue.color(
                Vars.tile(tileId, "dialOuter"),
                withAlpha(Role.INK.argb, ThermostatMetrics.DIAL_OUTER_ALPHA),
            ),
            VarValue.float(Vars.tile(tileId, "sweep"), sweepOf(entityState?.number("temperature"))),
            VarValue.float(Vars.tile(tileId, "sweepLow"), lowSweep),
            VarValue.float(Vars.tile(tileId, "sweepHigh"), highSweep),
            VarValue.float(Vars.tile(tileId, "coolStart"), coolStart),
            VarValue.float(Vars.tile(tileId, "coolSweep"), coolSweep),
            VarValue.string(
                Vars.tile(tileId, "target"),
                if (dual) "" else tempLabel("temperature"),
            ),
            VarValue.string(
                Vars.tile(tileId, "low"),
                if (dual) tempLabel("target_temp_low") else "",
            ),
            VarValue.string(
                Vars.tile(tileId, "high"),
                if (dual) tempLabel("target_temp_high") else "",
            ),
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
    val a = (alpha.coerceIn(0f, 1f) * ThermostatMetrics.OPAQUE_ALPHA).toInt()
    return (a shl ThermostatMetrics.ALPHA_SHIFT) or (argb and ThermostatMetrics.RGB_MASK)
}

private fun DocContext.actionWord(action: String): String =
    i18n.t("state.$action").takeUnless { it == "state.$action" }
        ?: action.replace('_', ' ').replaceFirstChar { it.titlecase(i18n.locale) }
