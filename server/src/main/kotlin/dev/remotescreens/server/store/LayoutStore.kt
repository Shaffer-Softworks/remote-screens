package dev.remotescreens.server.store

import dev.remotescreens.shared.LayoutData
import dev.remotescreens.shared.SCHEMA_VERSION
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The screens, their pages and the adopted players, in one JSON file. Every write goes to a temporary file first
 * and replaces the old one in one move, so a power cut never leaves half a layout.
 */
class LayoutStore(dir: File) {
    private val log = LoggerFactory.getLogger(LayoutStore::class.java)
    private val file = File(dir, "layouts.json")
    private val _data = MutableStateFlow(load())
    val data: StateFlow<LayoutData> = _data

    @Synchronized
    fun update(change: (LayoutData) -> LayoutData): LayoutData {
        val next = change(_data.value)
        if (next != _data.value) {
            save(next)
            _data.value = next
        }
        return next
    }

    private fun load(): LayoutData {
        if (!file.exists()) return LayoutData()
        val raw = JSON.parseToJsonElement(file.readText()).jsonObject
        val version = (raw["schemaVersion"] as? JsonPrimitive)?.intOrNull ?: 1
        if (version > SCHEMA_VERSION) {
            // Written by a newer add-on. Keep it untouched and run on an empty layout rather than lose it.
            val keep = File(file.parentFile, "layouts.v$version.json")
            file.copyTo(keep, overwrite = true)
            log.error("layouts.json is schema {} but this add-on knows {}; kept a copy at {}", version, SCHEMA_VERSION, keep)
            return LayoutData()
        }
        val migrated = Migrations.migrate(raw)
        if (version < SCHEMA_VERSION) {
            file.copyTo(File(file.parentFile, "layouts.v$version.bak.json"), overwrite = true)
        }
        return JSON.decodeFromJsonElement(LayoutData.serializer(), migrated)
    }

    private fun save(data: LayoutData) {
        val tmp = File(file.parentFile, "layouts.json.tmp")
        tmp.writeText(JSON.encodeToString(LayoutData.serializer(), data))
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    companion object {
        val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    }
}

/**
 * One step per schema version, from the version it reads to the next. A step works on raw JSON so it never depends
 * on today's classes. Every layout a released add-on wrote must load: tests/fixtures/layouts holds one of each.
 */
object Migrations {
    private val steps: Map<Int, (JsonObject) -> JsonObject> = mapOf(
        // 1 -> 2 goes here when schema 2 exists.
    )

    fun migrate(raw: JsonObject): JsonObject {
        var obj = raw
        var version = (obj["schemaVersion"] as? JsonPrimitive)?.intOrNull ?: 1
        while (version < SCHEMA_VERSION) {
            val step = steps[version] ?: error("no migration from schema $version")
            obj = step(obj)
            version++
            obj = JsonObject(obj + ("schemaVersion" to JsonPrimitive(version)))
        }
        return obj
    }
}
