import { beforeEach, describe, expect, it } from 'vitest'
import { PASTELS, activeThemeId, colorOf, cssColor, pastel, setThemes, themeById, themeVars } from './palette'
import type { ResolvedTheme } from './types'

function theme(id: string, page: string, teal: string): ResolvedTheme {
  return {
    id,
    name: id,
    base: id,
    builtin: true,
    dark: true,
    roles: { page, card_header: '#494949', scrim: '#CC000000' },
    pastels: { teal, yellow: '#FFC94D' },
    overrides: { roles: {}, pastels: {} },
  }
}

describe('theme palette', () => {
  beforeEach(() => {
    setThemes([theme('rpi', '#494949', '#14E1BA'), theme('light', '#EDEBE7', '#0F9E84')])
    activeThemeId.value = 'rpi'
  })

  it('looks keys up in the active theme, pastels first', () => {
    expect(colorOf('page')).toBe('#494949')
    expect(colorOf('teal')).toBe('#14E1BA')
    expect(colorOf('nope')).toBeUndefined()
    expect(colorOf(undefined)).toBeUndefined()
    activeThemeId.value = 'light'
    expect(colorOf('page')).toBe('#EDEBE7')
    expect(pastel(undefined, 'fan').color).toBe('#0F9E84')
  })

  it('takes an explicit theme over the active one', () => {
    expect(colorOf('page', themeById('light'))).toBe('#EDEBE7')
  })

  it('falls back to the default theme for an unknown id', () => {
    expect(themeById('gone')?.id).toBe('rpi')
  })

  it('picks a tile pastel from its key, its domain, or teal', () => {
    expect(pastel('yellow', 'fan')).toEqual({ key: 'yellow', color: '#FFC94D' })
    expect(pastel(undefined, 'light').key).toBe('yellow')
    expect(pastel('bogus', undefined).key).toBe('teal')
  })

  it('keeps the pastel list in step with the active theme', () => {
    expect(PASTELS.find((p) => p.key === 'teal')?.color).toBe('#14E1BA')
    activeThemeId.value = 'light'
    expect(PASTELS.find((p) => p.key === 'teal')?.color).toBe('#0F9E84')
  })

  it('turns the add-on alpha order into CSS and names CSS variables', () => {
    expect(cssColor('#CC000000')).toBe('#000000CC')
    expect(cssColor('#123456')).toBe('#123456')
    const vars = themeVars(themeById('rpi'))
    expect(vars['--rc-card-header']).toBe('#494949')
    expect(vars['--rc-scrim']).toBe('#000000CC')
    expect(vars['--rc-p-teal']).toBe('#14E1BA')
  })
})
