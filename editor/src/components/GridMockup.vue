<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { dragged } from '../model/drag'
import { fitsAt, reflow, spec } from '../model/grid'
import { pastel } from '../model/palette'
import { iconOf, nameOf, stateOf } from '../model/tiles'
import type { Page, Screen, Tile } from '../model/types'
import { domainOf } from '../model/types'
import { entityById } from '../store'
import MdiIcon from './MdiIcon.vue'

const props = defineProps<{
  screen: Screen
  page: Page
  device: { w: number; h: number }
  selectedId: string | null
  moved: Set<string>
}>()
const emit = defineEmits<{
  select: [id: string | null]
  move: [id: string, col: number, row: number]
  add: [source: { entityId?: string; kind?: string }, at: { col: number; row: number }]
}>()

const box = ref<HTMLElement>()
const room = ref({ w: 800, h: 500 })
let observer: ResizeObserver | undefined
onMounted(() => {
  observer = new ResizeObserver(([e]) => (room.value = { w: e.contentRect.width, h: e.contentRect.height }))
  observer.observe(box.value!)
})
onBeforeUnmount(() => observer?.disconnect())

const g = computed(() => spec(props.screen.grid, props.device.w, props.device.h, props.screen.topBar.show))
// The frame is 10px around the device and its margin 10px more.
const scale = computed(() => Math.max(0.1, Math.min((room.value.w - 24) / props.device.w, (room.value.h - 24) / props.device.h)))
const dark = computed(() => props.screen.settings.darkMode)
const placed = computed(() => reflow(props.page.tiles, props.screen.grid))
const visiblePages = computed(() => props.screen.pages.filter((p) => !p.hidden))
const cells = computed(() => {
  const out: { col: number; row: number }[] = []
  for (let row = 0; row < g.value.rows; row++) for (let col = 0; col < g.value.cols; col++) out.push({ col, row })
  return out
})

function rect(col: number, row: number, w: number, h: number) {
  return { left: `${g.value.x(col)}px`, top: `${g.value.y(row)}px`, width: `${g.value.w(w)}px`, height: `${g.value.h(h)}px` }
}

function colours(t: Tile) {
  const state = entityById.value.get(t.entityId ?? '')?.state
  if (state === 'off' || state === 'unavailable') {
    return dark.value ? { background: '#1f2023', color: '#e6e6e6' } : { background: '#ffffff', color: '#1d1d1f' }
  }
  const p = pastel(t.color, domainOf(t))
  return { background: dark.value ? p.dark : p.light, color: dark.value ? p.inkDark : p.inkLight }
}

const pageTitle = (id: string) => props.screen.pages.find((p) => p.id === id)?.title

// Dropping: the hovered cell is where the dragged tile's grabbed cell lands.
const hover = ref<{ col: number; row: number; ok: boolean } | null>(null)

function target(col: number, row: number) {
  const d = dragged.value
  if (!d) return null
  const c = d.type === 'tile' ? col - d.grabCol : col
  const r = d.type === 'tile' ? row - d.grabRow : row
  const others = props.page.tiles.filter((t) => d.type !== 'tile' || t.id !== d.id)
  return { col: c, row: r, ok: fitsAt(others, props.screen.grid, c, r, d.w, d.h) || (d.type === 'new' && fitsAt(others, props.screen.grid, col, row, 1, 1)) }
}

function over(e: DragEvent, col: number, row: number): void {
  const t = target(col, row)
  if (!t) return
  hover.value = t
  if (t.ok) e.preventDefault()
}

function drop(col: number, row: number): void {
  const d = dragged.value
  const t = target(col, row)
  hover.value = null
  dragged.value = null
  if (!d || !t?.ok) return
  if (d.type === 'tile') emit('move', d.id, t.col, t.row)
  else emit('add', { entityId: d.entityId, kind: d.kind }, fitsAt(props.page.tiles, props.screen.grid, t.col, t.row, d.w, d.h) ? t : { col, row })
}

function start(e: DragEvent, p: { tile: Tile; col: number; row: number; w: number; h: number }): void {
  const el = e.currentTarget as HTMLElement
  const r = el.getBoundingClientRect()
  const grabCol = Math.min(p.w - 1, Math.floor(((e.clientX - r.left) / r.width) * p.w))
  const grabRow = Math.min(p.h - 1, Math.floor(((e.clientY - r.top) / r.height) * p.h))
  e.dataTransfer?.setData('text/plain', p.tile.id)
  // Chrome cancels a drag whose source changes during dragstart (here: pointer-events: none).
  setTimeout(() => {
    dragged.value = { type: 'tile', id: p.tile.id, w: p.w, h: p.h, grabCol, grabRow }
    emit('select', p.tile.id)
  })
}

const highlight = computed(() => {
  const h = hover.value
  const d = dragged.value
  if (!h || !d) return null
  return { ...rect(Math.max(0, h.col), Math.max(0, h.row), d.w, d.h), ok: h.ok }
})
</script>

