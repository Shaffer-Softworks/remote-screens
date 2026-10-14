package dev.remotescreens.server.data.layout

import dev.remotescreens.shared.LayoutData
import dev.remotescreens.shared.SCHEMA_VERSION
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private const val LAYOUT_FILE_NAME = "layouts.json"

/**
 * The screens, their pages and the adopted players, in one JSON file. Every write goes to a temporary file first
 * and replaces the old one in one move, so a power cut never leaves half a layout.
 */
class LayoutRepository(dataDir: File) {
    private val logger = LoggerFactory.getLogger(LayoutRepository::class.java)
    private val file = File(dataDir, LAYOUT_FILE_NAME)
    private val _layout = MutableStateFlow(load())
    val layout: StateFlow<LayoutData> = _layout.asStateFlow()

    @Synchronized
    fun update(change: (LayoutData) -> LayoutData): LayoutData {
        val next = change(_layout.value)
        if (next != _layout.value) {
            save(next)
            _layout.value = next
        }
        return next
    }

    private fun load(): LayoutData {
        if (!file.exists()) return LayoutData()
        val raw = JSON.parseToJsonElement(file.readText()).jsonObject
        val version = (raw["schemaVersion"] as? JsonPrimitive)?.intOrNull ?: 1
        return if (version > SCHEMA_VERSION) setAsideNewerLayout(version) else migrate(raw, version)
    }

    /** Written by a newer add-on. Keep it untouched and run on an empty layout rather than lose it. */
    private fun setAsideNewerLayout(version: Int): LayoutData {
        val keep = File(file.parentFile, "layouts.v$version.json")
        file.copyTo(keep, overwrite = true)
        logger.error(
            "{} is schema {} but this add-on knows {}; kept a copy at {}",
            LAYOUT_FILE_NAME,
            version,
            SCHEMA_VERSION,
            keep,
        )
        return LayoutData()
    }

    private fun migrate(raw: JsonObject, version: Int): LayoutData {
        val migrated = LayoutMigrations.migrate(raw)
        if (version < SCHEMA_VERSION) {
            file.copyTo(File(file.parentFile, "layouts.v$version.bak.json"), overwrite = true)
        }
        return JSON.decodeFromJsonElement(LayoutData.serializer(), migrated)
    }

    private fun save(layout: LayoutData) {
        val temporaryFile = File(file.parentFile, "$LAYOUT_FILE_NAME.tmp")
        temporaryFile.writeText(JSON.encodeToString(LayoutData.serializer(), layout))
        Files.move(
            temporaryFile.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }

    companion object {
        val JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
        }
    }
}
