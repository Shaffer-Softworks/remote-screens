// The add-on's editor API. Relative, so it works under ingress.

import type {
  AreaInfo,
  Enrollment,
  EnrollmentRequest,
  EntityInfo,
  GridSize,
  Info,
  LayoutData,
  PlayerStatus,
  Preferences,
  ResolvedTheme,
  Screen,
  Theme,
} from './model/types'
import type { ScreenFile } from './model/screenFile'
import type { NotificationRequest, NotificationResult } from './model/notifications'

export class ApiError extends Error {}

function requestBody(body: unknown): { payload?: BodyInit; json: boolean } {
  if (body === undefined) return { json: false }
  if (typeof FormData !== 'undefined' && body instanceof FormData) return { payload: body, json: false }
  return { payload: JSON.stringify(body), json: true }
}

async function call<T>(method: string, path: string, body?: unknown): Promise<T> {
  const request = requestBody(body)
  const response = await fetch(`api/${path}`, {
    method,
    headers: request.json ? { 'Content-Type': 'application/json' } : undefined,
    body: request.payload,
  })
  if (!response.ok) {
    const text = await response.text()
    let message = text
    try {
      message = JSON.parse(text).error ?? text
    } catch {
      // not JSON
    }
    throw new ApiError(message || `${response.status} ${response.statusText}`)
  }
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

export const api = {
  sendNotification: (request: NotificationRequest) =>
    call<NotificationResult>('POST', 'notifications', request),
  info: () => call<Info>('GET', 'info'),
  layout: () => call<LayoutData>('GET', 'layout'),
  preferences: (prefs: Preferences) => call<Preferences>('PUT', 'preferences', prefs),
  createScreen: (name: string, size?: { widthDp: number; heightDp: number }, grid?: GridSize) =>
    call<Screen>('POST', 'screens', { name, grid, ...size }),
  importScreen: (file: ScreenFile) => call<Screen>('POST', 'screens/import', file),
  saveScreen: (screen: Screen) =>
    call<Screen>('PUT', `screens/${encodeURIComponent(screen.id)}`, screen),
  uploadPageBackground: (screenId: string, pageId: string, file: File) => {
    const body = new FormData()
    body.append('file', file)
    return call<{ backgroundImage: string }>(
      'POST',
      `screens/${encodeURIComponent(screenId)}/pages/${encodeURIComponent(pageId)}/background`,
      body,
    )
  },
  deleteScreen: (id: string) => call<void>('DELETE', `screens/${encodeURIComponent(id)}`),
  themes: () => call<ResolvedTheme[]>('GET', 'themes'),
  saveTheme: (theme: Theme) =>
    call<ResolvedTheme>('PUT', `themes/${encodeURIComponent(theme.id)}`, theme),
  deleteTheme: (id: string) => call<void>('DELETE', `themes/${encodeURIComponent(id)}`),
  players: () => call<PlayerStatus[]>('GET', 'players'),
  pair: (code: string, screenId: string, name?: string) =>
    call<void>('POST', 'pair', { code, screenId, name }),
  adopt: (deviceId: string, screenId: string, name?: string) =>
    call<void>('POST', `players/${encodeURIComponent(deviceId)}/adopt`, { screenId, name }),
  renamePlayer: (deviceId: string, name: string) =>
    call<void>('PATCH', `players/${encodeURIComponent(deviceId)}`, { name }),
  forgetPlayer: (deviceId: string) =>
    call<void>('DELETE', `players/${encodeURIComponent(deviceId)}`),
  identify: (deviceId: string) =>
    call<void>('POST', `players/${encodeURIComponent(deviceId)}/identify`),
  preview: (deviceId: string, screen: Screen) =>
    call<void>('POST', `players/${encodeURIComponent(deviceId)}/preview`, screen),
  enrollment: (request: EnrollmentRequest) => call<Enrollment>('POST', 'enrollment', request),
  entities: () => call<EntityInfo[]>('GET', 'entities'),
  areas: () => call<AreaInfo[]>('GET', 'areas'),
  searchIcons: (query: string) => call<string[]>('GET', `icons?q=${encodeURIComponent(query)}`),
  iconPaths: (names: string[]) =>
    call<Record<string, string>>('GET', `icons/paths?names=${encodeURIComponent(names.join(','))}`),
}
