import { describe, expect, it } from 'vitest'
import { MAX_DP, MIN_DP, PRESETS, clampDp, presetOf, toDp, toPx } from './devices'

describe('device sizes', () => {
  it('keeps a custom size whole and within the limits', () => {
    expect(clampDp(1100.6, 1280)).toBe(1101)
    expect(clampDp(50, 1280)).toBe(MIN_DP)
    expect(clampDp(99999, 1280)).toBe(MAX_DP)
    expect(clampDp(Number(''), 800)).toBe(800)
    expect(clampDp(NaN, 800)).toBe(800)
  })

  it('turns a spec sheet in pixels into dp', () => {
    // Sonoff NSPanel Pro 120: 750 × 1334 px at 320 dpi.
    expect([clampDp(toDp(750, 320), 0), clampDp(toDp(1334, 320), 0)]).toEqual([375, 667])
    expect(toPx(375, 320)).toBe(750)
    expect(presetOf({ widthDp: 375, heightDp: 667 })?.label).toContain('NSPanel')
  })

  it('finds the preset a saved size matches, and none for a custom one', () => {
    expect(presetOf({ widthDp: 1024, heightDp: 600 })?.key).toBe('1024x600')
    expect(presetOf({ widthDp: 1100, heightDp: 700 })).toBeUndefined()
    expect(presetOf(undefined)).toBeUndefined()
    expect(new Set(PRESETS.map(preset => preset.key)).size).toBe(PRESETS.length)
  })
})
