// A port of shared/src/main/kotlin/.../Grid.kt. tests/fixtures/grid_parity.json holds the answers both must
// give, so change both or a test fails. The add-on works in 32-bit floats, hence the Math.fround.

import type { GridSize } from './types'

const f = Math.fround

export const TARGET_CELL = 240
export const MIN_CELLS = 2
export const MAX_CELLS = 8
export const PADDING = 12
export const GAP = 10
export const TOP_BAR = 44
export const PAGE_BAR = 24

export interface GridSpec {
  cols: number
  rows: number
  widthDp: number
  heightDp: number
  padding: number
  gap: number
  topBar: number
  pageBar: number
  cellW: number
  cellH: number
  large: boolean
  x(col: number): number
  y(row: number): number
  w(span: number): number
  h(span: number): number
}

function available(widthDp: number, heightDp: number, showTopBar: boolean): [number, number] {
  const top = showTopBar ? TOP_BAR : 0
  return [f(widthDp - 2 * PADDING), f(f(f(heightDp - top) - PAGE_BAR) - 2 * PADDING)]
}

function clamp(v: number, lo: number, hi: number): number {
  return Math.min(Math.max(v, lo), hi)
}

function fit(avail: number): number {
  return clamp(Math.round(f(f(avail + GAP) / f(TARGET_CELL + GAP))), MIN_CELLS, MAX_CELLS)
}

function round1(v: number): number {
  return f(Math.floor(f(f(v * 10) + 0.5)) / 10)
}

/** The grid a device of this size gets when it is adopted. */
export function suggest(widthDp: number, heightDp: number): GridSize {
  const [w, h] = available(widthDp, heightDp, true)
  return { cols: fit(w), rows: fit(h) }
}

/** The geometry of [size] on a device of [widthDp] by [heightDp]. */
export function spec(size: GridSize, widthDp: number, heightDp: number, showTopBar = true): GridSpec {
  const [availW, availH] = available(widthDp, heightDp, showTopBar)
  const cols = clamp(size.cols, 1, MAX_CELLS)
  const rows = clamp(size.rows, 1, MAX_CELLS)
  const cellW = round1(f(f(availW - f(GAP * (cols - 1))) / cols))
  const cellH = round1(f(f(availH - f(GAP * (rows - 1))) / rows))
  const topBar = showTopBar ? TOP_BAR : 0
  return {
    cols,
    rows,
    widthDp,
    heightDp,
    padding: PADDING,
    gap: GAP,
    topBar,
    pageBar: PAGE_BAR,
    cellW,
    cellH,
    large: cellW >= 170 && cellH >= 170,
    x: (col) => f(PADDING + f(col * f(cellW + GAP))),
    y: (row) => f(f(topBar + PADDING) + f(row * f(cellH + GAP))),
    w: (span) => f(f(span * cellW) + f((span - 1) * GAP)),
    h: (span) => f(f(span * cellH) + f((span - 1) * GAP)),
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
  const full = tiles.find((t) => t.full)
  if (full) return [{ tile: full, col: 0, row: 0, w: size.cols, h: size.rows }]
  const taken = Array.from({ length: size.rows }, () => new Array<boolean>(size.cols).fill(false))
  const out: Placed<T>[] = []
  const byPosition = (a: T, b: T) => a.row - b.row || a.col - b.col
  const fits = tiles.filter((t) => t.col + t.w <= size.cols && t.row + t.h <= size.rows)
  const rest = tiles.filter((t) => !fits.includes(t))
  for (const t of [...fits].sort(byPosition)) {
    if (free(taken, t.col, t.row, t.w, t.h)) {
      mark(taken, t.col, t.row, t.w, t.h)
      out.push({ tile: t, col: t.col, row: t.row, w: t.w, h: t.h })
    } else {
      const p = spot(taken, t, size)
      if (p) out.push(p)
    }
  }
  for (const t of [...rest].sort(byPosition)) {
    const p = spot(taken, t, size)
    if (p) out.push(p)
  }
  return out
}

function spot<T extends Placeable>(taken: boolean[][], t: T, size: GridSize): Placed<T> | null {
  let w = Math.min(t.w, size.cols)
  let h = Math.min(t.h, size.rows)
  while (w >= 1 && h >= 1) {
    for (let r = 0; r <= size.rows - h; r++) {
      for (let c = 0; c <= size.cols - w; c++) {
        if (free(taken, c, r, w, h)) {
          mark(taken, c, r, w, h)
          return { tile: t, col: c, row: r, w, h }
        }
      }
    }
    if (w >= h) w--
    else h--
  }
  return null
}

function free(taken: boolean[][], c: number, r: number, w: number, h: number): boolean {
  for (let y = r; y < r + h; y++) {
    for (let x = c; x < c + w; x++) {
      if (y >= taken.length || x >= taken[y].length || taken[y][x]) return false
    }
  }
  return true
}

function mark(taken: boolean[][], c: number, r: number, w: number, h: number): void {
  for (let y = r; y < r + h; y++) for (let x = c; x < c + w; x++) taken[y][x] = true
}

/** Whether [tile] can sit at col/row with w/h among [others] on [size], without overlapping or leaving the grid. */
export function fitsAt(others: Placeable[], size: GridSize, col: number, row: number, w: number, h: number): boolean {
  if (col < 0 || row < 0 || col + w > size.cols || row + h > size.rows) return false
  return others.every((o) => col >= o.col + o.w || o.col >= col + w || row >= o.row + o.h || o.row >= row + h)
}

/** The first free spot for a w by h tile in reading order, or null when the page is full. */
export function firstFree(others: Placeable[], size: GridSize, w: number, h: number): { col: number; row: number } | null {
  for (let row = 0; row + h <= size.rows; row++) {
    for (let col = 0; col + w <= size.cols; col++) {
      if (fitsAt(others, size, col, row, w, h)) return { col, row }
    }
  }
  return null
}
