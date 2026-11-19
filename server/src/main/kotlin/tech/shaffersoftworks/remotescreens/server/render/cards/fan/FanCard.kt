@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards.fan

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.ControlVerb
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.HaRenderFlags
import tech.shaffersoftworks.remotescreens.server.render.cards.bar
import tech.shaffersoftworks.remotescreens.server.render.cards.bigToggle
import tech.shaffersoftworks.remotescreens.server.render.cards.flowKeys
import tech.shaffersoftworks.remotescreens.server.render.cards.section

private const val FAN_PERCENTAGE_FLAG = HaRenderFlags.FAN_PERCENTAGE_FLAG
private const val FAN_OSCILLATE_FLAG = 2
private const val FAN_DIRECTION_FLAG = 4

internal fun DocContext.fanCard(
    entityId: String,
    entityState: EntityState?,
    width: Float,
) {
    bigToggle(entityId, entityState)

    if (entityState?.supports(FAN_PERCENTAGE_FLAG) == true) {
        section(i18n.t("ui.speed"))
        bar(entityId = entityId, verb = ControlVerb.PERCENTAGE, width = width)
    }

    structural(entityId) {
        listOf(
            it?.fan?.oscillating?.toString(),
            it?.fan?.direction,
            it?.fan?.presetModes?.joinToString(),
        ).joinToString("|")
    }

    if (entityState?.supports(
            FAN_OSCILLATE_FLAG,
        ) == true || entityState?.fan?.oscillating != null) {
        section(i18n.t("ui.oscillate"))

        flowKeys(
            entityId = entityId,
            verb = "oscillate",
            choices = listOf("true" to i18n.t("state.on"), "false" to i18n.t("state.off")),
        ) { it.fan.oscillating?.toString() }
    }

    if (entityState?.supports(
            FAN_DIRECTION_FLAG,
        ) == true || entityState?.fan?.direction != null) {
        section(i18n.t("ui.direction"))

        flowKeys(
            entityId = entityId,
            verb = "direction",
            choices = listOf("forward" to i18n.t("ui.forward"), "reverse" to i18n.t("ui.reverse")),
        ) { it.fan.direction }
    }

    val presets = entityState?.fan?.presetModes.orEmpty()

    if (presets.isNotEmpty()) {
        section(i18n.t("ui.preset"))

        flowKeys(
            entityId = entityId,
            verb = "preset",
            choices = presets.map { it to it.replace('_', ' ').replaceFirstChar(Char::uppercase) },
        ) {
            it.fan.presetMode
        }
    }
}
