// Captures the README screenshots from the editor of a demo server (see "Development" in README.md).
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

await home()
await page.getByRole('button', { name: 'Themes' }).click()
await page.getByRole('button', { name: 'Duplicate to edit' }).waitFor()
await shot('themes')

await browser.close()
