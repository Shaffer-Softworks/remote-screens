package dev.remotescreens.server.store

import dev.remotescreens.shared.SCHEMA_VERSION
import dev.remotescreens.shared.Screen
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

class LayoutStoreTest {
    private val fixtures = File(System.getProperty("fixtures.dir") ?: "../tests/fixtures", "layouts")

    private fun dirWith(layout: String): File = Files.createTempDirectory("layouts").toFile().also {
        File(it, "layouts.json").writeText(layout)
    }

    @Test
    fun everyReleasedSchemaLoads() {
        val files = fixtures.listFiles { f -> f.name.matches(Regex("v\\d+\\.json")) }.orEmpty()
        assertTrue(files.isNotEmpty(), "no layout fixtures in $fixtures")
        for (f in files) {
            val data = LayoutStore(dirWith(f.readText())).data.value
            assertEquals(SCHEMA_VERSION, data.schemaVersion, f.name)
            assertTrue(data.screens.isNotEmpty(), "${f.name} lost its screens")
            assertTrue(data.players.isNotEmpty(), "${f.name} lost its players")
        }
    }

    @Test
    fun theV1FixtureKeepsEveryField() {
        val data = LayoutStore(dirWith(File(fixtures, "v1.json").readText())).data.value
        val s = data.screens.single()
        assertEquals(4, s.grid.cols)
        assertEquals(listOf("home", "more"), s.pages.map { it.id })
        assertTrue(s.pages[1].hidden)
        assertEquals("script.turn_on", s.pages[0].tiles[2].tap.service)
        assertEquals("history", s.pages[0].tiles[4].options["show"])
        assertEquals("06:45", s.settings.nightEnd)
        assertEquals(2, s.settings.orientation)
        assertEquals("sensor.outdoor", s.topBar.temperatureEntity)
        assertEquals("nl", data.preferences.language)
        assertEquals(1280, data.players.first().widthDp)
    }

    @Test
    fun aNewerSchemaIsKeptAsideNotOverwritten() {
        val dir = dirWith("""{"schemaVersion": ${SCHEMA_VERSION + 1}, "screens": [], "somethingNew": true}""")
        val store = LayoutStore(dir)
        assertTrue(store.data.value.screens.isEmpty())
        assertTrue(File(dir, "layouts.v${SCHEMA_VERSION + 1}.json").readText().contains("somethingNew"))
    }

    @Test
    fun updatesAreWrittenAndReadBack() {
        val dir = Files.createTempDirectory("layouts").toFile()
        LayoutStore(dir).update { it.copy(screens = listOf(Screen(id = "a", name = "A"))) }
        assertEquals(listOf("a"), LayoutStore(dir).data.value.screens.map { it.id })
        assertEquals(listOf("layouts.json"), dir.list()!!.toList())
    }

    @Test
    fun migrationsRunInOrderAndStampTheVersion() {
        val steps = mapOf<Int, (JsonObject) -> JsonObject>(
            1 to { JsonObject(it + ("one" to JsonPrimitive(true))) },
            2 to { o -> JsonObject(o + ("two" to JsonPrimitive(o["one"]?.jsonPrimitive?.content == "true"))) },
        )
        val out = Migrations.migrate(JsonObject(mapOf("schemaVersion" to JsonPrimitive(1))), steps, target = 3)
        assertEquals(3, out["schemaVersion"]?.jsonPrimitive?.intOrNull)
        assertEquals("true", out["two"]?.jsonPrimitive?.content)
        val unversioned = Migrations.migrate(JsonObject(emptyMap()), steps, target = 2)
        assertEquals(2, unversioned["schemaVersion"]?.jsonPrimitive?.intOrNull)
        assertFailsWith<IllegalStateException> { Migrations.migrate(JsonObject(emptyMap()), emptyMap(), target = 2) }
        assertTrue(out.jsonObject.containsKey("one"))
    }
}
