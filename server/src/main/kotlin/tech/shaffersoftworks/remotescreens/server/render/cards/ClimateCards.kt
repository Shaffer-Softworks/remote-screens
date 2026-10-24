@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards

import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.gap
import tech.shaffersoftworks.remotescreens.server.render.widgets.key
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.usesDualSetpoint

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.shared.VarValue

internal fun DocContext.climateCard(entityId: String, entityState: EntityState?) {
    val step = entityState?.number("target_temp_step")
        ?: if (homeAssistantConfig.temperatureUnit.contains("F")) {
            CardChrome.TEMP_STEP_F
        } else {
            CardChrome.TEMP_STEP_C
        }
    val dualSetpoint = entityState.usesDualSetpoint()
    gap(CardChrome.PANEL_CORNER_DP)
    if (dualSetpoint) {
        climateDualSetpoint(entityId, entityState, step)
    } else {
        predict(ref(entityId, "temperature", "+$step"), entityId)
        predict(ref(entityId, "temperature", "-$step"), entityId)
        scope.Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
            val doc = this@climateCard
            doc.key(
                ref(entityId, "temperature", "-$step"),
                icon = "minus",
                heightDp = CardChrome.STEPPER_KEY_DP,
                widthDp = CardChrome.STEPPER_KEY_DP,
                round = true,
            )
            Column(Modifier.horizontalWeight(1f), horizontal = RcHorizontalPositioning.Center) {
                doc.label(
                    doc.i18n.t("ui.target"),
                    Type.BODY,
                    doc.role(Role.INK_SOFT),
                    align = RcTextAlign.Center,
                )
                doc.label(
                    doc.text(Vars.entity(entityId, Vars.KEY_VALUE)),
                    Type.DISPLAY,
                    doc.role(Role.INK),
                    Weight.NORMAL,
                    align = RcTextAlign.Center,
                )
            }
            doc.key(
                ref(entityId, "temperature", "+$step"),
                icon = "plus",
                heightDp = CardChrome.STEPPER_KEY_DP,
                widthDp = CardChrome.STEPPER_KEY_DP,
                round = true,
            )
        }
    }
    val modes = entityState?.list("hvac_modes").orEmpty()
    structural(entityId) {
        listOf("hvac_modes", "preset_modes", "fan_modes", "swing_modes").joinToString("|") { key ->
            it?.list(key)?.joinToString() ?: ""
        } + "|${it?.state}|${it.usesDualSetpoint()}|${it?.number("target_temp_high")}|${it?.number("target_temp_low")}"
    }
    if (modes.isNotEmpty()) {
        section(i18n.t("ui.mode"))
        flowKeys(entityId, "mode", modes.map { it to i18n.state("climate", it) }) { it.state }
    }
    val presets = entityState?.list("preset_modes").orEmpty()
    if (presets.isNotEmpty()) {
        section(i18n.t("ui.preset"))
        flowKeys(
            entityId,
            "preset",
            presets.map { it to it.replace('_', ' ').replaceFirstChar(Char::uppercase) },
        ) { it.attr("preset_mode") }
    }
    val fans = entityState?.list("fan_modes").orEmpty()
    if (fans.isNotEmpty()) {
        section(i18n.t("ui.speed"))
        flowKeys(
            entityId,
            "fan",
            fans.map { it to it.replaceFirstChar(Char::uppercase) },
        ) { it.attr("fan_mode") }
    }
    val swings = entityState?.list("swing_modes").orEmpty()
    if (swings.isNotEmpty()) {
        section(i18n.t("ui.swing"))
        flowKeys(
            entityId,
            "swing",
            swings.map { it to it.replace('_', ' ').replaceFirstChar(Char::uppercase) },
        ) {
            it.attr("swing_mode")
        }
    }
}

