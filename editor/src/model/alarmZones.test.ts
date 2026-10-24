import { describe, expect, it } from 'vitest'
import { formatAlarmZones, parseAlarmZones } from './alarmZones'

describe('alarm zones', () => {
  it('reads the option the server reads', () => {
    expect(
      parseAlarmZones('alarm_control_panel.basement=Basement; alarm_control_panel.garage ;'),
    ).toEqual([
      { entityId: 'alarm_control_panel.basement', label: 'Basement' },
      { entityId: 'alarm_control_panel.garage', label: '' },
    ])
    expect(parseAlarmZones(undefined)).toEqual([])
  })

  it('writes labels that cannot split the list', () => {
    expect(
      formatAlarmZones([
        { entityId: 'alarm_control_panel.upstairs', label: 'Up;stairs=1' },
        { entityId: '', label: 'Unchosen' },
        { entityId: 'alarm_control_panel.garage', label: ' ' },
      ]),
    ).toBe('alarm_control_panel.upstairs=Up stairs 1;alarm_control_panel.garage')
  })
})
