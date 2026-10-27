<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ALARM_ZONES_OPTION, parseAlarmZones } from '../model/alarmZones'
import { CAMERAS_OPTION, parseCameraList } from '../model/cameraList'
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
import { intercomStacks } from '../model/intercomLayout'
import { colorOf, pastel } from '../model/palette'
import { iconOf, nameOf, stateOf } from '../model/tiles'
import type { Page, Screen, Tile, TileSource } from '../model/types'
import { domainOf, kindOf } from '../model/types'
import { entityById, state } from '../store'
import HaWeatherIcon from './HaWeatherIcon.vue'
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
  add: [source: TileSource, at: { col: number; row: number }]
}>()

const wallpaper = computed(() => {
  const token = props.page.backgroundImage
  if (!token) return undefined
  const screenId = encodeURIComponent(props.screen.id)
  const pageId = encodeURIComponent(props.page.id)
  return `url("api/screens/${screenId}/pages/${pageId}/background?v=${encodeURIComponent(token)}")`
})

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

// As the add-on draws them (server/.../render/tiles/Tiles.kt and ScreenRenderer.kt).
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
/** Keep in step with PortraitFit in WeatherPanel.kt. */
const WEATHER_WIDE_DP = 520
const WEATHER_NARROW_DP = 420
const WEATHER_FORECAST = [
  { day: 'Monday', condition: 'partlycloudy', label: 'Partly cloudy', high: '19°', low: '10°' },
  { day: 'Tuesday', condition: 'rainy', label: 'Rain', high: '16°', low: '9°' },
  { day: 'Wednesday', condition: 'cloudy', label: 'Cloudy', high: '18°', low: '11°' },
  { day: 'Thursday', condition: 'sunny', label: 'Sunny', high: '21°', low: '12°' },
  { day: 'Friday', condition: 'partlycloudy', label: 'Partly cloudy', high: '20°', low: '11°' },
  { day: 'Saturday', condition: 'rainy', label: 'Rain', high: '17°', low: '10°' },
  { day: 'Sunday', condition: 'sunny', label: 'Sunny', high: '22°', low: '13°' },
]
const weatherContent = computed(() => ({
  w: props.device.w,
  h: props.device.h - gridLayout.value.topBar - gridLayout.value.navBar,
}))
const weatherIsPortrait = computed(() => weatherContent.value.h > weatherContent.value.w)
const weatherHeroIcon = computed(() => {
  const width = weatherContent.value.w
  if (width < WEATHER_NARROW_DP) return 72
  if (width >= WEATHER_WIDE_DP) return 110
  return 96
})
const isThermostatPanel = (tile: Tile) => kindOf(tile) === 'climate' && tile.full === true
const isMusicPanel = (tile: Tile) => kindOf(tile) === 'music' && tile.full === true
/** RPI's alarm page (AlarmPanel.kt): status and zones on the left, the pin keypad on the right. */
const isAlarmPanel = (tile: Tile) => kindOf(tile) === 'alarm_control_panel' && tile.full === true
const ALARM_ACTIONS = [
  { label: 'Arm Home', icon: 'shield-home' },
  { label: 'Arm Away', icon: 'shield-lock' },
  { label: 'Night Mode', icon: 'shield-moon' },
  { label: 'Disarm', icon: 'shield-lock-open', danger: true },
]
const ALARM_KEYS = ['1', '2', '3', '4', '5', '6', '7', '8', '9', 'Clear', '0', '⌫']
/** RPI's lights page (LightsPanel.kt): every Home Assistant area on the left, its lights once one is chosen. */
const isLightsPanel = (tile: Tile) => kindOf(tile) === 'lights' && tile.full === true
const LIGHTS_SAMPLE_ROOMS = ['Bedroom', 'Garden', 'Kitchen', 'Living Room']
const lightsRooms = computed(() => {
  const names = state.areas
    .map(area => area.name)
    .sort((nameA, nameB) => nameA.localeCompare(nameB))
  return names.length ? names : LIGHTS_SAMPLE_ROOMS
})
/** RPI's cameras page (CamerasPanel.kt): the tile's cameras in a list on the left, the chosen one's video on the right. */
const isCamerasPanel = (tile: Tile) => kindOf(tile) === 'cameras' && tile.full === true
const isIntercomPanel = (tile: Tile) => kindOf(tile) === 'intercom' && tile.full === true
const intercomPage = computed(() => ({
  w: props.device.w,
  h: props.device.h - gridLayout.value.topBar - gridLayout.value.navBar,
}))
const intercomIsStacked = computed(() => intercomStacks(intercomPage.value.w, intercomPage.value.h))
const intercomIsNarrow = computed(() => intercomPage.value.w < NARROW)
function cameraNames(tile: Tile): string[] {
  return parseCameraList(tile.options[CAMERAS_OPTION]).map(
    camera => camera.label || (entityById.value.get(camera.entityId)?.name ?? camera.entityId),
  )
}
function alarmZoneLabels(tile: Tile): string[] {
  return [
    'All Zones',
    ...parseAlarmZones(tile.options[ALARM_ZONES_OPTION]).map(
      zone => zone.label || (entityById.value.get(zone.entityId)?.name ?? zone.entityId),
    ),
  ]
}
/** A calendar that owns the page: the Full page size, or a tile that already covers the grid. */
function isCalendarPanel(placement: { tile: Tile; w: number; h: number }): boolean {
  return (
    kindOf(placement.tile) === 'calendar' &&
    (placement.tile.full === true ||
      (placement.w >= gridLayout.value.cols && placement.h >= gridLayout.value.rows))
  )
}
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
    isThermostatPanel(placement.tile) ||
    isMusicPanel(placement.tile) ||
    isAlarmPanel(placement.tile) ||
    isLightsPanel(placement.tile) ||
    isCalendarPanel(placement)
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
  const barScale = Math.min(Math.max(gridLayout.value.topBar / TOP_BAR, 0.7), 2)
  return {
    barScale,
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
  const barScale = barH / (navBar(props.device.w) - 2)
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
      labels === 'text'
        ? Math.min(Math.max(14 * barScale, 10), 24)
        : Math.min(Math.max(10 * barScale, 9), 16),
    gap: 5 * Math.min(barScale, 1.5),
    background: colorOf(bar.background),
    line: colorOf(bar.line),
    active: colorOf(bar.active),
  }
})
const people = computed(() => state.entities.filter(entity => entity.domain === 'person'))
const weather = computed(() => state.entities.find(entity => entity.domain === 'weather'))
const headerWeather = computed(() => {
  const entityId = props.screen.topBar.temperatureEntity
  const entity = entityId ? entityById.value.get(entityId) : undefined
  return entity?.domain === 'weather' ? entity : undefined
})

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
      { entityId: drag.entityId, kind: drag.kind, full: drag.full },
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
      :style="{
        width: `${device.w}px`,
        height: `${device.h}px`,
        transform: `scale(${scale})`,
        backgroundImage: wallpaper,
        backgroundSize: wallpaper ? 'cover' : undefined,
        backgroundPosition: wallpaper ? 'center' : undefined,
      }"
      @click.self="emit('select', null)"
      @dragleave.self="hover = null"
    >
      <div
        v-if="screen.topBar.show"
        class="topbar"
        :style="{
          height: `${gridLayout.topBar}px`,
          fontSize: `${20 * top.barScale}px`,
          gap: `${15 * top.barScale}px`,
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
        <div class="grow middle" :style="{ fontSize: `${16 * top.barScale}px` }">
          <template v-if="page.hidden">
            <span
              class="back"
              :style="{
                width: `${Math.min(34 * top.barScale, gridLayout.topBar - 6)}px`,
                height: `${Math.min(34 * top.barScale, gridLayout.topBar - 6)}px`,
              }"
            >
              <MdiIcon name="arrow-left" :size="20 * top.barScale" />
            </span>
            <span>{{ page.title }}</span>
          </template>
        </div>
        <span v-if="screen.topBar.temperatureEntity" class="stat">
          <HaWeatherIcon
            v-if="headerWeather"
            :condition="headerWeather.state"
            :size="26 * top.barScale"
          />
          <MdiIcon v-else name="thermometer" :size="26 * top.barScale" class="blue" />14°</span
        >
        <span v-if="screen.topBar.people" class="stat"
          ><MdiIcon name="home-account" :size="26 * top.barScale" class="teal" />{{
            people.filter(person => person.state === 'home').length
          }}</span
        >
        <span v-if="screen.topBar.wifi" class="stat"
          ><MdiIcon name="wifi" :size="26 * top.barScale" class="teal" />3</span
        >
        <span v-if="screen.topBar.battery" class="stat"
          ><MdiIcon name="battery" :size="26 * top.barScale" class="teal" />87%</span
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
          'weather-portrait': isWeatherPanel(placement.tile) && weatherIsPortrait,
          'weather-wide': isWeatherPanel(placement.tile) && weatherContent.w >= WEATHER_WIDE_DP,
          'weather-narrow': isWeatherPanel(placement.tile) && weatherContent.w < WEATHER_NARROW_DP,
          'thermostat-panel': isThermostatPanel(placement.tile),
          'music-panel': isMusicPanel(placement.tile),
          'alarm-panel': isAlarmPanel(placement.tile),
          'lights-panel': isLightsPanel(placement.tile),
          'cameras-panel': isCamerasPanel(placement.tile),
          'intercom-panel': isIntercomPanel(placement.tile),
          'intercom-compact': isIntercomPanel(placement.tile) && intercomIsStacked,
          'intercom-narrow': isIntercomPanel(placement.tile) && intercomIsNarrow,
          'calendar-panel': isCalendarPanel(placement),
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
        <template v-else-if="isMusicPanel(placement.tile)">
          <div class="ma-split">
            <div class="ma-left">
              <div class="ma-now">
                <span class="ma-art" />
                <span class="ma-meta">
                  <strong>Phantom Grid</strong>
                  <span>Summer Nights</span>
                  <span class="soft">Vol. 01 · Indie Electronic</span>
                  <span class="ma-times"><span>0:00</span><span>4:54</span></span>
                  <span class="ma-progress" />
                  <span class="ma-transport">
                    <MdiIcon name="skip-previous" :size="22" />
                    <MdiIcon name="pause" :size="22" />
                    <MdiIcon name="skip-next" :size="22" />
                    <MdiIcon name="volume-minus" :size="22" />
                    <MdiIcon name="volume-plus" :size="22" />
                  </span>
                </span>
              </div>
              <div class="ma-card">
                <span class="ma-label">Browse</span>
                <span class="ma-chips"
                  ><i>Artist</i><i>Album</i><i class="on">Track</i><i>Playlist</i><i>Radio</i></span
                >
                <span class="ma-row">Track · Where I Find God</span>
                <span class="ma-row">Track · Neon Skyline</span>
                <span class="ma-more">Load more</span>
              </div>
            </div>
            <div class="ma-card">
              <span class="ma-segments"><i class="on">Search</i><i>Queue</i></span>
              <span class="ma-field">Search library</span>
              <span class="ma-label">Results</span>
              <span class="ma-row">Track · Night Drive</span>
            </div>
          </div>
        </template>
        <template v-else-if="isAlarmPanel(placement.tile)">
          <div class="al-split">
            <div class="al-card al-main">
              <div class="al-status">
                <span class="al-circle"><MdiIcon name="shield-lock-open" :size="54" /></span>
                <span class="al-words"
                  ><strong>Disarmed</strong><span>System not secure</span></span
                >
                <i class="al-accent" />
              </div>
              <i class="al-divider" />
              <span class="al-label">Select Zone</span>
              <span class="al-chips">
                <i
                  v-for="(zone, zoneIndex) in alarmZoneLabels(placement.tile)"
                  :key="zone"
                  :class="{ on: zoneIndex === 0 }"
                  >{{ zone }}</i
                >
              </span>
              <span class="al-actions">
                <i
                  v-for="action in ALARM_ACTIONS"
                  :key="action.label"
                  :class="{ danger: action.danger }"
                  ><MdiIcon :name="action.icon" :size="18" />{{ action.label }}</i
                >
              </span>
            </div>
            <div class="al-card al-keypad">
              <span class="al-label">Enter code</span>
              <span class="al-dots"><i /><i /><i /><i /></span>
              <span class="al-keys"
                ><i v-for="key in ALARM_KEYS" :key="key">{{ key }}</i></span
              >
            </div>
          </div>
        </template>
        <template v-else-if="isLightsPanel(placement.tile)">
          <div class="li-rooms">
            <i class="li-accent" />
            <span class="li-title">LIGHTING CONTROL</span>
            <span v-for="roomName in lightsRooms" :key="roomName" class="li-room">{{
              roomName
            }}</span>
          </div>
          <div class="li-prompt">
            <MdiIcon name="gesture-tap" :size="64" />
            <span>Select a room to see its lights</span>
          </div>
        </template>
        <template v-else-if="isCamerasPanel(placement.tile)">
          <div class="cm-list">
            <i class="cm-accent" />
            <span class="cm-title">CAMERAS</span>
            <span
              v-for="cameraName in cameraNames(placement.tile)"
              :key="cameraName"
              class="cm-row"
            >
              <MdiIcon name="cctv" :size="48" />
              <span class="cm-bar">{{ cameraName }}</span>
            </span>
          </div>
          <div class="cm-pane" />
        </template>
        <template v-else-if="isIntercomPanel(placement.tile)">
          <div class="ic-side">
            <i class="ic-accent" />
            <span class="ic-title">INTERCOM</span>
            <div class="ic-center">
              <span class="ic-status">Online</span>
              <span class="ic-actions">
                <span class="ic-button"
                  ><MdiIcon name="microphone-outline" :size="28" class="ic-mic" />Mute</span
                >
                <span class="ic-button"
                  ><MdiIcon name="phone-outline" :size="28" />Hang Up</span
                >
              </span>
            </div>
          </div>
          <div class="ic-stations">
            <span class="ic-station">
              <MdiIcon name="account-tie-voice-outline" :size="32" />
              <span class="ic-who"><span>Living Room</span><small>Online</small></span>
              <span class="ic-button ic-call"><MdiIcon name="phone-outline" :size="28" />Call</span>
            </span>
            <span class="ic-station">
              <MdiIcon name="account-tie-voice-outline" :size="32" />
              <span class="ic-who"><span>Kitchen</span><small>In Call</small></span>
              <span class="ic-button ic-call"><MdiIcon name="phone-outline" :size="28" />Call</span>
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
              <text
                x="-36"
                y="28"
                text-anchor="middle"
                class="dial-heat"
                font-size="34"
                font-weight="300"
              >
                18°
              </text>
              <text
                x="36"
                y="28"
                text-anchor="middle"
                class="dial-cool"
                font-size="34"
                font-weight="300"
              >
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
        <template v-else-if="isCalendarPanel(placement)">
          <span class="cal-title">Upcoming events</span>
          <i class="cal-rule" />
          <span class="cal-day">Wed, Jul 15</span>
          <span class="cal-row">
            <span class="cal-time">5:30PM</span>
            <i class="cal-rule-v" />
            <span class="cal-copy">
              <span class="cal-event">Soccer practice</span>
              <span class="cal-meta">Family · Field 2</span>
            </span>
          </span>
          <span class="cal-fab"><MdiIcon name="plus" :size="24" /></span>
        </template>
        <template v-else-if="isWeatherPanel(placement.tile) && weatherIsPortrait">
          <span class="wx-name">{{
            nameOf(placement.tile, entityById.get(placement.tile.entityId ?? ''), pageTitle)
          }}</span>
          <span class="wx-now"
            ><HaWeatherIcon condition="partlycloudy" :size="weatherHeroIcon" />14°</span
          >
          <span class="wx-condition">Partly cloudy</span>
          <span class="wx-details">
            <span class="wx-detail"><small>Wind</small><strong>12 km/h</strong></span>
            <span class="wx-detail"><small>Humidity</small><strong>71%</strong></span>
            <span class="wx-detail"><small>Pressure</small><strong>1,013 hPa</strong></span>
          </span>
          <div class="wx-days">
            <span v-for="day in WEATHER_FORECAST" :key="day.day" class="wx-day">
              <span class="wx-weekday">{{ day.day }}</span>
              <HaWeatherIcon :condition="day.condition" :size="40" />
              <span class="wx-cond soft">{{ day.label }}</span>
              <span class="wx-temps"
                ><strong>{{ day.high }}</strong
                ><small class="wx-low soft"> / {{ day.low }}</small></span
              >
            </span>
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

