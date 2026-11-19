@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards.lawnmower

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.cards.CardChrome
import tech.shaffersoftworks.remotescreens.server.render.cards.ref
import tech.shaffersoftworks.remotescreens.server.render.widgets.icon
import tech.shaffersoftworks.remotescreens.server.render.widgets.key
import tech.shaffersoftworks.remotescreens.server.render.widgets.spacer

internal fun DocContext.lawnMowerCard(entityId: String) {
    spacer(CardChrome.FLOW_PAD_DP)

    rcScope.Row(Modifier.fillMaxWidth()) {
        val doc = this@lawnMowerCard

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
            ref = ref(entityId, "dock"),
            text = doc.i18n.t("ui.dock"),
            icon = "home-import-outline",
            heightDp = 56f,
            modifier = Modifier.horizontalWeight(1f),
        )
    }
}
