// The screen's colours, from the add-on's themes (GET /themes, server/.../render/theme/Themes.kt): the colour of each role
// (`card`, `ink_soft`) and of each pastel a tile lights up in when it is on. Previews draw in the active theme, which
// the screen editor picks; the colours are also set as CSS variables (`--rc-card`, `--rc-p-teal`) for stylesheets.

import { computed, reactive, ref, watchEffect } from 'vue'
import type { ResolvedTheme } from './types'

export interface Pastel {
  key: string
  color: string
}

/** Pastel keys are stored in layouts: never rename one. */
export const PASTEL_KEYS = [
  'yellow',
  'orange',
  'red',
  'pink',
  'purple',
  'blue',
  'teal',
  'green',
  'grey',
] as const

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

export const DEFAULT_THEME = 'rpi'

/** Every theme the add-on knows, built-in first. */
export const themes = ref<ResolvedTheme[]>([])
/** The theme previews draw in. */
export const activeThemeId = ref(DEFAULT_THEME)

export function setThemes(list: ResolvedTheme[]): void {
  themes.value = list
}

/** Theme [id], or the default for an id nobody knows (as the add-on resolves it). */
export function themeById(id: string | undefined): ResolvedTheme | undefined {
  return (
    themes.value.find(theme => theme.id === id) ??
    themes.value.find(theme => theme.id === DEFAULT_THEME) ??
    themes.value[0]
  )
}

export const activeTheme = computed(() => themeById(activeThemeId.value))

/** The pastels of the active theme, kept in place so templates and lists of keys can use it directly. */
export const PASTELS: Pastel[] = reactive(PASTEL_KEYS.map(key => ({ key, color: '' })))

/** The colours offered for a background: the surface roles, then the pastels. */
export const BACKGROUND_SWATCHES = [
  'card',
  'card_header',
  'page',
  'header',
  'header_dark',
  'nav',
  'key',
  'track',
  'accent',
  'blue',
  'ok',
  'warn',
  'danger',
  ...PASTEL_KEYS,
]
/** The colours offered for a line or ink. */
export const INK_SWATCHES = [
  'ink',
  'ink_soft',
  'ink_dim',
  'ink_dark',
  'accent',
  'accent_soft',
  'accent_dark',
  'nav_line',
  ...PASTEL_KEYS,
]

/** `#AARRGGBB` (the add-on's order) as CSS's `#RRGGBBAA`; `#RRGGBB` as it is. */
export function cssColor(hex: string | undefined): string | undefined {
  if (!hex) return undefined
  return hex.length === 9 ? `#${hex.slice(3)}${hex.slice(1, 3)}` : hex
}

const HEX = /^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$/

/** Whether [value] is a colour of its own (`#RRGGBB` or `#AARRGGBB`) rather than a theme key. */
export function isHex(value: string | undefined): value is string {
  return !!value && HEX.test(value)
}

/** The CSS colour of a role or pastel key or of a hex colour, or undefined for none. */
export function colorOf(
  key: string | undefined,
  theme: ResolvedTheme | undefined = activeTheme.value,
): string | undefined {
  if (isHex(key)) return cssColor(key)
  if (!key || !theme) return undefined
  return cssColor(theme.pastels[key] ?? theme.roles[key])
}

/** Resolve [value] to a stored hex (`#RRGGBB` or `#AARRGGBB`), or undefined when unknown. */
function hexOf(value: string | undefined, theme: ResolvedTheme | undefined): string | undefined {
  const hex = isHex(value)
    ? value
    : (value && theme && (theme.pastels[value] ?? theme.roles[value])) || undefined
  return isHex(hex) ? hex : undefined
}

/** [value] (a key or hex colour) as `#rrggbb` for a colour input, dropping any alpha; black when it is unknown. */
export function rgbOf(
  value: string | undefined,
  theme: ResolvedTheme | undefined = activeTheme.value,
): string {
  const hex = hexOf(value, theme)
  return hex ? `#${hex.slice(-6)}`.toLowerCase() : '#000000'
}

/** Alpha byte (0–255) of [value]; opaque when it is a 6-digit hex, a theme key without AA, or unknown. */
export function alphaOf(
  value: string | undefined,
  theme: ResolvedTheme | undefined = activeTheme.value,
): number {
  const hex = hexOf(value, theme)
  return hex && hex.length === 9 ? parseInt(hex.slice(1, 3), 16) : 0xff
}

/** `#RRGGBB` when [alpha] is opaque, else `#AARRGGBB` (the add-on's order). [rgb] may be `#rrggbb` from a colour input. */
export function packHex(rgb: string, alpha: number = 0xff): string {
  const body = rgb.replace(/^#/, '').slice(-6).toUpperCase()
  const alphaByte = Math.min(255, Math.max(0, Math.round(alpha)))
  return alphaByte === 0xff
    ? `#${body}`
    : `#${alphaByte.toString(16).padStart(2, '0').toUpperCase()}${body}`
}

/** The colour a tile lights up in: its own pastel or hex colour, else its domain's. */
export function pastel(
  key: string | undefined,
  domain: string | undefined,
  theme: ResolvedTheme | undefined = activeTheme.value,
): Pastel {
  if (isHex(key)) return { key, color: cssColor(key)! }
  let k = key ?? (domain && BY_DOMAIN[domain]) ?? 'teal'
  if (!(PASTEL_KEYS as readonly string[]).includes(k)) k = 'teal'
  return { key: k, color: cssColor(theme?.pastels[k]) ?? '' }
}

/** A theme's colours as CSS variables: `--rc-card-header` for roles, `--rc-p-teal` for pastels. */
export function themeVars(theme: ResolvedTheme | undefined): Record<string, string> {
  const out: Record<string, string> = {}
  if (!theme) return out
  for (const [key, value] of Object.entries(theme.roles))
    out[`--rc-${key.replace(/_/g, '-')}`] = cssColor(value)!
  for (const [key, value] of Object.entries(theme.pastels)) out[`--rc-p-${key}`] = cssColor(value)!
  return out
}

watchEffect(
  () => {
    const theme = activeTheme.value
    for (const pastel of PASTELS) pastel.color = cssColor(theme?.pastels[pastel.key]) ?? ''
    if (typeof document === 'undefined') return
    const root = document.documentElement.style
    for (const [key, value] of Object.entries(themeVars(theme))) root.setProperty(key, value)
  },
  { flush: 'sync' },
)