.tile.weather-panel.weather-portrait {
  align-items: stretch;
}

.tile.weather-panel.weather-portrait .wx-name {
  margin-top: 20px;
  font-size: 28px;
  text-align: center;
}

.tile.weather-panel.weather-portrait.weather-wide .wx-name {
  font-size: 32px;
}

.tile.weather-panel.weather-portrait .wx-now {
  margin-top: 12px;
  justify-content: center;
  gap: 16px;
  font-size: 84px;
}

.tile.weather-panel.weather-portrait.weather-wide .wx-now {
  font-size: 96px;
}

.tile.weather-panel.weather-portrait.weather-narrow .wx-now {
  font-size: 64px;
}

.tile.weather-panel.weather-portrait .wx-condition {
  font-size: 22px;
  text-align: center;
}

.tile.weather-panel.weather-portrait .wx-details {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin: 20px 16px 16px;
  font-size: 18px;
  text-align: center;
}

.tile.weather-panel.weather-portrait .wx-detail {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.tile.weather-panel.weather-portrait .wx-detail small {
  font-size: 13px;
  color: var(--rc-ink-soft);
  font-weight: 400;
}

.tile.weather-panel.weather-portrait .wx-days {
  flex: 1;
  min-height: 0;
  flex-direction: column;
  justify-content: space-evenly;
  align-items: stretch;
  padding: 8px 0;
  overflow-y: auto;
}

.tile.weather-panel.weather-portrait .wx-day {
  width: auto;
  flex-direction: row;
  justify-content: flex-start;
  gap: 10px;
  min-height: 56px;
  padding: 0 16px;
}

.tile.weather-panel.weather-portrait .wx-weekday {
  width: 112px;
  flex: none;
}

.tile.weather-panel.weather-portrait.weather-narrow .wx-weekday {
  width: 92px;
}

.tile.weather-panel.weather-portrait .wx-cond {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: left;
}

.tile.weather-panel.weather-portrait .wx-temps {
  flex: none;
  margin-left: auto;
}

.tile.weather-panel.weather-portrait .wx-temps strong {
  font-weight: 500;
}

.tile.weather-panel.weather-portrait .wx-low {
  font-size: 13px;
}

.tile.thermostat-panel {
  align-items: stretch;
}

.tile.music-panel {
  align-items: stretch;
  padding: 16px;
  background: var(--rc-page);
}

.tile.alarm-panel {
  align-items: stretch;
  padding: 24px 32px;
  background: var(--rc-page);
  border: none;
  border-radius: 0;
}

.tile.lights-panel {
  flex-direction: row;
  align-items: stretch;
  padding: 0;
  background: var(--rc-page);
  border: none;
  border-radius: 0;
}

.li-rooms {
  width: 350px;
  flex: none;
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding-top: 28px;
  overflow-x: hidden;
  overflow-y: auto;
  background: var(--rc-strip);
}

.li-accent {
  width: 100px;
  height: 10px;
  margin-left: 20px;
  background: var(--rc-accent-dark);
}

.li-title {
  margin: 7px 0 0 20px;
  font-size: 15px;
  color: var(--rc-ink);
}

.li-room {
  height: 100px;
  flex: none;
  display: flex;
  align-items: center;
  padding: 0 10px 0 20px;
  font-size: 30px;
  color: var(--rc-ink);
}

.li-prompt {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  font-size: 20px;
  color: var(--rc-ink-dim);
}

.tile.cameras-panel {
  flex-direction: row;
  align-items: stretch;
  padding: 0;
  background: var(--rc-page);
  border: none;
  border-radius: 0;
}

.tile.intercom-panel {
  flex-direction: row;
  align-items: stretch;
  padding: 0;
  background: #494949;
  border: none;
  border-radius: 0;
  color: #fff;
}

.ic-side {
  width: 350px;
  flex: none;
  display: flex;
  flex-direction: column;
  padding: 28px 0 0;
  background: #3b3b3b;
}

.ic-accent {
  width: 120px;
  height: 10px;
  margin-left: 20px;
  background: #1da48c;
}

.ic-title {
  margin: 10px 0 0 20px;
  font-size: 15px;
  color: #fff;
}

.ic-center {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.ic-status {
  color: #fff;
  font-size: 20px;
}

.ic-actions {
  display: flex;
  gap: 10px;
}

.ic-stations {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 28px 15px 15px;
}

.ic-station {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  height: 100px;
  padding: 8px 10px;
  background: #2f2e2e;
  color: #fff;
  border: 1px solid #000;
  border-radius: 8px;
  font-size: 18px;
  box-sizing: border-box;
}

.ic-station::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 1px;
  background: #1da48c;
}

.ic-who {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.ic-station small {
  color: #fff;
  font-size: 14px;
}

.ic-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 150px;
  height: 50px;
  background: #444;
  color: #fff;
  border: 1px solid #000;
  border-radius: 5px;
  font-size: 14px;
}

.ic-button.ic-call {
  height: 60px;
}

.ic-mic {
  color: #00ff00;
}

.tile.intercom-panel.intercom-compact {
  flex-direction: column;
}

.tile.intercom-panel.intercom-compact .ic-side {
  width: 100%;
  flex: none;
  padding-bottom: 8px;
}

.tile.intercom-panel.intercom-compact .ic-center {
  flex: none;
}

.tile.intercom-panel.intercom-compact .ic-stations {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding-top: 12px;
}

.tile.intercom-panel.intercom-narrow .ic-actions {
  width: 100%;
  box-sizing: border-box;
  padding: 0 12px;
}

.tile.intercom-panel.intercom-narrow .ic-button {
  width: calc(50% - 12px);
  height: 44px;
  font-size: 13px;
}

.tile.intercom-panel.intercom-narrow .ic-button.ic-call {
  width: auto;
  min-width: 72px;
  height: 48px;
}

.tile.intercom-panel.intercom-narrow .ic-station {
  height: auto;
  min-height: 72px;
}

.cm-list {
  width: 350px;
  flex: none;
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 28px 20px 0;
  overflow: hidden;
  background: var(--rc-strip);
}

.cm-accent {
  width: 100px;
  height: 10px;
  margin-bottom: -10px;
  background: var(--rc-accent-dark);
}

.cm-title {
  margin: -10px 0 -10px;
  font-size: 15px;
  color: var(--rc-ink);
}

.cm-row {
  position: relative;
  height: 180px;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--rc-ink-dim);
  background: var(--rc-track);
}

