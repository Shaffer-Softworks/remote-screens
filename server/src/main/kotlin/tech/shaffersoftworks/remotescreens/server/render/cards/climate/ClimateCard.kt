@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards.climate

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.horizontalWeight
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.Domain
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.cards.CardChrome
import tech.shaffersoftworks.remotescreens.server.render.cards.flowKeys
import tech.shaffersoftworks.remotescreens.server.render.cards.ref
import tech.shaffersoftworks.remotescreens.server.render.cards.section
import tech.shaffersoftworks.remotescreens.server.render.panels.thermostat.usesDualSetpoint
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.key
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.spacer
import tech.shaffersoftworks.remotescreens.shared.VarValue

internal const val DEFAULT_CLIMATE_LIKE_MIN = 40.0
internal const val DEFAULT_CLIMATE_LIKE_MAX = 60.0
private const val TEMP_STEP_F = 1.0
private const val TEMP_STEP_C = 0.5

internal fun DocContext.climateCard(entityId: String, entityState: EntityState?) {
    val step = temperatureStep(entityState)
    spacer(CardChrome.PANEL_CORNER_DP)

    if (entityState.usesDualSetpoint()) {
        climateDualSetpoint(entityId = entityId, entityState = entityState, step = step)
    } else {
        predictTemperature(entityId = entityId, field = "temperature", step = step)

        temperatureStepper(
            entityId = entityId,
            field = "temperature",
            step = step,
            caption = i18n.t("ui.target"),
            valueName = Vars.KEY_VALUE,
        )
    }

    rememberClimateStructure(entityId)

    choiceSection(
        entityId = entityId,
        title = i18n.t("ui.mode"),
        verb = "mode",
        choices = entityState?.climate?.hvacModes.orEmpty(),
        labelOf = { i18n.state(Domain.CLIMATE.value, it) },
        chosen = { it.state },
    )

    choiceSection(
        entityId = entityId,
        title = i18n.t("ui.preset"),
        verb = "preset",
        choices = entityState?.climate?.presetModes.orEmpty(),
        labelOf = { it.asChoiceLabel(replaceUnderscores = true) },
        chosen = { it.climate.presetMode },
    )

    choiceSection(
        entityId = entityId,
        title = i18n.t("ui.speed"),
        verb = "fan",
        choices = entityState?.climate?.fanModes.orEmpty(),
        labelOf = { it.asChoiceLabel(replaceUnderscores = false) },
        chosen = { it.climate.fanMode },
    )

    choiceSection(
        entityId = entityId,
        title = i18n.t("ui.swing"),
        verb = "swing",
        choices = entityState?.climate?.swingModes.orEmpty(),
        labelOf = { it.asChoiceLabel(replaceUnderscores = true) },
        chosen = { it.climate.swingMode },
    )
}

internal fun DocContext.climateDualSetpoint(
    entityId: String,
    @Suppress("UNUSED_PARAMETER") entityState: EntityState?,
    step: Double,
) {
    bind(entityId) { states ->
        val climate = states[entityId]?.climate

        listOf(
            VarValue.string(Vars.entity(entityId, "temp_low"), temperatureLabel(climate?.targetTempLow)),
            VarValue.string(Vars.entity(entityId, "temp_high"), temperatureLabel(climate?.targetTempHigh)),
        )
    }

    predictTemperature(entityId = entityId, field = "temperature_low", step = step)
    predictTemperature(entityId = entityId, field = "temperature_high", step = step)

    temperatureStepper(
        entityId = entityId,
        field = "temperature_low",
        step = step,
        caption = i18n.t("ui.low"),
        valueName = "temp_low",
    )

    spacer(CardChrome.PANEL_CORNER_DP)

    temperatureStepper(
        entityId = entityId,
        field = "temperature_high",
        step = step,
        caption = i18n.t("ui.high"),
        valueName = "temp_high",
    )
}

private fun DocContext.temperatureStep(entityState: EntityState?): Double =
    entityState?.climate?.targetTempStep
        ?: if (homeAssistantConfig.temperatureUnit.contains("F")) TEMP_STEP_F else TEMP_STEP_C

private fun DocContext.temperatureLabel(temperature: Double?): String =
    temperature?.let { i18n.number(it, if (it % 1.0 == 0.0) 0 else 1) + "°" } ?: "–"

private fun DocContext.predictTemperature(entityId: String, field: String, step: Double) {
    predict(ref(entityId = entityId, verb = field, arg = "+$step"), entityId)
    predict(ref(entityId = entityId, verb = field, arg = "-$step"), entityId)
}

private fun DocContext.temperatureStepper(
    entityId: String,
    field: String,
    step: Double,
    caption: String,
    valueName: String,
) {
    rcScope.Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
        val doc = this@temperatureStepper
        doc.stepKey(entityId = entityId, field = field, amount = "-$step", icon = "minus")

        Column(Modifier.horizontalWeight(1f), horizontal = RcHorizontalPositioning.Center) {
            doc.label(
                text = caption,
                sp = Type.BODY,
                color = doc.role(Role.INK_SOFT),
                align = RcTextAlign.Center,
            )

            doc.label(
                text = doc.text(Vars.entity(entityId, valueName)),
                sp = Type.DISPLAY,
                color = doc.role(Role.INK),
                weight = Weight.NORMAL,
                align = RcTextAlign.Center,
            )
        }

        doc.stepKey(entityId = entityId, field = field, amount = "+$step", icon = "plus")
    }
}

private fun DocContext.stepKey(entityId: String, field: String, amount: String, icon: String) {
    key(
        ref = ref(entityId = entityId, verb = field, arg = amount),
        icon = icon,
        heightDp = CardChrome.STEPPER_KEY_DP,
        widthDp = CardChrome.STEPPER_KEY_DP,
        round = true,
    )
}

private fun DocContext.rememberClimateStructure(entityId: String) {
    structural(entityId) {
        val climate = it?.climate

        val lists = listOf(climate?.hvacModes, climate?.presetModes, climate?.fanModes, climate?.swingModes)
            .joinToString("|") { modes -> modes?.joinToString().orEmpty() }

        listOf(lists, it?.state, it.usesDualSetpoint(), climate?.targetTempHigh, climate?.targetTempLow)
            .joinToString("|")
    }
}

private fun DocContext.choiceSection(
    entityId: String,
    title: String,
    verb: String,
    choices: List<String>,
    labelOf: (String) -> String,
    chosen: (EntityState) -> String?,
) {
    if (choices.isEmpty()) return

    section(title)

    flowKeys(
        entityId = entityId,
        verb = verb,
        choices = choices.map { it to labelOf(it) },
        chosen = chosen,
    )
}

private fun String.asChoiceLabel(replaceUnderscores: Boolean): String {
    val words = if (replaceUnderscores) replace('_', ' ') else this

    return words.replaceFirstChar(Char::uppercase)
}
