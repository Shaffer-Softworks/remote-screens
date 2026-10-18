package tech.shaffersoftworks.remotescreens.server.render.tiles

import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.TapAction
import tech.shaffersoftworks.remotescreens.shared.TapType
import tech.shaffersoftworks.remotescreens.shared.Tile
import tech.shaffersoftworks.remotescreens.shared.TypeSpec

/** Where a tile sits, in dp. An [edge] tile fills the page from edge to edge, without the grid's margins. */
data class TileBox(val x: Float, val y: Float, val w: Float, val h: Float, val cols: Int, val rows: Int, val full: Boolean, val edge: Boolean = false)

/** Whether the tile is a page of its own (the home panel, a full weather or climate tile), drawn without a card from edge to edge. */
fun Tile.edgeToEdge(): Boolean = full && effectiveKind() in setOf("home", "weather", "climate")

/** What a tap (or a hold) on a tile sends. */
fun tapRef(tile: Tile, action: TapAction, spec: TypeSpec?, pageIndex: (String) -> Int?, hold: Boolean): ActionRef? {
    val hasEntityFor = tile.entityId != null || spec?.needsEntity == false
    fun card() = if (spec != null && spec.card != "none" && hasEntityFor) {
        ActionRef(
            tile.id,
            "card",
        )
    } else {
        null
    }
    return when (action.type) {
        TapType.NONE -> null
        TapType.TOGGLE -> ActionRef(tile.id, "toggle")
        TapType.CARD -> card()
        TapType.ACTION -> ActionRef(tile.id, "run")
        TapType.PAGE -> action.page?.let(pageIndex)?.let { ActionRef("_", "page", it.toString()) }
        TapType.URL -> action.url?.takeIf { it.isNotBlank() }?.let { ActionRef("_", "url", it) }
        TapType.EVENT -> action.event?.takeIf { it.isNotBlank() }?.let { ActionRef(
            tile.id,
            "event",
            it,
        ) }
        TapType.DEFAULT -> when {
            hold -> card()
            tile.effectiveKind() == "nav" -> tile.options["page"]?.let(
                pageIndex,
            )?.let { ActionRef("_", "page", it.toString()) }
            tile.effectiveKind() == "favourite" -> ActionRef(tile.id, "favourite")
            spec == null -> null
            spec.tap == "toggle" -> ActionRef(tile.id, "toggle")
            spec.tap == "action" -> spec.defaultVerb?.let { ActionRef(tile.id, it) }
            else -> card()
        }
    }
}
