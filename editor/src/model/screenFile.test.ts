import { describe, expect, it } from 'vitest'
import { SCREEN_FILE_KIND, isScreenFile, screenFile, screenFileName } from './screenFile'
import type { Screen, ScreenSettings, Theme, TopBar } from './types'

const topBar: TopBar = { show: true, clock: true, people: false }

function settings(theme?: string, darkTheme?: string): ScreenSettings {
  return {
    brightness: 80,
    darkMode: false,
    standbyMinutes: 10,
    standbyBrightness: 0,
    nightBrightness: 5,
    nightDark: true,
    backToHomeSeconds: 0,
    swipe: false,
    orientation: 0,
    theme,
    darkTheme,
  }
}

function sample(theme?: string, darkTheme?: string): Screen {
  return {
    id: 's1',
    name: 'Kitchen',
    grid: { cols: 3, rows: 3 },
    pages: [{ id: 'home', title: 'Home', tiles: [] }],
    homePage: 'home',
    settings: settings(theme, darkTheme),
    topBar,
  }
}

function custom(id: string): Theme {
  return { id, name: id, base: 'rpi', roles: {}, pastels: {} }
}

describe('screen file', () => {
  it('includes only the custom themes the screen names', () => {
    const file = screenFile(sample('kitchen', 'rpi'), [custom('kitchen'), custom('unused')])
    expect(file.kind).toBe(SCREEN_FILE_KIND)
    expect(file.schemaVersion).toBe(2)
    expect(file.screen.pages.map(page => page.id)).toEqual(['home'])
    expect(file.themes.map(entry => entry.id)).toEqual(['kitchen'])
  })

  it('includes the day theme and the dark theme when both are custom', () => {
    const file = screenFile(sample('day', 'night'), [
      custom('day'),
      custom('night'),
      custom('other'),
    ])
    expect(file.themes.map(entry => entry.id)).toEqual(['day', 'night'])
  })

  it('rejects a file that is not this version of a screen', () => {
    expect(isScreenFile(screenFile(sample('kitchen'), []))).toBe(true)
    expect(isScreenFile({ kind: 'layout', schemaVersion: 2, screen: sample() })).toBe(false)
    expect(isScreenFile({ kind: SCREEN_FILE_KIND, schemaVersion: 3, screen: sample() })).toBe(false)
    expect(isScreenFile({ kind: SCREEN_FILE_KIND, schemaVersion: 2 })).toBe(false)
  })

  it('names the download from the screen name', () => {
    expect(screenFileName('Kitchen Tablet')).toBe('kitchen-tablet')
    expect(screenFileName('   ')).toBe('screen')
  })
})
