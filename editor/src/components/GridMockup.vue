<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { dragged } from '../model/drag'
import {
  NARROW,
  TOP_BAR,
  fitsAt,
  navBar,
  navBarHeight,
  reflow,
  spec,
  topBarHeight,
} from '../model/grid'
import { colorOf, pastel } from '../model/palette'
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
  observer = new ResizeObserver(
    ([e]) => (room.value = { w: e.contentRect.width, h: e.contentRect.height }),
  )
  observer.observe(box.value!)
})
onBeforeUnmount(() => observer?.disconnect())

const gridLayout = computed(() =>
  spec(
    props.screen.grid,
    props.device.w,
    props.device.h,
    topBarHeight(props.screen.topBar),
    navBarHeight(props.screen.navBar, props.device.w),
  ),
)
// The frame is 10px around the device and its margin 10px more.
const scale = computed(() =>
  Math.max(
    0.1,
    Math.min((room.value.w - 24) / props.device.w, (room.value.h - 24) / props.device.h),
  ),
)
const placed = computed(() => reflow(props.page.tiles, props.screen.grid))
const visiblePages = computed(() => props.screen.pages.filter(page => !page.hidden))
const cells = computed(() => {
  const out: { col: number; row: number }[] = []
  for (let row = 0; row < gridLayout.value.rows; row++)
    for (let col = 0; col < gridLayout.value.cols; col++) out.push({ col, row })
  return out
})

function rect(col: number, row: number, w: number, h: number) {
  return {
    left: `${gridLayout.value.x(col)}px`,
    top: `${gridLayout.value.y(row)}px`,
    width: `${gridLayout.value.w(w)}px`,
    height: `${gridLayout.value.h(h)}px`,
  }
}

// As the add-on draws them (server/.../render/Tiles.kt and ScreenRenderer.kt).
const CARD_EDGE = 2
const NO_HEADER = ['clock', 'bedside', 'energy', 'person', 'device_tracker']
const ALWAYS_LIT = ['scene', 'script', 'button', 'input_button', 'nav', 'favourite']
const ON_STATES = [
  'on',
  'open',
  'unlocked',
  'playing',
  'home',
  'cleaning',
  'heat',
  'cool',
  'heat_cool',
  'auto',
]

const isHomePanel = (tile: Tile) => kindOf(tile) === 'home'
const isWeatherPanel = (tile: Tile) => kindOf(tile) === 'weather' && tile.full === true
const WEATHER_DAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday']
const isThermostatPanel = (tile: Tile) => kindOf(tile) === 'climate' && tile.full === true
const THERMOSTAT_MODES = [
  { label: 'Heat/Cool', icon: 'sun-snowflake-variant', on: true },
  { label: 'Heat', icon: 'fire' },
  { label: 'Cool', icon: 'snowflake' },
  { label: 'Off', icon: 'power' },
]
const DIAL_TICKS = Array.from({ length: 100 }, (_, i) => i * 3.6)

function place(placement: { tile: Tile; col: number; row: number; w: number; h: number }) {
  if (
    isHomePanel(placement.tile) ||
    isWeatherPanel(placement.tile) ||
    isThermostatPanel(placement.tile)
  ) {
    return {
      left: '0px',
      top: `${gridLayout.value.topBar}px`,
      width: `${props.device.w}px`,
      height: `${props.device.h - gridLayout.value.topBar - gridLayout.value.navBar}px`,
    }
  }
  const edge = placement.tile.borderColor
    ? { borderColor: colorOf(placement.tile.borderColor) }
    : {}
  return { ...rect(placement.col, placement.row, placement.w, placement.h), ...edge }
}

function hasHeader(placement: { tile: Tile; w: number; h: number }): boolean {
  const kind = kindOf(placement.tile)
  const asMap =
    kind === 'map' ||
    (['person', 'device_tracker'].includes(kind) && placement.tile.options.map === 'true')
  return (
    (asMap || !NO_HEADER.includes(kind)) &&
    gridLayout.value.w(placement.w) - 2 * CARD_EDGE >= 96 &&
    gridLayout.value.h(placement.h) - 2 * CARD_EDGE >= 96
  )
}

