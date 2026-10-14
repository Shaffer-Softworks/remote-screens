package dev.remotescreens.shared

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The grid answers the editor must give too (`editor/src/model/grid.test.ts` reads the same file).
 * `./gradlew :shared:test -PupdateFixtures` writes the answers for the inputs in the file; review the diff.
 */
class GridParityTest {
    private val file = File(System.getProperty("fixtures.dir") ?: "../tests/fixtures", "grid_parity.json")
    private val json = Json { prettyPrint = true; prettyPrintIndent = "  " }

    @Test
    fun matchesFixture() {
        val fixture = Json.parseToJsonElement(file.readText()).jsonObject
        val answered = answer(fixture)
        if (System.getProperty("fixtures.update") == "true") {
            file.writeText(json.encodeToString(JsonElement.serializer(), answered) + "\n")
            return
        }
        compare(answered, fixture, "")
    }

    @Test
    fun fixtureHasAnswers() {
        val fixture = Json.parseToJsonElement(file.readText()).jsonObject
        for (section in listOf("suggest", "spec", "reflow")) {
            val cases = fixture.getValue(section).jsonArray
            assertTrue(cases.isNotEmpty(), "$section has cases")
            for (c in cases) assertTrue("expect" in c.jsonObject, "$section case without an answer: $c")
        }
    }

    private fun answer(fixture: JsonObject): JsonObject = buildJsonObject {
        put("suggest", buildJsonArray {
            for (c in fixture.getValue("suggest").jsonArray.map { it.jsonObject }) {
                val g = Grid.suggest(c.f("width"), c.f("height"))
                add(inputs(c) + ("expect" to buildJsonObject { put("cols", g.cols); put("rows", g.rows) }))
            }
        })
        put("spec", buildJsonArray {
            for (c in fixture.getValue("spec").jsonArray.map { it.jsonObject }) {
                val grid = GridSize(c.i("cols"), c.i("rows"))
                val s = Grid.spec(grid, c.f("width"), c.f("height"), c.getValue("topBar").jsonPrimitive.boolean)
                add(inputs(c) + ("expect" to buildJsonObject {
                    put("cols", s.cols)
                    put("rows", s.rows)
                    put("cellW", s.cellW)
                    put("cellH", s.cellH)
                    put("topBar", s.topBar)
                    put("large", s.large)
                    put("x", floats((0 until s.cols).map { s.x(it) }))
                    put("y", floats((0 until s.rows).map { s.y(it) }))
                    put("w", floats((1..s.cols).map { s.w(it) }))
                    put("h", floats((1..s.rows).map { s.h(it) }))
                }))
            }
        })
        put("reflow", buildJsonArray {
            for (c in fixture.getValue("reflow").jsonArray.map { it.jsonObject }) {
                val tiles = c.getValue("tiles").jsonArray.map { it.jsonObject }.map { t ->
                    Tile(t.s("id"), col = t.i("col"), row = t.i("row"), w = t.i("w"), h = t.i("h"),
                        full = t["full"]?.jsonPrimitive?.booleanOrNull ?: false)
                }
                val placed = Reflow.place(tiles, GridSize(c.i("cols"), c.i("rows")))
                add(inputs(c) + ("expect" to buildJsonArray {
                    for (p in placed) add(buildJsonObject {
                        put("id", p.tile.id); put("col", p.col); put("row", p.row); put("w", p.w); put("h", p.h)
                    })
                }))
            }
        })
    }

    private fun inputs(c: JsonObject) = JsonObject(c - "expect")
    private operator fun JsonObject.plus(pair: Pair<String, JsonElement>) = JsonObject(this.toMap() + pair)
    private fun floats(values: List<Float>) = JsonArray(values.map { JsonPrimitive(it) })
    private fun JsonObject.f(key: String) = getValue(key).jsonPrimitive.float
    private fun JsonObject.i(key: String) = getValue(key).jsonPrimitive.int
    private fun JsonObject.s(key: String) = getValue(key).jsonPrimitive.content

    /** Numbers may differ in the last float digit between Kotlin and the file. */
    private fun compare(actual: JsonElement, expected: JsonElement, path: String) {
        when {
            actual is JsonObject && expected is JsonObject -> {
                assertEquals(expected.keys, actual.keys, "keys at $path")
                for (k in expected.keys) compare(actual.getValue(k), expected.getValue(k), "$path.$k")
            }
            actual is JsonArray && expected is JsonArray -> {
                assertEquals(expected.size, actual.size, "length at $path")
                for (i in expected.indices) compare(actual[i], expected[i], "$path[$i]")
            }
            actual is JsonPrimitive && expected is JsonPrimitive -> {
                val a = actual.content.toDoubleOrNull()
                val e = expected.content.toDoubleOrNull()
                if (a != null && e != null && !actual.isString) {
                    if (abs(a - e) > 0.01) fail("$path: expected $e, got $a")
                } else {
                    assertEquals(expected.content, actual.content, path)
                }
            }
            else -> fail("$path: expected $expected, got $actual")
        }
    }
}
