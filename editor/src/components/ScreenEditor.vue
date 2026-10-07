<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { api } from '../api'
import { firstFree, fitsAt, MAX_CELLS, MIN_CELLS, reflow } from '../model/grid'
import { newTile } from '../model/tiles'
import type { Page, Tile } from '../model/types'
import { attempt, dirty, editing, playersOf, save, toast } from '../store'
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

watch(pageId, () => (selectedId.value = null))

// The device the mockup is drawn for: a tablet that shows the screen, else a common size.
const PRESETS = [
  { key: '1280x800', label: '10" landscape', w: 1280, h: 800 },
  { key: '800x1280', label: '10" portrait', w: 800, h: 1280 },
  { key: '960x600', label: '8" landscape', w: 960, h: 600 },
  { key: '412x915', label: 'Phone', w: 412, h: 915 },
]
const devices = computed(() => [
  ...playersOf(screen.value.id)
    .filter((p) => p.widthDp > 0)
    .map((p) => ({ key: `p:${p.deviceId}`, label: p.name, w: p.widthDp, h: p.heightDp })),
  ...PRESETS,
])
const deviceKey = ref(devices.value[0].key)
const device = computed(() => devices.value.find((d) => d.key === deviceKey.value) ?? devices.value[0])

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

function addPage(): void {
  if (screen.value.pages.length >= 8) return toast('At most 8 pages')
  const id = `p${Date.now().toString(36)}`
  screen.value.pages.push({ id, title: `Page ${screen.value.pages.length + 1}`, tiles: [] })
  pageId.value = id
  panel.value = 'page'
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
      <span class="grow" />
      <label class="row muted">
        <MdiIcon name="tablet" :size="18" />
        <select v-model="deviceKey" aria-label="Device">
          <option v-for="d in devices" :key="d.key" :value="d.key">{{ d.label }} ({{ d.w }} × {{ d.h }})</option>
        </select>
      </label>
      <span class="row stepper" title="Columns × rows">
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
        {{ p.title }}
      </button>
      <button class="icon" title="Add a page" @click="addPage"><MdiIcon name="plus" :size="18" /></button>
    </nav>

    <main>
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
          <PagePanel v-else-if="panel === 'page'" :screen="screen" :page="page" @delete="deletePage" />
          <ScreenPanel v-else :screen="screen" />
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

@media (max-width: 900px) {
  main {
    flex-direction: column;
  }

  .side {
    width: auto;
  }
}
</style>
