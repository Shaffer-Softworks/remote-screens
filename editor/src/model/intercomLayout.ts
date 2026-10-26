/** Matches `IntercomMetrics`: the RPI page is 1280×650, and stays side by side only while that scale still fits. */
export const INTERCOM_BASE_W = 1280
export const INTERCOM_BASE_H = 650
export const INTERCOM_SIDE_MIN_W = 720
export const INTERCOM_SIDE_MIN_SCALE = 0.65

/** True when the keys belong in a header above the station list. */
export function intercomStacks(pageW: number, pageH: number): boolean {
  const scale = Math.min(pageW / INTERCOM_BASE_W, pageH / INTERCOM_BASE_H)
  const wide = pageW >= INTERCOM_SIDE_MIN_W && scale >= INTERCOM_SIDE_MIN_SCALE
  return !wide
}
