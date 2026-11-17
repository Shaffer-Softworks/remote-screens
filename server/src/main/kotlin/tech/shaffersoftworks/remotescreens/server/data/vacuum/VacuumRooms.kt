package tech.shaffersoftworks.remotescreens.server.data.vacuum

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState

/** `VacuumEntityFeature.CLEAN_AREA`: the vacuum takes `vacuum.clean_area` with Home Assistant area ids. */
const val VACUUM_CLEAN_AREA_FEATURE = 16384

const val VACUUM_PREFIX = "vacuum."

/** How a vacuum cleans the rooms chosen in its dialog. */
enum class VacuumCleanMethod {
    /** `vacuum.clean_area` with the Home Assistant areas mapped to the vacuum's segments. */
    AREA,

    /** `vacuum.send_command` / `app_segment_clean` with the ids from `room_ids` (or the names in `room_list`). */
    SEGMENT,
}

/** One selectable room for a vacuum's selective clean: an area id or a segment id. */
data class VacuumRoom(
    val id: String,
    val name: String,
)

/** The rooms a vacuum can clean selectively, and how to ask it to. */
data class VacuumRooms(
    val method: VacuumCleanMethod,
    val rooms: List<VacuumRoom>,
) {
    /** The ids of [selected] that are still rooms of this vacuum, in list order. */
    fun selectedIds(selected: Set<String>): List<String> = rooms.map { it.id }.filter { it in selected }
}

fun EntityState.supportsCleanArea(): Boolean =
    ((number("supported_features")?.toInt() ?: 0) and VACUUM_CLEAN_AREA_FEATURE) != 0

/**
 * Segment rooms from attributes some integrations publish: parallel `room_ids` + `room_list`, or `room_list`
 * alone (name as id).
 */
fun EntityState.vacuumSegmentRooms(): List<VacuumRoom> {
    val ids = list("room_ids")
    return list("room_list").mapIndexed { index, name ->
        VacuumRoom(
            id = ids.getOrNull(index)?.takeIf { it.isNotBlank() } ?: name,
            name = name,
        )
    }
}

/** The vacuum a page or card shows: the one chosen in its list, else the tile's, else the only one. */
fun selectedVacuumId(
    chosen: String?,
    tileEntityId: String?,
    vacuumIds: Collection<String>
): String? =
    chosen?.takeIf { it in vacuumIds }
        ?: tileEntityId?.takeIf { it in vacuumIds }
        ?: vacuumIds.singleOrNull()
