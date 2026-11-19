@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards.input

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.fillMaxWidth
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.cards.CardChrome
import tech.shaffersoftworks.remotescreens.server.render.cards.ref
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.icon
import tech.shaffersoftworks.remotescreens.server.render.widgets.key
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.spacer

internal fun DocContext.inputTextCard(entityId: String, entityState: EntityState?) {
    spacer(CardChrome.PANEL_CORNER_DP)

    label(
        text = entityState?.state?.ifBlank { i18n.t("ui.empty_text") } ?: "—",
        sp = Type.HEADLINE,
        color = role(Role.INK),
        weight = Weight.MEDIUM,
    )

    spacer(CardChrome.CARD_GAP_DP)

    key(
        ref = ref(entityId, "clear"),
        text = i18n.t("ui.clear"),
        icon = "close",
        heightDp = CardChrome.FLOW_KEY_H_DP,
        modifier = Modifier.fillMaxWidth(),
    )
}

internal fun DocContext.inputDatetimeCard(
    @Suppress("UNUSED_PARAMETER") entityId: String,
    entityState: EntityState?,
) {
    spacer(CardChrome.PANEL_CORNER_DP)

    label(
        text = entityState?.state ?: "—",
        sp = Type.DISPLAY,
        color = role(Role.INK),
        weight = Weight.NORMAL,
        align = RcTextAlign.Center,
    )

    spacer(CardChrome.FLOW_PAD_DP)
    label(text = i18n.t("ui.datetime_hint"), sp = Type.BODY, color = role(Role.INK_SOFT), align = RcTextAlign.Center)
}
