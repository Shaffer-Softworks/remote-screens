@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards

import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.gap
import tech.shaffersoftworks.remotescreens.server.render.widgets.key
import tech.shaffersoftworks.remotescreens.server.render.widgets.label

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.data.vacuum.VacuumUiState
import tech.shaffersoftworks.remotescreens.server.data.vacuum.vacuumRooms
import tech.shaffersoftworks.remotescreens.server.render.widgets.icon
import tech.shaffersoftworks.remotescreens.server.render.widgets.rounded
import tech.shaffersoftworks.remotescreens.server.render.widgets.tap
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import androidx.compose.remote.creation.dsl.RcColumnVerticalPositioning
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.padding

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
            text = this@lockCard.text(Vars.tile("e:$entityId", Vars.KEY_CONFIRM)),
            sp = Type.TITLE,
            color = this@lockCard.role(Role.WARN),
            weight = Weight.SEMI_BOLD,
            align = RcTextAlign.Center,
        )
    }
    if (entityState?.supports(CardChrome.FAN_PERCENTAGE_FLAG) == true) {
        gap(CardChrome.HEADER_GAP_DP)
        key(
            ref = ref(entityId, "open"),
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

internal fun DocContext.vacuumCard(
    entityId: String,
    entityState: EntityState?,
    uiTileId: String,
    ui: VacuumUiState = VacuumUiState(),
) {
    gap(CardChrome.FLOW_PAD_DP)
    scope.Row(Modifier.fillMaxWidth()) {
        val doc = this@vacuumCard
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
    gap(CardChrome.PANEL_CORNER_DP)
    val rooms = entityState?.vacuumRooms().orEmpty()
    if (rooms.isNotEmpty()) {
        structural(entityId) {
            listOf(
                it?.list("room_list")?.joinToString().orEmpty(),
                it?.list("room_ids")?.joinToString().orEmpty(),
            ).joinToString("|")
        }
    }
    scope.Row(Modifier.fillMaxWidth()) {
        val doc = this@vacuumCard
        doc.key(
            ref = ref(entityId, "locate"),
            text = doc.i18n.t("ui.locate"),
            icon = "map-marker",
            heightDp = CardChrome.FLOW_KEY_H_DP,
            modifier = Modifier.horizontalWeight(1f),
        )
        if (rooms.isNotEmpty()) {
            Spacer(Modifier.width(doc.units.px(CardChrome.PANEL_CORNER_DP)))
            doc.roomsKey(
                tileId = uiTileId,
                selectedCount = ui.selectedRooms.size,
                modifier = Modifier.horizontalWeight(1f),
            )
        }
    }
    val speeds = entityState?.list("fan_speed_list").orEmpty()
    if (speeds.isNotEmpty()) {
        structural(entityId) { it?.list("fan_speed_list")?.joinToString() ?: "" }
        section(i18n.t("ui.suction"))
        flowKeys(entityId, "fan_speed", speeds.map { it to it }) { it.attr("fan_speed") }
    }
}

/** Equal-weight Rooms key beside Locate: title plus how many rooms are selected. */
private fun DocContext.roomsKey(tileId: String, selectedCount: Int, modifier: Modifier) {
    val ink = role(Role.INK)
    val soft = role(Role.INK_DIM)
    val height = units.px(CardChrome.FLOW_KEY_H_DP)
    val box = tap(
        modifier.height(height)
            .clip(rounded(CardChrome.FLOW_KEY_H_DP / 2.5f))
            .background(role(Role.KEY))
            .padding(start = units.px(CardChrome.FLOW_PAD_DP), top = 0f, end = units.px(CardChrome.FLOW_PAD_DP), bottom = 0f),
        ActionRef(tileId, "vacuum_rooms"),
    )
    scope.Row(
        box,
        horizontal = RcRowHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@roomsKey
        Column(
            horizontal = RcHorizontalPositioning.Center,
            vertical = RcColumnVerticalPositioning.Center,
        ) {
            Row(vertical = RcVerticalPositioning.Center) {
                doc.icon("floor-plan", 18f, ink)
                Spacer(Modifier.width(doc.units.px(CardChrome.PANEL_CORNER_DP)))
                doc.label(
                    text = doc.i18n.t("ui.rooms"),
                    sp = Type.BODY,
                    color = ink,
                    weight = Weight.MEDIUM,
                )
            }
            if (selectedCount > 0) {
                doc.label(
                    text = doc.i18n.t("ui.rooms_selected", "count" to selectedCount),
                    sp = Type.CAPTION,
                    color = soft,
                )
            }
        }
    }
}

internal fun DocContext.lawnMowerCard(entityId: String) {
    gap(CardChrome.FLOW_PAD_DP)
    scope.Row(Modifier.fillMaxWidth()) {
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
