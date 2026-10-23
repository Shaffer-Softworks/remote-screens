import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const screenEditor = readFileSync(new URL('../components/ScreenEditor.vue', import.meta.url), 'utf8')
const canvas = readFileSync(
  new URL('../components/layouts/EditorLayoutCanvas.vue', import.meta.url),
  'utf8',
)

describe('editor preview-first shell', () => {
  it('keeps the single canvas shell', () => {
    expect(screenEditor).toContain('EditorLayoutCanvas')
    expect(screenEditor + canvas).not.toMatch(/layout switcher|Insert-Layers|Side-activities|EditorLayoutClassic/)
  })
})
