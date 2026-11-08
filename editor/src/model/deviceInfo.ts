import type { PlayerStatus, Screen } from './types'

export interface DeviceInfoRow { label: string; value: string }
export interface DeviceInfoSection { title: string; icon: string; rows: DeviceInfoRow[] }

const MISSING = 'Not reported'
const number = (value: number | null | undefined): value is number =>
  typeof value === 'number' && Number.isFinite(value)

export function infoValue(value: string | number | boolean | null | undefined, unit = ''): string {
  if (value == null || value === '' || (typeof value === 'number' && !Number.isFinite(value))) return MISSING
  if (typeof value === 'boolean') return value ? 'Yes' : 'No'
  return String(value) + (unit ? ' ' + unit : '')
}

export function infoTimestamp(value: number | null | undefined): string {
  if (!number(value) || value <= 0) return MISSING
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? MISSING : date.toLocaleString()
}

export function infoBytes(value: number | null | undefined): string {
  if (!number(value) || value < 0) return MISSING
  if (value < 1000) return value + ' B'
  const units = ['kB', 'MB', 'GB', 'TB']
  let amount = value / 1000
  let i = 0
  while (amount >= 1000 && i < units.length - 1) { amount /= 1000; i++ }
  return amount.toLocaleString(undefined, { maximumFractionDigits: 2 }) + ' ' + units[i]
}

export function infoDuration(value: number | null | undefined): string {
  if (!number(value) || value < 0) return MISSING
  const seconds = Math.floor(value)
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor(seconds / 3600) % 24
  const minutes = Math.floor(seconds / 60) % 60
  return [days ? days + 'd' : '', hours ? hours + 'h' : '', minutes ? minutes + 'm' : '',
    (!days && !hours && !minutes) ? seconds + 's' : ''].filter(Boolean).join(' ')
}

const PERMISSIONS: Record<string, string> = {
  CAMERA: 'Camera', RECORD_AUDIO: 'Microphone',
  ACCESS_FINE_LOCATION: 'Precise location', ACCESS_COARSE_LOCATION: 'Approximate location',
  ACCESS_BACKGROUND_LOCATION: 'Background location', POST_NOTIFICATIONS: 'Notifications',
  SYSTEM_ALERT_WINDOW: 'Display over other apps', REQUEST_INSTALL_PACKAGES: 'Install unknown apps',
  REQUEST_IGNORE_BATTERY_OPTIMIZATIONS: 'Unrestricted battery',
}
export function permissionName(permission: string): string {
  const key = permission.split('.').at(-1) ?? permission
  return PERMISSIONS[key] ?? key.replaceAll('_', ' ').toLowerCase().replace(/^./, character => character.toUpperCase())
}

