package tech.shaffersoftworks.remotescreens.server.render.tiles

import tech.shaffersoftworks.remotescreens.server.render.cards.CardType
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.TileKind
import tech.shaffersoftworks.remotescreens.shared.TapAction
import tech.shaffersoftworks.remotescreens.shared.TapType
import tech.shaffersoftworks.remotescreens.shared.Tile
import tech.shaffersoftworks.remotescreens.shared.TypeSpec

/** Where a tile sits, in dp. An [edge] tile fills the page from edge to edge, without the grid's margins. */
data class TileBox(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val cols: Int,
    val rows: Int,
    val full: Boolean,
    val edge: Boolean = false,
)

/** Whether the tile is a page of its own: the home panel, or a full tile of a kind that draws a whole page. */
fun Tile.edgeToEdge(): Boolean = full && tileKind() in TileKind.EDGE_PAGES

/** What a tap (or a hold) on a tile sends. */
fun tapRef(tile: Tile, action: TapAction, spec: TypeSpec?, pageIndex: (String) -> Int?, hold: Boolean): ActionRef? =
    when (action.type) {
        TapType.NONE -> null
        TapType.TOGGLE -> ActionRef(tile.id, "toggle")
        TapType.CARD -> openCard(tile, spec)
        TapType.ACTION -> runAction(tile, hold)
        TapType.PAGE -> pageAction(action, pageIndex)
        TapType.URL -> urlAction(action)
        TapType.EVENT -> eventAction(tile, action)
        TapType.DEFAULT -> defaultTap(
            tile = tile,
            spec = spec,
            pageIndex = pageIndex,
            hold = hold
        ) { openCard(tile, spec) }
    }

private fun openCard(tile: Tile, spec: TypeSpec?): ActionRef? {
    val hasEntity = tile.entityId != null || spec?.needsEntity == false

    if (spec == null || CardType.fromWire(spec.card) == CardType.NONE || !hasEntity) return null

    return ActionRef(tile.id, "card")
}

private fun runAction(tile: Tile, hold: Boolean): ActionRef = ActionRef(
    tile = tile.id,
    verb = ActionRef.VERB_RUN,
    arg = if (hold) ActionRef.ARG_HOLD else ActionRef.ARG_TAP,
)

private fun pageAction(action: TapAction, pageIndex: (String) -> Int?): ActionRef? =
    action.page?.let(pageIndex)?.let { ActionRef(tile = "_", verb = "page", arg = it.toString()) }

private fun urlAction(action: TapAction): ActionRef? =
    action.url?.takeIf { it.isNotBlank() }?.let { ActionRef(tile = "_", verb = "url", arg = it) }

private fun eventAction(tile: Tile, action: TapAction): ActionRef? =
    action.event?.takeIf { it.isNotBlank() }?.let { ActionRef(tile = tile.id, verb = "event", arg = it) }

private fun defaultTap(
    tile: Tile,
    spec: TypeSpec?,
    pageIndex: (String) -> Int?,
    hold: Boolean,
    card: () -> ActionRef?,
): ActionRef? = when {
    hold -> card()
    tile.tileKind() == TileKind.NAV -> tile.options[Tile.OPTION_PAGE]?.let(
        pageIndex,
    )?.let { ActionRef(tile = "_", verb = "page", arg = it.toString()) }
    tile.tileKind() == TileKind.FAVOURITE -> ActionRef(tile.id, TileKind.FAVOURITE.wire)
    spec == null -> null
    spec.tap == "toggle" -> ActionRef(tile.id, "toggle")
    spec.tap == "action" -> spec.defaultVerb?.let { ActionRef(tile.id, it) }
    else -> card()
}
