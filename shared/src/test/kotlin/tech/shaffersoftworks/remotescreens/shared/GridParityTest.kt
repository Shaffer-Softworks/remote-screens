package tech.shaffersoftworks.remotescreens.shared

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
    private val file = File(
        System.getProperty("fixtures.dir") ?: "../tests/fixtures",
        "grid_parity.json",
    )
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
            for (case in cases) assertTrue(
                "expect" in case.jsonObject,
                "$section case without an answer: $case",
            )
        }
    }

    private fun answer(fixture: JsonObject): JsonObject = buildJsonObject {
        put("suggest", buildJsonArray {
            for (case in fixture.getValue("suggest").jsonArray.map { it.jsonObject }) {
                val gridSize = Grid.suggest(case.floatAt("width"), case.floatAt("height"))
                add(inputs(case) + ("expect" to buildJsonObject {
                    put("cols", gridSize.cols)
                    put("rows", gridSize.rows)
                }))
            }
        })
        put("spec", buildJsonArray {
            for (case in fixture.getValue("spec").jsonArray.map { it.jsonObject }) {
                // `topBar` is whether the default bar shows, or its height; `navBar`, if given, the bottom bar's.
                val topBarValue = case.getValue("topBar").jsonPrimitive
                val topBarHeight = topBarValue.booleanOrNull?.let { if (it) Grid.TOP_BAR else 0f }
                    ?: topBarValue.float
                val navBarHeight = case["navBar"]?.jsonPrimitive?.float ?: Grid.navBar(
                    case.floatAt("width"),
                )
                val grid = GridSize(case.intAt("cols"), case.intAt("rows"))
                val spec = Grid.spec(
                    grid,
                    case.floatAt("width"),
                    case.floatAt("height"),
                    topBarHeight,
                    navBarHeight,
                )
                add(inputs(case) + ("expect" to buildJsonObject {
                    put("cols", spec.cols)
                    put("rows", spec.rows)
                    put("cellW", spec.cellW)
                    put("cellH", spec.cellH)
                    put("topBar", spec.topBar)
                    put("navBar", spec.navBar)
                    put("large", spec.large)
                    put("x", floats((0 until spec.cols).map { col -> spec.x(col) }))
                    put("y", floats((0 until spec.rows).map { row -> spec.y(row) }))
                    put("w", floats((1..spec.cols).map { span -> spec.w(span) }))
                    put("h", floats((1..spec.rows).map { span -> spec.h(span) }))
                }))
            }
        })
        put("reflow", buildJsonArray {
            for (case in fixture.getValue("reflow").jsonArray.map { it.jsonObject }) {
                val tiles = case.getValue("tiles").jsonArray.map { it.jsonObject }.map { tileJson ->
                    Tile(
                        tileJson.stringAt("id"),
                        col = tileJson.intAt("col"),
                        row = tileJson.intAt("row"),
                        w = tileJson.intAt("w"),
                        h = tileJson.intAt("h"),
                        full = tileJson["full"]?.jsonPrimitive?.booleanOrNull ?: false,
                    )
                }
                val placed = Reflow.place(tiles, GridSize(case.intAt("cols"), case.intAt("rows")))
                add(inputs(case) + ("expect" to buildJsonArray {
                    for (placement in placed) add(buildJsonObject {
                        put("id", placement.tile.id)
                        put("col", placement.col)
                        put("row", placement.row)
                        put("w", placement.w)
                        put("h", placement.h)
                    })
                }))
            }
        })
    }

    private fun inputs(case: JsonObject) = JsonObject(case - "expect")
    private operator fun JsonObject.plus(pair: Pair<String, JsonElement>) = JsonObject(
        this.toMap() + pair,
    )
    private fun floats(values: List<Float>) = JsonArray(values.map { JsonPrimitive(it) })
    private fun JsonObject.floatAt(key: String) = getValue(key).jsonPrimitive.float
    private fun JsonObject.intAt(key: String) = getValue(key).jsonPrimitive.int
    private fun JsonObject.stringAt(key: String) = getValue(key).jsonPrimitive.content

    /** Numbers may differ in the last float digit between Kotlin and the file. */
    private fun compare(actual: JsonElement, expected: JsonElement, path: String) {
        when {
            actual is JsonObject && expected is JsonObject -> {
                assertEquals(expected.keys, actual.keys, "keys at $path")
                for (key in expected.keys) compare(
                    actual.getValue(key),
                    expected.getValue(key),
                    "$path.$key",
                )
            }
            actual is JsonArray && expected is JsonArray -> {
                assertEquals(expected.size, actual.size, "length at $path")
                for (index in expected.indices) compare(
                    actual[index],
                    expected[index],
                    "$path[$index]",
                )
            }
            actual is JsonPrimitive && expected is JsonPrimitive -> {
                val actualNumber = actual.content.toDoubleOrNull()
                val expectedNumber = expected.content.toDoubleOrNull()
                if (actualNumber != null && expectedNumber != null && !actual.isString) {
                    if (abs(actualNumber - expectedNumber) > 0.01) {
                        fail("$path: expected $expectedNumber, got $actualNumber")
                    }
                } else {
                    assertEquals(expected.content, actual.content, path)
                }
            }
            else -> fail("$path: expected $expected, got $actual")
        }
    }
}
