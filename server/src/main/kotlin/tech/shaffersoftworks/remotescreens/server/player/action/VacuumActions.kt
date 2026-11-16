package tech.shaffersoftworks.remotescreens.server.player.action

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistant
import tech.shaffersoftworks.remotescreens.server.data.renderdata.RenderDataRepository
import tech.shaffersoftworks.remotescreens.server.data.vacuum.VACUUM_PREFIX
import tech.shaffersoftworks.remotescreens.server.data.vacuum.VacuumCleanMethod
import tech.shaffersoftworks.remotescreens.server.data.vacuum.VacuumUiStore
import tech.shaffersoftworks.remotescreens.server.data.vacuum.selectedVacuumId
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Verbs of a full vacuum page / vacuum card: choosing a vacuum, opening the rooms dialog, toggling rooms, and
 * starting a selective clean (`vacuum.clean_area`, or `vacuum.send_command` / `app_segment_clean`).
 */
class VacuumActions(
    private val homeAssistant: HomeAssistant,
    private val renderData: RenderDataRepository?,
) {
    suspend fun handle(verb: String, arg: String, target: TapTarget): TapOutcome {
        val repository = renderData ?: return TapOutcome.Ignored
        val store = repository.vacuumUi
        val tileId = target.tileId
        val isHandled = when (verb) {
            "vacuum_select" -> {
                val isVacuum = arg.startsWith(VACUUM_PREFIX)
                if (isVacuum) store.selectVacuum(tileId, arg)
                isVacuum
            }
            "vacuum_rooms" -> openRooms(repository = repository, target = target)
            "vacuum_rooms_dismiss" -> {
                store.dismissRooms(tileId)
                true
            }
            "vacuum_rooms_toggle" -> {
                val isRoom = arg.isNotBlank()
                if (isRoom) store.toggleRoom(tileId, arg)
                isRoom
            }
            "vacuum_rooms_clean" -> cleanRooms(repository = repository, target = target)
            else -> false
        }
        return if (isHandled) TapOutcome.Rebuild else TapOutcome.Ignored
    }

    private suspend fun openRooms(repository: RenderDataRepository, target: TapTarget): Boolean {
        val entityId = roomsEntityId(store = repository.vacuumUi, target = target)
        val hasRooms = entityId != null && repository.vacuumRooms(entityId) != null
        if (hasRooms) repository.vacuumUi.openRooms(target.tileId)
        return hasRooms
    }

    private suspend fun cleanRooms(repository: RenderDataRepository, target: TapTarget): Boolean {
        val store = repository.vacuumUi
        val entityId = roomsEntityId(store = store, target = target)
        val rooms = entityId?.let { repository.vacuumRooms(it) }
        val roomIds = rooms?.selectedIds(store.state(target.tileId).selectedRooms).orEmpty()
        if (entityId == null || rooms == null || roomIds.isEmpty()) return false

        when (rooms.method) {
            VacuumCleanMethod.AREA -> homeAssistant.callService(
                domain = "vacuum",
                service = "clean_area",
                data = areaCleanData(entityId = entityId, areaIds = roomIds),
            )
            VacuumCleanMethod.SEGMENT -> homeAssistant.callService(
                domain = "vacuum",
                service = "send_command",
                data = segmentCleanData(entityId = entityId, segmentIds = roomIds),
            )
        }
        store.afterClean(target.tileId)
        return true
    }

    /** The vacuum whose rooms dialog is open, chosen the same way the page chooses what to show. */
    private fun roomsEntityId(store: VacuumUiStore, target: TapTarget): String? = selectedVacuumId(
        chosen = store.state(target.tileId).selectedVacuum,
        tileEntityId = target.entityId,
        vacuumIds = homeAssistant.states.value.keys.filter { it.startsWith(VACUUM_PREFIX) },
    )

    private fun areaCleanData(entityId: String, areaIds: List<String>): JsonObject = buildJsonObject {
        put("entity_id", entityId)
        put("cleaning_area_id", JsonArray(areaIds.map(::JsonPrimitive)))
    }

    private fun segmentCleanData(entityId: String, segmentIds: List<String>): JsonObject = buildJsonObject {
        put("entity_id", entityId)
        put("command", SEGMENT_CLEAN_COMMAND)
        put(
            "params",
            JsonArray(
                segmentIds.map { id ->
                    id.toIntOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(id)
                },
            ),
        )
    }

    private companion object {
        const val SEGMENT_CLEAN_COMMAND = "app_segment_clean"
    }
}