<template>
  <div ref="box" class="room" @click.self="emit('select', null)">
    <div
      class="device"
      :class="{ dark, dropping: dragged !== null }"
      :style="{ width: `${device.w}px`, height: `${device.h}px`, transform: `scale(${scale})` }"
      @click.self="emit('select', null)"
      @dragleave.self="hover = null"
    >
      <div v-if="screen.topBar.show" class="topbar" :style="{ height: `${g.topBar}px` }">
        <span>{{ page.title }}</span>
        <span class="grow" />
        <span v-if="screen.topBar.clock">12:34</span>
      </div>

      <div
        v-for="c in cells"
        :key="`${c.col}:${c.row}`"
        class="cell"
        :style="rect(c.col, c.row, 1, 1)"
        @dragover="over($event, c.col, c.row)"
        @drop.prevent="drop(c.col, c.row)"
        @click="emit('select', null)"
      />

      <div v-if="highlight" class="landing" :class="{ bad: !highlight.ok }" :style="highlight" />

      <div
        v-for="p in placed"
        :key="p.tile.id"
        class="tile"
        :class="{ selected: p.tile.id === selectedId, moved: moved.has(p.tile.id), dragging: dragged?.type === 'tile' && dragged.id === p.tile.id }"
        :style="{ ...rect(p.col, p.row, p.w, p.h), ...colours(p.tile) }"
        draggable="true"
        @dragstart="start($event, p)"
        @dragend="dragged = null; hover = null"
        @click.stop="emit('select', p.tile.id)"
      >
        <span v-if="p.tile.kind === 'clock' || p.tile.kind === 'bedside'" class="clock">12:34</span>
        <template v-else>
          <MdiIcon :name="iconOf(p.tile, entityById.get(p.tile.entityId ?? ''))" :size="g.large ? 40 : 30" />
          <span class="grow" />
          <strong>{{ nameOf(p.tile, entityById.get(p.tile.entityId ?? ''), pageTitle) }}</strong>
          <span class="state">{{ stateOf(entityById.get(p.tile.entityId ?? '')) }}</span>
        </template>
        <span v-if="moved.has(p.tile.id)" class="flag" title="Moved or shrunk to fit this grid"><MdiIcon name="alert" :size="18" /></span>
      </div>

      <p v-if="page.tiles.length === 0" class="empty">Drag entities here, or click one in the list.</p>

      <div class="dots" :style="{ top: `${device.h - g.pageBar}px`, height: `${g.pageBar}px` }">
        <span v-for="p in visiblePages" :key="p.id" class="dot" :class="{ on: p.id === page.id }" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.room {
  flex: 1;
  min-height: 0;
  position: relative;
  overflow: hidden;
}

.device {
  position: absolute;
  left: 0;
  top: 0;
  transform-origin: 0 0;
  background: #f1f0eb;
  color: #1d1d1f;
  border-radius: 18px;
  box-shadow: 0 0 0 10px #1d1d1f, var(--shadow);
  margin: 10px;
  overflow: hidden;
  font-family: Roboto, system-ui, sans-serif;
}

.device.dark {
  background: #121316;
  color: #ececef;
}

.topbar {
  position: absolute;
  left: 12px;
  right: 12px;
  top: 0;
  display: flex;
  align-items: center;
  font-size: 18px;
  font-weight: 500;
}

.cell {
  position: absolute;
  border-radius: 24px;
  border: 2px dashed rgb(128 128 128 / 25%);
}

.landing {
  position: absolute;
  border-radius: 24px;
  background: rgb(61 127 214 / 25%);
  outline: 3px solid var(--accent);
  pointer-events: none;
  z-index: 2;
}

.landing.bad {
  background: rgb(196 65 58 / 15%);
  outline-color: var(--danger);
}

.tile {
  position: absolute;
  border-radius: 24px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  cursor: grab;
  overflow: hidden;
  font-size: 17px;
  z-index: 1;
}

.tile.selected {
  outline: 4px solid var(--accent);
  outline-offset: 3px;
}

.tile.dragging {
  opacity: 0.4;
}

.device.dropping .tile {
  pointer-events: none;
}

.tile.moved {
  outline: 3px dashed #c98a00;
}

.tile .clock {
  margin: auto;
  font-size: 56px;
  font-weight: 300;
}

.tile strong {
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.state {
  opacity: 0.75;
  font-size: 15px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.flag {
  position: absolute;
  top: 10px;
  right: 12px;
  color: #c98a00;
}

.empty {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  margin: 0;
  font-size: 22px;
  opacity: 0.5;
  pointer-events: none;
}

.dots {
  position: absolute;
  left: 0;
  right: 0;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 8px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.25;
}

.dot.on {
  opacity: 0.9;
}
</style>
