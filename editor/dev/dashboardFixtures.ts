import type { EntityInfo, Node, PlayerStatus, ResolvedTheme, Screen, Tile } from '../src/model/types'

const tile = (id: string, kind: string, name: string, col: number, row: number): Tile => ({
  id, kind, name, col, row, w: 1, h: 1,
  tap: { type: 'none' }, hold: { type: 'none' }, options: {},
})
const settings = {
  brightness: 80, darkMode: false, standbyMinutes: 10, standbyBrightness: 10,
  nightBrightness: 20, nightDark: true, backToHomeSeconds: 60, swipe: true, orientation: 0,
}
const root: Node = {
  id: 'bedroom-root', type: 'column', props: { gap: '24' },
  size: { width: 'fill', height: 'fill' }, style: { padding: 32, corner: 0, background: 'page' },
  children: [
    { id: 'heading', type: 'text', props: { text: 'Good evening', size: '48', color: 'ink' },
      size: { width: 'fill', height: 'wrap' }, style: { padding: 0, corner: 0 }, children: [] },
    { id: 'caption', type: 'text', props: { text: 'Bedroom · 20.0 °C', size: '28', color: 'ink_soft' },
      size: { width: 'fill', height: 'wrap' }, style: { padding: 0, corner: 0 }, children: [] },
    { id: 'light', type: 'button', props: { text: 'Bedside lights', color: 'accent', ink: 'ink' },
      size: { width: 'fill', height: 'dp:100' }, style: { padding: 16, corner: 14 }, children: [] },
  ],
}
export const dashboardScreens: Screen[] = ['Kitchen', 'Living room', 'Bedroom'].map((name, i) => ({
  id: ['kitchen', 'living', 'bedroom'][i], name,
  designSize: { widthDp: 1280, heightDp: 800 },
  grid: { cols: 4, rows: 2 }, homePage: 'home',
  settings: { ...settings, theme: i === 2 ? 'preview-warm' : 'rpi' },
  topBar: { show: true, clock: true, people: false },
  navBar: {},
  pages: [
    { id: 'home', title: 'Home', icon: 'home',
      tiles: [
        { ...tile('lights', 'light', 'Lights', 0, 0), entityId: 'light.living_room' },
        { ...tile('temperature', 'sensor', 'Temperature', 1, 0), entityId: 'sensor.temperature' },
        tile('blinds', 'cover', 'Blinds', 2, 0), tile('music', 'music', 'Music', 3, 0),
        tile('relax', 'scene', 'Relax', 0, 1), tile('movie', 'scene', 'Movie', 1, 1),
        tile('night', 'scene', 'Night', 2, 1), tile('weather', 'weather', 'Weather', 3, 1),
      ],
      ...(i === 2 ? { tiles: [], root } : {}),
    },
    { id: 'lights', title: 'Lights', icon: 'lightbulb-outline', tiles: [] },
    { id: 'climate', title: 'Climate', icon: 'thermometer', tiles: [] },
  ],
}))
export const dashboardPlayers: PlayerStatus[] = dashboardScreens.map((screen, i) => ({
  deviceId: 'preview-' + screen.id, name: screen.name + ' tablet', screenId: screen.id,
  online: i !== 2, awake: i === 0, widthDp: 1280, heightDp: 800, appVersion: 100,
  lastSeen: Date.now() - (i === 2 ? 3600000 : 0), page: 0,
  battery: i === 2 ? undefined : i === 0 ? 86 : 72, charging: i === 0,
  kiosk: i === 0 ? 'locked' : undefined,
  wifi: i === 0 ? 4 : undefined,
  telemetryAt: i === 0 ? Date.now() : undefined,
  telemetry: i === 0 ? {
    hardware: ['light', 'proximity', 'accelerometer'],
    battery: { health: 'good', temperatureC: 29.5, voltageMv: 4200, currentMa: 850, chargerType: 'usb', cycles: 145 },
    network: { ssid: 'Preview home', bssid: '02:00:00:00:00:01', ipAddress: '192.0.2.10', linkSpeedMbps: 433, frequencyMhz: 5180, rssiDbm: -48 },
    system: { manufacturer: 'Sample vendor', model: 'Wall tablet', osVersion: '14', securityPatch: '2026-09-01', appVersionName: 'UI preview',
      storageFreeBytes: 24000000000, storageTotalBytes: 64000000000, memoryAvailableBytes: 1800000000, memoryTotalBytes: 4000000000, uptimeSeconds: 90360 },
    environment: { illuminanceLux: 120, isNear: false },
    motion: { isMoving: false, orientation: 'landscape', lastMotionAt: Date.now() - 3600000 },
    permissions: { 'android.permission.CAMERA': true, 'android.permission.RECORD_AUDIO': false, 'android.permission.POST_NOTIFICATIONS': true },
  } : undefined,
}))
export function pendingTablets(count = 1): PlayerStatus[] {
  return Array.from({ length: count }, (_, i) => ({
    deviceId: 'pending-' + i, name: i === 0 ? 'Hallway tablet' : 'Office tablet',
    pairingCode: i === 0 ? '482 916' : '725 340', online: true, awake: true,
    widthDp: 1280, heightDp: 800, appVersion: 100, lastSeen: Date.now(), page: 0,
  }))
}
export const dashboardEntities: EntityInfo[] = [
  { entityId: 'light.living_room', name: 'Living room', state: 'on', domain: 'light', area: 'living' },
  { entityId: 'sensor.temperature', name: 'Temperature', state: '21.5', domain: 'sensor', unit: '°C', area: 'living' },
]
const roles = {
  page: '#1B1E24', card: '#2B3038', card_header: '#262B33', card_edge: '#14171C',
  header: '#20252D', header_dark: '#171B22', nav: '#171B22', nav_on: '#254C7C', nav_line: '#4D9AFF',
  ink: '#FFFFFF', ink_soft: '#CDD3DD', ink_dim: '#ABB3C0', ink_faint: '#6E7786', ink_dark: '#101826',
  divider: '#444C59', track: '#404957', accent: '#4D9AFF', accent_dark: '#286CBF', accent_soft: '#91C0FF',
  blue: '#7DB9FF', home: '#20252D', home_strip: '#CBD4E0', weather_title: '#C4D8F5',
  strip: '#242B35', ok: '#94EFBD', warn: '#FFE18C', danger: '#FF8B86', scrim: '#99000000',
  key: '#353E4B', key_on: '#4D9AFF', key_on_ink: '#071422',
}
const pastels = { yellow: '#FFE18C', orange: '#FFBC88', red: '#FF9D96', pink: '#F5B1D6',
  purple: '#C6B5FF', blue: '#91C0FF', teal: '#8DDDD6', green: '#94EFBD', grey: '#CBD3DF' }
export const dashboardThemes: ResolvedTheme[] = [
  { id: 'rpi', name: 'Preview blue', base: 'rpi', builtin: true, dark: true,
    roles, pastels, overrides: { roles: {}, pastels: {} } },
  { id: 'preview-warm', name: 'Preview warm', base: 'rpi', builtin: false, dark: true,
    roles: { ...roles, page: '#2B2423', card: '#40322E', accent: '#D89C78' },
    pastels, overrides: { roles: {}, pastels: {} } },
]