internal fun DocContext.climateDualSetpoint(
    entityId: String,
    @Suppress("UNUSED_PARAMETER") entityState: EntityState?,
    step: Double,
) {
    bind(entityId) { states ->
        val climate = states[entityId]
        fun tempLabel(attr: String) =
            climate?.number(
                attr,
            )?.let { i18n.number(it, if (it % 1.0 == 0.0) 0 else 1) + "°" } ?: "–"
        listOf(
            VarValue.string(Vars.entity(entityId, "temp_low"), tempLabel("target_temp_low")),
            VarValue.string(Vars.entity(entityId, "temp_high"), tempLabel("target_temp_high")),
        )
    }
    predict(ref(entityId, "temperature_low", "+$step"), entityId)
    predict(ref(entityId, "temperature_low", "-$step"), entityId)
    predict(ref(entityId, "temperature_high", "+$step"), entityId)
    predict(ref(entityId, "temperature_high", "-$step"), entityId)
    scope.Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
        val doc = this@climateDualSetpoint
        doc.key(
            ref(entityId, "temperature_low", "-$step"),
            icon = "minus",
            heightDp = CardChrome.STEPPER_KEY_DP,
            widthDp = CardChrome.STEPPER_KEY_DP,
            round = true,
        )
        Column(Modifier.horizontalWeight(1f), horizontal = RcHorizontalPositioning.Center) {
            doc.label(
                doc.i18n.t("ui.low"),
                Type.BODY,
                doc.role(Role.INK_SOFT),
                align = RcTextAlign.Center,
            )
            doc.label(
                doc.text(Vars.entity(entityId, "temp_low")),
                Type.DISPLAY,
                doc.role(Role.INK),
                Weight.NORMAL,
                align = RcTextAlign.Center,
            )
        }
        doc.key(
            ref(entityId, "temperature_low", "+$step"),
            icon = "plus",
            heightDp = CardChrome.STEPPER_KEY_DP,
            widthDp = CardChrome.STEPPER_KEY_DP,
            round = true,
        )
    }
    gap(CardChrome.PANEL_CORNER_DP)
    scope.Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
        val doc = this@climateDualSetpoint
        doc.key(
            ref(entityId, "temperature_high", "-$step"),
            icon = "minus",
            heightDp = CardChrome.STEPPER_KEY_DP,
            widthDp = CardChrome.STEPPER_KEY_DP,
            round = true,
        )
        Column(Modifier.horizontalWeight(1f), horizontal = RcHorizontalPositioning.Center) {
            doc.label(
                doc.i18n.t("ui.high"),
                Type.BODY,
                doc.role(Role.INK_SOFT),
                align = RcTextAlign.Center,
            )
            doc.label(
                doc.text(Vars.entity(entityId, "temp_high")),
                Type.DISPLAY,
                doc.role(Role.INK),
                Weight.NORMAL,
                align = RcTextAlign.Center,
            )
        }
        doc.key(
            ref(entityId, "temperature_high", "+$step"),
            icon = "plus",
            heightDp = CardChrome.STEPPER_KEY_DP,
            widthDp = CardChrome.STEPPER_KEY_DP,
            round = true,
        )
    }
}

internal fun DocContext.waterHeaterCard(entityId: String, entityState: EntityState?, w: Float) {
    val min = entityState?.number("min_temp") ?: CardChrome.DEFAULT_CLIMATE_LIKE_MIN
    val max = entityState?.number("max_temp") ?: CardChrome.DEFAULT_CLIMATE_LIKE_MAX
    val step = entityState?.number("target_temp_step") ?: 1.0
    predict(ref(entityId, "temperature", "+$step"), entityId)
    predict(ref(entityId, "temperature", "-$step"), entityId)
    gap(CardChrome.PANEL_CORNER_DP)
    scope.Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
        val doc = this@waterHeaterCard
        doc.key(
            ref(entityId, "temperature", "-$step"),
            icon = "minus",
            heightDp = CardChrome.STEPPER_KEY_DP,
            widthDp = CardChrome.STEPPER_KEY_DP,
            round = true,
        )
        Column(Modifier.horizontalWeight(1f), horizontal = RcHorizontalPositioning.Center) {
            doc.label(
                doc.i18n.t("ui.target"),
                Type.BODY,
                doc.role(Role.INK_SOFT),
                align = RcTextAlign.Center,
            )
            doc.label(
                doc.text(Vars.entity(entityId, Vars.KEY_VALUE)),
                Type.DISPLAY,
                doc.role(Role.INK),
                Weight.NORMAL,
                align = RcTextAlign.Center,
            )
        }
        doc.key(
            ref(entityId, "temperature", "+$step"),
            icon = "plus",
            heightDp = CardChrome.STEPPER_KEY_DP,
            widthDp = CardChrome.STEPPER_KEY_DP,
            round = true,
        )
    }
    gap(CardChrome.CARD_GAP_DP)
    bar(entityId, "temperature", w, min, max)
    val modes = entityState?.list("operation_list").orEmpty()
    if (modes.isNotEmpty()) {
        structural(entityId) { it?.list("operation_list")?.joinToString() ?: "" }
        section(i18n.t("ui.mode"))
        flowKeys(
            entityId,
            "operation",
            modes.map { it to it.replace('_', ' ').replaceFirstChar(Char::uppercase) },
        ) {
            it.state
        }
    }
}

internal fun DocContext.valveCard(entityId: String, entityState: EntityState?, w: Float) {
    gap(CardChrome.FLOW_PAD_DP)
    scope.Row(Modifier.fillMaxWidth()) {
        val doc = this@valveCard
        doc.key(
            ref(entityId, "open"),
            text = doc.i18n.t("ui.open"),
            icon = "valve-open",
            heightDp = 56f,
            modifier = Modifier.horizontalWeight(1f),
        )
        Spacer(Modifier.width(doc.units.px(CardChrome.PANEL_CORNER_DP)))
        doc.key(
            ref(entityId, "stop"),
            text = doc.i18n.t("ui.stop"),
            icon = "stop",
            heightDp = 56f,
            modifier = Modifier.horizontalWeight(1f),
        )
        Spacer(Modifier.width(doc.units.px(CardChrome.PANEL_CORNER_DP)))
        doc.key(
            ref(entityId, "close"),
            text = doc.i18n.t("ui.close_cover"),
            icon = "valve-closed",
            heightDp = 56f,
            modifier = Modifier.horizontalWeight(1f),
        )
    }
    if (entityState?.number("current_position") != null) {
        section(i18n.t("ui.position"))
        bar(entityId, "position", w)
    }
}
