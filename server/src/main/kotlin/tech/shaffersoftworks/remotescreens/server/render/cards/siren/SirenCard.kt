@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards.siren

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.cards.CardChrome
import tech.shaffersoftworks.remotescreens.server.render.cards.bigToggle
import tech.shaffersoftworks.remotescreens.server.render.cards.flowKeys
import tech.shaffersoftworks.remotescreens.server.render.cards.section

internal fun DocContext.sirenCard(entityId: String, entityState: EntityState?) {
    bigToggle(entityId, entityState)
    val tones = entityState?.siren?.availableTones.orEmpty()

    if (tones.isNotEmpty()) {
        structural(entityId) { it?.siren?.availableTones?.joinToString().orEmpty() }
        section(i18n.t("ui.tone"))

        flowKeys(
            entityId = entityId,
            verb = "tone",
            choices = tones.take(CardChrome.SOURCES_TAKE).map { it to it },
        ) { it.siren.tone }
    }
}
