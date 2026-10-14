<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, provide, ref, watch, watchEffect } from 'vue'
import { api } from '../api'
import { DESIGN, useDesigner } from '../model/design'
import { DENSITIES, PRESETS, clampDp, presetOf, toDp, toPx } from '../model/devices'
import { firstFree, fitsAt, MAX_CELLS, MIN_CELLS, reflow } from '../model/grid'
import { history } from '../model/history'
import { DEFAULT_THEME, activeThemeId } from '../model/palette'
import { fromGrid, newRoot, parentOf } from '../model/nodes'
import { newTile } from '../model/tiles'
import type { Page, Screen, Tile } from '../model/types'
import { attempt, dirty, editing, playersOf, save, toast } from '../store'
import AttributesPanel from './AttributesPanel.vue'
import CodeView from './CodeView.vue'
import ComponentPalette from './ComponentPalette.vue'
import ComponentTree from './ComponentTree.vue'
import DesignCanvas from './DesignCanvas.vue'
import EntityLibrary from './EntityLibrary.vue'
import GridMockup from './GridMockup.vue'
import MdiIcon from './MdiIcon.vue'
import PagePanel from './PagePanel.vue'
import ScreenPanel from './ScreenPanel.vue'
import TileDrawer from './TileDrawer.vue'

const emit = defineEmits<{ close: [] }>()

const screen = computed(() => editing.value!)
const pageId = ref(screen.value.homePage)
const page = computed<Page>(() => screen.value.pages.find((p) => p.id === pageId.value) ?? screen.value.pages[0])
const selectedId = ref<string | null>(null)
const selected = computed<Tile | undefined>(() => page.value.tiles.find((t) => t.id === selectedId.value))
const panel = ref<'library' | 'page' | 'screen'>('library')

// A free-form page is edited in the designer: palette and tree, canvas or code, attributes.
const free = computed(() => page.value.root !== undefined)
const design = useDesigner(page, toast)
provide(DESIGN, design)
const node = design.selected
const view = ref<'design' | 'split' | 'code'>('design')
const sidePanel = ref<'page' | 'screen'>('page')

// The canvas draws in the screen's day theme, or the one it wears while dark.
const previewDark = ref(screen.value.settings.darkMode)
watchEffect(() => {
  const s = screen.value.settings
  activeThemeId.value = (previewDark.value ? s.darkTheme : s.theme) ?? DEFAULT_THEME
})
const sameTheme = computed(() => (screen.value.settings.theme ?? DEFAULT_THEME) === (screen.value.settings.darkTheme ?? DEFAULT_THEME))
function chooseDarkTheme(): void {
  panel.value = 'screen'
  sidePanel.value = 'screen'
}

watch(pageId, () => {
  selectedId.value = null
  design.select(null)
})