function accent(tile: Tile): string | null {
  const entityState = entityById.value.get(tile.entityId ?? '')?.state
  const on =
    ALWAYS_LIT.includes(kindOf(tile)) ||
    (entityState !== undefined && ON_STATES.includes(entityState))
  return on ? pastel(tile.color, domainOf(tile) ?? kindOf(tile)).color : null
}

const pageTitle = (id: string) => props.screen.pages.find(page => page.id === id)?.title
const navIcon = (page: Page) =>
  page.icon ?? (page.id === props.screen.homePage ? 'home' : 'view-dashboard')
// The bars as ScreenRenderer.kt draws them: text and icons scale with each bar's height against its default.
const top = computed(() => {
  const bar = props.screen.topBar
  const k = Math.min(Math.max(gridLayout.value.topBar / TOP_BAR, 0.7), 2)
  return {
    k,
    clockW:
      bar.clockWidth !== undefined
        ? (props.device.w * bar.clockWidth) / 100
        : Math.min(350, props.device.w * 0.42),
    date: bar.date !== false && props.device.w >= NARROW,
    background: colorOf(bar.background),
    clockBackground: colorOf(bar.clockBackground),
  }
})
const nav = computed(() => {
  const bar = props.screen.navBar ?? {}
  const barH = gridLayout.value.navBar - 2
  const count = visiblePages.value.length + 1
  const layout = bar.layout ?? 'spread'
  const k = barH / (navBar(props.device.w) - 2)
  const labels = bar.labels ?? 'both'
  return {
    labels,
    buttonW: layout === 'fill' ? props.device.w / count : Math.min(barH, props.device.w / count),
    justify:
      layout === 'center'
        ? 'center'
        : layout === 'fill'
          ? 'flex-start'
          : count > 2
            ? 'space-between'
            : 'space-evenly',
    iconSize: labels === 'icons' ? Math.min(48, barH * 0.55) : Math.min(40, barH * 0.4),
    fontSize:
      labels === 'text' ? Math.min(Math.max(14 * k, 10), 24) : Math.min(Math.max(10 * k, 9), 16),
    gap: 5 * Math.min(k, 1.5),
    background: colorOf(bar.background),
    line: colorOf(bar.line),
    active: colorOf(bar.active),
  }
})
const people = computed(() => state.entities.filter(entity => entity.domain === 'person'))
const weather = computed(() => state.entities.find(entity => entity.domain === 'weather'))

// Dropping: the hovered cell is where the dragged tile's grabbed cell lands.
const hover = ref<{ col: number; row: number; ok: boolean } | null>(null)

function target(col: number, row: number) {
  const drag = dragged.value
  if (!drag || (drag.type !== 'tile' && drag.type !== 'new')) return null
  const targetCol = drag.type === 'tile' ? col - drag.grabCol : col
  const targetRow = drag.type === 'tile' ? row - drag.grabRow : row
  const others = props.page.tiles.filter(tile => drag.type !== 'tile' || tile.id !== drag.id)
  return {
    col: targetCol,
    row: targetRow,
    ok:
      fitsAt(others, props.screen.grid, targetCol, targetRow, drag.w, drag.h) ||
      (drag.type === 'new' && fitsAt(others, props.screen.grid, col, row, 1, 1)),
  }
}

function over(event: DragEvent, col: number, row: number): void {
  const landing = target(col, row)
  if (!landing) return
  hover.value = landing
  if (landing.ok) event.preventDefault()
}

function drop(col: number, row: number): void {
  const drag = dragged.value
  const landing = target(col, row)
  hover.value = null
  dragged.value = null
  if (!drag || !landing?.ok) return
  if (drag.type === 'tile') emit('move', drag.id, landing.col, landing.row)
  else if (drag.type === 'new')
    emit(
      'add',
      { entityId: drag.entityId, kind: drag.kind },
      fitsAt(props.page.tiles, props.screen.grid, landing.col, landing.row, drag.w, drag.h)
        ? landing
        : { col, row },
    )
}

