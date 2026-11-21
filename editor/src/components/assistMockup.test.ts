import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createSSRApp } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { dashboardScreens, dashboardThemes } from '../../dev/dashboardFixtures'
import { activeThemeId, setThemes } from '../model/palette'
import GridMockup from './GridMockup.vue'

vi.mock('../model/icons', () => ({ iconPath: () => '' }))

beforeEach(() => {
  setThemes(structuredClone(dashboardThemes))
  activeThemeId.value = 'rpi'
})

describe('Assist editor mockup', () => {
  it('shows the sample turn while the phase is Speaking', async () => {
    const screen = structuredClone(dashboardScreens[0])
    const page = screen.pages.find(candidate => candidate.id === 'assist')
    expect(page).toBeDefined()

    const html = await renderToString(createSSRApp(GridMockup, {
      screen,
      page,
      device: { w: 1280, h: 800 },
      selectedId: null,
      moved: new Set<string>(),
    }))

    expect(html).toContain('ASSIST')
    expect(html).toContain('Speaking')
    expect(html).toContain('Turn off the kitchen lights')
    expect(html).toContain('The kitchen lights are off')
    expect(html).toContain('Preferred')
  })
})
