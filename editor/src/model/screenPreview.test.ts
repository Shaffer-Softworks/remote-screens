import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createSSRApp } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { dashboardPlayers, dashboardScreens, dashboardThemes, pendingTablets } from '../../dev/dashboardFixtures'
import ScreenPreview from '../components/ScreenPreview.vue'
import ScreensView from '../components/ScreensView.vue'
import EnrollTabletPanel from '../components/EnrollTabletPanel.vue'
import { state } from '../store'
import { activeThemeId, paletteFor, setThemes, themeById } from './palette'
import { previewDevice, previewPage, previewThemeId } from './screenPreview'

vi.mock('./icons', () => ({ iconPath: () => '' }))

beforeEach(() => {
  setThemes(structuredClone(dashboardThemes))
  activeThemeId.value = 'rpi'
  state.layout = { schemaVersion: 1, screens: structuredClone(dashboardScreens), preferences: {} }
  state.players = structuredClone(dashboardPlayers)
  state.info = null
})

describe('saved screen previews', () => {
  it('uses the configured home page, falls back safely, and handles an empty screen', () => {
    const screen = structuredClone(dashboardScreens[0])
    screen.homePage = 'climate'
    expect(previewPage(screen)?.id).toBe('climate')
    screen.homePage = 'missing'
    expect(previewPage(screen)?.id).toBe('home')
    screen.pages = []
    expect(previewPage(screen)).toBeUndefined()
  })

  it('uses design size, then an assigned tablet, then the default dimensions', () => {
    const screen = structuredClone(dashboardScreens[0])
    screen.designSize = { widthDp: 800, heightDp: 1280 }
    expect(previewDevice(screen, dashboardPlayers)).toEqual({ w: 800, h: 1280 })
    delete screen.designSize
    expect(previewDevice(screen, dashboardPlayers)).toEqual({ w: 1280, h: 800 })
    expect(previewDevice(screen, [{ ...dashboardPlayers[0], widthDp: 0 }])).toEqual({ w: 1280, h: 800 })
    expect(previewDevice(screen, [{ ...dashboardPlayers[0], widthDp: 720, heightDp: 1080 }])).toEqual({ w: 720, h: 1080 })
  })

  it('selects the saved light/dark theme without changing the designer theme', () => {
    const screen = structuredClone(dashboardScreens[0])
    screen.settings.theme = 'preview-warm'
    screen.settings.darkTheme = 'rpi'
    expect(previewThemeId(screen)).toBe('preview-warm')
    screen.settings.darkMode = true
    expect(previewThemeId(screen)).toBe('rpi')
    expect(activeThemeId.value).toBe('rpi')
  })

  it('keeps simultaneous colour resolvers isolated as the active editor theme changes', () => {
    const blue = paletteFor(() => themeById('rpi'))
    const warm = paletteFor(() => themeById('preview-warm'))
    activeThemeId.value = 'preview-warm'
    expect(blue.colorOf('page')).toBe('#1B1E24')
    expect(warm.colorOf('page')).toBe('#2B2423')
    expect(blue.pastel(undefined, 'light').color).toBe('#FFE18C')
  })

  it('renders grid and free-form previews without mutating saved layouts or active theme', async () => {
    const before = JSON.stringify(dashboardScreens)
    const grid = await renderToString(createSSRApp(ScreenPreview, { screen: dashboardScreens[0] }))
    const free = await renderToString(createSSRApp(ScreenPreview, { screen: dashboardScreens[2] }))
    expect(grid).toContain('--rc-page:#1B1E24')
    expect(free).toContain('--rc-page:#2B2423')
    expect(free).toContain('Good evening')
    expect(grid).toContain('inert')
    expect(free).toContain('aria-hidden="true"')
    expect(JSON.stringify(dashboardScreens)).toBe(before)
    expect(activeThemeId.value).toBe('rpi')
  })
})

describe('tablet setup and kiosk status', () => {
  it('keeps download and enrollment controls visible when no APK is hosted', async () => {
    const html = await renderToString(createSSRApp(ScreensView))
    expect(html).toContain('Download app')
    expect(html).toContain('Enroll a tablet')
    expect(html).toContain('aria-haspopup="dialog"')
    expect(html).toContain('github.com/Shaffer-Softworks/remote-screens/releases/latest')
    expect(html).not.toContain('<details')
  })

  it('uses the hosted APK download when available', async () => {
    state.info = { version: 'test', demo: false, connected: true, language: 'en', haVersion: 'test', locationName: 'Test home', playerApp: '1.0' }
    const html = await renderToString(createSSRApp(ScreensView))
    expect(html).toContain('href="api/player.apk"')
    const enrollment = await renderToString(createSSRApp(EnrollTabletPanel))
    expect(enrollment).toContain('<dialog')
    expect(enrollment).toContain('aria-labelledby="enrollment-title"')
    expect(enrollment).not.toContain('class="availability"')
    expect(enrollment).not.toMatch(/<fieldset[^>]*disabled/)
  })

  it('explains unavailable QR enrollment inside the dialog', async () => {
    const html = await renderToString(createSSRApp(EnrollTabletPanel))
    expect(html).toContain('<dialog')
    expect(html).toContain('QR enrollment requires the player APK')
    expect(html).toMatch(/<fieldset[^>]*disabled/)
  })

  it('retains locked and pinned status and labels offline values as last reported', async () => {
    state.players = [
      { ...dashboardPlayers[0], kiosk: 'locked' },
      { ...dashboardPlayers[1], kiosk: 'pinned' },
      { ...dashboardPlayers[2], kiosk: 'locked' },
    ]
    const html = await renderToString(createSSRApp(ScreensView))
    expect(html).toContain('title="Kiosk locked"')
    expect(html).toContain('title="Kiosk pinned"')
    expect(html).toContain('Last reported: kiosk locked')
    expect(html.match(/class="kiosk-status"/g)).toHaveLength(3)
  })
})

describe('dashboard pairing', () => {
  it('omits pairing entirely when no tablet is waiting', async () => {
    const html = await renderToString(createSSRApp(ScreensView))
    expect(html).not.toContain('Tablets waiting to pair')
    expect(html).toContain('class="device-list"')
  })

  it.each([1, 2])('places all %i waiting tablets immediately before the device list', async count => {
    state.players = [...dashboardPlayers, ...pendingTablets(count)]
    const html = await renderToString(createSSRApp(ScreensView))
    expect(html.indexOf('class="pairing"')).toBeLessThan(html.indexOf('class="device-list"'))
    expect(html).toContain('482 916')
    if (count === 2) expect(html).toContain('725 340')
    expect(html.match(/class="pairing-row"/g)).toHaveLength(count)
  })
})
