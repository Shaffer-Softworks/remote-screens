import type { AreaInfo, EntityInfo } from './types'

/** A blinds-page room row in the editor mockup. */
export interface BlindsRoomRow {
  areaId: string
  name: string
  /** Open-blinds subtitle, or null when the room is a name-only sample fallback. */
  subtitle: string | null
}

const SAMPLE_ROOM_NAMES = ['Bedroom', 'Living Room'] as const

/** Worded like the tablet's `ui.blinds_*` English strings. */
export function blindsOpenSubtitle(openCount: number): string {
  if (openCount <= 0) return 'All blinds are closed'
  if (openCount === 1) return '1 Blind is open'
  return `${openCount} Blinds are open`
}

/** Count of `cover.*` entities in [areaId] whose state is `open`. */
export function countBlindsOpen(entities: EntityInfo[], areaId: string): number {
  return entities.filter(
    entity =>
      entity.domain === 'cover' && entity.area === areaId && entity.state === 'open',
  ).length
}

/**
 * Rooms for the blinds mockup: areas that have at least one cover, with open-count subtitles,
 * or sample names with no subtitle when Home Assistant has not sent areas yet.
 */
export function blindsRoomRows(areas: AreaInfo[], entities: EntityInfo[]): BlindsRoomRow[] {
  if (areas.length === 0) {
    return SAMPLE_ROOM_NAMES.map(name => ({
      areaId: name,
      name,
      subtitle: null,
    }))
  }
  return [...areas]
    .sort((areaA, areaB) => areaA.name.localeCompare(areaB.name))
    .map((area): BlindsRoomRow | null => {
      const covers = entities.filter(
        entity => entity.domain === 'cover' && entity.area === area.id,
      )
      if (covers.length === 0) return null
      return {
        areaId: area.id,
        name: area.name,
        subtitle: blindsOpenSubtitle(countBlindsOpen(entities, area.id)),
      }
    })
    .filter((row): row is BlindsRoomRow => row != null)
}
