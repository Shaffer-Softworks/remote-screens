// The RPI theme of the add-on (server/.../render/Theme.kt): the screen's fixed colours (Role) and the accent a tile
// lights up in when it is on (Pastel).

export const RPI = {
  page: '#494949',
  card: '#333333',
  cardHeader: '#494949',
  header: '#494949',
  headerDark: '#2F2E2E',
  nav: '#2F2E2E',
  navOn: '#14E1BA',
  navLine: '#03DAC5',
  ink: '#FFFFFF',
  inkSoft: '#CACACA',
  inkDim: '#939393',
  accentDark: '#1DA48C',
  accentSoft: '#03DAC5',
  blue: '#0986DB',
  home: '#14E1BA',
  homeStrip: '#FFFFFF',
}

export interface Pastel {
  key: string
  color: string
}

export const PASTELS: Pastel[] = [
  { key: 'yellow', color: '#FFC94D' },
  { key: 'orange', color: '#FF9800' },
  { key: 'red', color: '#FF6E6E' },
  { key: 'pink', color: '#F48FB1' },
  { key: 'purple', color: '#B39DDB' },
  { key: 'blue', color: '#0986DB' },
  { key: 'teal', color: '#14E1BA' },
  { key: 'green', color: '#5CCB5F' },
  { key: 'grey', color: '#CACACA' },
]

const BY_DOMAIN: Record<string, string> = {
  light: 'yellow',
  switch: 'orange',
  input_boolean: 'orange',
  fan: 'teal',
  climate: 'teal',
  cover: 'purple',
  media_player: 'pink',
  lock: 'green',
  alarm_control_panel: 'green',
  scene: 'purple',
  script: 'purple',
  button: 'purple',
  vacuum: 'teal',
}

/** The server's `Role` names, as a free-form component names a colour. */
export const ROLES: Record<string, string> = {
  page: '#494949',
  card: '#333333',
  card_header: '#494949',
  header_dark: '#2F2E2E',
  key: '#5C5C5C',
  key_on: '#14E1BA',
  track: '#1B1B1B',
  divider: '#4A4A4A',
  ink: '#FFFFFF',
  ink_soft: '#CACACA',
  ink_dim: '#939393',
  ink_faint: '#5C5C5C',
  ink_dark: '#000000',
  accent: '#14E1BA',
  accent_dark: '#1DA48C',
  blue: '#0986DB',
  ok: '#289907',
  warn: '#F57C00',
  danger: '#A21C1C',
}

/** The hex of a role or pastel key, or undefined for neither. */
export function colorOf(key: string | undefined): string | undefined {
  if (!key) return undefined
  return PASTELS.find((p) => p.key === key)?.color ?? ROLES[key]
}

export function pastel(key: string | undefined, domain: string | undefined): Pastel {
  const k = key ?? (domain && BY_DOMAIN[domain]) ?? 'teal'
  return PASTELS.find((p) => p.key === k) ?? PASTELS[6]
}
