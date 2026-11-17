@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards

import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.spacer
import tech.shaffersoftworks.remotescreens.server.render.widgets.key
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.panels.thermostat.usesDualSetpoint
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.shared.VarValue
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.Domain

internal fun DocContext.climateCard(entityId: String, entityState: EntityState?) {
    val step = entityState?.climate?.targetTempStep
        ?: if (homeAssistantConfig.temperatureUnit.contains("F")) {
            CardChrome.TEMP_STEP_F
        } else {
            CardChrome.TEMP_STEP_C
        }
    val dualSetpoint = entityState.usesDualSetpoint()
    spacer(CardChrome.PANEL_CORNER_DP)

    if (dualSetpoint) {
        climateDualSetpoint(entityId, entityState, step)
    } else {
        predict(ref(entityId, "temperature", "+$step"), entityId)
        predict(ref(entityId, "temperature", "-$step"), entityId)
        rcScope.Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
            val doc = this@climateCard
            doc.key(
                ref = ref(entityId, "temperature", "-$step"),
                icon = "minus",
                heightDp = CardChrome.STEPPER_KEY_DP,
                widthDp = CardChrome.STEPPER_KEY_DP,
                round = true,
            )

            Column(Modifier.horizontalWeight(1f), horizontal = RcHorizontalPositioning.Center) {
                doc.label(
                    text = doc.i18n.t("ui.target"),
                    sp = Type.BODY,
                    color = doc.role(Role.INK_SOFT),
                    align = RcTextAlign.Center,
                )
                doc.label(
                    text = doc.text(Vars.entity(entityId, Vars.KEY_VALUE)),
                    sp = Type.DISPLAY,
                    color = doc.role(Role.INK),
                    weight = Weight.NORMAL,
                    align = RcTextAlign.Center,
                )
            }
            doc.key(
                ref = ref(entityId, "temperature", "+$step"),
                icon = "plus",
                heightDp = CardChrome.STEPPER_KEY_DP,
                widthDp = CardChrome.STEPPER_KEY_DP,
                round = true,
            )
        }
    }
    val modes = entityState?.climate?.hvacModes.orEmpty()
    structural(entityId) {
        val climate = it?.climate
        val modeLists = listOf(climate?.hvacModes, climate?.presetModes, climate?.fanModes, climate?.swingModes)
            .joinToString("|") { modeList -> modeList?.joinToString().orEmpty() }
        listOf(modeLists, it?.state, it.usesDualSetpoint(), climate?.targetTempHigh, climate?.targetTempLow)
            .joinToString("|")
    }
    if (modes.isNotEmpty()) {
        section(i18n.t("ui.mode"))
        flowKeys(entityId, "mode", modes.map {
            it to i18n.state(Domain.CLIMATE.value, it)
        }) {
            it.state
        }
    }
    val presets = entityState?.climate?.presetModes.orEmpty()
    if (presets.isNotEmpty()) {
        section(i18n.t("ui.preset"))
        flowKeys(
            entityId,
            "preset",
            presets.map { it to it.replace('_', ' ').replaceFirstChar(Char::uppercase) },
        ) { it.climate.presetMode }
    }
    val fans = entityState?.climate?.fanModes.orEmpty()
    if (fans.isNotEmpty()) {
        section(i18n.t("ui.speed"))
        flowKeys(
            entityId,
            "fan",
            fans.map { it to it.replaceFirstChar(Char::uppercase) },
        ) { it.climate.fanMode }
    }
    val swings = entityState?.climate?.swingModes.orEmpty()
    if (swings.isNotEmpty()) {
        section(i18n.t("ui.swing"))
        flowKeys(
            entityId,
            "swing",
            swings.map { it to it.replace('_', ' ').replaceFirstChar(Char::uppercase) },
        ) {
            it.climate.swingMode
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
        fun tempLabel(temperature: Double?) =
            temperature?.let { i18n.number(it, if (it % 1.0 == 0.0) 0 else 1) + "°" } ?: "–"
        listOf(
            VarValue.string(Vars.entity(entityId, "temp_low"), tempLabel(climate?.climate?.targetTempLow)),
            VarValue.string(Vars.entity(entityId, "temp_high"), tempLabel(climate?.climate?.targetTempHigh)),
        )
    }
    predict(ref(entityId, "temperature_low", "+$step"), entityId)
    predict(ref(entityId, "temperature_low", "-$step"), entityId)
    predict(ref(entityId, "temperature_high", "+$step"), entityId)
    predict(ref(entityId, "temperature_high", "-$step"), entityId)
    rcScope.Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
        val doc = this@climateDualSetpoint
        doc.key(
            ref = ref(entityId, "temperature_low", "-$step"),
            icon = "minus",
            heightDp = CardChrome.STEPPER_KEY_DP,
            widthDp = CardChrome.STEPPER_KEY_DP,
            round = true,
        )
        Column(Modifier.horizontalWeight(1f), horizontal = RcHorizontalPositioning.Center) {
            doc.label(
                text = doc.i18n.t("ui.low"),
                sp = Type.BODY,
                color = doc.role(Role.INK_SOFT),
                align = RcTextAlign.Center,
            )
            doc.label(
                text = doc.text(Vars.entity(entityId, "temp_low")),
                sp = Type.DISPLAY,
                color = doc.role(Role.INK),
                weight = Weight.NORMAL,
                align = RcTextAlign.Center,
            )
        }
        doc.key(
            ref = ref(entityId, "temperature_low", "+$step"),
            icon = "plus",
            heightDp = CardChrome.STEPPER_KEY_DP,
            widthDp = CardChrome.STEPPER_KEY_DP,
            round = true,
        )
    }
    spacer(CardChrome.PANEL_CORNER_DP)
    rcScope.Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
        val doc = this@climateDualSetpoint
        doc.key(
            ref = ref(entityId, "temperature_high", "-$step"),
            icon = "minus",
            heightDp = CardChrome.STEPPER_KEY_DP,
            widthDp = CardChrome.STEPPER_KEY_DP,
            round = true,
        )
        Column(Modifier.horizontalWeight(1f), horizontal = RcHorizontalPositioning.Center) {
            doc.label(
                text = doc.i18n.t("ui.high"),
                sp = Type.BODY,
                color = doc.role(Role.INK_SOFT),
                align = RcTextAlign.Center,
            )
            doc.label(
                text = doc.text(Vars.entity(entityId, "temp_high")),
                sp = Type.DISPLAY,
                color = doc.role(Role.INK),
                weight = Weight.NORMAL,
                align = RcTextAlign.Center,
            )
        }
        doc.key(
            ref = ref(entityId, "temperature_high", "+$step"),
            icon = "plus",
            heightDp = CardChrome.STEPPER_KEY_DP,
            widthDp = CardChrome.STEPPER_KEY_DP,
            round = true,
        )
    }
}

