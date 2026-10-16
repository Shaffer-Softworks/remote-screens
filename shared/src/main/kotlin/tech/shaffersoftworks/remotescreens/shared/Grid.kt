package tech.shaffersoftworks.remotescreens.shared

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The geometry of a page on a device. Every number is in dp.
 *
 * The editor ports this to TypeScript (`editor/src/model/grid.ts`); `tests/fixtures/grid_parity.json` holds the
 * answers both must give, so change both or a test fails.
 */
data class GridSpec(
    val cols: Int,
    val rows: Int,
    val widthDp: Float,
    val heightDp: Float,
    val padding: Float,
    val gap: Float,
    val topBar: Float,
    /** The bottom navigation bar: a 2 dp line over a row of page buttons, by default 100 dp tall, 64 dp when narrow. */
    val navBar: Float,
    val cellW: Float,
    val cellH: Float,
) {
    val large: Boolean get() = cellW >= 170f && cellH >= 170f

    fun x(col: Int): Float = padding + col * (cellW + gap)
    fun y(row: Int): Float = topBar + padding + row * (cellH + gap)
    fun w(span: Int): Float = span * cellW + (span - 1) * gap
    fun h(span: Int): Float = span * cellH + (span - 1) * gap
}

object Grid {
    /** About 1.5 inches: 2x3 on a phone, 5x3 on a 10-inch tablet held landscape. */
    const val TARGET_CELL = 240f
    const val MIN_CELLS = 2
    const val MAX_CELLS = 8
    const val PADDING = 12f
    const val GAP = 10f
    const val TOP_BAR = 48f
    const val NAV_BAR = 102f
    const val COMPACT_NAV_BAR = 66f

    /** Narrower than this is a phone or a small wall panel: a compact navigation bar and no date in the header. */
    const val NARROW = 600f

    fun navBar(widthDp: Float): Float = if (widthDp < NARROW) COMPACT_NAV_BAR else NAV_BAR

    /** The height [bar] takes: none when hidden, else its own or the default. */
    fun topBarHeight(bar: TopBar): Float = if (!bar.show) 0f else bar.height?.toFloat() ?: TOP_BAR

    fun navBarHeight(bar: NavBar, widthDp: Float): Float = bar.height?.toFloat() ?: navBar(widthDp)

    /** The grid a device of this size gets when it is adopted. */
    fun suggest(widthDp: Float, heightDp: Float): GridSize {
        val (availableWidth, availableHeight) = available(widthDp, heightDp, TOP_BAR, navBar(widthDp))
        return GridSize(fit(availableWidth), fit(availableHeight))
    }

    /** The geometry of [size] on a device of [widthDp] by [heightDp], between bars [topBar] and [navBar] dp tall. */
    fun spec(
        size: GridSize,
        widthDp: Float,
        heightDp: Float,
        topBar: Float = TOP_BAR,
        navBar: Float = navBar(widthDp),
    ): GridSpec {
        val (availableWidth, availableHeight) = available(widthDp, heightDp, topBar, navBar)
        val cols = size.cols.coerceIn(1, MAX_CELLS)
        val rows = size.rows.coerceIn(1, MAX_CELLS)
        val cellW = round1((availableWidth - GAP * (cols - 1)) / cols)
        val cellH = round1((availableHeight - GAP * (rows - 1)) / rows)
        return GridSpec(
            cols = cols,
            rows = rows,
            widthDp = widthDp,
            heightDp = heightDp,
            padding = PADDING,
            gap = GAP,
            topBar = topBar,
            navBar = navBar,
            cellW = cellW,
            cellH = cellH,
        )
    }

    private fun available(widthDp: Float, heightDp: Float, topBar: Float, navBar: Float): Pair<Float, Float> =
        (widthDp - 2 * PADDING) to (heightDp - topBar - navBar - 2 * PADDING)

    private fun fit(availableSpan: Float): Int =
        ((availableSpan + GAP) / (TARGET_CELL + GAP)).roundToInt().coerceIn(MIN_CELLS, MAX_CELLS)

    private const val TENTHS = 10f
    private const val HALF = 0.5f

    private fun round1(value: Float): Float = floor(value * TENTHS + HALF) / TENTHS
}

/**
 * Places tiles on a grid that may be smaller than the one they were laid out for: tiles that still fit keep their
 * cell, the rest move to the first free spot in reading order, shrinking if they must.
 */
object Reflow {
    data class Placed(val tile: Tile, val col: Int, val row: Int, val w: Int, val h: Int)

    fun place(tiles: List<Tile>, size: GridSize): List<Placed> {
        val taken = Array(size.rows) { BooleanArray(size.cols) }
        val placed = mutableListOf<Placed>()
        val full = tiles.firstOrNull { it.full }
        if (full != null) return listOf(Placed(full, 0, 0, size.cols, size.rows))

        val (fits, rest) = tiles.partition { it.col + it.w <= size.cols && it.row + it.h <= size.rows }
        for (tile in fits.sortedWith(compareBy({ it.row }, { it.col }))) {
            if (free(taken, tile.col, tile.row, tile.w, tile.h)) {
                mark(taken, tile.col, tile.row, tile.w, tile.h)
                placed += Placed(tile, tile.col, tile.row, tile.w, tile.h)
            } else {
                spot(taken, tile, size)?.let { placed += it }
            }
        }
        for (tile in rest.sortedWith(compareBy({ it.row }, { it.col }))) {
            spot(taken, tile, size)?.let { placed += it }
        }
        return placed
    }

    private fun spot(taken: Array<BooleanArray>, tile: Tile, size: GridSize): Placed? {
        var colSpan = tile.w.coerceAtMost(size.cols)
        var rowSpan = tile.h.coerceAtMost(size.rows)
        while (colSpan >= 1 && rowSpan >= 1) {
            for (row in 0..size.rows - rowSpan) for (col in 0..size.cols - colSpan) {
                if (free(taken, col, row, colSpan, rowSpan)) {
                    mark(taken, col, row, colSpan, rowSpan)
                    return Placed(tile, col, row, colSpan, rowSpan)
                }
            }
            if (colSpan >= rowSpan) colSpan-- else rowSpan--
        }
        return null
    }

    private fun free(taken: Array<BooleanArray>, col: Int, row: Int, colSpan: Int, rowSpan: Int): Boolean {
        for (cellRow in row until row + rowSpan) for (cellCol in col until col + colSpan) {
            if (cellRow >= taken.size || cellCol >= taken[cellRow].size || taken[cellRow][cellCol]) return false
        }
        return true
    }

    private fun mark(taken: Array<BooleanArray>, col: Int, row: Int, colSpan: Int, rowSpan: Int) {
        for (cellRow in row until row + rowSpan) for (cellCol in col until col + colSpan) {
            taken[cellRow][cellCol] = true
        }
    }
}
