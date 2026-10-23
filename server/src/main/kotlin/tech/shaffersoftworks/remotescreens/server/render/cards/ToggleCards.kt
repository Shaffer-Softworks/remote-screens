@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards

import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Role
import tech.shaffersoftworks.remotescreens.server.render.Type
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.Weight
import tech.shaffersoftworks.remotescreens.server.render.gap
import tech.shaffersoftworks.remotescreens.server.render.key
import tech.shaffersoftworks.remotescreens.server.render.label

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState

internal fun DocContext.toggleCard(entityId: String, entityState: EntityState?) {
    gap(CardChrome.PANEL_PAD_DP)
    bigToggle(entityId, entityState)
}

internal fun DocContext.lockCard(entityId: String, entityState: EntityState?) {
    gap(CardChrome.CARD_GAP_DP)
    bigToggle(entityId, entityState)
    gap(CardChrome.PANEL_CORNER_DP)
    scope.Box(Modifier.fillMaxWidth(), horizontal = RcHorizontalPositioning.Center) {
        this@lockCard.label(
            this@lockCard.text(Vars.tile("e:$entityId", Vars.KEY_CONFIRM)),
            Type.TITLE,
            this@lockCard.role(Role.WARN),
            Weight.SEMI_BOLD,
            align = RcTextAlign.Center,
        )
    }
    if (entityState?.supports(CardChrome.FAN_PERCENTAGE_FLAG) == true) {
        gap(CardChrome.HEADER_GAP_DP)
        key(
            ref(entityId, "open"),
            text = i18n.t("ui.open_door"),
            icon = "door-open",
            heightDp = CardChrome.LEVEL_BAR_H_DP,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

internal fun DocContext.sirenCard(entityId: String, entityState: EntityState?) {
    bigToggle(entityId, entityState)
    val tones = entityState?.list("available_tones").orEmpty()
    if (tones.isNotEmpty()) {
        structural(entityId) { it?.list("available_tones")?.joinToString() ?: "" }
        section(i18n.t("ui.tone"))
        flowKeys(
            entityId,
            "tone",
            tones.take(CardChrome.SOURCES_TAKE).map { it to it },
        ) { it.attr("tone") }
    }
}

internal fun DocContext.vacuumCard(entityId: String, entityState: EntityState?) {
    gap(CardChrome.FLOW_PAD_DP)
    scope.Row(Modifier.fillMaxWidth()) {
        val doc = this@vacuumCard
        doc.key(
            ref(entityId, "start"),
            text = doc.i18n.t("ui.start"),
            icon = "play",
            heightDp = 56f,
            modifier = Modifier.horizontalWeight(1f),
        )
        Spacer(Modifier.width(doc.units.px(CardChrome.PANEL_CORNER_DP)))
        doc.key(
            ref(entityId, "pause"),
            text = doc.i18n.t("ui.pause"),
            icon = "pause",
            heightDp = 56f,
            modifier = Modifier.horizontalWeight(1f),
        )
        Spacer(Modifier.width(doc.units.px(CardChrome.PANEL_CORNER_DP)))
        doc.key(
            ref(entityId, "dock"),
            text = doc.i18n.t("ui.dock"),
            icon = "home-import-outline",
            heightDp = 56f,
            modifier = Modifier.horizontalWeight(1f),
        )
    }
    gap(CardChrome.PANEL_CORNER_DP)
    key(
        ref(entityId, "locate"),
        text = i18n.t("ui.locate"),
        icon = "map-marker",
        heightDp = CardChrome.FLOW_KEY_H_DP,
        modifier = Modifier.fillMaxWidth(),
    )
    val speeds = entityState?.list("fan_speed_list").orEmpty()
    if (speeds.isNotEmpty()) {
        structural(entityId) { it?.list("fan_speed_list")?.joinToString() ?: "" }
        section(i18n.t("ui.suction"))
        flowKeys(entityId, "fan_speed", speeds.map { it to it }) { it.attr("fan_speed") }
    }
}

internal fun DocContext.lawnMowerCard(entityId: String) {
    gap(CardChrome.FLOW_PAD_DP)
    scope.Row(Modifier.fillMaxWidth()) {
        val doc = this@lawnMowerCard
        doc.key(
            ref(entityId, "start"),
            text = doc.i18n.t("ui.start"),
            icon = "play",
            heightDp = 56f,
            modifier = Modifier.horizontalWeight(1f),
        )
        Spacer(Modifier.width(doc.units.px(CardChrome.PANEL_CORNER_DP)))
        doc.key(
            ref(entityId, "pause"),
            text = doc.i18n.t("ui.pause"),
            icon = "pause",
            heightDp = 56f,
            modifier = Modifier.horizontalWeight(1f),
        )
        Spacer(Modifier.width(doc.units.px(CardChrome.PANEL_CORNER_DP)))
        doc.key(
            ref(entityId, "dock"),
            text = doc.i18n.t("ui.dock"),
            icon = "home-import-outline",
            heightDp = 56f,
            modifier = Modifier.horizontalWeight(1f),
        )
    }
}
