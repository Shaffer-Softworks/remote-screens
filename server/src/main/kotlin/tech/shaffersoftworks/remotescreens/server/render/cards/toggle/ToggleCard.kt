@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards.toggle

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.cards.CardChrome
import tech.shaffersoftworks.remotescreens.server.render.cards.bigToggle
import tech.shaffersoftworks.remotescreens.server.render.widgets.spacer

internal fun DocContext.toggleCard(entityId: String, entityState: EntityState?) {
    spacer(CardChrome.PANEL_PAD_DP)
    bigToggle(entityId, entityState)
}
