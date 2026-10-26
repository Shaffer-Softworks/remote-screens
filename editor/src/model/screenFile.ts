// A screen downloaded from the editor. `kind` matches server `ScreenFile.KIND`.

import type { Screen, Theme } from './types'

export const SCREEN_FILE_KIND = 'remote-screens.screen'
export const SCREEN_SCHEMA_VERSION = 2

export interface ScreenFile {
  kind: typeof SCREEN_FILE_KIND
  schemaVersion: number
  screen: Screen
  themes: Theme[]
}

/** The screen plus the custom themes it names. Built-in themes are not in the list. */
export function screenFile(
  screen: Screen,
  themes: Theme[],
  schemaVersion = SCREEN_SCHEMA_VERSION,
): ScreenFile {
  const referenced = new Set(
    [screen.settings.theme, screen.settings.darkTheme].filter(
      (themeId): themeId is string => themeId !== undefined,
    ),
  )
  return {
    kind: SCREEN_FILE_KIND,
    schemaVersion,
    screen,
    themes: themes.filter(theme => referenced.has(theme.id)),
  }
}

/** A download name: letters and digits, or `screen` when the name has none. */
export function screenFileName(name: string): string {
  const slug = name
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 60)
  return slug || 'screen'
}

export function isScreenFile(value: unknown): value is ScreenFile {
  if (typeof value !== 'object' || value === null) return false
  const file = value as Partial<ScreenFile>
  const screen = file.screen
  return (
    file.kind === SCREEN_FILE_KIND &&
    typeof file.schemaVersion === 'number' &&
    file.schemaVersion <= SCREEN_SCHEMA_VERSION &&
    typeof screen === 'object' &&
    screen !== null &&
    typeof screen.name === 'string' &&
    Array.isArray(screen.pages)
  )
}
