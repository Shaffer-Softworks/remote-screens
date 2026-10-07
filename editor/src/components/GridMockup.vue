<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { dragged } from '../model/drag'
import { fitsAt, reflow, spec } from '../model/grid'
import { pastel } from '../model/palette'
import { iconOf, nameOf, stateOf } from '../model/tiles'
import type { Page, Screen, Tile } from '../model/types'
import { domainOf, kindOf } from '../model/types'
import { entityById, state } from '../store'
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

// As the add-on draws them (server/.../render/Tiles.kt and ScreenRenderer.kt).
const CARD_EDGE = 2
const NO_HEADER = ['clock', 'bedside', 'energy', 'person', 'device_tracker']
const ALWAYS_LIT = ['scene', 'script', 'button', 'input_button', 'nav', 'favourite']
const ON_STATES = ['on', 'open', 'unlocked', 'playing', 'home', 'cleaning', 'heat', 'cool', 'heat_cool', 'auto']

const isHomePanel = (t: Tile) => kindOf(t) === 'home'
const isWeatherPanel = (t: Tile) => kindOf(t) === 'weather' && t.full === true
const WEATHER_DAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday']

function place(p: { tile: Tile; col: number; row: number; w: number; h: number }) {
  if ((isHomePanel(p.tile) || isWeatherPanel(p.tile)) && p.tile.full) {
    return { left: '0px', top: `${g.value.topBar}px`, width: `${props.device.w}px`, height: `${props.device.h - g.value.topBar - g.value.navBar}px` }
  }
  return rect(p.col, p.row, p.w, p.h)
}

function hasHeader(p: { tile: Tile; w: number; h: number }): boolean {
  const kind = kindOf(p.tile)
  const asMap = kind === 'map' || (['person', 'device_tracker'].includes(kind) && p.tile.options.map === 'true')
  return (asMap || !NO_HEADER.includes(kind)) && g.value.w(p.w) - 2 * CARD_EDGE >= 96 && g.value.h(p.h) - 2 * CARD_EDGE >= 96
}

function accent(t: Tile): string | null {
  const s = entityById.value.get(t.entityId ?? '')?.state
  const on = ALWAYS_LIT.includes(kindOf(t)) || (s !== undefined && ON_STATES.includes(s))
  return on ? pastel(t.color, domainOf(t) ?? kindOf(t)).color : null
}

