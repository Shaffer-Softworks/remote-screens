import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import {
  firstFree,
  fitsAt,
  navBar,
  navBarHeight,
  reflow,
  spec,
  suggest,
  TOP_BAR,
  topBarHeight,
} from './grid'

// The answers the add-on's Grid gives too (shared/src/test/.../GridParityTest.kt).
const fixture = JSON.parse(
  readFileSync(
    fileURLToPath(new URL('../../../tests/fixtures/grid_parity.json', import.meta.url)),
    'utf8',
  ),
)

describe('the same grid as the add-on', () => {
  for (const case_ of fixture.suggest) {
    it(`suggests for ${case_.width}x${case_.height}`, () => {
      expect(suggest(case_.width, case_.height)).toEqual(case_.expect)
    })
  }

  for (const case_ of fixture.spec) {
    // `topBar` is whether the default bar shows, or its height; `navBar`, when given, is the bottom bar's height.
    const top = typeof case_.topBar === 'boolean' ? (case_.topBar ? TOP_BAR : 0) : case_.topBar
    const bars = `${top === TOP_BAR ? '' : ` with a ${top} dp top bar`}${case_.navBar === undefined ? '' : ` and a ${case_.navBar} dp nav bar`}`
    it(`measures ${case_.cols}x${case_.rows} on ${case_.width}x${case_.height}${bars}`, () => {
      const grid = spec(
        { cols: case_.cols, rows: case_.rows },
        case_.width,
        case_.height,
        top,
        case_.navBar ?? navBar(case_.width),
      )
      const expected = case_.expect
      expect([grid.cols, grid.rows, grid.large]).toEqual([
        expected.cols,
        expected.rows,
        expected.large,
      ])
      expect(grid.cellW).toBeCloseTo(expected.cellW, 3)
      expect(grid.cellH).toBeCloseTo(expected.cellH, 3)
      expect(grid.topBar).toBeCloseTo(expected.topBar, 3)
      expect(grid.navBar).toBeCloseTo(expected.navBar, 3)
      expected.x.forEach((value: number, i: number) => expect(grid.x(i)).toBeCloseTo(value, 3))
      expected.y.forEach((value: number, i: number) => expect(grid.y(i)).toBeCloseTo(value, 3))
      expected.w.forEach((value: number, i: number) => expect(grid.w(i + 1)).toBeCloseTo(value, 3))
      expected.h.forEach((value: number, i: number) => expect(grid.h(i + 1)).toBeCloseTo(value, 3))
    })
  }

  for (const case_ of fixture.reflow) {
    it(`reflows: ${case_.name}`, () => {
      const placed = reflow(case_.tiles, { cols: case_.cols, rows: case_.rows })
      expect(
        placed.map(item => ({
          id: item.tile.id,
          col: item.col,
          row: item.row,
          w: item.w,
          h: item.h,
        })),
      ).toEqual(case_.expect)
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
  const tile = { id: 'a', col: 0, row: 0, w: 2, h: 1 }

  it('does not overlap or leave the grid', () => {
    expect(fitsAt([tile], size, 1, 0, 1, 1)).toBe(false)
    expect(fitsAt([tile], size, 2, 0, 1, 1)).toBe(true)
    expect(fitsAt([tile], size, 2, 0, 2, 1)).toBe(false)
    expect(fitsAt([tile], size, 0, 1, 3, 1)).toBe(true)
  })

  it('finds the first free spot in reading order', () => {
    expect(firstFree([tile], size, 1, 1)).toEqual({ col: 2, row: 0 })
    expect(firstFree([tile], size, 2, 1)).toEqual({ col: 0, row: 1 })
    expect(
      firstFree(
        [tile, { id: 'b', col: 0, row: 1, w: 3, h: 1 }, { id: 'c', col: 2, row: 0, w: 1, h: 1 }],
        size,
        1,
        1,
      ),
    ).toBeNull()
  })
})
