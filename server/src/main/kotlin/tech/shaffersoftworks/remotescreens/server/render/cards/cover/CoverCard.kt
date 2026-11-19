@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards.cover

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.ControlVerb
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.HaRenderFlags
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.cards.CardChrome
import tech.shaffersoftworks.remotescreens.server.render.cards.bar
import tech.shaffersoftworks.remotescreens.server.render.cards.ref
import tech.shaffersoftworks.remotescreens.server.render.cards.section
import tech.shaffersoftworks.remotescreens.server.render.widgets.key
import tech.shaffersoftworks.remotescreens.server.render.widgets.spacer
import tech.shaffersoftworks.remotescreens.shared.VarValue

private const val PERCENT_SCALE = 100.0

internal fun DocContext.coverCard(entityId: String, entityState: EntityState?, width: Float) {
    spacer(CardChrome.FLOW_PAD_DP)
    val verbs = coverKeyVerbs(entityState)

    rcScope.Row(Modifier.fillMaxWidth()) {
        val doc = this@coverCard

        doc.key(
            ref = ref(entityId, verbs.open),
            text = doc.i18n.t("ui.open"),
            icon = "arrow-up",
            heightDp = 56f,
            modifier = Modifier.horizontalWeight(1f),
        )

        Spacer(Modifier.width(doc.units.px(CardChrome.PANEL_CORNER_DP)))

        doc.key(
            ref = ref(entityId, verbs.stop),
            text = doc.i18n.t("ui.stop"),
            icon = "stop",
            heightDp = 56f,
            modifier = Modifier.horizontalWeight(1f),
        )

        Spacer(Modifier.width(doc.units.px(CardChrome.PANEL_CORNER_DP)))

        doc.key(
            ref = ref(entityId, verbs.close),
            text = doc.i18n.t("ui.close_cover"),
            icon = "arrow-down",
            heightDp = 56f,
            modifier = Modifier.horizontalWeight(1f),
        )
    }

    val canSetShade = entityState?.supports(HaRenderFlags.COVER_POSITION_FLAG) == true

    if (canSetShade) {
        section(i18n.t("ui.position"))
        bar(entityId = entityId, verb = ControlVerb.POSITION, width = width)
    }

    if (entityState?.supports(HaRenderFlags.COVER_TILT_POSITION_FLAG) == true) {
        bind(
            entityId,
        ) { s -> listOf(
            VarValue.float(
                Vars.entity(entityId, ControlVerb.TILT),
                ((s[entityId]?.cover?.currentTiltPosition ?: 0.0) / PERCENT_SCALE)
                    .toFloat(),
            ),
        ) }

        section(i18n.t(if (canSetShade) "ui.tilt" else "ui.position"))
        bar(entityId = entityId, verb = ControlVerb.TILT, width = width, level = ControlVerb.TILT)
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
