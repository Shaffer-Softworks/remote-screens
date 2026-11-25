package tech.shaffersoftworks.remotescreens.server.render

import tech.shaffersoftworks.remotescreens.shared.Drawer
import tech.shaffersoftworks.remotescreens.shared.Node
import tech.shaffersoftworks.remotescreens.shared.Page
import tech.shaffersoftworks.remotescreens.shared.Screen
import tech.shaffersoftworks.remotescreens.shared.TapAction
import tech.shaffersoftworks.remotescreens.shared.TapType
import tech.shaffersoftworks.remotescreens.shared.Tile
import tech.shaffersoftworks.remotescreens.shared.TileKind

/**
 * The page a panel hello is allowed to draw: one page, the screen's own top and bottom bars,
 * long-press removed. A free-form page keeps the layout the editor shows. A grid keeps the
 * tiles whose drawing stays inside the opcode budget (no full-page panels, pictures, or maps).
 */
internal fun Screen.panelProfile(): Screen {
    val source = pages.firstOrNull { page -> page.id == homePage } ?: pages.firstOrNull()
    val pageId = source?.id ?: HOME_PAGE_ID
    val root = source?.root?.withoutHolds()
    val kept = if (root != null) {
        source.tiles.map { tile -> tile.copy(hold = TapAction(type = TapType.NONE)) }
    } else {
        panelTiles(source).take(PANEL_TILE_CAP)
    }

    return copy(
        pages = listOf(
            Page(
                id = pageId,
                title = source?.title ?: name,
                tiles = kept,
                root = root,
            ),
        ),
        homePage = pageId,
        drawer = Drawer(style = DRAWER_OFF),
    )
}

private fun Node.withoutHolds(): Node = copy(
    hold = if (hold == null) null else TapAction(type = TapType.NONE),
    children = children.map { child -> child.withoutHolds() },
    tile = tile?.copy(hold = TapAction(type = TapType.NONE)),
)

private fun panelTiles(source: Page?): List<Tile> {
    val placed = source?.tiles.orEmpty().filter { tile -> tile.drawsOnPanel() }
    val tiles = if (placed.isNotEmpty()) placed else source?.allTiles().orEmpty().filter { tile -> tile.drawsOnPanel() }

    return tiles.map { tile -> tile.copy(hold = TapAction(type = TapType.NONE)) }
}

private fun Tile.drawsOnPanel(): Boolean {
    val named = tileKind()
    val kindFits = if (named != null) named in PANEL_KINDS else domain != null && domain in PANEL_DOMAINS
    val streamsVideo = options[Tile.OPTION_STREAM] == Tile.OPTION_TRUE
    val showsMap = options[Tile.OPTION_MAP] == Tile.OPTION_TRUE
    val showsGraph = options[Tile.OPTION_GRAPH] == Tile.OPTION_TRUE

    return !full && !streamsVideo && !showsMap && !showsGraph && kindFits
}

private const val HOME_PAGE_ID = "home"
private const val DRAWER_OFF = "off"
private const val PANEL_TILE_CAP = 6

private val PANEL_KINDS = setOf(
    TileKind.BUTTON,
    TileKind.CLOCK,
    TileKind.INPUT_BUTTON,
    TileKind.SCENE,
    TileKind.SCRIPT,
    TileKind.SENSOR,
)

private val PANEL_DOMAINS = setOf(
    "binary_sensor",
    "button",
    "cover",
    "fan",
    "input_boolean",
    "input_button",
    "input_number",
    "light",
    "lock",
    "number",
    "scene",
    "script",
    "sensor",
    "switch",
)
