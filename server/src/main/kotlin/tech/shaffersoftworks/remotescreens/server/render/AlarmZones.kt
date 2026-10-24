package tech.shaffersoftworks.remotescreens.server.render

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.shared.Tile
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** The tile option that lists an alarm page's zones: `alarm_control_panel.basement=Basement;…`. */
const val ALARM_ZONES_OPTION = "zones"
private const val ALARM_DOMAIN_PREFIX = "alarm_control_panel."
private const val ZONE_SEPARATOR = ';'
private const val LABEL_SEPARATOR = '='

/** RPI's alarm page takes a four-digit pin and shows one dot per digit. */
const val ALARM_PIN_LENGTH = 4

/** Whether dot [index] of the alarm page's pin row is lit, as a visibility: `t.e:<entity>.dot.<index>`. */
fun alarmDotVar(entityId: String, index: Int): String = Vars.tile("e:$entityId", "dot.$index")

/** A full page of its own for an alarm entity: RPI's alarm screen rather than a card. */
fun Tile.isAlarmPage(): Boolean = full && effectiveKind() == "alarm_control_panel"

/** One zone chip on the alarm page: an alarm entity and the word on its chip (empty for the entity's name). */
data class AlarmZone(val entityId: String, val label: String)

/** The tile's own entity first (All Zones, labelled by the page), then the zones of its `zones` option. */
fun Tile.alarmZones(): List<AlarmZone> {
    val ownEntityId = entityId ?: return emptyList()
    val listed = options[ALARM_ZONES_OPTION].orEmpty()
        .split(ZONE_SEPARATOR)
        .mapNotNull { entry ->
            val zoneEntityId = entry.substringBefore(LABEL_SEPARATOR).trim()
            val label = entry.substringAfter(LABEL_SEPARATOR, "").trim()
            AlarmZone(zoneEntityId, label).takeIf {
                zoneEntityId.startsWith(ALARM_DOMAIN_PREFIX) && zoneEntityId != ownEntityId
            }
        }
        .distinctBy { it.entityId }
    return listOf(AlarmZone(ownEntityId, "")) + listed
}

/** The zone [selectedEntityId] names, or All Zones when it names none of them. */
fun List<AlarmZone>.selectedZone(selectedEntityId: String?): AlarmZone? =
    firstOrNull { it.entityId == selectedEntityId } ?: firstOrNull()

/** Whether Alarmo reports open sensors on this panel (`open_sensors` is a map, or null when all are closed). */
fun EntityState?.hasOpenSensors(): Boolean = when (val sensors = this?.attributes?.get("open_sensors")) {
    is JsonObject -> sensors.isNotEmpty()
    is JsonArray -> sensors.isNotEmpty()
    else -> false
}
