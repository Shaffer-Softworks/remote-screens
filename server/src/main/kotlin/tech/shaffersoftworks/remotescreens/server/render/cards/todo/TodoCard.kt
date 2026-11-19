@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards.todo

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.fillMaxWidth
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.cards.CardChrome
import tech.shaffersoftworks.remotescreens.server.render.cards.ref
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.widgets.icon
import tech.shaffersoftworks.remotescreens.server.render.widgets.key
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.spacer

private const val TODO_CARD_TAKE = 12

internal fun DocContext.todoCard(entityId: String) {
    val items = data.todos[entityId].orEmpty()
    spacer(CardChrome.FLOW_PAD_DP)

    if (items.isEmpty()) {
        label(text = i18n.t("ui.no_todos"), sp = Type.HEADLINE, color = role(Role.INK_SOFT))

        return
    }

    items.take(TODO_CARD_TAKE).forEach { item ->
        key(
            ref = ref(entityId = entityId, verb = "complete", arg = item.uid),
            text = item.summary,
            icon = "checkbox-blank-outline",
            heightDp = CardChrome.FLOW_KEY_H_DP,
            modifier = Modifier.fillMaxWidth(),
        )

        spacer(CardChrome.FLOW_PAD_DP)
    }
}