function clearDrag(): void {
  dragged.value = null
  hover.value = null
}

function start(
  event: DragEvent,
  placement: { tile: Tile; col: number; row: number; w: number; h: number },
): void {
  const el = event.currentTarget as HTMLElement
  const bounds = el.getBoundingClientRect()
  const grabCol = Math.min(
    placement.w - 1,
    Math.floor(((event.clientX - bounds.left) / bounds.width) * placement.w),
  )
  const grabRow = Math.min(
    placement.h - 1,
    Math.floor(((event.clientY - bounds.top) / bounds.height) * placement.h),
  )
  event.dataTransfer?.setData('text/plain', placement.tile.id)
  // Chrome cancels a drag whose source changes during dragstart (here: pointer-events: none).
  setTimeout(() => {
    dragged.value = {
      type: 'tile',
      id: placement.tile.id,
      w: placement.w,
      h: placement.h,
      grabCol,
      grabRow,
    }
    emit('select', placement.tile.id)
  })
}

/** A free-form page draws its own components (the default slot) in the content area instead of the grid. */
const free = computed(() => props.page.root !== undefined)

const highlight = computed(() => {
  const landing = hover.value
  const drag = dragged.value
  if (!landing || !drag || (drag.type !== 'tile' && drag.type !== 'new')) return null
  return {
    ...rect(Math.max(0, landing.col), Math.max(0, landing.row), drag.w, drag.h),
    ok: landing.ok,
  }
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
      <div
        v-if="screen.topBar.show"
        class="topbar"
        :style="{
          height: `${gridLayout.topBar}px`,
          fontSize: `${20 * top.k}px`,
          gap: `${15 * top.k}px`,
          background: top.background,
        }"
      >
        <div
          v-if="screen.topBar.clock"
          class="when"
          :style="{ width: `${top.clockW}px`, background: top.clockBackground }"
        >
          <strong>12:34</strong><span v-if="top.date">24 MAY 2026</span>
        </div>
        <div class="grow middle" :style="{ fontSize: `${16 * top.k}px` }">
          <template v-if="page.hidden">
            <span
              class="back"
              :style="{
                width: `${Math.min(34 * top.k, gridLayout.topBar - 6)}px`,
                height: `${Math.min(34 * top.k, gridLayout.topBar - 6)}px`,
              }"
            >
              <MdiIcon name="arrow-left" :size="20 * top.k" />
            </span>
            <span>{{ page.title }}</span>
          </template>
        </div>
        <span v-if="screen.topBar.temperatureEntity" class="stat"
          ><MdiIcon name="thermometer" :size="26 * top.k" class="blue" />14°</span
        >
        <span v-if="screen.topBar.people" class="stat"
          ><MdiIcon name="home-account" :size="26 * top.k" class="teal" />{{
            people.filter(person => person.state === 'home').length
          }}</span
        >
        <span v-if="screen.topBar.wifi" class="stat"
          ><MdiIcon name="wifi" :size="26 * top.k" class="teal" />3</span
        >
        <span v-if="screen.topBar.battery" class="stat"
          ><MdiIcon name="battery" :size="26 * top.k" class="teal" />87%</span
        >
      </div>

      <div
        v-if="free"
        class="free"
        :style="{
          top: `${gridLayout.topBar}px`,
          width: `${device.w}px`,
          height: `${device.h - gridLayout.topBar - gridLayout.navBar}px`,
        }"
      >
        <slot />
      </div>

      <div
        v-for="cell in free ? [] : cells"
        :key="`${cell.col}:${cell.row}`"
        class="cell"
        :style="rect(cell.col, cell.row, 1, 1)"
        @dragover="over($event, cell.col, cell.row)"
        @drop.prevent="drop(cell.col, cell.row)"
        @click="emit('select', null)"
      />

      <div v-if="highlight" class="landing" :class="{ bad: !highlight.ok }" :style="highlight" />

      <div
        v-for="placement in free ? [] : placed"
        :key="placement.tile.id"
        class="tile"
        :class="{
          panel: isHomePanel(placement.tile),
          'weather-panel': isWeatherPanel(placement.tile) || isThermostatPanel(placement.tile),
          'thermostat-panel': isThermostatPanel(placement.tile),
          selected: placement.tile.id === selectedId,
          moved: moved.has(placement.tile.id),
          dragging: dragged?.type === 'tile' && dragged.id === placement.tile.id,
        }"
        :style="place(placement)"
        draggable="true"
        @dragstart="start($event, placement)"
        @dragend="clearDrag"
        @click.stop="emit('select', placement.tile.id)"
      >
        <template v-if="isHomePanel(placement.tile)">
          <div class="home-top">
            <div class="home-weather">
              <i class="bar" />
              <span class="caption">TEMPERATURE OUTSIDE NOW</span>
              <span class="temp"
                ><MdiIcon name="weather-partly-cloudy" :size="110" />{{
                  weather ? '14°' : '–'
                }}</span
              >
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
        <template v-else-if="isThermostatPanel(placement.tile)">
          <div class="th-top">
            <div class="th-head"><i class="bar th-bar" /><span>ADJUST THE TEMPERATURE</span></div>
          </div>
          <div class="th-middle">
            <span class="th-reading"><span>CURRENT TEMP</span><strong>21°</strong></span>
            <span class="th-keys">
              <span class="th-key"><MdiIcon name="plus" :size="28" /></span>
              <span class="th-key"><MdiIcon name="minus" :size="28" /></span>
            </span>
            <svg class="th-dial" viewBox="-200 -200 400 400" width="380" height="380">
              <defs>
                <radialGradient id="th-glow" cx="50%" cy="50%" r="50%">
                  <stop offset="0%" stop-color="var(--rc-blue)" stop-opacity="0.45" />
                  <stop offset="100%" stop-color="var(--rc-ink)" stop-opacity="0.15" />
                </radialGradient>
              </defs>
              <circle r="180" fill="url(#th-glow)" class="dial-ring" stroke-width="14" />
              <line
                v-for="a in DIAL_TICKS"
                :key="a"
                x1="0"
                y1="191"
                x2="0"
                y2="205"
                class="dial-tick"
                :transform="`rotate(${a})`"
              />
              <path
                d="M 0 180 A 180 180 0 0 1 25 -178"
                fill="none"
                class="dial-arc heat"
                stroke-width="14"
                stroke-linecap="round"
              />
              <path
                d="M 0 -180 A 180 180 0 0 1 -175 -45"
                fill="none"
                class="dial-arc cool"
                stroke-width="14"
                stroke-linecap="round"
              />
              <circle cx="25" cy="-178" r="14" class="dial-knob heat" stroke-width="4" />
              <circle cx="-175" cy="-45" r="14" class="dial-knob cool" stroke-width="4" />
              <text y="-40" text-anchor="middle" class="dial-ink" font-size="20">Cooling</text>
              <text x="-36" y="28" text-anchor="middle" class="dial-heat" font-size="34" font-weight="300">
                18°
              </text>
              <text x="36" y="28" text-anchor="middle" class="dial-cool" font-size="34" font-weight="300">
                24°
              </text>
            </svg>
            <span class="th-keys">
              <span class="th-key"><MdiIcon name="plus" :size="28" /></span>
              <span class="th-key"><MdiIcon name="minus" :size="28" /></span>
            </span>
            <span class="th-reading"><span>CURRENT HUMIDITY</span><strong>45%</strong></span>
          </div>
          <div class="th-controls">
            <div class="th-modes">
              <span v-for="m in THERMOSTAT_MODES" :key="m.label" :class="{ on: m.on }"
                ><MdiIcon :name="m.icon" :size="22" />{{ m.label }}</span
              >
            </div>
            <div class="th-fan">
              <MdiIcon name="fan" :size="28" />
              <span><small>Fan mode</small><strong>Auto</strong></span>
            </div>
          </div>
        </template>
        <template v-else-if="isWeatherPanel(placement.tile)">
          <span class="wx-name">{{
            nameOf(placement.tile, entityById.get(placement.tile.entityId ?? ''), pageTitle)
          }}</span>
          <span class="wx-now"><MdiIcon name="weather-partly-cloudy" :size="110" />14°</span>
          <span class="wx-condition">Partly cloudy</span>
          <span class="wx-details"
            ><span>Wind 12 km/h</span><span>Humidity 71%</span><span>Pressure 1013 hPa</span></span
          >
          <div class="wx-days">
            <span
              v-for="day in WEATHER_DAYS.slice(0, Math.min(7, Math.floor((device.w - 20) / 120)))"
              :key="day"
              class="wx-day"
            >
              <span>{{ day }}</span>
              <MdiIcon name="weather-partly-cloudy" :size="60" />
              <span class="soft">Partly cloudy</span>
              <span><strong>19°</strong><small class="soft"> / 10°</small></span>
            </span>
          </div>
        </template>
        <template v-else>
          <template v-if="hasHeader(placement)">
            <div class="head" :class="{ tall: gridLayout.h(placement.h) - 2 * CARD_EDGE >= 200 }">
              {{ nameOf(placement.tile, entityById.get(placement.tile.entityId ?? ''), pageTitle) }}
            </div>
            <i class="line" :style="{ background: accent(placement.tile) ?? undefined }" />
          </template>
          <div class="body">
            <span
              v-if="placement.tile.kind === 'clock' || placement.tile.kind === 'bedside'"
              class="clock"
              >12:34</span
            >
            <template v-else>
              <MdiIcon
                :name="iconOf(placement.tile, entityById.get(placement.tile.entityId ?? ''))"
                :size="gridLayout.large ? 40 : 30"
                :style="{ color: accent(placement.tile) ?? undefined }"
                class="icon"
              />
              <span class="grow" />
              <strong v-if="!hasHeader(placement)">{{
                nameOf(placement.tile, entityById.get(placement.tile.entityId ?? ''), pageTitle)
              }}</strong>
              <span class="state" :class="{ big: hasHeader(placement) }">{{
                stateOf(entityById.get(placement.tile.entityId ?? ''))
              }}</span>
            </template>
          </div>
        </template>
        <span
          v-if="moved.has(placement.tile.id)"
          class="flag"
          title="Moved or shrunk to fit this grid"
          ><MdiIcon name="alert" :size="18"
        /></span>
      </div>

      <p v-if="!free && page.tiles.length === 0" class="empty">
        Drag entities here, or click one in the list.
      </p>

      <div
        class="nav"
        :class="`labels-${nav.labels}`"
        :style="{
          top: `${device.h - gridLayout.navBar}px`,
          height: `${gridLayout.navBar}px`,
          background: nav.background,
          borderTopColor: nav.line,
        }"
      >
        <div class="buttons" :style="{ justifyContent: nav.justify }">
          <span
            v-for="b in [
              ...visiblePages.map(navPage => ({
                id: navPage.id,
                icon: navIcon(navPage),
                title: navPage.title,
                on: navPage.id === page.id,
              })),
              { id: '_settings', icon: 'cog', title: 'Settings', on: false },
            ]"
            :key="b.id"
            class="button"
            :class="{ on: b.on }"
            :style="{
              width: `${nav.buttonW}px`,
              fontSize: `${nav.fontSize}px`,
              gap: `${nav.gap}px`,
              background: b.on ? nav.active : undefined,
            }"
          >
            <MdiIcon v-if="nav.labels !== 'text'" :name="b.icon" :size="nav.iconSize" />
            <span v-if="nav.labels !== 'icons'">{{ b.title.toUpperCase() }}</span>
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Colours are the active theme's (--rc-*, set by model/palette.ts). */
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
  background: var(--rc-page);
  color: var(--rc-ink);
  border-radius: 18px;
  box-shadow:
    0 0 0 10px #1d1d1f,
    var(--shadow);
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
  background: var(--rc-header);
  font-size: 20px;
}

