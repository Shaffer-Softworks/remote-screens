@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards

import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Role
import tech.shaffersoftworks.remotescreens.server.render.Type
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.Weight
import tech.shaffersoftworks.remotescreens.server.render.gap
import tech.shaffersoftworks.remotescreens.server.render.key
import tech.shaffersoftworks.remotescreens.server.render.label

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.horizontalWeight
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import java.awt.Color

internal fun DocContext.lightCard(entityId: String, entityState: EntityState?, w: Float) {
    val modes = entityState?.list("supported_color_modes").orEmpty()
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
    val effects = entityState?.list("effect_list").orEmpty()
    if (effects.isNotEmpty()) {
        structural(entityId) { it?.list("effect_list")?.joinToString() ?: "" }
        section(i18n.t("ui.effects"))
        flowKeys(
            entityId,
            "effect",
            effects.take(CardChrome.EFFECTS_TAKE).map { it to it },
        ) { it.attr("effect") }
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
            it?.attr("oscillating"),
            it?.attr("direction"),
            it?.list("preset_modes")?.joinToString(),
        ).joinToString("|")
    }
    if (entityState?.supports(
            CardChrome.FAN_OSCILLATE_FLAG,
        ) == true || entityState?.attr("oscillating") != null) {
        section(i18n.t("ui.oscillate"))
        flowKeys(
            entityId,
            "oscillate",
            listOf("true" to i18n.t("state.on"), "false" to i18n.t("state.off")),
        ) { it.attr("oscillating")?.lowercase() }
    }
    if (entityState?.supports(
            CardChrome.FAN_DIRECTION_FLAG,
        ) == true || entityState?.attr("direction") != null) {
        section(i18n.t("ui.direction"))
        flowKeys(
            entityId,
            "direction",
            listOf("forward" to i18n.t("ui.forward"), "reverse" to i18n.t("ui.reverse")),
        ) { it.attr("direction") }
    }
    val presets = entityState?.list("preset_modes").orEmpty()
    if (presets.isNotEmpty()) {
        section(i18n.t("ui.preset"))
        flowKeys(
            entityId,
            "preset",
            presets.map { it to it.replace('_', ' ').replaceFirstChar(Char::uppercase) },
        ) {
            it.attr("preset_mode")
        }
    }
}

internal fun DocContext.humidifierCard(entityId: String, entityState: EntityState?, w: Float) {
    val min = entityState?.number("min_humidity") ?: CardChrome.DEFAULT_HUMIDITY_MIN
    val max = entityState?.number("max_humidity") ?: CardChrome.DEFAULT_HUMIDITY_MAX
    val step = entityState?.number("humidity_step") ?: CardChrome.DEFAULT_HUMIDITY_STEP
    bigToggle(entityId, entityState)
    predict(ref(entityId, "humidity", "+$step"), entityId)
    predict(ref(entityId, "humidity", "-$step"), entityId)
    section(i18n.t("ui.target_humidity"))
    scope.Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
        val doc = this@humidifierCard
        doc.key(
            ref(entityId, "humidity", "-$step"),
            icon = "minus",
            heightDp = CardChrome.STEPPER_KEY_DP,
            widthDp = CardChrome.STEPPER_KEY_DP,
            round = true,
        )
        Column(Modifier.horizontalWeight(1f), horizontal = RcHorizontalPositioning.Center) {
            doc.label(
                doc.text(Vars.entity(entityId, Vars.KEY_VALUE)),
                Type.DISPLAY,
                doc.role(Role.INK),
                Weight.NORMAL,
                align = RcTextAlign.Center,
            )
        }
        doc.key(
            ref(entityId, "humidity", "+$step"),
            icon = "plus",
            heightDp = CardChrome.STEPPER_KEY_DP,
            widthDp = CardChrome.STEPPER_KEY_DP,
            round = true,
        )
    }
    gap(CardChrome.CARD_GAP_DP)
    bar(entityId, "humidity", w, min, max)
    val modes = entityState?.list("available_modes").orEmpty()
    if (modes.isNotEmpty()) {
        structural(entityId) { it?.list("available_modes")?.joinToString() ?: "" }
        section(i18n.t("ui.mode"))
        flowKeys(
            entityId,
            "mode",
            modes.map { it to it.replace('_', ' ').replaceFirstChar(Char::uppercase) },
        ) {
            it.attr("mode")
        }
    }
}
