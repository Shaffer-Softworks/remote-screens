// The add-on's editor API. Relative, so it works under ingress.

import type { AreaInfo, EntityInfo, GridSize, Info, LayoutData, PlayerStatus, Preferences, ResolvedTheme, Screen, Theme } from './model/types'

export class ApiError extends Error {}

async function call<T>(method: string, path: string, body?: unknown): Promise<T> {
  const r = await fetch(`api/${path}`, {
    method,
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!r.ok) {
    const text = await r.text()
    let message = text
    try {
      message = JSON.parse(text).error ?? text
    } catch {
      // not JSON
    }
    throw new ApiError(message || `${r.status} ${r.statusText}`)
  }
  if (r.status === 204) return undefined as T
  return (await r.json()) as T
}

export const api = {
  info: () => call<Info>('GET', 'info'),
  layout: () => call<LayoutData>('GET', 'layout'),
  preferences: (p: Preferences) => call<Preferences>('PUT', 'preferences', p),
  createScreen: (name: string, size?: { widthDp: number; heightDp: number }, grid?: GridSize) =>
    call<Screen>('POST', 'screens', { name, grid, ...size }),
  saveScreen: (s: Screen) => call<Screen>('PUT', `screens/${encodeURIComponent(s.id)}`, s),
  deleteScreen: (id: string) => call<void>('DELETE', `screens/${encodeURIComponent(id)}`),
  themes: () => call<ResolvedTheme[]>('GET', 'themes'),
  saveTheme: (t: Theme) => call<ResolvedTheme>('PUT', `themes/${encodeURIComponent(t.id)}`, t),
  deleteTheme: (id: string) => call<void>('DELETE', `themes/${encodeURIComponent(id)}`),
  players: () => call<PlayerStatus[]>('GET', 'players'),
  pair: (code: string, screenId: string, name?: string) => call<void>('POST', 'pair', { code, screenId, name }),
  adopt: (deviceId: string, screenId: string, name?: string) =>
    call<void>('POST', `players/${encodeURIComponent(deviceId)}/adopt`, { screenId, name }),
  renamePlayer: (deviceId: string, name: string) => call<void>('PATCH', `players/${encodeURIComponent(deviceId)}`, { name }),
  forgetPlayer: (deviceId: string) => call<void>('DELETE', `players/${encodeURIComponent(deviceId)}`),
  identify: (deviceId: string) => call<void>('POST', `players/${encodeURIComponent(deviceId)}/identify`),
  preview: (deviceId: string, s: Screen) => call<void>('POST', `players/${encodeURIComponent(deviceId)}/preview`, s),
  entities: () => call<EntityInfo[]>('GET', 'entities'),
  areas: () => call<AreaInfo[]>('GET', 'areas'),
  searchIcons: (q: string) => call<string[]>('GET', `icons?q=${encodeURIComponent(q)}`),
  iconPaths: (names: string[]) => call<Record<string, string>>('GET', `icons/paths?names=${encodeURIComponent(names.join(','))}`),
}
