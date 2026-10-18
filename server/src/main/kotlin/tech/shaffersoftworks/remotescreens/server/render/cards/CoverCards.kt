@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.shared.VarValue

internal fun DocContext.coverCard(entityId: String, entityState: EntityState?, w: Float) {
    gap(CardChrome.FLOW_PAD_DP)
    scope.Row(Modifier.fillMaxWidth()) {
        val doc = this@coverCard
        doc.key(
            ref(entityId, "open"),
            text = doc.i18n.t("ui.open"),
            icon = "arrow-up",
            heightDp = 56f,
            modifier = Modifier.horizontalWeight(1f),
        )
        Spacer(Modifier.width(doc.units.px(CardChrome.PANEL_CORNER_DP)))
        doc.key(
            ref(entityId, "stop"),
            text = doc.i18n.t("ui.stop"),
            icon = "stop",
            heightDp = 56f,
            modifier = Modifier.horizontalWeight(1f),
        )
        Spacer(Modifier.width(doc.units.px(CardChrome.PANEL_CORNER_DP)))
        doc.key(
            ref(entityId, "close"),
            text = doc.i18n.t("ui.close_cover"),
            icon = "arrow-down",
            heightDp = 56f,
            modifier = Modifier.horizontalWeight(1f),
        )
    }
    if (entityState?.supports(CardChrome.COVER_POSITION_FLAG) == true) {
        section(i18n.t("ui.position"))
        bar(entityId, "position", w)
    }
    if (entityState?.supports(CardChrome.COVER_TILT_FLAG) == true) {
        bind(
            entityId,
        ) { s -> listOf(
            VarValue.float(
                Vars.entity(entityId, "tilt"),
                ((s[entityId]?.number("current_tilt_position") ?: 0.0) / 100).toFloat(),
            ),
        ) }
        section(i18n.t("ui.tilt"))
        bar(entityId, "tilt", w, level = "tilt")
    }
}
