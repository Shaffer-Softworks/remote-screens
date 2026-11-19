package tech.shaffersoftworks.remotescreens.server.preview

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.render.cards.CardRenderer
import tech.shaffersoftworks.remotescreens.server.render.cards.CardType
import tech.shaffersoftworks.remotescreens.shared.Catalogue
import tech.shaffersoftworks.remotescreens.shared.TileKind
import tech.shaffersoftworks.remotescreens.shared.GridSize
import tech.shaffersoftworks.remotescreens.shared.Page
import tech.shaffersoftworks.remotescreens.shared.Tile
import tech.shaffersoftworks.remotescreens.shared.TypeSpec

internal fun PreviewSession.catalogue() {
    for ((kind, spec) in Catalogue.types.toSortedMap()) {
        val entity = originalStates.values.firstOrNull { it.domain.value == kind }?.entityId ?: when (kind) {
            TileKind.MUSIC.wire, TileKind.FAVOURITE.wire -> "media_player.living_room"
            CardType.HISTORY.wire -> "sensor.power"
            else -> if (spec.needsEntity) "$kind.preview" else null
        }

        val states = if (entity != null && entity !in originalStates) {
            originalStates + (entity to EntityState(entity, "off"))
        } else {
            originalStates
        }

        tileSizes(
            kind = kind,
            spec = spec,
            entity = entity,
            states = states,
        )

        if (CardType.fromWire(spec.card) != CardType.NONE && entity != null) {
            card(group = "Cards", label = "$kind · ${spec.card}", entity = entity, kind = kind, states = states)
            output.covered += "card:$kind"
        }

        if (entity != null) {
            entityStates(kind = kind, spec = spec, entity = entity, states = states)
        }
    }

    // These card entry points do not require a catalogue entity.
    for (kind in listOf(CardType.ENERGY.wire, CardType.HISTORY.wire)) {
        card(group = "Cards", label = kind, entity = "sensor.power", kind = kind, states = originalStates)
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

        val tile = Tile(
            id = "sample",
            entityId = entity,
            kind = kind,
            w = dims[0],
            h = dims[1],
            full = full,
            options = options(kind),
        )

        page(
            group = "Tiles",
            label = "$kind · $size",
            page = Page(
                id = "preview",
                title = spec.name,
                tiles = listOf(tile),
            ),
            renderInput = input(states),
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
        stateTile(kind = kind, title = spec.name, entity = entity, states = changed, state = state)

        if (CardType.fromWire(spec.card) != CardType.NONE) {
            card(group = "Card states", label = "$kind · $state", entity = entity, kind = kind, states = changed)
        }
    }

    val attributes = JsonObject(
        sampleEntity.attributes + (
            "friendly_name" to JsonPrimitive("A very long entity name for the downstairs living room and kitchen")
            ),
    )

    val longLabel = states + (entity to sampleEntity.copy(attributes = attributes))
    stateTile(kind = kind, title = spec.name, entity = entity, states = longLabel, state = "long label")
}

private fun PreviewSession.stateTile(
    kind: String,
    title: String,
    entity: String,
    states: Map<String, EntityState>,
    state: String,
) {
    val tile = Tile(id = "sample", entityId = entity, kind = kind, w = 2, h = 2, options = options(kind))

    page(
        group = "States",
        label = "$kind · $state",
        page = Page(
            id = "preview",
            title = title,
            tiles = listOf(tile),
        ),
        renderInput = input(states),
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

    emit(
        group = group,
        label = label,
        renderInput = cardInput,
    ) {
        CardRenderer.render(entityId = entity, kind = kind, device = wall, input = cardInput)
    }
}

private fun options(kind: String): Map<String, String> = when (kind) {
    TileKind.BEDSIDE.wire -> mapOf("keys" to "light.living_room,lock.front_door")
    TileKind.NAV.wire -> mapOf(Tile.OPTION_PAGE to "preview")
    TileKind.FAVOURITE.wire -> mapOf("title" to "Sunday Morning", "content_id" to "playlist:1")
    TileKind.MUSIC.wire -> mapOf("config_entry_id" to "demo_music_assistant")
    else -> emptyMap()
}
