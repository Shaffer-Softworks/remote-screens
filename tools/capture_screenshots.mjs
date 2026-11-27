// Captures the README / wiki editor screenshots from a demo server (see docs/DEMO.md).
// Usage: npm i --no-save playwright && npx playwright install chromium
//        python3 tools/docs_demo_layout.py append   # optional docs panels
//        node tools/capture_screenshots.mjs [http://localhost:18099]
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const base = process.argv[2] ?? 'http://localhost:18099'
const out = join(dirname(fileURLToPath(import.meta.url)), '..', 'docs', 'screenshots')
mkdirSync(out, { recursive: true })

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 2 })
const shot = async (name) => {
  await page.waitForTimeout(800)
  await page.screenshot({ path: join(out, `${name}.png`) })
  console.log(`${name}.png`)
}
const home = async () => {
  await page.goto(base)
  await page.getByRole('heading', { name: 'Living room tablet' }).waitFor()
}
const openScreen = async () => {
  await home()
  await page.getByRole('button', { name: 'Edit Living room tablet' }).click()
  await page.getByRole('button', { name: 'Save' }).waitFor()
}
const PAGE_IDS = {
  Home: 'home',
  'Living room': 'living',
  Rooms: 'rooms',
  More: 'more',
  Extras: 'extras',
  Compose: 'compose',
  Weather: 'weather',
  Thermostat: 'thermostat',
  Music: 'music',
  Lights: 'lights',
  Cameras: 'cameras',
  Blinds: 'blinds',
  Security: 'security',
  Vacuum: 'vacuum',
  Energy: 'energy_dashboard',
  Assist: 'assist',
}
const selectPage = async (title) => {
  const picker = page.locator('select[aria-label="Page"]')
  if (!(await picker.count())) return false
  const pageId = PAGE_IDS[title]
  if (!pageId) return false
  try {
    await picker.selectOption(pageId)
    return true
  } catch (error) {
    console.warn(`select ${title}: ${error}`)
    return false
  }
}
const shotPage = async (title, file) => {
  if (!(await selectPage(title))) {
    console.warn(`skip ${file}.png (no page "${title}")`)
    return
  }
  await page.waitForTimeout(600)
  await shot(file)
}

await home()
await shot('screens')

await openScreen()
await shot('editor')

await page.getByRole('button', { name: 'Screen' }).click()
await page.getByRole('heading', { name: 'Standby' }).waitFor()
const screensaver = page.getByRole('checkbox', { name: 'Screensaver' })
if (await screensaver.count()) {
  await screensaver.check()
  await page.waitForTimeout(400)
  await page.locator('.flyout').screenshot({ path: join(out, 'editor-screensaver.png') })
  console.log('editor-screensaver.png')
} else {
  console.warn('skip editor-screensaver.png (no Screensaver checkbox)')
}

if (await selectPage('Living room')) {
  await shot('editor-room')
  await page.locator('.room .tile').first().click()
  await shot('editor-tile')
}

await shotPage('Extras', 'editor-extras')
if (await page.locator('.room .tile').count()) {
  await page.locator('.room .tile').filter({ hasText: /humid|bedroom/i }).first().click().catch(async () => {
    await page.locator('.room .tile').nth(0).click()
  })
  await shot('editor-card')
}

await shotPage('Compose', 'editor-compose')
await shotPage('Thermostat', 'editor-thermostat')
await shotPage('Lights', 'editor-lights')
await shotPage('Cameras', 'editor-cameras')
await shotPage('Blinds', 'editor-blinds')
await shotPage('Security', 'editor-security')
await shotPage('Vacuum', 'editor-vacuum')
await shotPage('Energy', 'editor-energy-dashboard')
await shotPage('Assist', 'editor-assist')

await home()
await page.getByRole('button', { name: 'Themes' }).click()
await page.getByRole('button', { name: 'Duplicate to edit' }).waitFor()
await shot('themes')

await home()
const enroll = page.getByRole('button', { name: 'Enroll a tablet' })
if (await enroll.count()) {
  await enroll.click()
  await page.getByRole('button', { name: 'Show QR code' }).click()
  await page.waitForTimeout(600)
  await shot('editor-enroll')
} else {
  console.warn('skip editor-enroll.png (set REMOTE_SCREENS_APK so playerApp is advertised)')
}

await browser.close()