const pageTitle = (id: string) => props.screen.pages.find((p) => p.id === id)?.title
const navIcon = (p: Page) => p.icon ?? (p.id === props.screen.homePage ? 'home' : 'view-dashboard')
const navButtonW = computed(() => Math.min(g.value.navBar - 2, props.device.w / (visiblePages.value.length + 1)))
const people = computed(() => state.entities.filter((e) => e.domain === 'person'))
const weather = computed(() => state.entities.find((e) => e.domain === 'weather'))

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
      :class="{ dropping: dragged !== null }"
      :style="{ width: `${device.w}px`, height: `${device.h}px`, transform: `scale(${scale})` }"
      @click.self="emit('select', null)"
      @dragleave.self="hover = null"
    >
      <div v-if="screen.topBar.show" class="topbar" :style="{ height: `${g.topBar}px` }">
        <div v-if="screen.topBar.clock" class="when" :style="{ width: `${Math.min(350, device.w * 0.42)}px` }">
          <strong>12:34</strong><span>24 MAY 2026</span>
        </div>
        <div class="grow middle">
          <template v-if="page.hidden">
            <span class="back"><MdiIcon name="arrow-left" :size="20" /></span>
            <span>{{ page.title }}</span>
          </template>
        </div>
        <span v-if="screen.topBar.temperatureEntity" class="stat"><MdiIcon name="thermometer" :size="26" class="blue" />14°</span>
        <span v-if="screen.topBar.people" class="stat"><MdiIcon name="home-account" :size="26" class="teal" />{{ people.filter((p) => p.state === 'home').length }}</span>
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
        :class="{
          panel: isHomePanel(p.tile),
          'weather-panel': isWeatherPanel(p.tile),
          selected: p.tile.id === selectedId,
          moved: moved.has(p.tile.id),
          dragging: dragged?.type === 'tile' && dragged.id === p.tile.id,
        }"
        :style="place(p)"
        draggable="true"
        @dragstart="start($event, p)"
        @dragend="dragged = null; hover = null"
        @click.stop="emit('select', p.tile.id)"
      >
        <template v-if="isHomePanel(p.tile)">
          <div class="home-top">
            <div class="home-weather">
              <i class="bar" />
              <span class="caption">TEMPERATURE OUTSIDE NOW</span>
              <span class="temp"><MdiIcon name="weather-partly-cloudy" :size="110" />{{ weather ? '14°' : '–' }}</span>
            </div>
            <div class="home-event">
              <i class="bar thin" />
              <span class="caption small">NEXT EVENT</span>
              <span class="weekday">Monday</span>
              <span class="time">7:30 PM</span>
              <span class="title">Dentist appointment</span>
            </div>
          </div>
          <div v-if="people.length" class="home-strip">
            <span v-for="who in people" :key="who.entityId" class="person">
              <span class="photo">{{ who.name.slice(0, 1).toUpperCase() }}</span>
              <span class="who">
                <span>{{ who.name }}</span>
                <span class="where">{{ who.state === 'home' ? 'HOME' : 'AWAY' }}</span>
              </span>
            </span>
          </div>
        </template>
        <template v-else-if="isWeatherPanel(p.tile)">
          <span class="wx-name">{{ nameOf(p.tile, entityById.get(p.tile.entityId ?? ''), pageTitle) }}</span>
          <span class="wx-now"><MdiIcon name="weather-partly-cloudy" :size="110" />14°</span>
          <span class="wx-condition">Partly cloudy</span>
          <span class="wx-details"><span>Wind 12 km/h</span><span>Humidity 71%</span><span>Pressure 1013 hPa</span></span>
          <div class="wx-days">
            <span v-for="d in WEATHER_DAYS.slice(0, Math.min(7, Math.floor((device.w - 20) / 120)))" :key="d" class="wx-day">
              <span>{{ d }}</span>
              <MdiIcon name="weather-partly-cloudy" :size="60" />
              <span class="soft">Partly cloudy</span>
              <span><strong>19°</strong><small class="soft"> / 10°</small></span>
            </span>
          </div>
        </template>
        <template v-else>
          <template v-if="hasHeader(p)">
            <div class="head" :class="{ tall: g.h(p.h) - 2 * CARD_EDGE >= 200 }">{{ nameOf(p.tile, entityById.get(p.tile.entityId ?? ''), pageTitle) }}</div>
            <i class="line" :style="{ background: accent(p.tile) ?? undefined }" />
          </template>
          <div class="body">
            <span v-if="p.tile.kind === 'clock' || p.tile.kind === 'bedside'" class="clock">12:34</span>
            <template v-else>
              <MdiIcon
                :name="iconOf(p.tile, entityById.get(p.tile.entityId ?? ''))"
                :size="g.large ? 40 : 30"
                :style="{ color: accent(p.tile) ?? undefined }"
                class="icon"
              />
              <span class="grow" />
              <strong v-if="!hasHeader(p)">{{ nameOf(p.tile, entityById.get(p.tile.entityId ?? ''), pageTitle) }}</strong>
              <span class="state" :class="{ big: hasHeader(p) }">{{ stateOf(entityById.get(p.tile.entityId ?? '')) }}</span>
            </template>
          </div>
        </template>
        <span v-if="moved.has(p.tile.id)" class="flag" title="Moved or shrunk to fit this grid"><MdiIcon name="alert" :size="18" /></span>
      </div>

      <p v-if="page.tiles.length === 0" class="empty">Drag entities here, or click one in the list.</p>

      <div class="nav" :style="{ top: `${device.h - g.navBar}px`, height: `${g.navBar}px` }">
        <div class="buttons" :class="{ between: visiblePages.length + 1 > 2 }">
          <span v-for="p in visiblePages" :key="p.id" class="button" :class="{ on: p.id === page.id }" :style="{ width: `${navButtonW}px` }">
            <MdiIcon :name="navIcon(p)" :size="40" />
            <span>{{ p.title.toUpperCase() }}</span>
          </span>
          <span class="button" :style="{ width: `${navButtonW}px` }">
            <MdiIcon name="cog" :size="40" />
            <span>SETTINGS</span>
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* The RPI theme: editor/src/model/palette.ts (RPI) has the same colours. */
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
  background: #494949;
  color: #ffffff;
  border-radius: 18px;
  box-shadow: 0 0 0 10px #1d1d1f, var(--shadow);
  margin: 10px;
  overflow: hidden;
  font-family: Roboto, system-ui, sans-serif;
}

.topbar {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  display: flex;
  align-items: center;
  gap: 15px;
  padding-right: 20px;
  background: #494949;
  font-size: 20px;
}

.topbar .when {
  align-self: stretch;
  display: flex;
  align-items: center;
  justify-content: space-evenly;
  background: #2f2e2e;
}

.topbar .middle {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-left: 20px;
  font-size: 16px;
}

.topbar .back {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: #5c5c5c;
}

