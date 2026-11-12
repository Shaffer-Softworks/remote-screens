// A screen downloaded from the editor. `kind` matches server `ScreenFile.KIND`.

import type { Page, Screen, Theme } from './types'

export const SCREEN_FILE_KIND = 'remote-screens.screen'
export const SCREEN_SCHEMA_VERSION = 2

/** A page wallpaper embedded as base64 (JPEG or PNG). */
export interface ScreenBackgroundAsset {
  pageId: string
  mime: string
  data: string
}

export interface ScreenFile {
  kind: typeof SCREEN_FILE_KIND
  schemaVersion: number
  screen: Screen
  themes: Theme[]
  backgrounds?: ScreenBackgroundAsset[]
}

/** The screen plus the custom themes it names. Built-in themes are not in the list. */
export function screenFile(
  screen: Screen,
  themes: Theme[],
  schemaVersion = SCREEN_SCHEMA_VERSION,
  backgrounds: ScreenBackgroundAsset[] = [],
): ScreenFile {
  const referenced = new Set(
    [screen.settings.theme, screen.settings.darkTheme].filter(
      (themeId): themeId is string => themeId !== undefined,
    ),
  )
  const file: ScreenFile = {
    kind: SCREEN_FILE_KIND,
    schemaVersion,
    screen,
    themes: themes.filter(theme => referenced.has(theme.id)),
  }
  if (backgrounds.length > 0) file.backgrounds = backgrounds
  return file
}

/** Clears background tokens from pages so the JSON does not imply missing files. */
export function screenWithoutBackgroundTokens(screen: Screen): Screen {
  return {
    ...screen,
    pages: screen.pages.map(page => {
      if (!page.backgroundImage) return page
      const { backgroundImage: _removed, ...rest } = page
      return rest as Page
    }),
  }
}

/** Relative URL for a page wallpaper (same path the mockup and PagePanel use). */
export function pageBackgroundUrl(screenId: string, pageId: string, token: string): string {
  return `api/screens/${encodeURIComponent(screenId)}/pages/${encodeURIComponent(pageId)}/background?v=${encodeURIComponent(token)}`
}

/** Fetches each page wallpaper and returns base64 assets for export / duplicate. */
export async function packScreenBackgrounds(screen: Screen): Promise<ScreenBackgroundAsset[]> {
  const assets: ScreenBackgroundAsset[] = []
  for (const page of screen.pages) {
    const token = page.backgroundImage
    if (!token) continue
    const response = await fetch(pageBackgroundUrl(screen.id, page.id, token))
    if (!response.ok) continue
    const blob = await response.blob()
    const data = await blobToBase64(blob)
    if (!data) continue
    assets.push({
      pageId: page.id,
      mime: blob.type || 'image/jpeg',
      data,
    })
  }
  return assets
}

async function blobToBase64(blob: Blob): Promise<string | null> {
  const buffer = await blob.arrayBuffer()
  const bytes = new Uint8Array(buffer)
  if (bytes.length === 0) return null
  let binary = ''
  for (const byte of bytes) binary += String.fromCharCode(byte)
  return btoa(binary)
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