.cm-bar {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 40px;
  display: flex;
  align-items: center;
  padding-left: 5px;
  font-size: 12px;
  color: var(--rc-ink);
  background: color-mix(in srgb, var(--rc-accent-dark) 50%, transparent);
}

.cm-pane {
  flex: 1;
}

.al-split {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 28px;
}

.al-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 24px;
  border-radius: 24px;
  background: var(--rc-card);
  color: var(--rc-ink);
}

.al-main {
  flex: 1.45;
}

.al-keypad {
  flex: 0.85;
  padding: 28px;
}

.al-status {
  display: flex;
  align-items: center;
  gap: 28px;
}

.al-circle {
  width: 120px;
  height: 120px;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  border: 2px solid var(--rc-accent);
  background: #0a1518;
  color: #ffffff;
}

.al-words {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 18px;
  color: var(--rc-ink-soft);
}

.al-words strong {
  font-size: 34px;
  font-weight: 600;
  color: var(--rc-accent);
}

.al-accent {
  width: 4px;
  height: 120px;
  border-radius: 8px;
  background: var(--rc-accent);
}

.al-divider {
  height: 1px;
  margin: 18px 0 14px;
  background: var(--rc-divider);
}

.al-label {
  font-size: 18px;
  margin-bottom: 12px;
}

