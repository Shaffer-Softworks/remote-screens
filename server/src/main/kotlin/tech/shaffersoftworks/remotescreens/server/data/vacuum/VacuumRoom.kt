package tech.shaffersoftworks.remotescreens.server.data.vacuum

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState

/** One selectable room for a vacuum's selective clean. */
data class VacuumRoom(
    val id: String,
    val name: String,
)

/**
 * Rooms a vacuum can clean selectively, from Home Assistant attributes.
 * Prefers parallel `room_ids` + `room_list`; falls back to `room_list` alone (name as id).
 */
fun EntityState.vacuumRooms(): List<VacuumRoom> {
    val names = list("room_list")
    if (names.isEmpty()) return emptyList()
    val ids = list("room_ids")
    return names.mapIndexed { index, name ->
        VacuumRoom(
            id = ids.getOrNull(index)?.takeIf { it.isNotBlank() } ?: name,
            name = name,
        )
    }
}
