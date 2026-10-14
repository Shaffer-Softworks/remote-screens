// `v-range` on an `<input type="range">`: keeps `--fill` (0 to 1) at its value, for style.css to draw the filled track.

import type { Directive } from 'vue'

function fill(el: HTMLInputElement): void {
  const min = Number(el.min || 0)
  const max = Number(el.max || 100)
  const f = max > min ? (Number(el.value) - min) / (max - min) : 0
  el.style.setProperty('--fill', String(Math.min(Math.max(f, 0), 1)))
}

const onInput = (e: Event): void => fill(e.target as HTMLInputElement)

export const vRange: Directive<HTMLInputElement> = {
  mounted(el) {
    fill(el)
    el.addEventListener('input', onInput)
  },
  updated: fill,
  unmounted(el) {
    el.removeEventListener('input', onInput)
  },
}

declare module 'vue' {
  interface ComponentCustomProperties {
    vRange: typeof vRange
  }
}