.topbar .when {
  align-self: stretch;
  display: flex;
  align-items: center;
  justify-content: space-evenly;
  background: var(--rc-header-dark);
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
  background: var(--rc-key);
}

.topbar .stat {
  display: flex;
  align-items: center;
  gap: 4px;
}

.blue {
  color: var(--rc-blue);
}

.teal {
  color: var(--rc-accent-soft);
}

.cell {
  position: absolute;
  border-radius: 10px;
  border: 2px dashed rgb(255 255 255 / 15%);
}

.free {
  position: absolute;
  left: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
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
  border: 2px solid var(--rc-card-edge);
  border-radius: 10px;
  background: var(--rc-card);
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
  background: var(--rc-card-header);
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
  background: var(--rc-accent-soft, var(--rc-accent));
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
  color: var(--rc-ink-dim);
}

.tile.panel {
  border: none;
  border-radius: 0;
  background: var(--rc-home);
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
  background: var(--rc-page);
  align-items: center;
  cursor: pointer;
}

.wx-name {
  margin-top: 16px;
  color: var(--rc-weather-title);
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
  background: var(--rc-strip);
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

.tile.thermostat-panel {
  align-items: stretch;
}

.th-head {
  display: flex;
  flex-direction: column;
  padding: 28px 0 0 20px;
  font-size: 15px;
}

.th-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.th-bar {
  width: 120px;
  margin-bottom: 10px;
}

.dial-ring {
  stroke: var(--rc-ink);
}

.th-middle {
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: center;
}

.th-reading {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
  color: var(--rc-accent-dark);
  font-size: 25px;
}

.th-reading strong {
  color: var(--rc-ink);
  font-size: 70px;
  font-weight: 300;
}

.dial-tick {
  stroke: color-mix(in srgb, var(--rc-blue) 30%, transparent);
}

.dial-arc.heat {
  stroke: var(--rc-warn);
}

.dial-arc.cool {
  stroke: var(--rc-blue);
}

.dial-knob.heat {
  fill: var(--rc-warn);
  stroke: var(--rc-ink);
}

.dial-knob.cool {
  fill: var(--rc-blue);
  stroke: var(--rc-ink);
}

.dial-ink {
  fill: var(--rc-ink);
}

.dial-heat {
  fill: var(--rc-warn);
}

.dial-cool {
  fill: var(--rc-blue);
}

.th-keys {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 0 24px;
}

.th-key {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--rc-key);
}