.al-keypad .al-label {
  font-size: 22px;
  text-align: center;
  margin-bottom: 22px;
}

.al-chips,
.al-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
}

.al-chips i,
.al-actions i,
.al-keys i {
  font-style: normal;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--rc-key);
  color: var(--rc-ink);
}

.al-chips i {
  min-width: 130px;
  height: 56px;
  padding: 0 14px;
  border-radius: 14px;
  border: 1px solid var(--rc-divider);
  font-size: 15px;
}

.al-chips i.on {
  border-color: transparent;
  background: #10272a;
  color: #ffffff;
}

.al-actions {
  margin-top: auto;
}

.al-actions i {
  flex: 1 1 40%;
  gap: 10px;
  height: 64px;
  border-radius: 16px;
  font-size: 16px;
}

.al-actions i.danger {
  background: var(--rc-danger);
  color: #ffffff;
}

.al-dots {
  display: flex;
  justify-content: center;
  gap: 18px;
  margin-bottom: 28px;
}

.al-dots i {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--rc-ink-dim);
}

.al-keys {
  flex: 1;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px 14px;
}

.al-keys i {
  border-radius: 16px;
  font-size: 22px;
}

.tile.calendar-panel {
  align-items: stretch;
  justify-content: flex-start;
  padding: 16px 24px 88px;
  background: #333333;
  color: #ffffff;
  border: none;
  border-radius: 0;
}

