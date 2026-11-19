@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards.humidifier

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.horizontalWeight
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.cards.CardChrome
import tech.shaffersoftworks.remotescreens.server.render.cards.bar
import tech.shaffersoftworks.remotescreens.server.render.cards.bigToggle
import tech.shaffersoftworks.remotescreens.server.render.cards.flowKeys
import tech.shaffersoftworks.remotescreens.server.render.cards.ref
import tech.shaffersoftworks.remotescreens.server.render.cards.section
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.key
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.spacer

private const val DEFAULT_HUMIDITY_MIN = 0.0
private const val DEFAULT_HUMIDITY_MAX = 100.0
private const val DEFAULT_HUMIDITY_STEP = 5.0

internal fun DocContext.humidifierCard(
    entityId: String,
    entityState: EntityState?,
    width: Float,
) {
    val min = entityState?.humidifier?.minHumidity ?: DEFAULT_HUMIDITY_MIN
    val max = entityState?.humidifier?.maxHumidity ?: DEFAULT_HUMIDITY_MAX
    val step = entityState?.humidifier?.humidityStep ?: DEFAULT_HUMIDITY_STEP
    bigToggle(entityId, entityState)
    predict(ref(entityId = entityId, verb = "humidity", arg = "+$step"), entityId)
    predict(ref(entityId = entityId, verb = "humidity", arg = "-$step"), entityId)
    section(i18n.t("ui.target_humidity"))

    rcScope.Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
        val doc = this@humidifierCard

        doc.key(
            ref = ref(entityId = entityId, verb = "humidity", arg = "-$step"),
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
            ref = ref(entityId = entityId, verb = "humidity", arg = "+$step"),
            icon = "plus",
            heightDp = CardChrome.STEPPER_KEY_DP,
            widthDp = CardChrome.STEPPER_KEY_DP,
            round = true,
        )
    }

    spacer(CardChrome.CARD_GAP_DP)
    bar(entityId = entityId, verb = "humidity", width = width, min = min, max = max)
    val modes = entityState?.humidifier?.availableModes.orEmpty()

    if (modes.isNotEmpty()) {
        structural(entityId) { it?.humidifier?.availableModes?.joinToString().orEmpty() }
        section(i18n.t("ui.mode"))

        flowKeys(
            entityId = entityId,
            verb = "mode",
            choices = modes.map { it to it.replace('_', ' ').replaceFirstChar(Char::uppercase) },
        ) {
            it.humidifier.mode
        }
    }
}
