// The library's "Other tiles": kinds that are no entity, and the full-page panels.
// Every edge-to-edge page the add-on draws (TileBox.kt `edgeToEdge`) must be offered here.

import type { EntityInfo, TileSource } from './types'

/** Tiles that are no entity. History and favourite music are an entity shown another way (see the tile drawer). */
export const OTHER_TILE_KINDS = [
  'home',
  'clock',
  'bedside',
  'nav',
  'energy',
  'map',
  'lights',
  'blinds',
  'security',
  'cameras',
  'intercom',
]

/**
 * Kinds listed under Other tiles that should open as a full page even when the catalogue also
 * allows a smaller size (energy still offers 2×2 from the size picker).
 */
export const OTHER_TILE_FULL_KINDS = [
  'home',
  'bedside',
  'lights',
  'blinds',
  'security',
  'cameras',
  'intercom',
  'energy',
]

/** Source for an Other-tiles kind click or drag. */
export function kindSource(kind: string): TileSource {
  return OTHER_TILE_FULL_KINDS.includes(kind) ? { kind, full: true } : { kind }
}

export interface PanelSpec {
  name: string
  icon: string
  /** The domain of the entity the panel shows. */
  domain: string
  /** Set when the panel is a kind of its own over that entity, as Music Assistant is. */
  kind?: string
}

/** A full tile of each of these is a page of its own. */
export const PANELS: PanelSpec[] = [
  { name: 'Alarm panel', icon: 'shield-lock', domain: 'alarm_control_panel' },
  { name: 'Weather panel', icon: 'weather-partly-cloudy', domain: 'weather' },
  { name: 'Thermostat panel', icon: 'thermostat', domain: 'climate' },
  { name: 'Music panel', icon: 'music', domain: 'media_player', kind: 'music' },
  { name: 'Calendar panel', icon: 'calendar', domain: 'calendar' },
  { name: 'Vacuum panel', icon: 'robot-vacuum', domain: 'vacuum' },
]

/** A full tile of the panel on the first entity of its domain, or undefined when there is none. */
export function panelSource(panel: PanelSpec, entities: EntityInfo[]): TileSource | undefined {
  const entity = entities.find(candidate => candidate.domain === panel.domain)
  return entity && { entityId: entity.entityId, kind: panel.kind, full: true }
}
