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

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.horizontalWeight
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import java.awt.Color

internal fun DocContext.lightCard(entityId: String, entityState: EntityState?, w: Float) {
    val modes = entityState?.light?.supportedColorModes.orEmpty()
    bigToggle(entityId, entityState)
    if (modes.any { it != "onoff" }) {
        section(i18n.t("ui.brightness"))
        bar(entityId, "brightness", w)
    }
    if (modes.any { it in setOf("hs", "rgb", "rgbw", "rgbww", "xy") }) {
        section(i18n.t("ui.colour"))
        swatches(
            HUES.map { h -> Color.HSBtoRGB(
                h / CardChrome.HUE_DEGREES,
                CardChrome.HUE_SATURATION,
                1f,
            ) to ref(entityId, "hue", h.toString()) },
        )
    }
    if ("color_temp" in modes) {
        section(i18n.t("ui.white"))
        swatches(
            KELVINS.map { kelvin -> kelvinColor(
                kelvin,
            ) to ref(entityId, "kelvin", kelvin.toString()) },
        )
    }
    val effects = entityState?.light?.effectList.orEmpty()
    if (effects.isNotEmpty()) {
        structural(entityId) { it?.light?.effectList?.joinToString() ?: "" }
        section(i18n.t("ui.effects"))
        flowKeys(
            entityId,
            "effect",
            effects.take(CardChrome.EFFECTS_TAKE).map { it to it },
        ) { it.light.effect }
    }
}

internal fun DocContext.fanCard(entityId: String, entityState: EntityState?, w: Float) {
    bigToggle(entityId, entityState)
    if (entityState?.supports(CardChrome.FAN_PERCENTAGE_FLAG) == true) {
        section(i18n.t("ui.speed"))
        bar(entityId, "percentage", w)
    }
    structural(entityId) {
        listOf(
            it?.fan?.oscillating?.toString(),
            it?.fan?.direction,
            it?.fan?.presetModes?.joinToString(),
        ).joinToString("|")
    }
    if (entityState?.supports(
            CardChrome.FAN_OSCILLATE_FLAG,
        ) == true || entityState?.fan?.oscillating != null) {
        section(i18n.t("ui.oscillate"))
        flowKeys(
            entityId,
            "oscillate",
            listOf("true" to i18n.t("state.on"), "false" to i18n.t("state.off")),
        ) { it.fan.oscillating?.toString() }
    }
    if (entityState?.supports(
            CardChrome.FAN_DIRECTION_FLAG,
        ) == true || entityState?.fan?.direction != null) {
        section(i18n.t("ui.direction"))
        flowKeys(
            entityId,
            "direction",
            listOf("forward" to i18n.t("ui.forward"), "reverse" to i18n.t("ui.reverse")),
        ) { it.fan.direction }
    }
    val presets = entityState?.fan?.presetModes.orEmpty()
    if (presets.isNotEmpty()) {
        section(i18n.t("ui.preset"))
        flowKeys(
            entityId,
            "preset",
            presets.map { it to it.replace('_', ' ').replaceFirstChar(Char::uppercase) },
        ) {
            it.fan.presetMode
        }
    }
}

internal fun DocContext.humidifierCard(entityId: String, entityState: EntityState?, w: Float) {
    val min = entityState?.humidifier?.minHumidity ?: CardChrome.DEFAULT_HUMIDITY_MIN
    val max = entityState?.humidifier?.maxHumidity ?: CardChrome.DEFAULT_HUMIDITY_MAX
    val step = entityState?.humidifier?.humidityStep ?: CardChrome.DEFAULT_HUMIDITY_STEP
    bigToggle(entityId, entityState)
    predict(ref(entityId, "humidity", "+$step"), entityId)
    predict(ref(entityId, "humidity", "-$step"), entityId)
    section(i18n.t("ui.target_humidity"))
    rcScope.Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
        val doc = this@humidifierCard
        doc.key(
            ref = ref(entityId, "humidity", "-$step"),
            icon = "minus",
            heightDp = CardChrome.STEPPER_KEY_DP,
            widthDp = CardChrome.STEPPER_KEY_DP,
            round = true,
        )
        Column(Modifier.horizontalWeight(1f), horizontal = RcHorizontalPositioning.Center) {
            doc.label(
                text = doc.text(Vars.entity(entityId, Vars.KEY_VALUE)),
                sp = Type.DISPLAY,
                color = doc.role(Role.INK),
                weight = Weight.NORMAL,
                align = RcTextAlign.Center,
            )
        }
        doc.key(
            ref = ref(entityId, "humidity", "+$step"),
            icon = "plus",
            heightDp = CardChrome.STEPPER_KEY_DP,
            widthDp = CardChrome.STEPPER_KEY_DP,
            round = true,
        )
    }
    spacer(CardChrome.CARD_GAP_DP)
    bar(entityId = entityId, verb = "humidity", width = w, min = min, max = max)
    val modes = entityState?.humidifier?.availableModes.orEmpty()
    if (modes.isNotEmpty()) {
        structural(entityId) { it?.humidifier?.availableModes?.joinToString() ?: "" }
        section(i18n.t("ui.mode"))
        flowKeys(
            entityId,
            "mode",
            modes.map { it to it.replace('_', ' ').replaceFirstChar(Char::uppercase) },
        ) {
            it.humidifier.mode
        }
    }
}
