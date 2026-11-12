package tech.shaffersoftworks.remotescreens.server.service

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.demo.DemoHome
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HomeAssistantCatalogServiceTest {
    @Test
    fun anEntityWithoutItsOwnAreaTakesTheDevicesArea() = runBlocking {
        val catalog = HomeAssistantCatalogService(DemoHome())

        val entities = catalog.entities()
        val hallwaySwitch = assertNotNull(entities.find { it.entityId == "switch.hallway_light" })
        assertEquals("hallway", hallwaySwitch.area)

        val livingRoomLight = assertNotNull(entities.find { it.entityId == "light.living_room" })
        assertEquals("living_room", livingRoomLight.area)
    }

    @Test
    fun areasComeFromTheAreaRegistry() = runBlocking {
        val catalog = HomeAssistantCatalogService(DemoHome())

        val areas = catalog.areas()
        assertEquals(
            listOf("Bedroom", "Garden", "Hallway", "Kitchen", "Living room"),
            areas.map { it.name },
        )
        assertNull(areas.find { it.id == "missing" })
    }
}
