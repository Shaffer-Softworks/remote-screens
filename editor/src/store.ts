// What the editor knows: the add-on's layouts and players, Home Assistant's entities and areas, and the screen
// being edited (a copy until it is saved).

import { computed, reactive, ref } from 'vue'
import { api } from './api'
import { setThemes } from './model/palette'
import type { AreaInfo, EntityInfo, Info, LayoutData, PlayerStatus, Screen } from './model/types'

export const state = reactive({
  info: null as Info | null,
  layout: null as LayoutData | null,
  players: [] as PlayerStatus[],
  entities: [] as EntityInfo[],
  areas: [] as AreaInfo[],
  error: '' as string,
  toast: '' as string,
})

/** The screen open in the editor, edited in place, and what it was when last saved. */
export const editing = ref<Screen | null>(null)
const saved = ref('')

export const dirty = computed(() => editing.value !== null && JSON.stringify(editing.value) !== saved.value)

export const entityById = computed(() => new Map(state.entities.map((e) => [e.entityId, e])))

let toastTimer: ReturnType<typeof setTimeout> | undefined
export function toast(message: string): void {
  state.toast = message
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => (state.toast = ''), 3500)
}

export async function attempt<T>(what: () => Promise<T>): Promise<T | undefined> {
  try {
    return await what()
  } catch (e) {
    toast(e instanceof Error ? e.message : String(e))
    return undefined
  }
}

export async function load(): Promise<void> {
  try {
    const [info, layout, players, entities, areas, themes] = await Promise.all([
      api.info(),
      api.layout(),
      api.players(),
      api.entities(),
      api.areas(),
      api.themes(),
    ])
    setThemes(themes)
    Object.assign(state, { info, layout, players, entities, areas, error: '' })
  } catch (e) {
    state.error = e instanceof Error ? e.message : String(e)
  }
}

export async function refreshLayout(): Promise<void> {
  state.layout = (await attempt(api.layout)) ?? state.layout
}

export async function refreshThemes(): Promise<void> {
  const list = await attempt(api.themes)
  if (list) setThemes(list)
}

let polling: ReturnType<typeof setInterval> | undefined
/** Players come and go (and show pairing codes), so they are asked for every few seconds. */
export function pollPlayers(): void {
  polling ??= setInterval(async () => {
    try {
      state.players = await api.players()
    } catch {
      // the next poll tries again
    }
  }, 4000)
}

export function open(screen: Screen | null): void {
  editing.value = screen ? (JSON.parse(JSON.stringify(screen)) as Screen) : null
  if (editing.value) editing.value.navBar ??= {}
  saved.value = screen ? JSON.stringify(editing.value) : ''
}

/** Saves the screen; the add-on sends it to the tablets showing it. */
export async function save(): Promise<boolean> {
  const s = editing.value
  if (!s) return false
  const stored = await attempt(() => api.saveScreen(JSON.parse(JSON.stringify(s))))
  if (!stored) return false
  open(stored)
  if (state.layout) state.layout.screens = state.layout.screens.map((x) => (x.id === stored.id ? stored : x))
  toast('Saved and sent')
  return true
}

export function playersOf(screenId: string): PlayerStatus[] {
  return state.players.filter((p) => p.screenId === screenId)
}
