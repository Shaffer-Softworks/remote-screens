// The pastel tile colours of the add-on's theme (server/.../render/Theme.kt, Pastel).

export interface Pastel {
  key: string
  light: string
  dark: string
  inkLight: string
  inkDark: string
}

export const PASTELS: Pastel[] = [
  { key: 'yellow', light: '#FFE9A8', dark: '#5C4A12', inkLight: '#3D2F00', inkDark: '#FFE9A8' },
  { key: 'orange', light: '#FFD3AE', dark: '#5E3414', inkLight: '#4A2200', inkDark: '#FFD3AE' },
  { key: 'red', light: '#FFC4C0', dark: '#5E1F1C', inkLight: '#4D0A06', inkDark: '#FFC4C0' },
  { key: 'pink', light: '#FAC8E3', dark: '#5A2140', inkLight: '#4A0B2D', inkDark: '#FAC8E3' },
  { key: 'purple', light: '#DCCBFA', dark: '#3D2B63', inkLight: '#2A1458', inkDark: '#DCCBFA' },
  { key: 'blue', light: '#C4DBFF', dark: '#1E3A66', inkLight: '#0B2A5C', inkDark: '#C4DBFF' },
  { key: 'teal', light: '#BDEBE4', dark: '#184C46', inkLight: '#053D36', inkDark: '#BDEBE4' },
  { key: 'green', light: '#CDEDC0', dark: '#2A4B1F', inkLight: '#173D0A', inkDark: '#CDEDC0' },
  { key: 'grey', light: '#E3E3E6', dark: '#3A3C42', inkLight: '#26272B', inkDark: '#E3E3E6' },
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

export function pastel(key: string | undefined, domain: string | undefined): Pastel {
  const k = key ?? (domain && BY_DOMAIN[domain]) ?? 'blue'
  return PASTELS.find((p) => p.key === k) ?? PASTELS[5]
}
