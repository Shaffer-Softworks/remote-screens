package tech.shaffersoftworks.remotescreens.server.player.action

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistant
import tech.shaffersoftworks.remotescreens.server.data.renderdata.RenderDataRepository
import tech.shaffersoftworks.remotescreens.server.data.vacuum.vacuumRooms
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Verbs of a full vacuum page / vacuum card: choosing a vacuum, opening the rooms dialog, toggling rooms,
 * and starting a selective clean via `vacuum.send_command` / `app_segment_clean`.
 */
class VacuumActions(
    private val homeAssistant: HomeAssistant,
    private val renderData: RenderDataRepository?,
) {
    suspend fun handle(verb: String, arg: String, target: TapTarget): TapOutcome {
        val tileId = target.tileId
        val store = renderData?.vacuumUi ?: return TapOutcome.Ignored
        return when (verb) {
            "vacuum_select" -> {
                if (!arg.startsWith("vacuum.")) return TapOutcome.Ignored
                store.selectVacuum(tileId, arg)
                TapOutcome.Rebuild
            }
            "vacuum_rooms" -> {
                val entityId = roomsEntityId(target) ?: return TapOutcome.Ignored
                val rooms = homeAssistant.states.value[entityId]?.vacuumRooms().orEmpty()
                if (rooms.isEmpty()) return TapOutcome.Ignored
                store.openRooms(tileId)
                TapOutcome.Rebuild
            }
            "vacuum_rooms_dismiss" -> {
                store.dismissRooms(tileId)
                TapOutcome.Rebuild
            }
            "vacuum_rooms_toggle" -> {
                if (arg.isBlank()) return TapOutcome.Ignored
                store.toggleRoom(tileId, arg)
                TapOutcome.Rebuild
            }
            "vacuum_rooms_clear" -> {
                store.clearRooms(tileId)
                TapOutcome.Rebuild
            }
            "vacuum_rooms_clean" -> cleanRooms(tileId = tileId, target = target)
            else -> TapOutcome.Ignored
        }
    }

    private suspend fun cleanRooms(tileId: String, target: TapTarget): TapOutcome {
        val store = renderData?.vacuumUi ?: return TapOutcome.Ignored
        val entityId = roomsEntityId(target) ?: return TapOutcome.Ignored
        val selected = store.state(tileId).selectedRooms
        if (selected.isEmpty()) return TapOutcome.Ignored
        val rooms = homeAssistant.states.value[entityId]?.vacuumRooms().orEmpty()
        val allowed = rooms.map { it.id }.toSet()
        val params = selected.filter { it in allowed }
        if (params.isEmpty()) return TapOutcome.Ignored

        homeAssistant.callService(
            domain = "vacuum",
            service = "send_command",
            data = buildJsonObject {
                put("entity_id", entityId)
                put("command", SEGMENT_CLEAN_COMMAND)
                put(
                    "params",
                    JsonArray(
                        params.map { id ->
                            id.toIntOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(id)
                        },
                    ),
                )
            },
        )
        store.afterClean(tileId)
        return TapOutcome.Rebuild
    }

    /** The vacuum whose rooms dialog is open: the page's selection, else the card / tile entity. */
    private fun roomsEntityId(target: TapTarget): String? {
        val store = renderData?.vacuumUi ?: return target.entityId
        val ui = store.state(target.tileId)
        return ui.selectedVacuum?.takeIf { it.startsWith("vacuum.") }
            ?: target.entityId?.takeIf { it.startsWith("vacuum.") }
            ?: target.tile?.entityId?.takeIf { it.startsWith("vacuum.") }
    }

    private companion object {
        const val SEGMENT_CLEAN_COMMAND = "app_segment_clean"
    }
}
