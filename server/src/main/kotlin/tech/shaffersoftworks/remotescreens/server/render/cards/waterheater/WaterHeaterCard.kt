@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards.waterheater

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
import tech.shaffersoftworks.remotescreens.server.render.cards.climate.DEFAULT_CLIMATE_LIKE_MAX
import tech.shaffersoftworks.remotescreens.server.render.cards.climate.DEFAULT_CLIMATE_LIKE_MIN
import tech.shaffersoftworks.remotescreens.server.render.cards.bar
import tech.shaffersoftworks.remotescreens.server.render.cards.flowKeys
import tech.shaffersoftworks.remotescreens.server.render.cards.ref
import tech.shaffersoftworks.remotescreens.server.render.cards.section
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.key
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.spacer

internal fun DocContext.waterHeaterCard(entityId: String, entityState: EntityState?, width: Float) {
    val min = entityState?.climate?.minTemp ?: DEFAULT_CLIMATE_LIKE_MIN
    val max = entityState?.climate?.maxTemp ?: DEFAULT_CLIMATE_LIKE_MAX
    val step = entityState?.climate?.targetTempStep ?: 1.0
    predict(ref(entityId = entityId, verb = "temperature", arg = "+$step"), entityId)
    predict(ref(entityId = entityId, verb = "temperature", arg = "-$step"), entityId)
    spacer(CardChrome.PANEL_CORNER_DP)

    rcScope.Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
        val doc = this@waterHeaterCard

        doc.key(
            ref = ref(entityId = entityId, verb = "temperature", arg = "-$step"),
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
            ref = ref(entityId = entityId, verb = "temperature", arg = "+$step"),
            icon = "plus",
            heightDp = CardChrome.STEPPER_KEY_DP,
            widthDp = CardChrome.STEPPER_KEY_DP,
            round = true,
        )
    }

    spacer(CardChrome.CARD_GAP_DP)
    bar(entityId = entityId, verb = "temperature", width = width, min = min, max = max)
    val modes = entityState?.climate?.operationList.orEmpty()

    if (modes.isNotEmpty()) {
        structural(entityId) { it?.climate?.operationList?.joinToString().orEmpty() }
        section(i18n.t("ui.mode"))

        flowKeys(
            entityId = entityId,
            verb = "operation",
            choices = modes.map { it to it.replace('_', ' ').replaceFirstChar(Char::uppercase) },
        ) {
            it.state
        }
    }
}
