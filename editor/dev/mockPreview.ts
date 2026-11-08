import { readFileSync } from 'node:fs'
import type { Plugin } from 'vite'
import type { Info, LayoutData } from '../src/model/types'
import { dashboardEntities, dashboardPlayers, dashboardScreens, dashboardThemes, pendingTablets } from './dashboardFixtures'

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
      const layout: LayoutData = { schemaVersion: 1, screens: dashboardScreens, preferences: {}, themes: [] }
      // Set REMOTE_SCREENS_MOCK_PENDING=0 or 2 to inspect other pairing states.
      const pendingCount = Math.max(0, Math.min(5, Number(process.env.REMOTE_SCREENS_MOCK_PENDING ?? 1) || 0))
      const players = [...dashboardPlayers, ...pendingTablets(pendingCount)]
      const info: Info = {
        version: 'UI preview', demo: true, connected: true, language: 'en',
        haVersion: 'Preview', locationName: 'Home Assistant (preview)',
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
          case '/api/entities': return send(dashboardEntities)
          case '/api/areas': return send([{ id: 'living', name: 'Living room' }])
          case '/api/themes': return send(dashboardThemes)
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
