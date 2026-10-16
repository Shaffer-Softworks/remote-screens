// Sizes to design a screen for, in dp as the tablet reports them, and a custom size within the add-on's limits.
import type { DeviceSize } from './types'

export interface DevicePreset {
  key: string
  label: string
  w: number
  h: number
}

/** The add-on keeps a custom size within these (`DeviceSize.MIN` and `MAX` in shared/Layout.kt). */
export const MIN_DP = 200
export const MAX_DP = 4000

export const PRESETS: DevicePreset[] = [
  { key: '1280x800', label: '10" tablet, landscape', w: 1280, h: 800 },
  { key: '800x1280', label: '10" tablet, portrait', w: 800, h: 1280 },
  { key: '1366x1024', label: '12" tablet, landscape', w: 1366, h: 1024 },
  { key: '1024x768', label: '9" tablet, landscape', w: 1024, h: 768 },
  { key: '960x600', label: '8" tablet, landscape', w: 960, h: 600 },
  { key: '1024x600', label: '7" tablet or wall panel, landscape', w: 1024, h: 600 },
  { key: '600x1024', label: '7" tablet or wall panel, portrait', w: 600, h: 1024 },
  { key: '375x667', label: 'Sonoff NSPanel Pro 120', w: 375, h: 667 },
  { key: '960x540', label: 'TV or monitor (1080p at 2×)', w: 960, h: 540 },
  { key: '412x915', label: 'Phone', w: 412, h: 915 },
]

/** [n] rounded and kept within the add-on's limits; a blank or bad entry becomes [fallback]. */
export function clampDp(value: number, fallback: number): number {
  if (!Number.isFinite(value) || value <= 0) return fallback
  return Math.min(Math.max(Math.round(value), MIN_DP), MAX_DP)
}

/** Android's density buckets, in dpi; a dp is a pixel at 160. */
export const DENSITIES = [120, 160, 213, 240, 320, 480, 640]

/** [px] pixels at [dpi] in dp, as Android counts them. */
export function toDp(px: number, dpi: number): number {
  return (px * 160) / dpi
}

/** [dp] in pixels at [dpi]. */
export function toPx(dp: number, dpi: number): number {
  return Math.round((dp * dpi) / 160)
}

/** The preset [size] matches, if any. */
export function presetOf(size: DeviceSize | undefined): DevicePreset | undefined {
  return size && PRESETS.find(preset => preset.w === size.widthDp && preset.h === size.heightDp)
}
