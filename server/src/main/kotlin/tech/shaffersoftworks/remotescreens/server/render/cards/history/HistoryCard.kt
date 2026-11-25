@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards.history

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.horizontalWeight
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.fill
import tech.shaffersoftworks.remotescreens.server.render.cards.CardChrome
import tech.shaffersoftworks.remotescreens.server.render.cards.media.PLAY_KEY_DP
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.spacer

internal fun DocContext.historyCard(entityId: String, width: Float, height: Float) {
    refreshEvery(HISTORY_REFRESH_S)
    val points = data.history[entityId].orEmpty()
    val entityState = states[entityId]

    if (points.isNotEmpty() && entityState != null) {
        val max = points.maxBy { it.second }
        val min = points.minBy { it.second }
        val unit = entityState.common.unitOfMeasurement.orEmpty()

        rcScope.Row(Modifier.fillMaxWidth()) {
            val doc = this@historyCard

            doc.label(
                text = doc.i18n.t("ui.max", "value" to "${doc.i18n.number(max.second, 1)} $unit"),
                sp = Type.BODY,
                color = doc.role(Role.DANGER),
                weight = Weight.MEDIUM,
                modifier = Modifier.horizontalWeight(1f),
            )

            doc.label(
                text = doc.i18n.t("ui.min", "value" to "${doc.i18n.number(min.second, 1)} $unit"),
                sp = Type.BODY,
                color = doc.role(Role.ACCENT),
                weight = Weight.MEDIUM,
            )
        }

        spacer(CardChrome.PANEL_CORNER_DP)
    }

    graph(
        points = points,
        wDp = width,
        hDp = (height - CardChrome.MEDIA_CTRL_DP).coerceAtLeast(PLAY_KEY_DP),
        line = fill(Role.ACCENT),
        soft = role(Role.INK_SOFT),
    )
}
