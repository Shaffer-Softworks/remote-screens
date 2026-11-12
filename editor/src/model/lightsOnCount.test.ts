import { describe, expect, it } from 'vitest'
import {
  countLightsOn,
  lightsOnSubtitle,
  lightsRoomRows,
} from './lightsOnCount'
import type { AreaInfo, EntityInfo } from './types'

function light(entityId: string, area: string, state: string): EntityInfo {
  return {
    entityId,
    name: entityId,
    state,
    domain: 'light',
    area,
  }
}

describe('lights on count', () => {
  it('words the tablet English strings', () => {
    expect(lightsOnSubtitle(0)).toBe('All lights are off')
    expect(lightsOnSubtitle(1)).toBe('1 Light is on')
    expect(lightsOnSubtitle(2)).toBe('2 Lights are on')
  })

  it('counts only lights that are on in the area', () => {
    const entities: EntityInfo[] = [
      light('light.a', 'living_room', 'on'),
      light('light.b', 'living_room', 'off'),
      light('light.c', 'kitchen', 'on'),
      {
        entityId: 'switch.hallway_light',
        name: 'Hallway',
        state: 'on',
        domain: 'switch',
        area: 'living_room',
      },
    ]
    expect(countLightsOn(entities, 'living_room')).toBe(1)
    expect(countLightsOn(entities, 'kitchen')).toBe(1)
    expect(countLightsOn(entities, 'hallway')).toBe(0)
  })

  it('builds room rows with subtitles, or sample names without', () => {
    const areas: AreaInfo[] = [
      { id: 'kitchen', name: 'Kitchen' },
      { id: 'living_room', name: 'Living room' },
    ]
    const entities = [light('light.a', 'living_room', 'on'), light('light.b', 'living_room', 'on')]
    expect(lightsRoomRows(areas, entities)).toEqual([
      { areaId: 'kitchen', name: 'Kitchen', subtitle: 'All lights are off' },
      { areaId: 'living_room', name: 'Living room', subtitle: '2 Lights are on' },
    ])
    expect(lightsRoomRows([], [])).toEqual([
      { areaId: 'Bedroom', name: 'Bedroom', subtitle: null },
      { areaId: 'Garden', name: 'Garden', subtitle: null },
      { areaId: 'Kitchen', name: 'Kitchen', subtitle: null },
      { areaId: 'Living Room', name: 'Living Room', subtitle: null },
    ])
  })
})