.cal-title {
  font-size: 28px;
  font-weight: 300;
}

.cal-rule {
  height: 2px;
  background: #03dac5;
  margin: 12px 0 16px;
}

.cal-day {
  color: #1da48c;
  font-size: 20px;
  font-weight: 600;
  margin: 8px 0 4px;
}

.cal-row {
  display: flex;
  align-items: center;
  height: 72px;
  background: #3b3b3b;
  padding: 10px 16px;
  gap: 16px;
}

.cal-time {
  width: 110px;
  font-size: 18px;
  font-weight: 300;
}

.cal-rule-v {
  width: 2px;
  align-self: stretch;
  background: #4a4a4a;
}

.cal-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.cal-event {
  font-size: 22px;
}

.cal-meta {
  color: #cacaca;
  font-size: 16px;
}

.cal-fab {
  position: absolute;
  right: 24px;
  bottom: 24px;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: #03dac5;
  color: #333333;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ma-split {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 16px;
}

.ma-left {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.ma-left .ma-card,
.ma-split > .ma-card {
  flex: 1;
  min-height: 0;
  min-width: 0;
}

.ma-now {
  display: flex;
  gap: 16px;
  align-items: center;
  padding: 14px;
  border-radius: 12px;
  background: color-mix(in srgb, var(--rc-card) 22%, transparent);
}

.ma-art {
  width: 120px;
  height: 120px;
  border-radius: 8px;
  background: var(--rc-track);
  flex-shrink: 0;
}

.ma-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: var(--rc-ink-soft);
  font-size: 14px;
}

