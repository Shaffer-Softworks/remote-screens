@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards.timer

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.TimerState
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.cards.CardChrome
import tech.shaffersoftworks.remotescreens.server.render.cards.ref
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.icon
import tech.shaffersoftworks.remotescreens.server.render.widgets.key
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.spacer

internal fun DocContext.timerCard(entityId: String, entityState: EntityState?) {
    spacer(CardChrome.PANEL_CORNER_DP)

    label(
        text = text(Vars.entity(entityId, Vars.KEY_VALUE)),
        sp = Type.DISPLAY,
        color = role(Role.INK),
        weight = Weight.NORMAL,
        align = RcTextAlign.Center,
    )

    spacer(CardChrome.CARD_GAP_DP)

    rcScope.Row(Modifier.fillMaxWidth()) {
        val doc = this@timerCard

        doc.key(
            ref = ref(entityId, "start"),
            text = doc.i18n.t("ui.start"),
            icon = "play",
            heightDp = 56f,
            modifier = Modifier.horizontalWeight(1f),
        )

        Spacer(Modifier.width(doc.units.px(CardChrome.PANEL_CORNER_DP)))

        doc.key(
            ref = ref(entityId, "pause"),
            text = doc.i18n.t("ui.pause"),
            icon = "pause",
            heightDp = 56f,
            modifier = Modifier.horizontalWeight(1f),
        )

        Spacer(Modifier.width(doc.units.px(CardChrome.PANEL_CORNER_DP)))

        doc.key(
            ref = ref(entityId, "cancel"),
            text = doc.i18n.t("ui.cancel"),
            icon = "close",
            heightDp = 56f,
            modifier = Modifier.horizontalWeight(1f),
        )
    }

    if (entityState?.timerState in setOf(TimerState.ACTIVE, TimerState.PAUSED)) {
        spacer(CardChrome.PANEL_CORNER_DP)

        key(
            ref = ref(entityId, "finish"),
            text = i18n.t("ui.finish"),
            icon = "check",
            heightDp = CardChrome.FLOW_KEY_H_DP,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
