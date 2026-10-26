@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards

import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.HaRenderFlags
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.widgets.gap
import tech.shaffersoftworks.remotescreens.server.render.widgets.key

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.shared.VarValue

internal fun DocContext.coverCard(entityId: String, entityState: EntityState?, w: Float) {
    gap(CardChrome.FLOW_PAD_DP)
    val verbs = coverKeyVerbs(entityState)
    scope.Row(Modifier.fillMaxWidth()) {
        val doc = this@coverCard
        doc.key(
            ref(entityId, verbs.open),
            text = doc.i18n.t("ui.open"),
            icon = "arrow-up",
            heightDp = 56f,
            modifier = Modifier.horizontalWeight(1f),
        )
        Spacer(Modifier.width(doc.units.px(CardChrome.PANEL_CORNER_DP)))
        doc.key(
            ref(entityId, verbs.stop),
            text = doc.i18n.t("ui.stop"),
            icon = "stop",
            heightDp = 56f,
            modifier = Modifier.horizontalWeight(1f),
        )
        Spacer(Modifier.width(doc.units.px(CardChrome.PANEL_CORNER_DP)))
        doc.key(
            ref(entityId, verbs.close),
            text = doc.i18n.t("ui.close_cover"),
            icon = "arrow-down",
            heightDp = 56f,
            modifier = Modifier.horizontalWeight(1f),
        )
    }
    val canSetShade = entityState?.supports(CardChrome.COVER_POSITION_FLAG) == true
    if (canSetShade) {
        section(i18n.t("ui.position"))
        bar(entityId, "position", w)
    }
    if (entityState?.supports(CardChrome.COVER_TILT_FLAG) == true) {
        bind(
            entityId,
        ) { s -> listOf(
            VarValue.float(
                Vars.entity(entityId, "tilt"),
                ((s[entityId]?.number("current_tilt_position") ?: 0.0) / CardChrome.PERCENT_SCALE)
                    .toFloat(),
            ),
        ) }
        section(i18n.t(if (canSetShade) "ui.tilt" else "ui.position"))
        bar(entityId, "tilt", w, level = "tilt")
    }
}

private data class CoverKeyVerbs(val open: String, val stop: String, val close: String)

/** Shade travel uses open/stop/close. A tilt-only blind uses the slat services instead. */
private fun coverKeyVerbs(entityState: EntityState?): CoverKeyVerbs {
    val canMoveShade = entityState?.supports(HaRenderFlags.COVER_OPEN_FLAG) == true ||
        entityState?.supports(HaRenderFlags.COVER_CLOSE_FLAG) == true
    val canTilt = entityState?.supports(HaRenderFlags.COVER_OPEN_TILT_FLAG) == true ||
        entityState?.supports(HaRenderFlags.COVER_CLOSE_TILT_FLAG) == true
    return if (!canMoveShade && canTilt) {
        CoverKeyVerbs(open = "open_tilt", stop = "stop_tilt", close = "close_tilt")
    } else {
        CoverKeyVerbs(open = "open", stop = "stop", close = "close")
    }
}