internal fun DocContext.waterHeaterCard(entityId: String, entityState: EntityState?, w: Float) {
    val min = entityState?.climate?.minTemp ?: CardChrome.DEFAULT_CLIMATE_LIKE_MIN
    val max = entityState?.climate?.maxTemp ?: CardChrome.DEFAULT_CLIMATE_LIKE_MAX
    val step = entityState?.climate?.targetTempStep ?: 1.0
    predict(ref(entityId, "temperature", "+$step"), entityId)
    predict(ref(entityId, "temperature", "-$step"), entityId)
    spacer(CardChrome.PANEL_CORNER_DP)
    rcScope.Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
        val doc = this@waterHeaterCard
        doc.key(
            ref = ref(entityId, "temperature", "-$step"),
            icon = "minus",
            heightDp = CardChrome.STEPPER_KEY_DP,
            widthDp = CardChrome.STEPPER_KEY_DP,
            round = true,
        )
        Column(Modifier.horizontalWeight(1f), horizontal = RcHorizontalPositioning.Center) {
            doc.label(
                text = doc.i18n.t("ui.target"),
                sp = Type.BODY,
                color = doc.role(Role.INK_SOFT),
                align = RcTextAlign.Center,
            )
            doc.label(
                text = doc.text(Vars.entity(entityId, Vars.KEY_VALUE)),
                sp = Type.DISPLAY,
                color = doc.role(Role.INK),
                weight = Weight.NORMAL,
                align = RcTextAlign.Center,
            )
        }
        doc.key(
            ref = ref(entityId, "temperature", "+$step"),
            icon = "plus",
            heightDp = CardChrome.STEPPER_KEY_DP,
            widthDp = CardChrome.STEPPER_KEY_DP,
            round = true,
        )
    }
    spacer(CardChrome.CARD_GAP_DP)
    bar(entityId = entityId, verb = "temperature", width = w, min = min, max = max)
    val modes = entityState?.climate?.operationList.orEmpty()
    if (modes.isNotEmpty()) {
        structural(entityId) { it?.climate?.operationList?.joinToString() ?: "" }
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
    spacer(CardChrome.FLOW_PAD_DP)
    rcScope.Row(Modifier.fillMaxWidth()) {
        val doc = this@valveCard
        doc.key(
            ref = ref(entityId, "open"),
            text = doc.i18n.t("ui.open"),
            icon = "valve-open",
            heightDp = 56f,
            modifier = Modifier.horizontalWeight(1f),
        )
        Spacer(Modifier.width(doc.units.px(CardChrome.PANEL_CORNER_DP)))
        doc.key(
            ref = ref(entityId, "stop"),
            text = doc.i18n.t("ui.stop"),
            icon = "stop",
            heightDp = 56f,
            modifier = Modifier.horizontalWeight(1f),
        )
        Spacer(Modifier.width(doc.units.px(CardChrome.PANEL_CORNER_DP)))
        doc.key(
            ref = ref(entityId, "close"),
            text = doc.i18n.t("ui.close_cover"),
            icon = "valve-closed",
            heightDp = 56f,
            modifier = Modifier.horizontalWeight(1f),
        )
    }
    if (entityState?.cover?.currentPosition != null) {
        section(i18n.t("ui.position"))
        bar(entityId, "position", w)
    }
}
