@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards.lock

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.fillMaxWidth
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.HaRenderFlags
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.cards.CardChrome
import tech.shaffersoftworks.remotescreens.server.render.cards.bigToggle
import tech.shaffersoftworks.remotescreens.server.render.cards.ref
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.icon
import tech.shaffersoftworks.remotescreens.server.render.widgets.key
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.spacer

internal fun DocContext.lockCard(entityId: String, entityState: EntityState?) {
    spacer(CardChrome.CARD_GAP_DP)
    bigToggle(entityId, entityState)
    spacer(CardChrome.PANEL_CORNER_DP)

    rcScope.Box(Modifier.fillMaxWidth(), horizontal = RcHorizontalPositioning.Center) {
        this@lockCard.label(
            text = this@lockCard.text(Vars.tile("e:$entityId", Vars.KEY_CONFIRM)),
            sp = Type.TITLE,
            color = this@lockCard.role(Role.WARN),
            weight = Weight.SEMI_BOLD,
            align = RcTextAlign.Center,
        )
    }

    if (entityState?.supports(HaRenderFlags.FAN_PERCENTAGE_FLAG) == true) {
        spacer(CardChrome.HEADER_GAP_DP)

        key(
            ref = ref(entityId, "open"),
            text = i18n.t("ui.open_door"),
            icon = "door-open",
            heightDp = CardChrome.LEVEL_BAR_H_DP,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
