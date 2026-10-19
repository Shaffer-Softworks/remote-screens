// Captures the README / wiki editor screenshots from a demo server (see docs/DEMO.md).
// Usage: npm i --no-save playwright && npx playwright install chromium
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
  await page.getByText('Living room tablet').waitFor()
}

await home()
await shot('screens')

await page.getByText('Living room tablet').click()
await page.getByRole('button', { name: 'Add tiles' }).waitFor()
await shot('editor')

await page.getByRole('button', { name: 'Living room', exact: true }).click()
await shot('editor-room')

await page.locator('.room .tile').first().click()
await shot('editor-tile')

await page.getByRole('button', { name: 'Extras', exact: true }).click()
await page.waitForTimeout(400)
await shot('editor-extras')

// Detail-style view: humidifier tile selected on Extras (inspector + domain tile).
await page.locator('.room .tile').filter({ hasText: /humid|bedroom/i }).first().click().catch(async () => {
  await page.locator('.room .tile').nth(8).click()
})
await shot('editor-card')

await page.getByRole('button', { name: 'Compose', exact: true }).click()
await page.waitForTimeout(500)
await shot('editor-compose')

await page.getByRole('navigation').getByRole('button', { name: 'Thermostat', exact: true }).click()
await page.waitForTimeout(500)
await shot('editor-thermostat')

await home()
await page.getByRole('button', { name: 'Themes' }).click()
await page.getByRole('button', { name: 'Duplicate to edit' }).waitFor()
await shot('themes')

await home()
// Enroll only appears when the demo server hands out an APK (REMOTE_SCREENS_APK=...).
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
