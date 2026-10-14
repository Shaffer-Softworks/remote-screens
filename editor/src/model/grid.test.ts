import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { firstFree, fitsAt, navBar, navBarHeight, reflow, spec, suggest, TOP_BAR, topBarHeight } from './grid'

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
    // `topBar` is whether the default bar shows, or its height; `navBar`, when given, is the bottom bar's height.
    const top = typeof c.topBar === 'boolean' ? (c.topBar ? TOP_BAR : 0) : c.topBar
    const bars = `${top === TOP_BAR ? '' : ` with a ${top} dp top bar`}${c.navBar === undefined ? '' : ` and a ${c.navBar} dp nav bar`}`
    it(`measures ${c.cols}x${c.rows} on ${c.width}x${c.height}${bars}`, () => {
      const s = spec({ cols: c.cols, rows: c.rows }, c.width, c.height, top, c.navBar ?? navBar(c.width))
      const e = c.expect
      expect([s.cols, s.rows, s.large]).toEqual([e.cols, e.rows, e.large])
      expect(s.cellW).toBeCloseTo(e.cellW, 3)
      expect(s.cellH).toBeCloseTo(e.cellH, 3)
      expect(s.topBar).toBeCloseTo(e.topBar, 3)
      expect(s.navBar).toBeCloseTo(e.navBar, 3)
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

describe('bar heights', () => {
  it('follow the settings, or the defaults when unset', () => {
    expect(topBarHeight({ show: true, clock: true, people: true })).toBe(TOP_BAR)
    expect(topBarHeight({ show: true, clock: true, people: true, height: 72 })).toBe(72)
    expect(topBarHeight({ show: false, clock: true, people: true, height: 72 })).toBe(0)
    expect(navBarHeight(undefined, 1280)).toBe(navBar(1280))
    expect(navBarHeight({}, 400)).toBe(navBar(400))
    expect(navBarHeight({ height: 80 }, 1280)).toBe(80)
  })
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
