import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createSSRApp } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { dashboardPlayers, dashboardScreens } from '../../dev/dashboardFixtures'
import DeviceInfoDialog from '../components/DeviceInfoDialog.vue'
import { state } from '../store'
import { deviceInfoSections, infoBytes, infoDuration, infoTimestamp, infoValue, permissionName } from './deviceInfo'
import type { PlayerStatus } from './types'

vi.mock('./icons', () => ({ iconPath: () => '' }))

const values = (player: PlayerStatus) => Object.fromEntries(
  deviceInfoSections(player, dashboardScreens[0]).flatMap(section => section.rows.map(row => [row.label, row.value])),
)

beforeEach(() => {
  state.players = structuredClone(dashboardPlayers)
  state.layout = { schemaVersion: 1, screens: structuredClone(dashboardScreens), preferences: {} }
})

describe('device info values', () => {
  it('keeps real zero and false readings distinct from missing values', () => {
    expect(infoValue(0, 'lx')).toBe('0 lx')
    expect(infoValue(false)).toBe('No')
    expect(infoValue(null)).toBe('Not reported')
    expect(infoValue(NaN)).toBe('Not reported')
    const rows = values({ ...dashboardPlayers[0], battery: 0, charging: false, wifi: 0,
      telemetry: { environment: { illuminanceLux: 0, humidityPercent: 0, isNear: false }, motion: { isMoving: false } } })
    expect(rows['Battery level']).toBe('0 %')
    expect(rows.Charging).toBe('No')
    expect(rows['Signal level']).toBe('0 / 4')
    expect(rows['Proximity detected']).toBe('No')
    expect(rows.Moving).toBe('No')
  })

  it('matches HA battery power derivation and preserves signed current', () => {
    const rows = values({ ...dashboardPlayers[0], telemetry: { battery: { voltageMv: 4000, currentMa: -500 } } })
    expect(rows.Power).toBe('2 W')
    expect(rows.Current).toBe('-500 mA')
    expect(values({ ...dashboardPlayers[0], telemetry: null }).Power).toBe('Not reported')
  })

  it('formats bytes, durations and missing timestamps without inventing data', () => {
    expect(infoBytes(24000000000)).toBe('24 GB')
    expect(infoBytes(0)).toBe('0 B')
    expect(infoBytes(undefined)).toBe('Not reported')
    expect(infoDuration(90360)).toBe('1d 1h 6m')
    expect(infoDuration(0)).toBe('0s')
    expect(infoTimestamp(0)).toBe('Not reported')
    expect(infoTimestamp(1700000000000)).not.toBe('Not reported')
  })

  it('lists reported permissions with readable names and correct grant states', () => {
    expect(permissionName('android.permission.RECORD_AUDIO')).toBe('Microphone')
    expect(permissionName('vendor.NEW_PERMISSION')).toBe('New permission')
    const rows = values({ ...dashboardPlayers[0], telemetry: {
      permissions: { 'android.permission.CAMERA': true, 'android.permission.RECORD_AUDIO': false },
    } })
    expect(rows.Summary).toBe('Permissions missing')
    expect(rows.Camera).toBe('Granted')
    expect(rows.Microphone).toBe('Denied')
    expect(values({ ...dashboardPlayers[0], telemetry: {} }).Summary).toBe('Not reported')
  })

  it('shows opt-in location data when supplied and preserves equator coordinates', () => {
    const rows = values({ ...dashboardPlayers[0], telemetry: { location: {
      latitude: 0, longitude: 0, accuracyM: 0, altitudeM: -3, fixedAt: 1700000000000,
    } } })
    expect(rows.Latitude).toBe('0')
    expect(rows.Longitude).toBe('0')
    expect(rows.Accuracy).toBe('0 m')
    expect(rows.Altitude).toBe('-3 m')
  })

  it('masks the kiosk PIN and handles unknown current pages', () => {
    const screen = structuredClone(dashboardScreens[0])
    screen.settings.kioskPin = '987654'
    const sections = deviceInfoSections({ ...dashboardPlayers[0], page: 999 }, screen)
    const serialized = JSON.stringify(sections)
    expect(serialized).not.toContain('987654')
    expect(serialized).toContain('Configured')
    expect(sections[0].rows.find(row => row.label === 'Current page')?.value).toBe('Not reported')
  })
})

describe('device info popup', () => {
  it('renders the same telemetry groups as the integration without extra API requests', async () => {
    const html = await renderToString(createSSRApp(DeviceInfoDialog, { deviceId: dashboardPlayers[0].deviceId }))
    for (const title of ['Device &amp; connection', 'Battery &amp; power', 'Network', 'System', 'Environment &amp; motion', 'Permissions', 'Location', 'Assigned screen settings']) expect(html).toContain(title)
    expect(html).toContain('Preview home')
    expect(html).toContain('192.0.2.10')
    expect(html).toContain('aria-labelledby="device-info-title"')
  })

  it('explains missing telemetry for older players and disabled reporting', async () => {
    state.players[0].telemetry = undefined
    const html = await renderToString(createSSRApp(DeviceInfoDialog, { deviceId: dashboardPlayers[0].deviceId }))
    expect(html).toContain('No sensor report available yet')
    expect(html).toContain('Not reported')
  })

  it('marks offline data and handles a device removed while viewing', async () => {
    let html = await renderToString(createSSRApp(DeviceInfoDialog, { deviceId: dashboardPlayers[2].deviceId }))
    expect(html).toContain('Live readings are unavailable while offline')
    state.players = []
    html = await renderToString(createSSRApp(DeviceInfoDialog, { deviceId: dashboardPlayers[2].deviceId }))
    expect(html).toContain('no longer in the device list')
  })
})