.topbar .stat {
  display: flex;
  align-items: center;
  gap: 4px;
}

.blue {
  color: #0986db;
}

.teal {
  color: #03dac5;
}

.cell {
  position: absolute;
  border-radius: 10px;
  border: 2px dashed rgb(255 255 255 / 15%);
}

.landing {
  position: absolute;
  border-radius: 10px;
  background: rgb(20 225 186 / 25%);
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
  box-sizing: border-box;
  border: 2px solid #000;
  border-radius: 10px;
  background: #333333;
  display: flex;
  flex-direction: column;
  cursor: grab;
  overflow: hidden;
  font-size: 17px;
  z-index: 1;
}

.tile .head {
  flex: none;
  height: 36px;
  line-height: 36px;
  padding: 0 14px;
  background: #494949;
  font-size: 15px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tile .head.tall {
  height: 44px;
  line-height: 44px;
  font-size: 16px;
}

.tile .line {
  flex: none;
  height: 3px;
  background: #494949;
}

.tile .body {
  flex: 1;
  min-height: 0;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.tile .icon {
  color: #939393;
}

.tile.panel {
  border: none;
  border-radius: 0;
  background: #14e1ba;
  cursor: pointer;
}

.tile.selected {
  outline: 4px solid var(--accent);
  outline-offset: 3px;
}

.tile.panel.selected,
.tile.weather-panel.selected {
  outline-offset: -4px;
}

.tile.weather-panel {
  border: none;
  border-radius: 0;
  background: #494949;
  align-items: center;
  cursor: pointer;
}

.wx-name {
  margin-top: 16px;
  color: #ffbb33;
  font-size: 36px;
}

.wx-now {
  margin-top: auto;
  display: flex;
  align-items: center;
  gap: 24px;
  font-size: 110px;
  font-weight: 100;
  line-height: 1.2;
}

.wx-condition {
  font-size: 26px;
}

.wx-details {
  display: flex;
  gap: 50px;
  margin: 40px 0 auto;
  font-size: 20px;
}

.wx-days {
  align-self: stretch;
  display: flex;
  justify-content: space-evenly;
  padding: 10px;
  background: #3b3b3b;
}

.wx-day {
  width: 120px;
  padding: 10px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  font-size: 16px;
}

.wx-day .soft {
  color: #cacaca;
  font-size: 14px;
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
  color: #cacaca;
  font-size: 15px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.state.big {
  font-size: 16px;
}

.flag {
  position: absolute;
  top: 10px;
  right: 12px;
  color: #c98a00;
}

.home-top {
  flex: 1;
  min-height: 0;
  display: flex;
}

.home-weather,
.home-event {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.home-weather {
  width: 50%;
  padding: 30px 0 0 50px;
}

.home-event {
  width: 42%;
  margin-left: 8%;
  padding: 30px 32px 16px 48px;
}

.bar {
  width: 150px;
  height: 10px;
  margin-bottom: 20px;
  background: #1da48c;
}

.bar.thin {
  height: 8px;
  margin-bottom: 16px;
}

.caption {
  font-size: 20px;
}

.caption.small {
  font-size: 18px;
  font-weight: 500;
  margin-bottom: 28px;
}

.temp {
  display: flex;
  align-items: center;
  gap: 20px;
  font-size: 150px;
  font-weight: 100;
  line-height: 1.2;
}

.weekday {
  color: #1da48c;
  font-size: 32px;
  font-weight: 300;
}

.time {
  font-size: 72px;
  font-weight: 100;
}

.title {
  margin-top: 12px;
  font-size: 22px;
}

.home-strip {
  flex: none;
  height: 120px;
  display: flex;
  align-items: center;
  justify-content: space-evenly;
  background: #fff;
  color: #000;
}

.person {
  display: flex;
  align-items: center;
  gap: 20px;
  font-size: 16px;
}

.photo {
  display: grid;
  place-items: center;
  width: 70px;
  height: 70px;
  border-radius: 50%;
  background: #5c5c5c;
  color: #fff;
  font-size: 26px;
}

.who {
  display: flex;
  flex-direction: column;
}

.where {
  color: #03dac5;
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

.nav {
  position: absolute;
  left: 0;
  right: 0;
  display: flex;
  flex-direction: column;
  border-top: 2px solid #03dac5;
  box-sizing: border-box;
  background: #2f2e2e;
}

.nav .buttons {
  flex: 1;
  display: flex;
  justify-content: space-evenly;
}

.nav .buttons.between {
  justify-content: space-between;
}

.nav .button {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 5px;
  font-size: 10px;
  overflow: hidden;
  white-space: nowrap;
}

.nav .button.on {
  background: #14e1ba;
}
</style>