.ma-meta strong {
  color: var(--rc-ink);
  font-size: 18px;
  font-weight: 600;
}

.ma-times {
  display: flex;
  justify-content: space-between;
  margin-top: 6px;
  font-size: 12px;
}

.ma-progress {
  display: block;
  height: 4px;
  margin-top: 4px;
  border-radius: 2px;
  background: color-mix(in srgb, var(--rc-accent) 70%, var(--rc-track));
}

.ma-transport {
  display: flex;
  gap: 8px;
  margin-top: 6px;
  color: var(--rc-ink);
}

.ma-segments {
  display: flex;
  border-radius: 20px;
  overflow: hidden;
}

.ma-segments i {
  flex: 1;
  text-align: center;
  padding: 8px 10px;
  background: var(--rc-key);
  color: var(--rc-ink);
  font-style: normal;
  font-size: 13px;
}

.ma-segments i.on {
  background: var(--rc-accent, #14e1ba);
  color: #10221e;
}

.ma-field {
  border: 1px solid color-mix(in srgb, var(--rc-ink) 45%, transparent);
  border-radius: 4px;
  padding: 14px 12px 8px;
  color: var(--rc-ink-soft);
  font-size: 12px;
}

.ma-more {
  color: var(--rc-accent, #14e1ba);
  font-size: 14px;
}

.ma-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px;
  border-radius: 12px;
  background: color-mix(in srgb, var(--rc-card) 18%, transparent);
}

.ma-label {
  color: var(--rc-ink);
  font-weight: 600;
}

.ma-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.ma-chips i {
  font-style: normal;
  padding: 6px 10px;
  border-radius: 8px;
  background: var(--rc-key);
  color: var(--rc-ink);
  font-size: 12px;
}

.ma-chips i.on {
  background: var(--rc-key-on);
  color: var(--rc-key-on-ink);
}

.ma-row {
  padding: 8px 10px;
  border-radius: 10px;
  background: color-mix(in srgb, var(--rc-card) 14%, transparent);
  color: var(--rc-ink);
  font-size: 14px;
}

.ma-hint {
  color: var(--rc-ink-soft);
  font-size: 13px;
  margin-top: 8px;
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
