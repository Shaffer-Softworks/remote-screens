@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards.valve

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.ControlVerb
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.cards.CardChrome
import tech.shaffersoftworks.remotescreens.server.render.cards.bar
import tech.shaffersoftworks.remotescreens.server.render.cards.ref
import tech.shaffersoftworks.remotescreens.server.render.cards.section
import tech.shaffersoftworks.remotescreens.server.render.widgets.key
import tech.shaffersoftworks.remotescreens.server.render.widgets.spacer

internal fun DocContext.valveCard(entityId: String, entityState: EntityState?, width: Float) {
    spacer(CardChrome.FLOW_PAD_DP)

    rcScope.Row(Modifier.fillMaxWidth()) {
        val doc = this@valveCard

        doc.key(
            ref = ref(entityId, "open"),
            text = doc.i18n.t("ui.open"),
            icon = "valve-open",
            heightDp = 56f,
            modifier = Modifier.horizontalWeight(1f),
        )

        Spacer(Modifier.width(doc.units.px(CardChrome.PANEL_CORNER_DP)))

        doc.key(
            ref = ref(entityId, "stop"),
            text = doc.i18n.t("ui.stop"),
            icon = "stop",
            heightDp = 56f,
            modifier = Modifier.horizontalWeight(1f),
        )

        Spacer(Modifier.width(doc.units.px(CardChrome.PANEL_CORNER_DP)))

        doc.key(
            ref = ref(entityId, "close"),
            text = doc.i18n.t("ui.close_cover"),
            icon = "valve-closed",
            heightDp = 56f,
            modifier = Modifier.horizontalWeight(1f),
        )
    }

    if (entityState?.cover?.currentPosition != null) {
        section(i18n.t("ui.position"))
        bar(entityId = entityId, verb = ControlVerb.POSITION, width = width)
    }
}
