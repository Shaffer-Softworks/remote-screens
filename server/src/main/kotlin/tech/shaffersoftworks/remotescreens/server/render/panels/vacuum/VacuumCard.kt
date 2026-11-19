@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.panels.vacuum

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcColumnVerticalPositioning
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityId
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.data.vacuum.VacuumUiState
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.cards.CardChrome
import tech.shaffersoftworks.remotescreens.server.render.cards.flowKeys
import tech.shaffersoftworks.remotescreens.server.render.cards.ref
import tech.shaffersoftworks.remotescreens.server.render.cards.section
import tech.shaffersoftworks.remotescreens.server.render.panels.vacuum.vacuumRoomsDialog
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.widgets.KEY_CORNER_DIVISOR
import tech.shaffersoftworks.remotescreens.server.render.widgets.KEY_ICON_DP
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.icon
import tech.shaffersoftworks.remotescreens.server.render.widgets.key
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.rounded
import tech.shaffersoftworks.remotescreens.server.render.widgets.spacer
import tech.shaffersoftworks.remotescreens.server.render.widgets.tap
import tech.shaffersoftworks.remotescreens.shared.ActionRef

private const val VACUUM_ACTION_KEY_DP = 56f

private data class VacuumAction(val verb: String, val labelKey: String, val icon: String)

private val VACUUM_ACTIONS = listOf(
    VacuumAction(verb = "start", labelKey = "ui.start", icon = "play"),
    VacuumAction(verb = "pause", labelKey = "ui.pause", icon = "pause"),
    VacuumAction(verb = "dock", labelKey = "ui.dock", icon = "home-import-outline"),
)

internal fun cleaningModeEntityId(vacuumId: String): String =
    "select.${EntityId(vacuumId).objectId}_cleaning_mode"

// TODO add battery
internal fun vacuumBatteryEntityId(vacuumId: String): String =
    "sensor.${EntityId(vacuumId).objectId}_battery"

internal fun DocContext.vacuumCard(
    entityId: String,
    entityState: EntityState?,
    uiTileId: String,
    ui: VacuumUiState = VacuumUiState(),
) {
    spacer(CardChrome.FLOW_PAD_DP)

    rcScope.Row(Modifier.fillMaxWidth()) {
        val doc = this@vacuumCard

        VACUUM_ACTIONS.forEachIndexed { index, action ->
            if (index > 0) {
                Spacer(Modifier.width(doc.units.px(CardChrome.PANEL_CORNER_DP)))
            }

            doc.key(
                ref = ref(entityId, action.verb),
                text = doc.i18n.t(action.labelKey),
                icon = action.icon,
                heightDp = VACUUM_ACTION_KEY_DP,
                modifier = Modifier.horizontalWeight(1f),
            )
        }
    }

    spacer(CardChrome.PANEL_CORNER_DP)
    vacuumLocateRow(entityId = entityId, uiTileId = uiTileId, ui = ui)

    val speeds = entityState?.vacuum?.fanSpeedList.orEmpty()

    if (speeds.isNotEmpty()) {
        structural(entityId) { it?.vacuum?.fanSpeedList?.joinToString().orEmpty() }
        section(i18n.t("ui.suction"))

        flowKeys(
            entityId = entityId,
            verb = "fan_speed",
            choices = speeds.map { it to i18n.optionLabel(it) },
        ) {
            it.vacuum.fanSpeed
        }
    }

    val cleaningModeId = cleaningModeEntityId(entityId)
    val cleaningMode = cleaningModeId.let { states[it] }
    val cleaningModes = cleaningMode?.input?.options.orEmpty()

    if (cleaningModes.isNotEmpty()) {
        structural(cleaningModeId) { it?.input?.options?.joinToString().orEmpty() }
        section(i18n.t("ui.cleaning_mode"))

        flowKeys(
            entityId = cleaningModeId,
            verb = "option",
            choices = cleaningModes.map { it to i18n.optionLabel(it) },
        ) {
            it.state
        }
    }
}

/** Locate, and beside it a Rooms key when the vacuum has rooms to choose from. */
private fun DocContext.vacuumLocateRow(entityId: String, uiTileId: String, ui: VacuumUiState) {
    // Segment rooms come from attributes; a changed list must rebuild the key and the dialog.
    structural(entityId) {
        listOf(
            it?.vacuum?.roomList?.joinToString().orEmpty(),
            it?.vacuum?.roomIds?.joinToString().orEmpty(),
        ).joinToString("|")
    }

    val vacuumRooms = data.vacuumRooms[entityId]

    rcScope.Row(Modifier.fillMaxWidth()) {
        val doc = this@vacuumLocateRow

        doc.key(
            ref = ref(entityId, "locate"),
            text = doc.i18n.t("ui.locate"),
            icon = "map-marker",
            heightDp = CardChrome.FLOW_KEY_H_DP,
            modifier = Modifier.horizontalWeight(1f),
        )

        if (vacuumRooms != null) {
            Spacer(Modifier.width(doc.units.px(CardChrome.PANEL_CORNER_DP)))

            doc.roomsKey(
                tileId = uiTileId,
                selectedCount = vacuumRooms.selectedIds(ui.selectedRooms).size,
                modifier = Modifier.horizontalWeight(1f),
            )
        }
    }
}

/** Equal-weight Rooms key beside Locate: title plus how many rooms are selected. */
private fun DocContext.roomsKey(tileId: String, selectedCount: Int, modifier: Modifier) {
    val ink = role(Role.INK)
    val soft = role(Role.INK_DIM)
    val pad = units.px(CardChrome.FLOW_PAD_DP)

    val box = tap(
        modifier.height(units.px(CardChrome.FLOW_KEY_H_DP))
            .clip(rounded(CardChrome.FLOW_KEY_H_DP / KEY_CORNER_DIVISOR))
            .background(role(Role.KEY))
            .padding(start = pad, top = 0f, end = pad, bottom = 0f),
        ActionRef(tileId, "vacuum_rooms"),
    )

    rcScope.Row(
        modifier = box,
        horizontal = RcRowHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@roomsKey

        Column(
            horizontal = RcHorizontalPositioning.Center,
            vertical = RcColumnVerticalPositioning.Center,
        ) {
            Row(vertical = RcVerticalPositioning.Center) {
                doc.icon(name = "floor-plan", sizeDp = KEY_ICON_DP, color = ink)
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


/** A vacuum card's rooms dialog, over the whole card document, while it is open. */
internal fun DocContext.vacuumRoomsLayer(entityId: String, entityState: EntityState?, screenW: Float) {
    val tileId = "e:$entityId"
    val ui = data.vacuumUi[tileId]?.takeIf { it.roomsDialogOpen }
    val vacuumRooms = data.vacuumRooms[entityId]

    if (ui != null && vacuumRooms != null) {
        vacuumRoomsDialog(
            tileId = tileId,
            vacuumName = entityState?.name?.ifBlank { null } ?: entityId,
            vacuumRooms = vacuumRooms,
            ui = ui,
            screenW = screenW,
        )
    }
}
