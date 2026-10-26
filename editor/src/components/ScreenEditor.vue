<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, provide, ref, watch, watchEffect } from 'vue'
import { api } from '../api'
import { DESIGN, useDesigner } from '../model/design'
import { PRESETS, clampDp, presetOf, toDp, toPx } from '../model/devices'
import { firstFree, fitsAt, MAX_CELLS, MIN_CELLS, reflow } from '../model/grid'
import { history } from '../model/history'
import { DEFAULT_THEME, activeThemeId } from '../model/palette'
import { fromGrid, newRoot } from '../model/nodes'
import { newTile } from '../model/tiles'
import type { Page, Screen, Tile, TileSource } from '../model/types'
import { attempt, dirty, editing, playersOf, save, toast } from '../store'
import EditorToolbar from './EditorToolbar.vue'
import EditorLayoutCanvas from './layouts/EditorLayoutCanvas.vue'
import IntercomDesk from './IntercomDesk.vue'

const emit = defineEmits<{ close: [] }>()

const screen = computed(() => editing.value!)
const pageId = ref(screen.value.homePage)
const page = computed<Page>(
  () => screen.value.pages.find(entry => entry.id === pageId.value) ?? screen.value.pages[0],
)
const selectedId = ref<string | null>(null)
const selected = computed<Tile | undefined>(() =>
  page.value.tiles.find(tile => tile.id === selectedId.value),
)

const free = computed(() => page.value.root !== undefined)
const design = useDesigner(page, toast)
provide(DESIGN, design)
const node = design.selected
const view = ref<'design' | 'split' | 'code'>('design')
const railTool = ref<'insert' | 'layers' | 'page' | 'screen' | null>(null)

const previewDark = ref(screen.value.settings.darkMode)
watchEffect(() => {
  const settings = screen.value.settings
  activeThemeId.value = (previewDark.value ? settings.darkTheme : settings.theme) ?? DEFAULT_THEME
})
const sameTheme = computed(
  () =>
    (screen.value.settings.theme ?? DEFAULT_THEME) ===
    (screen.value.settings.darkTheme ?? DEFAULT_THEME),
)
function chooseDarkTheme(): void {
  railTool.value = 'screen'
}

watch(pageId, () => {
  selectedId.value = null
  design.select(null)
})

const past = history<Screen>()
past.reset(screen.value)
const canUndo = ref(false)
const canRedo = ref(false)
let settle: ReturnType<typeof setTimeout> | undefined
function record(): void {
  clearTimeout(settle)
  settle = undefined
  if (editing.value) past.record(editing.value)
  canUndo.value = past.canUndo()
  canRedo.value = past.canRedo()
}
watch(
  () => JSON.stringify(editing.value),
  () => {
    clearTimeout(settle)
    settle = setTimeout(record, 400)
  },
)
function restore(snapshot: Screen | undefined): void {
  if (!snapshot) return
  editing.value = snapshot
  canUndo.value = past.canUndo()
  canRedo.value = past.canRedo()
}
function undo(): void {
  if (settle) record()
  restore(past.undo())
}
function redo(): void {
  if (settle) record()
  restore(past.redo())
}

function onKey(e: KeyboardEvent): void {
  const typing = (e.target as HTMLElement | null)?.closest(
    'input, textarea, select, [contenteditable]',
  )
  const mod = e.metaKey || e.ctrlKey
  const key = e.key.toLowerCase()
  if (mod && (key === 'z' || key === 'y')) {
    if (typing) return
    e.preventDefault()
    if (key === 'y' || e.shiftKey) redo()
    else undo()
    return
  }
  if (typing) return
  if (e.key === 'Escape') {
    design.select(null)
    selectedId.value = null
    return
  }
  if (!free.value) return
  if (mod && key === 'd') {
    e.preventDefault()
    design.duplicateSelected()
  } else if ((e.key === 'Delete' || e.key === 'Backspace') && node.value) {
    e.preventDefault()
    design.removeSelected()
  } else if (e.key === 'ArrowUp' || e.key === 'ArrowDown') {
    e.preventDefault()
    if (e.altKey) design.shift(e.key === 'ArrowUp' ? -1 : 1)
    else design.step(e.key === 'ArrowUp' ? 'prev' : 'next')
  } else if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') {
    e.preventDefault()
    design.step(e.key === 'ArrowLeft' ? 'parent' : 'child')
  }
}
onMounted(() => window.addEventListener('keydown', onKey))
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKey)
  clearTimeout(settle)
})

