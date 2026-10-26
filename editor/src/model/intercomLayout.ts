/**
 * Matches `IntercomMetrics`. RPI's `IntercomScreen` uses a fixed 350dp column.
 * A page that cannot hold that column and a station name stacks, as `KeypadIntercomScreen` does.
 */
export const INTERCOM_BASE_W = 1280
export const INTERCOM_BASE_H = 650
const COLUMN_W = 350
const BUTTON_W = 150
const BUTTON_GAP = 10
const LIST_MIN_W = 15 + 15 + 8 * 2 + 10 * 3 + 40 + 150
const NAME_MIN_W = 72
const COLUMN_BODY_H = 28 + 10 + 10 + 24 + 28 + 10 + 50
const SCALE_MAX = 1.5

/** True when the keys belong in a header above the station list. */
export function intercomStacks(pageW: number, pageH: number): boolean {
  const raw = Math.min(pageW / INTERCOM_BASE_W, pageH / INTERCOM_BASE_H)
  const scale = Math.min(SCALE_MAX, Math.max(1, raw))
  const column = COLUMN_W * scale
  const keys = (BUTTON_W * 2 + BUTTON_GAP) * scale
  const listMin = (LIST_MIN_W + NAME_MIN_W) * scale
  const fits = pageW - column >= listMin && column >= keys && pageH >= COLUMN_BODY_H * scale
  return !fits
}
