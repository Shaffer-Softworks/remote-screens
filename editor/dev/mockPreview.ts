import { readFileSync } from 'node:fs'
import type { Plugin } from 'vite'
import type { Info, LayoutData, PlayerStatus, Screen } from '../src/model/types'

// Loaded by Vite's development server only. No backend or physical device is contacted.
export function mockPreview(): Plugin {
  return {
    name: 'remote-screens-mock-preview',
    apply: 'serve',
    configureServer(server) {
      const { icons } = JSON.parse(
        readFileSync(new URL('../../server/src/main/resources/mdi.json', import.meta.url), 'utf8'),
      ) as { icons: Record<string, string> }
      const key = (name: string) => name.replace(/^mdi:/, '').replace(/^mdi/, '').replaceAll('-', '').toLowerCase()
      const screen: Screen = {
        id: 'preview-home', name: 'Home',
        grid: { cols: 4, rows: 2 }, homePage: 'home',
        designSize: { widthDp: 1280, heightDp: 800 },
        pages: [{
          id: 'home', title: 'Home',
          tiles: [
            { id: 'light', entityId: 'light.living_room', name: 'Living room', col: 0, row: 0, w: 1, h: 1,
              tap: { type: 'toggle' }, hold: { type: 'card' }, options: {} },
            { id: 'temperature', entityId: 'sensor.temperature', name: 'Temperature', col: 1, row: 0, w: 1, h: 1,
              tap: { type: 'card' }, hold: { type: 'none' }, options: {} },
          ],
        }],
        settings: {
          brightness: 80, darkMode: false, standbyMinutes: 10, standbyBrightness: 10,
          nightBrightness: 20, nightDark: true, backToHomeSeconds: 60, swipe: true, orientation: 0,
        },
        topBar: { show: true, clock: true, people: true },
        navBar: {},
      }
      const layout: LayoutData = { schemaVersion: 1, screens: [screen], preferences: {}, themes: [] }
      const players: PlayerStatus[] = [
        { deviceId: 'preview-kitchen', name: 'Kitchen tablet', screenId: screen.id,
          online: true, awake: true, widthDp: 1280, heightDp: 800, appVersion: 100,
          lastSeen: Date.now(), page: 0, battery: 86, charging: true, kiosk: 'locked' },
        { deviceId: 'preview-hall', name: 'Hallway tablet', screenId: screen.id,
          online: false, awake: false, widthDp: 800, heightDp: 1280, appVersion: 100,
          lastSeen: Date.now() - 3600000, page: 0, battery: 42 },
      ]
      const info: Info = {
        version: 'UI preview', demo: true, connected: true, language: 'en',
        haVersion: 'Preview', locationName: 'UI preview · sample data',
      }
      server.middlewares.use((request, response, next) => {
        const url = new URL(request.url ?? '/', 'http://localhost')
        if (!url.pathname.startsWith('/api/')) return next()
        response.setHeader('Content-Type', 'application/json')
        response.setHeader('Cache-Control', 'no-store')
        const send = (data: unknown) => response.end(JSON.stringify(data))
        if (request.method !== 'GET') {
          response.statusCode = 409
          return send({ error: 'UI preview only. This action needs the real backend; no changes were made.' })
        }
        switch (url.pathname) {
          case '/api/info': return send(info)
          case '/api/layout': return send(layout)
          case '/api/players': return send(players)
          case '/api/entities': return send([
            { entityId: 'light.living_room', name: 'Living room', state: 'on', domain: 'light', area: 'living' },
            { entityId: 'sensor.temperature', name: 'Temperature', state: '21.5', domain: 'sensor', unit: '°C', area: 'living' },
          ])
          case '/api/areas': return send([{ id: 'living', name: 'Living room' }])
          case '/api/themes': return send([])
          case '/api/adb': return send({
            devices: [{ serial: '192.0.2.10:41001', state: 'device', description: 'Sample tablet · preview only' }],
            associations: { 'preview-kitchen': '192.0.2.10:41001' },
          })
          case '/api/icons/paths': return send(Object.fromEntries(
            (url.searchParams.get('names') ?? '').split(',').filter(Boolean)
              .map(name => [name, icons[key(name)] ?? icons[key('help-circle-outline')]]),
          ))
          case '/api/icons': return send(Object.keys(icons)
            .filter(name => name.includes(key(url.searchParams.get('q') ?? ''))).slice(0, 60))
          default:
            response.statusCode = 404
            return send({ error: 'This endpoint is not available in UI preview.' })
        }
      })
    },
  }
}
