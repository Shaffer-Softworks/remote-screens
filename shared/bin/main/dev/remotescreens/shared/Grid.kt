package dev.remotescreens.shared

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
    /** The bottom navigation bar: a 2 dp line over a row of 100 dp page buttons. */
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

    /** The grid a device of this size gets when it is adopted. */
    fun suggest(widthDp: Float, heightDp: Float): GridSize {
        val (availW, availH) = available(widthDp, heightDp, showTopBar = true)
        return GridSize(fit(availW), fit(availH))
    }

    /** The geometry of [size] on a device of [widthDp] by [heightDp]. */
    fun spec(size: GridSize, widthDp: Float, heightDp: Float, showTopBar: Boolean = true): GridSpec {
        val (availW, availH) = available(widthDp, heightDp, showTopBar)
        val cols = size.cols.coerceIn(1, MAX_CELLS)
        val rows = size.rows.coerceIn(1, MAX_CELLS)
        val cellW = round1((availW - GAP * (cols - 1)) / cols)
        val cellH = round1((availH - GAP * (rows - 1)) / rows)
        return GridSpec(
            cols = cols,
            rows = rows,
            widthDp = widthDp,
            heightDp = heightDp,
            padding = PADDING,
            gap = GAP,
            topBar = if (showTopBar) TOP_BAR else 0f,
            navBar = NAV_BAR,
            cellW = cellW,
            cellH = cellH,
        )
    }

    private fun available(widthDp: Float, heightDp: Float, showTopBar: Boolean): Pair<Float, Float> {
        val top = if (showTopBar) TOP_BAR else 0f
        return (widthDp - 2 * PADDING) to (heightDp - top - NAV_BAR - 2 * PADDING)
    }

    private fun fit(avail: Float): Int =
        ((avail + GAP) / (TARGET_CELL + GAP)).roundToInt().coerceIn(MIN_CELLS, MAX_CELLS)

    private fun round1(v: Float): Float = floor(v * 10f + 0.5f) / 10f
}

/**
 * Places tiles on a grid that may be smaller than the one they were laid out for: tiles that still fit keep their
 * cell, the rest move to the first free spot in reading order, shrinking if they must.
 */
object Reflow {
    data class Placed(val tile: Tile, val col: Int, val row: Int, val w: Int, val h: Int)

    fun place(tiles: List<Tile>, size: GridSize): List<Placed> {
        val taken = Array(size.rows) { BooleanArray(size.cols) }
        val out = mutableListOf<Placed>()
        val full = tiles.firstOrNull { it.full }
        if (full != null) return listOf(Placed(full, 0, 0, size.cols, size.rows))

        val (fits, rest) = tiles.partition { it.col + it.w <= size.cols && it.row + it.h <= size.rows }
        for (t in fits.sortedWith(compareBy({ it.row }, { it.col }))) {
            if (free(taken, t.col, t.row, t.w, t.h)) {
                mark(taken, t.col, t.row, t.w, t.h)
                out += Placed(t, t.col, t.row, t.w, t.h)
            } else {
                spot(taken, t, size)?.let { out += it }
            }
        }
        for (t in rest.sortedWith(compareBy({ it.row }, { it.col }))) spot(taken, t, size)?.let { out += it }
        return out
    }

    private fun spot(taken: Array<BooleanArray>, t: Tile, size: GridSize): Placed? {
        var w = t.w.coerceAtMost(size.cols)
        var h = t.h.coerceAtMost(size.rows)
        while (w >= 1 && h >= 1) {
            for (r in 0..size.rows - h) for (c in 0..size.cols - w) {
                if (free(taken, c, r, w, h)) {
                    mark(taken, c, r, w, h)
                    return Placed(t, c, r, w, h)
                }
            }
            if (w >= h) w-- else h--
        }
        return null
    }

    private fun free(taken: Array<BooleanArray>, c: Int, r: Int, w: Int, h: Int): Boolean {
        for (y in r until r + h) for (x in c until c + w) {
            if (y >= taken.size || x >= taken[y].size || taken[y][x]) return false
        }
        return true
    }

    private fun mark(taken: Array<BooleanArray>, c: Int, r: Int, w: Int, h: Int) {
        for (y in r until r + h) for (x in c until c + w) taken[y][x] = true
    }
}
