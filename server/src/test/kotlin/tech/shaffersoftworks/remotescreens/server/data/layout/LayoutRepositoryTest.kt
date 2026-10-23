package tech.shaffersoftworks.remotescreens.server.data.layout

import tech.shaffersoftworks.remotescreens.shared.NavBar
import tech.shaffersoftworks.remotescreens.shared.SCHEMA_VERSION
import tech.shaffersoftworks.remotescreens.shared.Screen
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LayoutRepositoryTest {
    private val fixtures = File(
        System.getProperty("fixtures.dir") ?: "../tests/fixtures",
        "layouts",
    )

    private fun dirWith(layout: String): File = Files.createTempDirectory("layouts").toFile().also {
        File(it, "layouts.json").writeText(layout)
    }

    @Test
    fun everyReleasedSchemaLoads() {
        val files = fixtures.listFiles { file -> file.name.matches(Regex("v\\d+\\.json")) }.orEmpty()
        assertTrue(files.isNotEmpty(), "no layout fixtures in $fixtures")
        for (file in files) {
            val data = LayoutRepository(dirWith(file.readText())).layout.value
            assertEquals(SCHEMA_VERSION, data.schemaVersion, file.name)
            assertTrue(data.screens.isNotEmpty(), "${file.name} lost its screens")
            assertTrue(data.players.isNotEmpty(), "${file.name} lost its players")
        }
    }

    @Test
    fun theV2FixtureKeepsItsFreeFormTree() {
        val s = LayoutRepository(
            dirWith(File(fixtures, "v2.json").readText()),
        ).layout.value.screens.single()
        val root = checkNotNull(s.pages[0].root)
        assertEquals("column", root.type)
        assertEquals(
            listOf("n1", "n2", "n3", "n4", "n5", "n6", "n7"),
            root.walk().map { it.id }.toList(),
        )
        assertEquals("light.kitchen", root.walk().first { it.type == "tile" }.tile?.entityId)
        assertEquals("value", root.walk().first { it.id == "n6" }.bind?.format)
        assertEquals(null, s.pages[1].root)
        assertEquals(setOf("t1", "n6", "n7"), s.pages[0].allTiles().map { it.id }.toSet())
    }

    @Test
    fun theV1FixtureKeepsEveryField() {
        val data = LayoutRepository(dirWith(File(fixtures, "v1.json").readText())).layout.value
        val s = data.screens.single()
        assertEquals(4, s.grid.cols)
        assertEquals(listOf("home", "more"), s.pages.map { it.id })
        assertTrue(s.pages[1].hidden)
        assertEquals("script.turn_on", s.pages[0].tiles[2].tap.service)
        assertEquals("history", s.pages[0].tiles[4].options["show"])
        assertEquals("06:45", s.settings.nightEnd)
        assertEquals(2, s.settings.orientation)
        assertEquals("sensor.outdoor", s.topBar.temperatureEntity)
        assertEquals(null, s.topBar.height)
        assertTrue(s.topBar.date)
        assertEquals(false, s.topBar.wifi)
        assertEquals(false, s.topBar.battery)
        assertEquals(NavBar(), s.navBar)
        assertEquals("nl", data.preferences.language)
        assertEquals(1280, data.players.first().widthDp)
    }

    @Test
    fun aNewerSchemaIsKeptAsideNotOverwritten() {
        val dir = dirWith(
            """{"schemaVersion": ${SCHEMA_VERSION + 1}, "screens": [], "somethingNew": true}""",
        )
        val repository = LayoutRepository(dir)
        assertTrue(repository.layout.value.screens.isEmpty())
        assertTrue(
            File(dir, "layouts.v${SCHEMA_VERSION + 1}.json").readText().contains("somethingNew"),
        )
    }

    @Test
    fun updatesAreWrittenAndReadBack() {
        val dir = Files.createTempDirectory("layouts").toFile()
        LayoutRepository(dir).update { it.copy(screens = listOf(Screen(id = "a", name = "A"))) }
        assertEquals(listOf("a"), LayoutRepository(dir).layout.value.screens.map { it.id })
        assertEquals(listOf("layouts.json"), checkNotNull(dir.list()).toList())
    }

    @Test
    fun migrationsRunInOrderAndStampTheVersion() {
        val steps = mapOf<Int, (JsonObject) -> JsonObject>(
            1 to { JsonObject(it + ("one" to JsonPrimitive(true))) },
            2 to { o -> JsonObject(
                o + ("two" to JsonPrimitive(o["one"]?.jsonPrimitive?.content == "true")),
            ) },
        )
        val out = LayoutMigrations.migrate(
            JsonObject(mapOf("schemaVersion" to JsonPrimitive(1))),
            steps,
            target = 3,
        )
        assertEquals(3, out["schemaVersion"]?.jsonPrimitive?.intOrNull)
        assertEquals("true", out["two"]?.jsonPrimitive?.content)
        val unversioned = LayoutMigrations.migrate(JsonObject(emptyMap()), steps, target = 2)
        assertEquals(2, unversioned["schemaVersion"]?.jsonPrimitive?.intOrNull)
        assertFailsWith<IllegalStateException> {
            LayoutMigrations.migrate(JsonObject(emptyMap()), emptyMap(), target = 2)
        }
        assertTrue(out.jsonObject.containsKey("one"))
    }
}
