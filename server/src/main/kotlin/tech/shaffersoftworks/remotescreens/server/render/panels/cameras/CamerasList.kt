package tech.shaffersoftworks.remotescreens.server.render.panels.cameras

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.shared.Tile

/** The tile option that lists a cameras page's cameras: `camera.front_door=Front Door;camera.garage`. */
const val CAMERAS_OPTION = "cameras"
private const val CAMERA_DOMAIN_PREFIX = "camera."
private const val CAMERA_SEPARATOR = ';'
private const val LABEL_SEPARATOR = '='

/** A full page of its own for a list of cameras: RPI's cameras screen rather than a card. */
fun Tile.isCamerasPage(): Boolean = full && effectiveKind() == "cameras"

/** One camera on the cameras page: its entity and the name on its row (empty for the entity's own name). */
data class CameraEntry(val entityId: String, val label: String)

/** The cameras of the tile's `cameras` option, in order. Only `camera.*` entities are kept. */
fun Tile.cameraEntries(): List<CameraEntry> = options[CAMERAS_OPTION].orEmpty()
    .split(CAMERA_SEPARATOR)
    .mapNotNull { entry ->
        val entityId = entry.substringBefore(LABEL_SEPARATOR).trim()
        val label = entry.substringAfter(LABEL_SEPARATOR, "").trim()
        CameraEntry(entityId, label).takeIf { entityId.startsWith(CAMERA_DOMAIN_PREFIX) }
    }
    .distinctBy { it.entityId }

/** The camera [selectedEntityId] names, or null when it names none of them (RPI starts with nothing selected). */
fun List<CameraEntry>.selectedCamera(selectedEntityId: String?): CameraEntry? =
    firstOrNull { it.entityId == selectedEntityId }

/** The words on a row and over the video: the chosen label, else Home Assistant's name for the camera. */
fun CameraEntry.displayName(entityState: EntityState?): String =
    label.ifEmpty { entityState?.name.orEmpty().ifBlank { entityId } }
