@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards.number

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
import tech.shaffersoftworks.remotescreens.server.render.cards.ref
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.key
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.spacer

internal fun DocContext.numberCard(entityId: String, entityState: EntityState?, width: Float) {
    val min = entityState?.input?.min ?: 0.0
    val max = entityState?.input?.max ?: CardChrome.LEVEL_DEFAULT_MAX
    val step = entityState?.input?.step ?: 1.0
    spacer(CardChrome.PANEL_CORNER_DP)

    rcScope.Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
        val doc = this@numberCard

        doc.key(
            ref = ref(entityId = entityId, verb = "value", arg = "-$step"),
            icon = "minus",
            heightDp = CardChrome.TOGGLE_ICON_DP,
            widthDp = CardChrome.TOGGLE_ICON_DP,
            round = true,
        )

        Box(Modifier.horizontalWeight(1f), horizontal = RcHorizontalPositioning.Center) {
            doc.label(
                text = doc.text(Vars.entity(entityId, Vars.KEY_STATE)),
                sp = Type.DISPLAY,
                color = doc.role(Role.INK),
                weight = Weight.NORMAL,
                align = RcTextAlign.Center,
            )
        }

        doc.key(
            ref = ref(entityId = entityId, verb = "value", arg = "+$step"),
            icon = "plus",
            heightDp = CardChrome.TOGGLE_ICON_DP,
            widthDp = CardChrome.TOGGLE_ICON_DP,
            round = true,
        )
    }

    spacer(CardChrome.CARD_GAP_DP)
    bar(entityId = entityId, verb = "value", width = width, min = min, max = max)
}
