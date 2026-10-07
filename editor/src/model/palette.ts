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

export function pastel(key: string | undefined, domain: string | undefined): Pastel {
  const k = key ?? (domain && BY_DOMAIN[domain]) ?? 'teal'
  return PASTELS.find((p) => p.key === k) ?? PASTELS[6]
}