const CUSTOM = 'custom'
const devices = computed(() => [
  ...playersOf(screen.value.id)
    .filter(player => player.widthDp > 0)
    .map(player => ({
      key: `p:${player.deviceId}`,
      label: player.name,
      w: player.widthDp,
      h: player.heightDp,
    })),
  ...PRESETS,
  {
    key: CUSTOM,
    label: 'Custom size',
    w: screen.value.designSize?.widthDp ?? 1280,
    h: screen.value.designSize?.heightDp ?? 800,
  },
])
function firstDevice(): string {
  const tablet = devices.value.find(device => device.key.startsWith('p:'))
  if (tablet) return tablet.key
  const own = screen.value.designSize
  return own ? (presetOf(own)?.key ?? CUSTOM) : PRESETS[0].key
}
const deviceKey = ref(firstDevice())
const device = computed(
  () => devices.value.find(entry => entry.key === deviceKey.value) ?? devices.value[0],
)

function pickDevice(key: string): void {
  if (key === CUSTOM && !screen.value.designSize)
    screen.value.designSize = { widthDp: device.value.w, heightDp: device.value.h }
  deviceKey.value = key
}
const customDpi = ref(0)
const shownSize = (dp: number) => (customDpi.value ? toPx(dp, customDpi.value) : dp)
function setCustom(dim: 'widthDp' | 'heightDp', value: number): void {
  const old = screen.value.designSize ?? { widthDp: device.value.w, heightDp: device.value.h }
  screen.value.designSize = {
    ...old,
    [dim]: clampDp(customDpi.value ? toDp(value, customDpi.value) : value, old[dim]),
  }
}
function rotateCustom(): void {
  const designSize = screen.value.designSize
  if (designSize)
    screen.value.designSize = { widthDp: designSize.heightDp, heightDp: designSize.widthDp }
}

const moved = computed(() => {
  const placed = reflow(page.value.tiles, screen.value.grid)
  const ids = new Set(
    placed
      .filter(
        placement =>
          !placement.tile.full &&
          (placement.col !== placement.tile.col ||
            placement.row !== placement.tile.row ||
            placement.w !== placement.tile.w ||
            placement.h !== placement.tile.h),
      )
      .map(placement => placement.tile.id),
  )
  const dropped = page.value.tiles.length - placed.length
  return { ids, dropped }
})

function resize(cols: number, rows: number): void {
  screen.value.grid = {
    cols: Math.min(Math.max(cols, MIN_CELLS), MAX_CELLS),
    rows: Math.min(Math.max(rows, MIN_CELLS), MAX_CELLS),
  }
}

function add(
  source: TileSource,
  at?: { col: number; row: number },
): void {
  const tile = newTile(source, 0, 0)
  const tiles = page.value.tiles
  const grid = screen.value.grid
  if (tile.full) {
    if (tiles.length) return toast('A full-page tile needs a page of its own')
  } else {
    const place = (w: number, h: number) =>
      at && fitsAt(tiles, grid, at.col, at.row, w, h) ? at : firstFree(tiles, grid, w, h)
    let spot = place(tile.w, tile.h)
    if (!spot) {
      spot = place(1, 1)
      tile.w = 1
      tile.h = 1
    }
    if (!spot) return toast('This page is full')
    tile.col = spot.col
    tile.row = spot.row
  }
  tiles.push(tile)
  selectedId.value = tile.id
}

