/** HA weather-state path layers — mirrors server HaWeatherIcons.kt / ha-weather-paths.json. */

import paths from './ha-weather-paths.json'

export const VIEWPORT = 17

export const COLORS = {
  sun: '#FDD93C',
  moon: '#FCF497',
  cloudBack: '#D4D4D4',
  cloudFront: '#F9F9F9',
  rain: '#30B3FF',
  snow: '#F9F9F9',
} as const

type Layer = keyof typeof COLORS

interface Piece {
  layer: Layer
  path: string
}

const SVG_STATES = new Set([
  'clear-night',
  'cloudy',
  'fog',
  'lightning',
  'lightning-rainy',
  'partlycloudy',
  'pouring',
  'rainy',
  'hail',
  'snowy',
  'snowy-rainy',
  'sunny',
  'windy',
  'windy-variant',
])

const CLOUDY = new Set([
  'partlycloudy',
  'cloudy',
  'fog',
  'windy',
  'windy-variant',
  'hail',
  'rainy',
  'snowy',
  'snowy-rainy',
  'pouring',
  'lightning',
  'lightning-rainy',
])

const RAINY = new Set(['hail', 'rainy', 'pouring', 'lightning-rainy'])
const WINDY = new Set(['windy', 'windy-variant'])
const SNOWY = new Set(['snowy', 'snowy-rainy'])
const LIGHTNING = new Set(['lightning', 'lightning-rainy'])

function path(key: keyof typeof paths): string {
  return paths[key]
}

/** Layered SVG pieces for a HA weather condition, or null to fall back to MDI. */
export function weatherPieces(condition: string, nightTime = false): Piece[] | null {
  if (!SVG_STATES.has(condition)) return null
  const pieces: Piece[] = []
  if (condition === 'sunny') pieces.push({ layer: 'sun', path: path('sunny') })
  else if (condition === 'clear-night') pieces.push({ layer: 'moon', path: path('clear_night') })
  else if (condition === 'partlycloudy' && nightTime)
    pieces.push({ layer: 'moon', path: path('partly_disk') })
  else if (condition === 'partlycloudy') pieces.push({ layer: 'sun', path: path('partly_disk') })
  if (CLOUDY.has(condition)) {
    pieces.push({ layer: 'cloudBack', path: path('cloud_back') })
    pieces.push({ layer: 'cloudFront', path: path('cloud_front') })
  }
  if (RAINY.has(condition)) {
    pieces.push({ layer: 'rain', path: path('rain_1') })
    pieces.push({ layer: 'rain', path: path('rain_2') })
    pieces.push({ layer: 'rain', path: path('rain_3') })
    pieces.push({ layer: 'rain', path: path('rain_4') })
  }
  if (condition === 'pouring') {
    pieces.push({ layer: 'rain', path: path('pour_1') })
    pieces.push({ layer: 'rain', path: path('pour_2') })
  }
  if (WINDY.has(condition)) {
    pieces.push({ layer: 'cloudBack', path: path('wind_1') })
    pieces.push({ layer: 'cloudBack', path: path('wind_2') })
  }
  if (SNOWY.has(condition)) {
    pieces.push({ layer: 'snow', path: path('snow_1') })
    pieces.push({ layer: 'snow', path: path('snow_2') })
    pieces.push({ layer: 'snow', path: path('snow_3') })
  }
  if (LIGHTNING.has(condition)) pieces.push({ layer: 'sun', path: path('bolt') })
  return pieces
}

export const MOCK_FORECAST_CONDITIONS = [
  'sunny',
  'partlycloudy',
  'rainy',
  'cloudy',
  'pouring',
  'snowy',
  'lightning-rainy',
] as const
