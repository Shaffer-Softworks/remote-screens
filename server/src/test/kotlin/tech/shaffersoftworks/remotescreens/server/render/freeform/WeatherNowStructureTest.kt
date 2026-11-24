package tech.shaffersoftworks.remotescreens.server.render.freeform

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistantConfig
import tech.shaffersoftworks.remotescreens.server.data.renderdata.RenderData
import tech.shaffersoftworks.remotescreens.server.i18n.I18n
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.theme.Themes
import tech.shaffersoftworks.remotescreens.server.render.theme.Units
import tech.shaffersoftworks.remotescreens.shared.Node
import tech.shaffersoftworks.remotescreens.shared.NodeBinding
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

private const val WEATHER_ENTITY = "weather.home"
private const val PAGE_DP = 200f
private const val BLOCK_H_DP = 80f

class WeatherNowStructureTest {
    @Test
    fun aTemperatureChangeDoesNotRebuildTheDocument() {
        val sunny = weather(state = "sunny", temperature = 14.2)
        val warmer = weather(state = "sunny", temperature = 15.8)
        val rain = weather(state = "rainy", temperature = 14.2)
        val recorded = structure(sunny)

        assertEquals(recorded.was, recorded.what(warmer))
        assertNotEquals(recorded.was, recorded.what(rain))
    }

    private fun structure(entity: EntityState): Recorded {
        val (_, context) = DocContext.build(
            widthPx = PAGE_DP.toInt(),
            heightPx = PAGE_DP.toInt(),
            description = "weather",
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
            doc.freeForm(
                root = Node(
                    id = "now",
                    type = "weather",
                    bind = NodeBinding(entityId = entity.entityId),
                ),
                xDp = 0f,
                yDp = 0f,
                wDp = PAGE_DP,
                hDp = BLOCK_H_DP,
                pageIndex = { null },
            )
        }

        val entry = context.structure.single { it.first == WEATHER_ENTITY }

        return Recorded(what = entry.second, was = entry.third)
    }

    private fun weather(state: String, temperature: Double): EntityState = EntityState(
        WEATHER_ENTITY,
        state,
        Json.parseToJsonElement(
            """{"temperature":$temperature,"temperature_unit":"°C"}""",
        ).jsonObject,
    )

    private class Recorded(
        val what: (EntityState?) -> String,
        val was: String,
    )
}
