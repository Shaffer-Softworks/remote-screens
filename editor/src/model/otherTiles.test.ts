import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { CATALOGUE } from '../generated/catalogue'
import { OTHER_TILE_KINDS, PANELS, panelSource } from './otherTiles'
import { newTile } from './tiles'
import type { EntityInfo } from './types'

function edgeToEdgeKinds(): string[] {
  const kotlin = readFileSync(
    fileURLToPath(
      new URL(
        '../../../server/src/main/kotlin/tech/shaffersoftworks/remotescreens/server/render/tiles/TileBox.kt',
        import.meta.url,
      ),
    ),
    'utf8',
  )
  const setStart = kotlin.indexOf('setOf(', kotlin.indexOf('fun Tile.edgeToEdge()'))
  const set = kotlin.slice(setStart, kotlin.indexOf(')', setStart))
  return [...set.matchAll(/"([a-z_]+)"/g)].map(match => match[1])
}

const entity = (entityId: string): EntityInfo =>
  ({ entityId, domain: entityId.split('.')[0], name: entityId, state: 'on' }) as EntityInfo

describe('other tiles', () => {
  it('offers every page the add-on draws edge to edge', () => {
    const offered = new Set([
      ...OTHER_TILE_KINDS,
      ...PANELS.map(panel => panel.kind ?? panel.domain),
    ])
    const kinds = edgeToEdgeKinds()
    expect(kinds.length).toBeGreaterThan(0)
    expect(kinds.filter(kind => !offered.has(kind)).sort()).toEqual([])
  })

  it('offers only kinds and panels the catalogue can make full', () => {
    for (const kind of OTHER_TILE_KINDS) expect(CATALOGUE[kind], kind).toBeDefined()
    for (const panel of PANELS)
      expect(CATALOGUE[panel.kind ?? panel.domain]?.sizes, panel.name).toContain('full')
  })

  it('makes a panel a full tile on the first entity of its domain', () => {
    const music = PANELS.find(panel => panel.kind === 'music')!
    const source = panelSource(music, [entity('light.desk'), entity('media_player.kitchen')])
    expect(source).toEqual({ entityId: 'media_player.kitchen', kind: 'music', full: true })
    expect(newTile(source!, 0, 0)).toMatchObject({ full: true, kind: 'music' })
  })

  it('has no panel when Home Assistant has no entity for it', () => {
    const alarm = PANELS.find(panel => panel.domain === 'alarm_control_panel')!
    expect(panelSource(alarm, [entity('light.desk')])).toBeUndefined()
  })

  it('keeps an entity added without full at its first small size', () => {
    expect(newTile({ entityId: 'weather.home' }, 0, 0).full).toBeUndefined()
  })
})
