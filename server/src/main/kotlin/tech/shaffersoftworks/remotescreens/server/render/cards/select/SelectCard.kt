@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards.select

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.fillMaxWidth
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.bindChoices
import tech.shaffersoftworks.remotescreens.server.render.cards.CardChrome
import tech.shaffersoftworks.remotescreens.server.render.cards.ref
import tech.shaffersoftworks.remotescreens.server.render.widgets.key
import tech.shaffersoftworks.remotescreens.server.render.widgets.spacer

internal fun DocContext.selectCard(entityId: String, entityState: EntityState?) {
    val options = entityState?.input?.options.orEmpty()
    structural(entityId) { it?.input?.options?.joinToString().orEmpty() }
    spacer(CardChrome.FLOW_PAD_DP)
    bindChoices(scopeId = entityId, group = "option", entityId = entityId, choices = options) { it.state }

    options.forEachIndexed { i, o ->
        key(
            ref = ref(entityId = entityId, verb = "option", arg = o),
            text = o,
            heightDp = CardChrome.LEVEL_BAR_H_DP,
            modifier = Modifier.fillMaxWidth(),
            bg = color(Vars.option(scope = entityId, group = "option", index = i, key = Vars.KEY_BG)),
            ink = color(Vars.option(scope = entityId, group = "option", index = i, key = Vars.KEY_INK)),
        )

        spacer(CardChrome.FLOW_PAD_DP)
    }
}
