// What the catalogue says about a tile: its default icon, name, sizes and tap.

import { CATALOGUE, type TypeSpec } from '../generated/catalogue'
import type { EntityInfo, Tile } from './types'
import { domainOf, kindOf } from './types'

export function specOf(tile: Pick<Tile, 'entityId' | 'kind'>): TypeSpec | undefined {
  return CATALOGUE[kindOf(tile)]
}

/** Sizes as [w, h]; `full` is the whole page. */
export function sizesOf(tile: Pick<Tile, 'entityId' | 'kind'>): { label: string; w: number; h: number; full: boolean }[] {
  const sizes = specOf(tile)?.sizes ?? ['1x1', '2x1', '2x2']
  return sizes.map((s) => {
    if (s === 'full') return { label: 'Full page', w: 1, h: 1, full: true }
    const [w, h] = s.split('x').map(Number)
    return { label: s.replace('x', ' × '), w, h, full: false }
  })
}

export function iconOf(tile: Tile, entity?: EntityInfo): string {
  if (tile.icon) return tile.icon
  if (entity?.icon) return entity.icon
  const spec = specOf(tile)
  const on = entity && ['on', 'open', 'unlocked', 'playing', 'home', 'cleaning'].includes(entity.state)
  return (on && spec?.iconOn) || spec?.icon || 'help-circle-outline'
}

export function nameOf(tile: Tile, entity?: EntityInfo, pageTitle?: (id: string) => string | undefined): string {
  if (tile.name) return tile.name
  if (entity) return entity.name
  if (kindOf(tile) === 'nav' && tile.options.page) return pageTitle?.(tile.options.page) ?? 'Page'
  return specOf(tile)?.name ?? kindOf(tile)
}

export function stateOf(entity?: EntityInfo): string {
  if (!entity) return ''
  if (['scene', 'button', 'input_button'].includes(entity.domain)) return ''
  if (entity.domain === 'script' && entity.state !== 'on') return ''
  return entity.unit ? `${entity.state} ${entity.unit}` : entity.state.replace(/_/g, ' ')
}

let seq = 0
export function newTileId(): string {
  seq = (seq + 1) % 1000
  return `t${Date.now().toString(36)}${seq.toString(36)}`
}

/** A new tile for an entity or a kind, at the first size the catalogue allows. */
export function newTile(source: { entityId?: string; kind?: string }, col: number, row: number): Tile {
  const tile: Tile = {
    id: newTileId(),
    entityId: source.entityId,
    kind: source.kind,
    col,
    row,
    w: 1,
    h: 1,
    tap: { type: 'default' },
    hold: { type: 'card' },
    options: {},
  }
  const first = sizesOf(tile).find((s) => !s.full) ?? sizesOf(tile)[0]
  tile.w = first.w
  tile.h = first.h
  tile.full = first.full || undefined
  return tile
}

export function tileDomain(tile: Tile): string | undefined {
  return domainOf(tile)
}