function moveTile(id: string, col: number, row: number): void {
  const tile = page.value.tiles.find(entry => entry.id === id)
  if (tile) Object.assign(tile, { col, row })
}

function removeTile(id: string): void {
  page.value.tiles = page.value.tiles.filter(tile => tile.id !== id)
  selectedId.value = null
}

function addPage(kind: 'free' | 'grid'): void {
  if (screen.value.pages.length >= 9) return toast('At most 9 pages')
  const id = `p${Date.now().toString(36)}`
  const newPage: Page = { id, title: `Page ${screen.value.pages.length + 1}`, tiles: [] }
  if (kind === 'free') newPage.root = newRoot()
  screen.value.pages.push(newPage)
  pageId.value = id
  railTool.value = 'page'
}

function convert(): void {
  const currentPage = page.value
  if (currentPage.root) {
    if (!confirm(`Make ${currentPage.title} a grid page? Its components are removed.`)) return
    delete currentPage.root
    currentPage.tiles = []
    design.select(null)
    return
  }
  currentPage.root = fromGrid(currentPage, screen.value.grid.cols, screen.value.grid.rows)
  currentPage.tiles = []
  selectedId.value = null
}

function deletePage(): void {
  if (screen.value.pages.length === 1) return
  if (page.value.tiles.length && !confirm(`Delete ${page.value.title} and its tiles?`)) return
  const id = page.value.id
  screen.value.pages = screen.value.pages.filter(entry => entry.id !== id)
  if (screen.value.homePage === id) screen.value.homePage = screen.value.pages[0].id
  pageId.value = screen.value.homePage
}

const previewOn = ref('')
const intercomOpen = ref(false)
async function preview(): Promise<void> {
  const id = previewOn.value || playersOf(screen.value.id).find(player => player.online)?.deviceId
  if (!id) return toast('No tablet is online to preview on')
  const shown = await attempt(async () => {
    await api.preview(id, JSON.parse(JSON.stringify(screen.value)))
    return true
  })
  if (shown) toast('Shown on the tablet until it is saved or reloaded')
}

const online = computed(() => playersOf(screen.value.id).filter(player => player.online))
</script>

<template>
  <div class="editor">
    <EditorToolbar
      :screen="screen"
      :can-undo="canUndo"
      :can-redo="canRedo"
      :preview-dark="previewDark"
      :same-theme="sameTheme"
      :free="free"
      :device-key="deviceKey"
      :devices="devices"
      :custom-dpi="customDpi"
      :device="device"
      :shown-size="shownSize"
      :online="online"
      :preview-on="previewOn"
      :dirty="dirty"
      :intercom-open="intercomOpen"
      @close="emit('close')"
      @undo="undo"
      @redo="redo"
      @update:preview-dark="previewDark = $event"
      @choose-dark-theme="chooseDarkTheme"
      @pick-device="pickDevice"
      @update:custom-dpi="customDpi = $event"
      @set-custom="setCustom"
      @rotate-custom="rotateCustom"
      @resize="resize"
      @update:preview-on="previewOn = $event"
      @preview="preview"
      @save="save"
      @toggle-intercom="intercomOpen = !intercomOpen"
    />

    <IntercomDesk v-if="intercomOpen" />
    <EditorLayoutCanvas
      v-else
      :screen="screen"
      :page="page"
      :free="free"
      :device="device"
      :view="view"
      :selected-id="selectedId"
      :selected="selected"
      :node="node"
      :rail-tool="railTool"
      :moved-ids="moved.ids"
      :moved-dropped="moved.dropped"
      @update:view="view = $event"
      @update:selected-id="selectedId = $event"
      @update:page-id="pageId = $event"
      @update:rail-tool="railTool = $event"
      @move="moveTile"
      @add="add"
      @delete-page="deletePage"
      @convert="convert"
      @remove-tile="removeTile"
      @add-page="addPage"
    />
  </div>
</template>

<style scoped>
.editor {
  display: flex;
  flex-direction: column;
  height: 100vh;
}
</style>
