package tech.shaffersoftworks.remotescreens.server.render.tiles

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistantConfig
import tech.shaffersoftworks.remotescreens.server.data.renderdata.RenderData
import tech.shaffersoftworks.remotescreens.server.i18n.I18n
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.bindEntity
import tech.shaffersoftworks.remotescreens.server.render.bindTileColors
import tech.shaffersoftworks.remotescreens.server.render.theme.Pastel
import tech.shaffersoftworks.remotescreens.server.render.theme.Themes
import tech.shaffersoftworks.remotescreens.server.render.theme.Units
import tech.shaffersoftworks.remotescreens.shared.Catalogue
import tech.shaffersoftworks.remotescreens.shared.Tile
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

private const val DOC_PX = 200
private const val ICON_DP = 24f
private const val TILE_W_DP = 160f
private const val TILE_H_DP = 120f
private const val TILE_COLS = 4
private const val TILE_ROWS = 3
private const val MOTION_ENTITY = "binary_sensor.front_door_camera_motion"
private const val SENSOR_ENTITY = "sensor.outdoor_temperature"
private const val SWITCH_ENTITY = "switch.porch"
private const val SENSOR_ATTRIBUTES =
    """{"device_class":"temperature","unit_of_measurement":"°C","friendly_name":"Outdoor"}"""
private const val SWITCH_ATTRIBUTES = """{"friendly_name":"Porch"}"""

class TileIconMotionTest {
    @Test
    fun motionOnAndOffDrawTheSameDocument() {
        val off = document(state = "off")
        val on = document(state = "on")

        assertTrue(off.structure.none { it.first == MOTION_ENTITY })
        assertTrue(on.structure.none { it.first == MOTION_ENTITY })
        assertContentEquals(off.bytes, on.bytes)
    }

    @Test
    fun aSensorReadingDoesNotChangeTheDocument() {
        val cool = tile(entityId = SENSOR_ENTITY, state = "14.2", attributes = SENSOR_ATTRIBUTES)
        val warm = tile(entityId = SENSOR_ENTITY, state = "15.8", attributes = SENSOR_ATTRIBUTES)

        assertContentEquals(cool, warm)
    }

    @Test
    fun aSwitchOnAndOffDrawsTheSameDocument() {
        val off = tile(entityId = SWITCH_ENTITY, state = "off", attributes = SWITCH_ATTRIBUTES)
        val on = tile(entityId = SWITCH_ENTITY, state = "on", attributes = SWITCH_ATTRIBUTES)

        assertContentEquals(off, on)
    }

    private fun tile(entityId: String, state: String, attributes: String): ByteArray {
        val entity = EntityState(entityId, state, Json.parseToJsonElement(attributes).jsonObject)

        val (bytes, _) = DocContext.build(
            widthPx = DOC_PX,
            heightPx = DOC_PX,
            description = "tile",
            newContext = {
                DocContext(
                    units = Units(1f),
                    palette = Themes.RPI,
                    i18n = I18n("en", emptyMap(), emptyMap(), true),
                    states = mapOf(entity.entityId to entity),
                    homeAssistantConfig = HomeAssistantConfig(),
                    data = RenderData(),
                )
            },
        ) { doc ->
            doc.bindEntity(entityId = entity.entityId)
            doc.bindTileColors(tileId = "t", entityId = entity.entityId, pastel = Pastel.TEAL)

            doc.entityTile(
                tile = Tile(id = "t", entityId = entity.entityId),
                box = TileBox(
                    x = 0f,
                    y = 0f,
                    w = TILE_W_DP,
                    h = TILE_H_DP,
                    cols = TILE_COLS,
                    rows = TILE_ROWS,
                    full = false,
                ),
                spec = Catalogue.of(entityId.substringBefore('.')),
                entityState = entity,
            )
        }

        return bytes
    }

    private fun document(state: String): BuiltIcon {
        val entity = EntityState(
            MOTION_ENTITY,
            state,
            Json.parseToJsonElement("""{"device_class":"motion","friendly_name":"Front door"}""").jsonObject,
        )

        val (bytes, context) = DocContext.build(
            widthPx = DOC_PX,
            heightPx = DOC_PX,
            description = "motion",
            newContext = {
                DocContext(
                    units = Units(1f),
                    palette = Themes.RPI,
                    i18n = I18n("en", emptyMap(), emptyMap(), true),
                    states = mapOf(entity.entityId to entity),
                    homeAssistantConfig = HomeAssistantConfig(),
                    data = RenderData(),
                )
            },
        ) { doc ->
            doc.bindEntity(entityId = entity.entityId)

            doc.tileIcon(
                tile = Tile(id = "m", entityId = entity.entityId),
                spec = Catalogue.of("binary_sensor"),
                entityState = entity,
                sizeDp = ICON_DP,
            )
        }

        return BuiltIcon(bytes = bytes, structure = context.structure)
    }

    private class BuiltIcon(
        val bytes: ByteArray,
        val structure: List<Triple<String, (EntityState?) -> String, String>>,
    )
}