// Undo and redo: a snapshot of the screen once an edit has settled.
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
function restore(s: Screen | undefined): void {
  if (!s) return
  editing.value = s
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
  const typing = (e.target as HTMLElement | null)?.closest('input, textarea, select, [contenteditable]')
  const mod = e.metaKey || e.ctrlKey
  const key = e.key.toLowerCase()
  if (mod && (key === 'z' || key === 'y')) {
    if (typing) return
    e.preventDefault()
    if (key === 'y' || e.shiftKey) redo()
    else undo()
    return
  }
  if (typing || !free.value) return
  if (mod && key === 'd') {
    e.preventDefault()
    design.duplicateSelected()
  } else if ((e.key === 'Delete' || e.key === 'Backspace') && node.value) {
    e.preventDefault()
    design.removeSelected()
  } else if (e.key === 'Escape') design.select(null)
  else if (e.key === 'ArrowUp' || e.key === 'ArrowDown') {
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

// The device the mockup is drawn for: a tablet that shows the screen, a common size, or the screen's own size.
const CUSTOM = 'custom'
const devices = computed(() => [
  ...playersOf(screen.value.id)
    .filter((p) => p.widthDp > 0)
    .map((p) => ({ key: `p:${p.deviceId}`, label: p.name, w: p.widthDp, h: p.heightDp })),
  ...PRESETS,
  { key: CUSTOM, label: 'Custom size', w: screen.value.designSize?.widthDp ?? 1280, h: screen.value.designSize?.heightDp ?? 800 },
])
function firstDevice(): string {
  const tablet = devices.value.find((d) => d.key.startsWith('p:'))
  if (tablet) return tablet.key
  const own = screen.value.designSize
  return own ? (presetOf(own)?.key ?? CUSTOM) : PRESETS[0].key
}
const deviceKey = ref(firstDevice())
const device = computed(() => devices.value.find((d) => d.key === deviceKey.value) ?? devices.value[0])

function pickDevice(key: string): void {
  // A custom size starts from the size shown before, and is kept with the screen.
  if (key === CUSTOM && !screen.value.designSize) screen.value.designSize = { widthDp: device.value.w, heightDp: device.value.h }
  deviceKey.value = key
}
/** 0 to type the custom size in dp, else the dpi of a size typed in pixels. */
const customDpi = ref(0)
const shownSize = (dp: number) => (customDpi.value ? toPx(dp, customDpi.value) : dp)
function setCustom(dim: 'widthDp' | 'heightDp', value: number): void {
  const old = screen.value.designSize ?? { widthDp: device.value.w, heightDp: device.value.h }
  screen.value.designSize = { ...old, [dim]: clampDp(customDpi.value ? toDp(value, customDpi.value) : value, old[dim]) }
}
function rotateCustom(): void {
  const s = screen.value.designSize
  if (s) screen.value.designSize = { widthDp: s.heightDp, heightDp: s.widthDp }
}

const moved = computed(() => {
  const placed = reflow(page.value.tiles, screen.value.grid)
  const ids = new Set(
    placed
      .filter((p) => !p.tile.full && (p.col !== p.tile.col || p.row !== p.tile.row || p.w !== p.tile.w || p.h !== p.tile.h))
      .map((p) => p.tile.id),
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

/** Adds a tile for an entity or a kind at a cell, or at the first free spot. */
function add(source: { entityId?: string; kind?: string }, at?: { col: number; row: number }): void {
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
  const t = page.value.tiles.find((x) => x.id === id)
  if (t) Object.assign(t, { col, row })
}

function removeTile(id: string): void {
  page.value.tiles = page.value.tiles.filter((t) => t.id !== id)
  selectedId.value = null
}

const choosingPage = ref(false)
function addPage(kind: 'free' | 'grid'): void {
  choosingPage.value = false
  if (screen.value.pages.length >= 8) return toast('At most 8 pages')
  const id = `p${Date.now().toString(36)}`
  const p: Page = { id, title: `Page ${screen.value.pages.length + 1}`, tiles: [] }
  if (kind === 'free') p.root = newRoot()
  screen.value.pages.push(p)
  pageId.value = id
  panel.value = 'page'
  sidePanel.value = 'page'
}

/** A grid page becomes a column of rows of its cards; a free-form page goes back to an empty grid. */
function convert(): void {
  const p = page.value
  if (p.root) {
    if (!confirm(`Make ${p.title} a grid page? Its components are removed.`)) return
    delete p.root
    p.tiles = []
    design.select(null)
    return
  }
  p.root = fromGrid(p, screen.value.grid.cols, screen.value.grid.rows)
  p.tiles = []
  selectedId.value = null
}

function deletePage(): void {
  if (screen.value.pages.length === 1) return
  if (page.value.tiles.length && !confirm(`Delete ${page.value.title} and its tiles?`)) return
  const id = page.value.id
  screen.value.pages = screen.value.pages.filter((p) => p.id !== id)
  if (screen.value.homePage === id) screen.value.homePage = screen.value.pages[0].id
  pageId.value = screen.value.homePage
}

const previewOn = ref('')
async function preview(): Promise<void> {
  const id = previewOn.value || playersOf(screen.value.id).find((p) => p.online)?.deviceId
  if (!id) return toast('No tablet is online to preview on')
  const shown = await attempt(async () => {
    await api.preview(id, JSON.parse(JSON.stringify(screen.value)))
    return true
  })
  if (shown) toast('Shown on the tablet until it is saved or reloaded')
}

const online = computed(() => playersOf(screen.value.id).filter((p) => p.online))
</script>

<template>
  <div class="editor">
    <header class="bar">
      <button class="icon" title="Back to screens" @click="emit('close')"><MdiIcon name="arrow-left" /></button>
      <input v-model="screen.name" class="name" aria-label="Screen name" />
      <span class="row">
        <button class="icon" title="Undo (Ctrl+Z)" :disabled="!canUndo" @click="undo"><MdiIcon name="undo" :size="18" /></button>
        <button class="icon" title="Redo (Ctrl+Shift+Z)" :disabled="!canRedo" @click="redo"><MdiIcon name="redo" :size="18" /></button>
      </span>
      <span class="grow" />
      <div class="segments" title="Preview the screen's day theme or its dark theme">
        <button :class="{ active: !previewDark }" @click="previewDark = false"><MdiIcon name="white-balance-sunny" :size="16" /> Day</button>
        <button :class="{ active: previewDark }" @click="previewDark = true"><MdiIcon name="weather-night" :size="16" /> Dark</button>
      </div>
      <button
        v-if="sameTheme"
        class="icon muted"
        title="Day and dark use the same theme, so they look alike. Choose a dark theme in the Screen settings."
        @click="chooseDarkTheme"
      >
        <MdiIcon name="information-outline" :size="18" />
      </button>
      <label class="row muted">
        <MdiIcon name="tablet" :size="18" />
        <select :value="deviceKey" aria-label="Device" @change="pickDevice(($event.target as HTMLSelectElement).value)">
          <option v-for="d in devices" :key="d.key" :value="d.key">{{ d.label }} ({{ d.w }} × {{ d.h }})</option>
        </select>
      </label>
      <span v-if="deviceKey === CUSTOM" class="row custom-size" title="Width × height, in dp or in pixels at a density">
        <input
          type="number"
          aria-label="Width"
          step="10"
          :value="shownSize(device.w)"
          @change="setCustom('widthDp', Number(($event.target as HTMLInputElement).value))"
        />
        ×
        <input
          type="number"
          aria-label="Height"
          step="10"
          :value="shownSize(device.h)"
          @change="setCustom('heightDp', Number(($event.target as HTMLInputElement).value))"
        />
        <select v-model.number="customDpi" aria-label="Units">
          <option :value="0">dp</option>
          <option v-for="d in DENSITIES" :key="d" :value="d">px at {{ d }} dpi</option>
        </select>
        <button class="icon" title="Swap width and height" @click="rotateCustom"><MdiIcon name="phone-rotate-landscape" :size="18" /></button>
      </span>
      <span v-if="!free" class="row stepper" title="Columns × rows">
        <button class="icon" :disabled="screen.grid.cols <= MIN_CELLS" @click="resize(screen.grid.cols - 1, screen.grid.rows)">−</button>
        {{ screen.grid.cols }}
        <button class="icon" :disabled="screen.grid.cols >= MAX_CELLS" @click="resize(screen.grid.cols + 1, screen.grid.rows)">+</button>
        ×
        <button class="icon" :disabled="screen.grid.rows <= MIN_CELLS" @click="resize(screen.grid.cols, screen.grid.rows - 1)">−</button>
        {{ screen.grid.rows }}
        <button class="icon" :disabled="screen.grid.rows >= MAX_CELLS" @click="resize(screen.grid.cols, screen.grid.rows + 1)">+</button>
      </span>
      <span class="row">
        <select v-if="online.length > 1" v-model="previewOn" aria-label="Preview on">
          <option value="">First tablet</option>
          <option v-for="p in online" :key="p.deviceId" :value="p.deviceId">{{ p.name }}</option>
        </select>
        <button :disabled="online.length === 0" title="Show this version on a tablet without saving" @click="preview">Preview on device</button>
        <button class="primary" :disabled="!dirty" @click="save">Save &amp; send</button>
      </span>
    </header>

    <nav class="tabs">
      <button
        v-for="p in screen.pages"
        :key="p.id"
        class="tab"
        :class="{ active: p.id === page.id, hidden: p.hidden }"
        @click="pageId = p.id"
        @dblclick="panel = 'page'"
      >
        <MdiIcon v-if="p.id === screen.homePage" name="home" :size="14" />
        <MdiIcon v-if="p.hidden" name="eye-off-outline" :size="14" />
        <MdiIcon v-if="p.root" name="vector-square" :size="14" />
        {{ p.title }}
      </button>
      <span class="new-page">
        <button class="icon" title="Add a page" @click="choosingPage = !choosingPage"><MdiIcon name="plus" :size="18" /></button>
        <span v-if="choosingPage" class="menu card">
          <button @click="addPage('free')">
            <MdiIcon name="vector-square" :size="18" />
            <span><strong>Free-form page</strong><small class="muted">Rows, columns and components, like Compose</small></span>
          </button>
          <button @click="addPage('grid')">
            <MdiIcon name="view-grid-outline" :size="18" />
            <span><strong>Grid page</strong><small class="muted">Tiles on the screen's grid</small></span>
          </button>
        </span>
      </span>
    </nav>

    <main v-if="free" class="designer">
      <aside class="left card">
        <ComponentPalette :page="page" />
        <ComponentTree />
      </aside>

      <section class="canvas">
        <div class="toolbar">
          <div class="segments views-toggle">
            <button :class="{ active: view === 'design' }" title="Design" @click="view = 'design'"><MdiIcon name="palette-outline" :size="16" /> Design</button>
            <button :class="{ active: view === 'split' }" title="Split" @click="view = 'split'"><MdiIcon name="view-split-vertical" :size="16" /> Split</button>
            <button :class="{ active: view === 'code' }" title="Code" @click="view = 'code'"><MdiIcon name="code-json" :size="16" /> Code</button>
          </div>
          <span class="grow" />
          <span class="muted keys">Arrows move the selection · Alt+arrows reorder · Ctrl+D duplicates · Delete removes</span>
        </div>
        <div class="views" :class="view">
          <DesignCanvas v-if="view !== 'code'" :screen="screen" :page="page" :device="device" />
          <CodeView v-if="view !== 'design'" :page="page" />
        </div>
        <p class="muted note">The canvas is close to the tablet, not exact; Preview on device shows the real thing.</p>
      </section>

      <aside class="side card">
        <AttributesPanel
          v-if="node"
          :node="node"
          :screen="screen"
          :is-root="node.id === page.root?.id"
          :parent-type="page.root ? parentOf(page.root, node.id)?.parent.type : undefined"
        />
        <template v-else>
          <div class="segments">
            <button :class="{ active: sidePanel === 'page' }" @click="sidePanel = 'page'">Page</button>
            <button :class="{ active: sidePanel === 'screen' }" @click="sidePanel = 'screen'">Screen</button>
          </div>
          <PagePanel v-if="sidePanel === 'page'" :screen="screen" :page="page" @delete="deletePage" @convert="convert" />
          <ScreenPanel v-else :screen="screen" :device-width="device?.w" />
        </template>
      </aside>
    </main>

    <main v-else>
      <section class="canvas">
        <GridMockup
          :screen="screen"
          :page="page"
          :device="device"
          :selected-id="selectedId"
          :moved="moved.ids"
          @select="selectedId = $event"
          @move="moveTile"
          @add="add"
        />
        <p v-if="moved.ids.size || moved.dropped" class="warn">
          <MdiIcon name="alert-outline" :size="16" />
          <template v-if="moved.dropped">{{ moved.dropped }} tile(s) do not fit this grid and are not shown. </template>
          <template v-if="moved.ids.size">{{ moved.ids.size }} tile(s) were moved or shrunk to fit; drag them to place them.</template>
        </p>
      </section>

      <aside class="side card">
        <TileDrawer
          v-if="selected"
          :tile="selected"
          :page="page"
          :screen="screen"
          @close="selectedId = null"
          @remove="removeTile(selected.id)"
        />
        <template v-else>
          <div class="segments">
            <button :class="{ active: panel === 'library' }" @click="panel = 'library'">Add tiles</button>
            <button :class="{ active: panel === 'page' }" @click="panel = 'page'">Page</button>
            <button :class="{ active: panel === 'screen' }" @click="panel = 'screen'">Screen</button>
          </div>
          <EntityLibrary v-if="panel === 'library'" :page="page" @add="add($event)" />
          <PagePanel v-else-if="panel === 'page'" :screen="screen" :page="page" @delete="deletePage" @convert="convert" />
          <ScreenPanel v-else :screen="screen" :device-width="device?.w" />
        </template>
      </aside>
    </main>
  </div>
</template>

<style scoped>
.editor {
  display: flex;
  flex-direction: column;
  height: 100vh;
}

.bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  background: var(--card);
  border-bottom: 1px solid var(--line);
  flex-wrap: wrap;
}

.name {
  font-size: 18px;
  border-color: transparent;
  background: transparent;
  max-width: 260px;
}

.name:hover {
  border-color: var(--line);
}

.custom-size input {
  width: 76px;
}

.stepper button {
  width: 26px;
  height: 26px;
  justify-content: center;
  align-items: center;
  padding: 0;
}

.tabs {
  display: flex;
  gap: 6px;
  padding: 10px 16px 0;
  flex-wrap: wrap;
}

.tab {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.tab.active {
  background: var(--ink);
  color: var(--bg);
  border-color: var(--ink);
}

.tab.hidden:not(.active) {
  opacity: 0.7;
}

main {
  flex: 1;
  display: flex;
  gap: 16px;
  padding: 12px 16px 16px;
  min-height: 0;
}

.canvas {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.warn {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  color: #9a6b00;
}

.side {
  width: 340px;
  flex: none;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.segments {
  display: flex;
  gap: 4px;
}

.segments button {
  flex: 1;
  padding: 6px 4px;
}

.segments button.active {
  background: var(--ink);
  color: var(--bg);
  border-color: var(--ink);
}

.new-page {
  position: relative;
}

.menu {
  position: absolute;
  top: 36px;
  left: 0;
  z-index: 10;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 6px;
  width: 300px;
}

.menu button {
  display: flex;
  align-items: center;
  gap: 10px;
  border: none;
  border-radius: 10px;
  text-align: left;
  padding: 8px 10px;
}

.menu button:hover {
  background: var(--bg);
}

.menu span {
  display: flex;
  flex-direction: column;
}

.left {
  width: 280px;
  flex: none;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.views-toggle button {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex: none;
  padding: 5px 12px;
}

.keys {
  font-size: 12px;
}

.views {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 12px;
}

.views.split > * {
  flex: 1 1 0;
}

.note {
  margin: 0;
  font-size: 12px;
}

@media (max-width: 1200px) {
  .keys {
    display: none;
  }
}

@media (max-width: 900px) {
  main {
    flex-direction: column;
  }

  .side,
  .left {
    width: auto;
  }
}
</style>