export function deviceInfoSections(player: PlayerStatus, screen?: Screen): DeviceInfoSection[] {
  const telemetry = player.telemetry
  const battery = telemetry?.battery
  const network = telemetry?.network
  const system = telemetry?.system
  const environment = telemetry?.environment
  const motion = telemetry?.motion
  const location = telemetry?.location
  const row = (label: string, value: string | number | boolean | null | undefined, unit = '') =>
    ({ label, value: infoValue(value, unit) })
  const section = (title: string, icon: string, rows: DeviceInfoRow[]) => ({ title, icon, rows })
  const permissions = Object.entries(telemetry?.permissions ?? {}).sort(([left], [right]) => left.localeCompare(right))
  const missingPermissions = permissions.filter(([, granted]) => granted === false).length
  const grantedPermissions = permissions.filter(([, granted]) => granted === true).length
  const permissionSummary = !permissions.length ? MISSING
    : missingPermissions ? 'Permissions missing'
      : grantedPermissions === permissions.length ? 'All granted' : 'Some permissions not reported'
  const power = number(battery?.voltageMv) && number(battery?.currentMa)
    ? Math.round(Math.abs(battery.voltageMv * battery.currentMa) / 1000000 * 100) / 100 : undefined
  const groups = [
    section('Device & connection', 'tablet', [
      row('Device name', player.name), row('Device ID', player.deviceId),
      row('Connection', player.online ? 'Online' : 'Offline'),
      row('Display state', player.online ? (player.awake ? 'Awake' : 'Standby') : undefined),
      row('Assigned screen', screen?.name ?? (player.screenId ? player.screenId : 'Unassigned')),
      row('Current page', player.online ? screen?.pages[player.page]?.title : undefined),
      row('Display size', player.widthDp > 0 && player.heightDp > 0 ? player.widthDp + ' × ' + player.heightDp + ' dp' : undefined),
      row('Kiosk mode', player.kiosk),
      row('App version code', player.appVersion > 0 ? player.appVersion : undefined),
      row('Last seen', infoTimestamp(player.lastSeen)),
      row('Last sensor report', infoTimestamp(player.telemetryAt)),
    ]),
    section('Battery & power', 'battery-outline', [
      row('Battery level', player.battery, '%'), row('Charging', player.charging),
      row('Health', battery?.health?.replaceAll('_', ' ')), row('Temperature', battery?.temperatureC, '°C'),
      row('Power', power, 'W'), row('Voltage', battery?.voltageMv, 'mV'),
      row('Current', battery?.currentMa, 'mA'), row('Charger type', battery?.chargerType),
      row('Charge cycles', battery?.cycles),
    ]),
    section('Network', 'wifi', [
      row('Wi-Fi network (SSID)', network?.ssid), row('BSSID', network?.bssid),
      row('IP address', network?.ipAddress), row('Signal strength', network?.rssiDbm, 'dBm'),
      row('Signal level', player.wifi != null ? player.wifi + ' / 4' : undefined),
      row('Link speed', network?.linkSpeedMbps, 'Mbps'), row('Frequency', network?.frequencyMhz, 'MHz'),
    ]),
    section('System', 'cog-outline', [
      row('Manufacturer', system?.manufacturer), row('Model', system?.model),
      row('Android version', system?.osVersion), row('Security patch', system?.securityPatch),
      row('App version', system?.appVersionName), row('Uptime', infoDuration(system?.uptimeSeconds)),
      row('Storage free', infoBytes(system?.storageFreeBytes)), row('Storage total', infoBytes(system?.storageTotalBytes)),
      row('Memory available', infoBytes(system?.memoryAvailableBytes)), row('Memory total', infoBytes(system?.memoryTotalBytes)),
    ]),
    section('Environment & motion', 'thermometer', [
      row('Illuminance', environment?.illuminanceLux, 'lx'), row('Pressure', environment?.pressureHpa, 'hPa'),
      row('Ambient temperature', environment?.temperatureC, '°C'), row('Humidity', environment?.humidityPercent, '%'),
      row('Proximity detected', environment?.isNear), row('Moving', motion?.isMoving),
      row('Orientation', motion?.orientation), row('Last motion', infoTimestamp(motion?.lastMotionAt)),
      row('Reported hardware', telemetry?.hardware?.length ? telemetry.hardware.join(', ') : undefined),
    ]),
    section('Permissions', 'shield-check-outline', [
      row('Summary', permissionSummary),
      ...permissions.map(([key, granted]) => row(permissionName(key),
        granted === true ? 'Granted' : granted === false ? 'Denied' : undefined)),
    ]),
    section('Location', 'map-marker-outline', [
      row('Latitude', location?.latitude), row('Longitude', location?.longitude),
      row('Accuracy', location?.accuracyM, 'm'), row('Altitude', location?.altitudeM, 'm'),
      row('Last fix', infoTimestamp(location?.fixedAt)),
    ]),
  ]
  if (screen) {
    const cfg = screen.settings
    groups.push(section('Assigned screen settings', 'monitor', [
      row('Brightness', cfg.brightness, '%'), row('Dark mode', cfg.darkMode),
      row('Standby after', cfg.standbyMinutes, 'min'), row('Standby brightness', cfg.standbyBrightness, '%'),
      row('Screen off in standby', cfg.standbyScreenOff ?? false),
      row('Wake on proximity', cfg.wakeOnProximity ?? false), row('Wake on face', cfg.wakeOnFace ?? false),
      row('Night start', cfg.nightStart ?? 'Not scheduled'), row('Night end', cfg.nightEnd ?? 'Not scheduled'),
      row('Night brightness', cfg.nightBrightness, '%'), row('Dark mode at night', cfg.nightDark),
      row('Back to home after', cfg.backToHomeSeconds, 's'), row('Kiosk lock configured', cfg.kiosk ?? false),
      row('Kiosk PIN', cfg.kioskPin ? 'Configured' : 'Not configured'),
      row('Sensor reporting', cfg.reportTelemetry ?? true), row('Location sharing', cfg.shareLocation ?? false),
    ]))
  }
  return groups
}
