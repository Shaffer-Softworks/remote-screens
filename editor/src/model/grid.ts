// A port of shared/src/main/kotlin/.../Grid.kt. tests/fixtures/grid_parity.json holds the answers both must
// give, so change both or a test fails. The add-on works in 32-bit floats, hence the Math.fround.

import type { GridSize, NavBar, TopBar } from './types'

const fround = Math.fround

export const TARGET_CELL = 240
export const MIN_CELLS = 2
export const MAX_CELLS = 8
export const PADDING = 12
export const GAP = 10
export const TOP_BAR = 48
export const NAV_BAR = 102
export const COMPACT_NAV_BAR = 66

/** Narrower than this is a phone or a small wall panel: a compact navigation bar and no date in the header. */
export const NARROW = 600

export function navBar(widthDp: number): number {
  return widthDp < NARROW ? COMPACT_NAV_BAR : NAV_BAR
}

/** The height [bar] takes: none when hidden, else its own or the default. */
export function topBarHeight(bar: TopBar): number {
  return bar.show ? (bar.height ?? TOP_BAR) : 0
}

export function navBarHeight(bar: NavBar | undefined, widthDp: number): number {
  return bar?.height ?? navBar(widthDp)
}

export interface GridSpec {
  cols: number
  rows: number
  widthDp: number
  heightDp: number
  padding: number
  gap: number
  topBar: number
  navBar: number
  cellW: number
  cellH: number
  large: boolean
  x(col: number): number
  y(row: number): number
  w(span: number): number
  h(span: number): number
}

function available(
  widthDp: number,
  heightDp: number,
  topBar: number,
  navBar: number,
): [number, number] {
  return [
    fround(widthDp - 2 * PADDING),
    fround(fround(fround(heightDp - topBar) - navBar) - 2 * PADDING),
  ]
}

function clamp(value: number, lo: number, hi: number): number {
  return Math.min(Math.max(value, lo), hi)
}

function fit(avail: number): number {
  return clamp(
    Math.round(fround(fround(avail + GAP) / fround(TARGET_CELL + GAP))),
    MIN_CELLS,
    MAX_CELLS,
  )
}

function round1(value: number): number {
  return fround(Math.floor(fround(fround(value * 10) + 0.5)) / 10)
}

/** The grid a device of this size gets when it is adopted. */
export function suggest(widthDp: number, heightDp: number): GridSize {
  const [w, h] = available(widthDp, heightDp, TOP_BAR, navBar(widthDp))
  return { cols: fit(w), rows: fit(h) }
}

/** The geometry of [size] on a device of [widthDp] by [heightDp], under a [topBar] and over a [nav] of these heights. */
export function spec(
  size: GridSize,
  widthDp: number,
  heightDp: number,
  topBar = TOP_BAR,
  nav = navBar(widthDp),
): GridSpec {
  topBar = fround(topBar)
  nav = fround(nav)
  const [availW, availH] = available(widthDp, heightDp, topBar, nav)
  const cols = clamp(size.cols, 1, MAX_CELLS)
  const rows = clamp(size.rows, 1, MAX_CELLS)
  const cellW = round1(fround(fround(availW - fround(GAP * (cols - 1))) / cols))
  const cellH = round1(fround(fround(availH - fround(GAP * (rows - 1))) / rows))
  return {
    cols,
    rows,
    widthDp,
    heightDp,
    padding: PADDING,
    gap: GAP,
    topBar,
    navBar: nav,
    cellW,
    cellH,
    large: cellW >= 170 && cellH >= 170,
    x: col => fround(PADDING + fround(col * fround(cellW + GAP))),
    y: row => fround(fround(topBar + PADDING) + fround(row * fround(cellH + GAP))),
    w: span => fround(fround(span * cellW) + fround((span - 1) * GAP)),
    h: span => fround(fround(span * cellH) + fround((span - 1) * GAP)),
  }
}

export interface Placeable {
  id: string
  col: number
  row: number
  w: number
  h: number
  full?: boolean
}

export interface Placed<T extends Placeable> {
  tile: T
  col: number
  row: number
  w: number
  h: number
}

/**
 * Places tiles on a grid that may be smaller than the one they were laid out for: tiles that still fit keep their
 * cell, the rest move to the first free spot in reading order, shrinking if they must.
 */
export function reflow<T extends Placeable>(tiles: T[], size: GridSize): Placed<T>[] {
  const full = tiles.find(tile => tile.full)
  if (full) return [{ tile: full, col: 0, row: 0, w: size.cols, h: size.rows }]
  const taken = Array.from({ length: size.rows }, () => new Array<boolean>(size.cols).fill(false))
  const out: Placed<T>[] = []
  const byPosition = (tileA: T, tileB: T) => tileA.row - tileB.row || tileA.col - tileB.col
  const fits = tiles.filter(
    tile => tile.col + tile.w <= size.cols && tile.row + tile.h <= size.rows,
  )
  const rest = tiles.filter(tile => !fits.includes(tile))
  for (const tile of [...fits].sort(byPosition)) {
    if (free(taken, tile.col, tile.row, tile.w, tile.h)) {
      mark(taken, tile.col, tile.row, tile.w, tile.h)
      out.push({ tile, col: tile.col, row: tile.row, w: tile.w, h: tile.h })
    } else {
      const placed = spot(taken, tile, size)
      if (placed) out.push(placed)
    }
  }
  for (const tile of [...rest].sort(byPosition)) {
    const placed = spot(taken, tile, size)
    if (placed) out.push(placed)
  }
  return out
}

function spot<T extends Placeable>(taken: boolean[][], tile: T, size: GridSize): Placed<T> | null {
  let w = Math.min(tile.w, size.cols)
  let h = Math.min(tile.h, size.rows)
  while (w >= 1 && h >= 1) {
    for (let row = 0; row <= size.rows - h; row++) {
      for (let col = 0; col <= size.cols - w; col++) {
        if (free(taken, col, row, w, h)) {
          mark(taken, col, row, w, h)
          return { tile, col, row, w, h }
        }
      }
    }
    if (w >= h) w--
    else h--
  }
  return null
}

function free(taken: boolean[][], col: number, row: number, w: number, h: number): boolean {
  for (let y = row; y < row + h; y++) {
    for (let x = col; x < col + w; x++) {
      if (y >= taken.length || x >= taken[y].length || taken[y][x]) return false
    }
  }
  return true
}

function mark(taken: boolean[][], col: number, row: number, w: number, h: number): void {
  for (let y = row; y < row + h; y++) for (let x = col; x < col + w; x++) taken[y][x] = true
}

/** Whether [tile] can sit at col/row with w/h among [others] on [size], without overlapping or leaving the grid. */
export function fitsAt(
  others: Placeable[],
  size: GridSize,
  col: number,
  row: number,
  w: number,
  h: number,
): boolean {
  if (col < 0 || row < 0 || col + w > size.cols || row + h > size.rows) return false
  return others.every(
    other =>
      col >= other.col + other.w ||
      other.col >= col + w ||
      row >= other.row + other.h ||
      other.row >= row + h,
  )
}

/** The first free spot for a w by h tile in reading order, or null when the page is full. */
export function firstFree(
  others: Placeable[],
  size: GridSize,
  w: number,
  h: number,
): { col: number; row: number } | null {
  for (let row = 0; row + h <= size.rows; row++) {
    for (let col = 0; col + w <= size.cols; col++) {
      if (fitsAt(others, size, col, row, w, h)) return { col, row }
    }
  }
  return null
}
