package tech.shaffersoftworks.remotescreens.server.preview

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.render.cards.CardRenderer
import tech.shaffersoftworks.remotescreens.shared.Catalogue
import tech.shaffersoftworks.remotescreens.shared.GridSize
import tech.shaffersoftworks.remotescreens.shared.Page
import tech.shaffersoftworks.remotescreens.shared.Tile
import tech.shaffersoftworks.remotescreens.shared.TypeSpec

internal fun PreviewSession.catalogue() {
    for ((kind, spec) in Catalogue.types.toSortedMap()) {
        val entity = originalStates.values.firstOrNull { it.domain == kind }?.entityId ?: when (kind) {
            "music", "favourite" -> "media_player.living_room"
            "history" -> "sensor.power"
            else -> if (spec.needsEntity) "$kind.preview" else null
        }
        val states = if (entity != null && entity !in originalStates) {
            originalStates + (entity to EntityState(entity, "off"))
        } else {
            originalStates
        }
        tileSizes(kind, spec, entity, states)
        if (spec.card != "none" && entity != null) {
            card("Cards", "$kind · ${spec.card}", entity, kind, states)
            output.covered += "card:$kind"
        }
        if (entity != null) {
            entityStates(kind, spec, entity, states)
        }
    }
    // These card entry points do not require a catalogue entity.
    for (kind in listOf("energy", "history")) {
        card("Cards", kind, "sensor.power", kind, originalStates)
    }
}

private fun PreviewSession.tileSizes(
    kind: String,
    spec: TypeSpec,
    entity: String?,
    states: Map<String, EntityState>,
) {
    for (size in spec.sizes) {
        val full = size == "full"
        val dims = if (full) listOf(1, 1) else size.split("x").map(String::toInt)
        val tile = Tile("sample", entity, kind, w = dims[0], h = dims[1], full = full, options = options(kind))
        page(
            "Tiles",
            "$kind · $size",
            Page("preview", spec.name, listOf(tile)),
            input(states),
            canvas = PageCanvas(grid = GridSize(if (full) 1 else GRID_COLS, if (full) 1 else GRID_ROWS)),
        )
        output.covered += "tile:$kind:$size"
    }
}

private fun PreviewSession.entityStates(
    kind: String,
    spec: TypeSpec,
    entity: String,
    states: Map<String, EntityState>,
) {
    val sampleEntity = states.getValue(entity)
    for (state in (spec.onStates + listOf("off", "unknown", "unavailable")).distinct()) {
        val changed = states + (entity to sampleEntity.copy(state = state))
        stateTile(kind, spec.name, entity, changed, state)
        if (spec.card != "none") {
            card("Card states", "$kind · $state", entity, kind, changed)
        }
    }
    val attributes = JsonObject(
        sampleEntity.attributes + (
            "friendly_name" to JsonPrimitive("A very long entity name for the downstairs living room and kitchen")
            ),
    )
    val longLabel = states + (entity to sampleEntity.copy(attributes = attributes))
    stateTile(kind, spec.name, entity, longLabel, "long label")
}

private fun PreviewSession.stateTile(
    kind: String,
    title: String,
    entity: String,
    states: Map<String, EntityState>,
    state: String,
) {
    val tile = Tile("sample", entity, kind, w = 2, h = 2, options = options(kind))
    page(
        "States",
        "$kind · $state",
        Page("preview", title, listOf(tile)),
        input(states),
        canvas = PageCanvas(grid = GridSize(GRID_COLS, GRID_ROWS)),
    )
}

private fun PreviewSession.card(
    group: String,
    label: String,
    entity: String,
    kind: String,
    states: Map<String, EntityState>,
) {
    val cardInput = input(states)
    emit(group, label, cardInput) { CardRenderer.render(entity, kind, wall, cardInput) }
}

private fun options(kind: String): Map<String, String> = when (kind) {
    "bedside" -> mapOf("keys" to "light.living_room,lock.front_door")
    "nav" -> mapOf("page" to "preview")
    "favourite" -> mapOf("title" to "Sunday Morning", "content_id" to "playlist:1")
    "music" -> mapOf("config_entry_id" to "demo_music_assistant")
    else -> emptyMap()
}