.th-controls {
  align-self: center;
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.th-modes {
  display: flex;
  border: 2px solid var(--rc-accent-dark);
  border-radius: 10px;
  overflow: hidden;
  background: var(--rc-page);
}

.th-modes span {
  min-width: 140px;
  height: 48px;
  padding: 0 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: var(--rc-page);
  color: var(--rc-ink);
  font-size: 16px;
  box-shadow: inset -2px 0 0 var(--rc-accent-dark);
}

.th-modes span:last-child {
  box-shadow: none;
}

.th-modes span.on {
  background: var(--rc-accent-dark);
  color: var(--rc-ink);
}

.th-fan {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 48px;
  padding: 0 14px;
  border: 2px solid var(--rc-accent-dark);
  border-radius: 10px;
  background: var(--rc-page);
}

.th-fan span {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
}

.th-fan small {
  font-size: 12px;
}

.th-fan strong {
  font-size: 16px;
  font-weight: 500;
}

.wx-day .soft {
  color: var(--rc-ink-soft);
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
  color: var(--rc-ink-soft);
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
  background: var(--rc-accent-dark);
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
  color: var(--rc-accent-dark);
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
  background: var(--rc-home-strip);
  color: var(--rc-ink-dark);
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
  background: var(--rc-key);
  color: var(--rc-ink);
  font-size: 26px;
}

.who {
  display: flex;
  flex-direction: column;
}

.where {
  color: var(--rc-accent-soft);
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
  border-top: 2px solid var(--rc-nav-line);
  box-sizing: border-box;
  background: var(--rc-nav);
}

.nav .buttons {
  flex: 1;
  display: flex;
}

.nav .button {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  white-space: nowrap;
}

.nav.labels-text .button {
  font-weight: 500;
  white-space: normal;
  text-align: center;
}

.nav .button.on {
  background: var(--rc-nav-on);
  color: var(--rc-ink);
}
</style>
