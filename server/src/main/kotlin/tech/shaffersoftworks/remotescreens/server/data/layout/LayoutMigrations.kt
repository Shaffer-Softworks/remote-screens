package tech.shaffersoftworks.remotescreens.server.data.layout

import tech.shaffersoftworks.remotescreens.shared.SCHEMA_VERSION
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

typealias LayoutMigration = (JsonObject) -> JsonObject

/**
 * One step per schema version, from the version it reads to the next. A step works on raw JSON so it never depends
 * on today's classes. Every layout a released add-on wrote must load: tests/fixtures/layouts holds one of each.
 */
object LayoutMigrations {
    private val steps: Map<Int, LayoutMigration> = mapOf(
        // Schema 2 adds free-form pages (`Page.root`); every schema 1 page stays a grid page as it is.
        1 to { it },
    )

    fun migrate(
        raw: JsonObject,
        steps: Map<Int, LayoutMigration> = this.steps,
        target: Int = SCHEMA_VERSION,
    ): JsonObject {
        var migrated = raw
        var version = (migrated["schemaVersion"] as? JsonPrimitive)?.intOrNull ?: 1
        while (version < target) {
            val step = steps[version] ?: error("no migration from schema $version")
            migrated = step(migrated)
            version++
            migrated = JsonObject(migrated + ("schemaVersion" to JsonPrimitive(version)))
        }
        return migrated
    }
}
