import type { AreaInfo, EntityInfo } from './types'

/** A lights-page room row in the editor mockup. */
export interface LightsRoomRow {
  areaId: string
  name: string
  /** Lights-on subtitle, or null when the room is a name-only sample fallback. */
  subtitle: string | null
}

const SAMPLE_ROOM_NAMES = ['Bedroom', 'Garden', 'Kitchen', 'Living Room'] as const

/** Worded like the tablet's `ui.lights_*` English strings. Switches and covers do not count. */
export function lightsOnSubtitle(lightsOnCount: number): string {
  if (lightsOnCount <= 0) return 'All lights are off'
  if (lightsOnCount === 1) return '1 Light is on'
  return `${lightsOnCount} Lights are on`
}

/** Count of `light.*` entities in [areaId] whose state is `on`. */
export function countLightsOn(entities: EntityInfo[], areaId: string): number {
  return entities.filter(
    entity =>
      entity.domain === 'light' && entity.area === areaId && entity.state === 'on',
  ).length
}

/**
 * Rooms for the lights mockup: real areas with lights-on subtitles, or sample names with no
 * subtitle when Home Assistant has not sent areas yet.
 */
export function lightsRoomRows(areas: AreaInfo[], entities: EntityInfo[]): LightsRoomRow[] {
  if (areas.length === 0) {
    return SAMPLE_ROOM_NAMES.map(name => ({
      areaId: name,
      name,
      subtitle: null,
    }))
  }
  return [...areas]
    .sort((areaA, areaB) => areaA.name.localeCompare(areaB.name))
    .map(area => ({
      areaId: area.id,
      name: area.name,
      subtitle: lightsOnSubtitle(countLightsOn(entities, area.id)),
    }))
}
