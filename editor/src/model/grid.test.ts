import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { firstFree, fitsAt, reflow, spec, suggest } from './grid'

// The answers the add-on's Grid gives too (shared/src/test/.../GridParityTest.kt).
const fixture = JSON.parse(
  readFileSync(fileURLToPath(new URL('../../../tests/fixtures/grid_parity.json', import.meta.url)), 'utf8'),
)

describe('the same grid as the add-on', () => {
  for (const c of fixture.suggest) {
    it(`suggests for ${c.width}x${c.height}`, () => {
      expect(suggest(c.width, c.height)).toEqual(c.expect)
    })
  }

  for (const c of fixture.spec) {
    it(`measures ${c.cols}x${c.rows} on ${c.width}x${c.height}${c.topBar ? '' : ' without a top bar'}`, () => {
      const s = spec({ cols: c.cols, rows: c.rows }, c.width, c.height, c.topBar)
      const e = c.expect
      expect([s.cols, s.rows, s.large]).toEqual([e.cols, e.rows, e.large])
      expect(s.cellW).toBeCloseTo(e.cellW, 3)
      expect(s.cellH).toBeCloseTo(e.cellH, 3)
      expect(s.topBar).toBeCloseTo(e.topBar, 3)
      e.x.forEach((v: number, i: number) => expect(s.x(i)).toBeCloseTo(v, 3))
      e.y.forEach((v: number, i: number) => expect(s.y(i)).toBeCloseTo(v, 3))
      e.w.forEach((v: number, i: number) => expect(s.w(i + 1)).toBeCloseTo(v, 3))
      e.h.forEach((v: number, i: number) => expect(s.h(i + 1)).toBeCloseTo(v, 3))
    })
  }

  for (const c of fixture.reflow) {
    it(`reflows: ${c.name}`, () => {
      const placed = reflow(c.tiles, { cols: c.cols, rows: c.rows })
      expect(placed.map((p) => ({ id: p.tile.id, col: p.col, row: p.row, w: p.w, h: p.h }))).toEqual(c.expect)
    })
  }
})

describe('placing tiles in the editor', () => {
  const size = { cols: 3, rows: 2 }
  const a = { id: 'a', col: 0, row: 0, w: 2, h: 1 }

  it('does not overlap or leave the grid', () => {
    expect(fitsAt([a], size, 1, 0, 1, 1)).toBe(false)
    expect(fitsAt([a], size, 2, 0, 1, 1)).toBe(true)
    expect(fitsAt([a], size, 2, 0, 2, 1)).toBe(false)
    expect(fitsAt([a], size, 0, 1, 3, 1)).toBe(true)
  })

  it('finds the first free spot in reading order', () => {
    expect(firstFree([a], size, 1, 1)).toEqual({ col: 2, row: 0 })
    expect(firstFree([a], size, 2, 1)).toEqual({ col: 0, row: 1 })
    expect(firstFree([a, { id: 'b', col: 0, row: 1, w: 3, h: 1 }, { id: 'c', col: 2, row: 0, w: 1, h: 1 }], size, 1, 1)).toBeNull()
  })
})
