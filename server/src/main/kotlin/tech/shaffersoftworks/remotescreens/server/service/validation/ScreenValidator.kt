package tech.shaffersoftworks.remotescreens.server.service.validation

import tech.shaffersoftworks.remotescreens.shared.DeviceSize
import tech.shaffersoftworks.remotescreens.shared.Grid
import tech.shaffersoftworks.remotescreens.shared.MAX_NODES_PER_PAGE
import tech.shaffersoftworks.remotescreens.shared.MAX_NODE_DEPTH
import tech.shaffersoftworks.remotescreens.shared.MAX_PAGES
import tech.shaffersoftworks.remotescreens.shared.MAX_TILES_PER_SCREEN
import tech.shaffersoftworks.remotescreens.shared.Node
import tech.shaffersoftworks.remotescreens.shared.NodeTypes
import tech.shaffersoftworks.remotescreens.shared.Page
import tech.shaffersoftworks.remotescreens.shared.Screen
import io.ktor.server.plugins.BadRequestException

/** What a screen may hold. The editor keeps to it; this is for anything else that writes. */
object ScreenValidator {
    const val MAX_SCREEN_NAME_LENGTH = 60
    const val DEFAULT_SCREEN_NAME = "Screen"
    private const val MAX_PAGE_TITLE_LENGTH = 40
    private const val MAX_BACKGROUND_TOKEN_LENGTH = 20
    private val BACKGROUND_TOKEN = Regex("^[0-9]{1,$MAX_BACKGROUND_TOKEN_LENGTH}$")

    /** Values of [tech.shaffersoftworks.remotescreens.shared.ScreenSettings.orientation]. */
    const val ORIENTATION_FOLLOW_DEVICE = 0
    const val ORIENTATION_PORTRAIT = 1
    const val ORIENTATION_LANDSCAPE = 2

    private val NODE_SIZE = Regex("^(wrap|fill|dp:\\d+(\\.\\d+)?|weight:\\d+(\\.\\d+)?)$")

    fun validate(screen: Screen): Screen {
        val pages = screen.pages
        val tiles = pages.flatMap { it.allTiles() }
        val gridTileCount = pages.sumOf { if (it.root == null) it.tiles.size else 0 }
        val grid = screen.grid
        val cellRange = Grid.MIN_CELLS..Grid.MAX_CELLS
        ensureValid(pages.isNotEmpty()) { "A screen needs a page" }
        ensureValid(pages.size <= MAX_PAGES) { "At most $MAX_PAGES pages" }
        ensureValid(pages.map { it.id }.toSet().size == pages.size) { "Page ids must differ" }
        ensureValid(gridTileCount <= MAX_TILES_PER_SCREEN) { "At most $MAX_TILES_PER_SCREEN tiles" }
        ensureValid(tiles.map { it.id }.toSet().size == tiles.size) { "Tile ids must differ" }
        for (page in pages) page.root?.let { validateTree(it, page.title) }
        ensureValid(grid.cols in cellRange && grid.rows in cellRange) { "Grid out of range" }
        return screen.copy(
            name = normalizeName(screen.name),
            homePage = screen.homePage.takeIf { home -> pages.any { it.id == home } } ?: pages.first().id,
            settings = screen.settings.validated(),
            topBar = screen.topBar.validated(),
            navBar = screen.navBar.validated(),
            designSize = screen.designSize?.let(::coerceDesignSize),
            pages = pages.map { page -> clampPage(page, grid.cols, grid.rows) },
        )
    }

    /** What a free-form page may hold: known components, ids that differ, a bounded size and depth. */
    fun validateTree(root: Node, pageTitle: String) {
        val nodes = root.walk().toList()
        ensureValid(
            nodes.size <= MAX_NODES_PER_PAGE,
        ) { "$pageTitle: at most $MAX_NODES_PER_PAGE components" }
        ensureValid(
            root.depth() <= MAX_NODE_DEPTH,
        ) { "$pageTitle: components nest at most $MAX_NODE_DEPTH deep" }
        ensureValid(
            nodes.map { it.id }.toSet().size == nodes.size,
        ) { "$pageTitle: component ids must differ" }
        for (node in nodes) {
            ensureValid(node.type in NodeTypes.ALL) { "$pageTitle: unknown component ${node.type}" }
            ensureValid(node.children.isEmpty() || node.type in NodeTypes.CONTAINERS) {
                "$pageTitle: a ${node.type} holds no components"
            }
            ensureValid(
                node.type != "tile" || node.tile != null,
            ) { "$pageTitle: tile ${node.id} has no card" }
            for (size in listOf(node.size.width, node.size.height)) {
                ensureValid(NODE_SIZE.matches(size)) { "$pageTitle: size $size of ${node.id}" }
            }
        }
    }

    fun normalizeName(name: String): String = name.trim().ifEmpty { DEFAULT_SCREEN_NAME }.take(
        MAX_SCREEN_NAME_LENGTH,
    )

    fun coerceDesignSize(size: DeviceSize): DeviceSize = DeviceSize(
        size.widthDp.coerceIn(DeviceSize.MIN, DeviceSize.MAX),
        size.heightDp.coerceIn(DeviceSize.MIN, DeviceSize.MAX),
    )

    /**
     * A screen designed tall or wide stays that way, whatever the tablet's sensor says; a square or unsized one
     * follows it.
     */
    fun orientationOf(size: DeviceSize?): Int = when {
        size == null || size.widthDp == size.heightDp -> ORIENTATION_FOLLOW_DEVICE
        size.heightDp > size.widthDp -> ORIENTATION_PORTRAIT
        else -> ORIENTATION_LANDSCAPE
    }

    private fun clampPage(page: Page, cols: Int, rows: Int): Page = page.copy(
        title = page.title.take(MAX_PAGE_TITLE_LENGTH),
        backgroundImage = page.backgroundImage?.takeIf { BACKGROUND_TOKEN.matches(it) },
        tiles = page.tiles.map { tile ->
            tile.copy(
                w = tile.w.coerceIn(1, cols),
                h = tile.h.coerceIn(1, rows),
                col = tile.col.coerceIn(0, cols - 1),
                row = tile.row.coerceIn(0, rows - 1),
            )
        },
    )

    private inline fun ensureValid(isValid: Boolean, message: () -> String) {
        if (!isValid) throw BadRequestException(message())
    }
}
