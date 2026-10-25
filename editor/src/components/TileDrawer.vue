<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { AlarmZone } from '../model/alarmZones'
import { ALARM_ZONES_OPTION, formatAlarmZones, parseAlarmZones } from '../model/alarmZones'
import type { CameraEntry } from '../model/cameraList'
import { CAMERAS_OPTION, formatCameraList, parseCameraList } from '../model/cameraList'
import { firstFree, fitsAt } from '../model/grid'
import { INK_SWATCHES, PASTEL_KEYS, pastel } from '../model/palette'
import { iconOf, nameOf, newTileId, sizesOf, specOf } from '../model/tiles'
import type { Page, Screen, TapAction, TapType, Tile } from '../model/types'
import { domainOf, kindOf } from '../model/types'
import { entityById, state, toast } from '../store'
import ColorSwatches from './ColorSwatches.vue'
import IconPicker from './IconPicker.vue'
import MdiIcon from './MdiIcon.vue'

const props = defineProps<{ tile: Tile; page: Page; screen: Screen }>()
const emit = defineEmits<{ close: []; remove: [] }>()

const entity = computed(() =>
  props.tile.entityId ? entityById.value.get(props.tile.entityId) : undefined,
)
const spec = computed(() => specOf(props.tile))
const domain = computed(() => domainOf(props.tile))
const others = computed(() => props.page.tiles.filter(tile => tile.id !== props.tile.id))
/** Drawn as a page of its own, without a card (Tiles.kt `edgeToEdge`). */
const edgeToEdge = computed(
  () =>
    kindOf(props.tile) === 'home' ||
    (!!props.tile.full &&
      [
        'weather',
        'climate',
        'music',
        'calendar',
        'alarm_control_panel',
        'lights',
        'cameras',
      ].includes(kindOf(props.tile))),
)

// A full alarm tile is RPI's alarm page: its own entity is All Zones, then the zones listed here.
const isAlarmPage = computed(
  () => !!props.tile.full && kindOf(props.tile) === 'alarm_control_panel',
)
const alarmZones = computed(() => parseAlarmZones(props.tile.options[ALARM_ZONES_OPTION]))
const alarmPanels = computed(() =>
  state.entities.filter(
    panel => panel.domain === 'alarm_control_panel' && panel.entityId !== props.tile.entityId,
  ),
)
const unusedAlarmPanels = computed(() =>
  alarmPanels.value.filter(
    panel => !alarmZones.value.some(zone => zone.entityId === panel.entityId),
  ),
)

function writeAlarmZones(zones: AlarmZone[]): void {
  const text = formatAlarmZones(zones)
  if (text) props.tile.options[ALARM_ZONES_OPTION] = text
  else delete props.tile.options[ALARM_ZONES_OPTION]
}

function setAlarmZone(zoneIndex: number, change: Partial<AlarmZone>): void {
  writeAlarmZones(
    alarmZones.value.map((zone, index) => (index === zoneIndex ? { ...zone, ...change } : zone)),
  )
}

function addAlarmZone(): void {
  const panel = unusedAlarmPanels.value[0]
  if (!panel) return toast('Every alarm panel is already a zone')
  writeAlarmZones([...alarmZones.value, { entityId: panel.entityId, label: '' }])
}

function removeAlarmZone(zoneIndex: number): void {
  writeAlarmZones(alarmZones.value.filter((_, index) => index !== zoneIndex))
}

const panelsForZone = (zone: AlarmZone) =>
  alarmPanels.value.filter(
    panel =>
      panel.entityId === zone.entityId ||
      !alarmZones.value.some(other => other.entityId === panel.entityId),
  )

// A full cameras tile is RPI's cameras page: it lists the cameras chosen here, and plays the one tapped.
const isCamerasPage = computed(() => !!props.tile.full && kindOf(props.tile) === 'cameras')
const cameraEntries = computed(() => parseCameraList(props.tile.options[CAMERAS_OPTION]))
const cameraEntities = computed(() => state.entities.filter(camera => camera.domain === 'camera'))
const unusedCameras = computed(() =>
  cameraEntities.value.filter(
    camera => !cameraEntries.value.some(entry => entry.entityId === camera.entityId),
  ),
)

function writeCameraEntries(cameras: CameraEntry[]): void {
  const text = formatCameraList(cameras)
  if (text) props.tile.options[CAMERAS_OPTION] = text
  else delete props.tile.options[CAMERAS_OPTION]
}

function setCameraEntry(cameraIndex: number, change: Partial<CameraEntry>): void {
  writeCameraEntries(
    cameraEntries.value.map((entry, index) =>
      index === cameraIndex ? { ...entry, ...change } : entry,
    ),
  )
}

function addCameraEntry(): void {
  const camera = unusedCameras.value[0]
  if (!camera) return toast('Every camera is already on the page')
  writeCameraEntries([...cameraEntries.value, { entityId: camera.entityId, label: '' }])
}

function removeCameraEntry(cameraIndex: number): void {
  writeCameraEntries(cameraEntries.value.filter((_, index) => index !== cameraIndex))
}

const camerasForEntry = (entry: CameraEntry) =>
  cameraEntities.value.filter(
    camera =>
      camera.entityId === entry.entityId ||
      !cameraEntries.value.some(other => other.entityId === camera.entityId),
  )

/** An entity may be shown another way: a sensor as a graph, a player as its favourite music. */
const SHOW_AS: Record<string, { kind: string; label: string }[]> = {
  sensor: [{ kind: 'history', label: 'History graph' }],
  media_player: [
    { kind: 'favourite', label: 'Favourite music' },
    { kind: 'music', label: 'Music Assistant' },
  ],
}
const showAs = computed(() => (domain.value ? (SHOW_AS[domain.value] ?? []) : []))

const sizes = computed(() => sizesOf(props.tile))
const isSize = (size: { w: number; h: number; full: boolean }) =>
  size.full
    ? !!props.tile.full
    : !props.tile.full && props.tile.w === size.w && props.tile.h === size.h

function setSize(size: { w: number; h: number; full: boolean }): void {
  const tile = props.tile
  if (size.full) {
    if (others.value.length) return toast('A full-page tile needs a page of its own')
    Object.assign(tile, { full: true, col: 0, row: 0, w: 1, h: 1 })
    return
  }
  const spot = fitsAt(others.value, props.screen.grid, tile.col, tile.row, size.w, size.h)
    ? { col: tile.col, row: tile.row }
    : firstFree(others.value, props.screen.grid, size.w, size.h)
  if (!spot) return toast(`No room for ${size.w} × ${size.h} on this page`)
  Object.assign(tile, { full: undefined, w: size.w, h: size.h, ...spot })
}

function setKind(kind: string | undefined): void {
  props.tile.kind = kind
  if (!sizes.value.some(isSize))
    setSize(sizes.value.find(sizeOption => !sizeOption.full) ?? sizes.value[0])
}

// Tap and hold.
const TAPS: { type: TapType; label: string }[] = [
  { type: 'default', label: 'Default' },
  { type: 'toggle', label: 'Toggle' },
  { type: 'card', label: 'Open its card' },
  { type: 'action', label: 'Run an action' },
  { type: 'page', label: 'Go to a page' },
  { type: 'none', label: 'Nothing' },
]
const defaultTap = computed(
  () => TAPS.find(tap => tap.type === spec.value?.tap)?.label.toLowerCase() ?? 'card',
)
const taps = computed(() => TAPS.filter(tap => tap.type !== 'toggle' || spec.value?.toggle))

function setTap(which: 'tap' | 'hold', type: TapType): void {
  const action: TapAction = { type }
  if (type === 'action') action.service = props.tile[which].service ?? defaultService()
  if (type === 'page')
    action.page =
      props.tile[which].page ?? props.screen.pages.find(page => page.id !== props.page.id)?.id
  props.tile[which] = action
}

function defaultService(): string {
  const domainName = domain.value
  return domainName
    ? `${domainName}.${domainName === 'scene' || domainName === 'script' ? 'turn_on' : 'toggle'}`
    : ''
}

// Service data as JSON text, applied when it parses.
const dataText = ref('')
const dataError = ref('')
watch(
  () => props.tile.id,
  () => {
    dataText.value = props.tile.tap.data ? JSON.stringify(props.tile.tap.data, null, 2) : ''
    dataError.value = ''
  },
  { immediate: true },
)
watch(dataText, text => {
  if (!text.trim()) {
    delete props.tile.tap.data
    dataError.value = ''
    return
  }
  try {
    const parsed = JSON.parse(text)
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) throw new Error()
    props.tile.tap.data = parsed
    dataError.value = ''
  } catch {
    dataError.value = 'Not a JSON object'
  }
})

function option(key: string): boolean {
  return props.tile.options[key] === 'true'
}

function setOption(key: string, on: boolean): void {
  if (on) props.tile.options[key] = 'true'
  else delete props.tile.options[key]
}

const OPTION_LABELS: Record<string, string> = {
  graph: 'Show a graph',
  live: 'Live picture',
  map: 'Show on a map',
}

function duplicate(): void {
  const tile = props.tile
  const spot = firstFree(props.page.tiles, props.screen.grid, tile.w, tile.h)
  if (tile.full || !spot) return toast('No room for a copy on this page')
  props.page.tiles.push({ ...JSON.parse(JSON.stringify(tile)), id: newTileId(), ...spot })
}

const entities = computed(() => state.entities.filter(entity => entity.domain === domain.value))
const pageTitle = (id: string) => props.screen.pages.find(page => page.id === id)?.title
</script>

<template>
  <div class="drawer">
    <div class="row">
      <MdiIcon :name="iconOf(tile, entity)" :size="26" />
      <h2 class="grow">{{ nameOf(tile, entity, pageTitle) }}</h2>
      <button class="icon" title="Close" @click="emit('close')">
        <MdiIcon name="close" :size="18" />
      </button>
    </div>
    <p class="muted id">{{ tile.entityId ?? spec?.name ?? tile.kind }}</p>

    <label v-if="tile.entityId && entities.length > 1" class="field">
      Entity
      <select v-model="tile.entityId">
        <option v-for="e in entities" :key="e.entityId" :value="e.entityId">
          {{ e.name }} ({{ e.entityId }})
        </option>
      </select>
    </label>

    <label v-if="showAs.length" class="field">
      Show as
      <select
        :value="tile.kind ?? ''"
        @change="setKind(($event.target as HTMLSelectElement).value || undefined)"
      >
        <option value="">{{ specOf({ entityId: tile.entityId })?.name ?? 'Tile' }}</option>
        <option v-for="k in showAs" :key="k.kind" :value="k.kind">{{ k.label }}</option>
      </select>
    </label>

    <label v-if="tile.kind === 'nav'" class="field">
      Page
      <select v-model="tile.options.page">
        <option v-for="p in screen.pages.filter(p => p.id !== page.id)" :key="p.id" :value="p.id">
          {{ p.title }}
        </option>
      </select>
    </label>

    <label class="field">
      Name
      <input
        :value="tile.name ?? ''"
        :placeholder="nameOf({ ...tile, name: undefined }, entity, pageTitle)"
        @input="tile.name = ($event.target as HTMLInputElement).value || undefined"
      />
    </label>

    <div class="field">
      <span class="label">Icon</span>
      <IconPicker v-model="tile.icon" :placeholder="iconOf({ ...tile, icon: undefined }, entity)" />
    </div>

    <div class="field">
      <span class="label">Colour</span>
      <ColorSwatches
        v-model="tile.color"
        :keys="[...PASTEL_KEYS]"
        :clear-color="pastel(undefined, domain).color"
      />
    </div>

    <div v-if="!edgeToEdge" class="field">
      <span class="label">Border colour</span>
      <ColorSwatches
        v-model="tile.borderColor"
        :keys="INK_SWATCHES"
        clear-title="The theme's card edge"
      />
    </div>

    <div class="field">
      <span class="label">Size</span>
      <div class="row wrap">
        <button
          v-for="s in sizes"
          :key="s.label"
          :class="{ primary: isSize(s) }"
          @click="setSize(s)"
        >
          {{ s.label }}
        </button>
      </div>
    </div>

    <label class="field">
      Tap
      <select
        :value="tile.tap.type"
        @change="setTap('tap', ($event.target as HTMLSelectElement).value as TapType)"
      >
        <option v-for="t in taps" :key="t.type" :value="t.type">
          {{ t.type === 'default' ? `Default (${defaultTap})` : t.label }}
        </option>
      </select>
    </label>
    <template v-if="tile.tap.type === 'action'">
      <label class="field"
        >Action<input v-model="tile.tap.service" placeholder="light.turn_on"
      /></label>
      <label class="field">
        Data (JSON)
        <textarea v-model="dataText" rows="3" placeholder='{"brightness_pct": 50}' />
        <span v-if="dataError" class="error">{{ dataError }}</span>
      </label>
    </template>
    <label v-if="tile.tap.type === 'page'" class="field">
      Page
      <select v-model="tile.tap.page">
        <option v-for="p in screen.pages" :key="p.id" :value="p.id">{{ p.title }}</option>
      </select>
    </label>

    <label class="field">
      Long press
      <select
        :value="tile.hold.type"
        @change="setTap('hold', ($event.target as HTMLSelectElement).value as TapType)"
      >
        <option value="card">Open its card</option>
        <option value="page">Go to a page</option>
        <option value="none">Nothing</option>
      </select>
    </label>
    <label v-if="tile.hold.type === 'page'" class="field">
      Page
      <select v-model="tile.hold.page">
        <option v-for="p in screen.pages" :key="p.id" :value="p.id">{{ p.title }}</option>
      </select>
    </label>

    <div v-if="isAlarmPage" class="field">
      <span class="label">Zones</span>
      <span>
        {{ entity?.name ?? tile.entityId }} is All Zones. Add other alarm panels, such as Alarmo
        areas, to arm them one at a time.
      </span>
      <div v-for="(zone, zoneIndex) in alarmZones" :key="zone.entityId" class="row zone">
        <select
          :value="zone.entityId"
          @change="
            setAlarmZone(zoneIndex, { entityId: ($event.target as HTMLSelectElement).value })
          "
        >
          <option
            v-for="panel in panelsForZone(zone)"
            :key="panel.entityId"
            :value="panel.entityId"
          >
            {{ panel.name }}
          </option>
        </select>
        <input
          :value="zone.label"
          :placeholder="entityById.get(zone.entityId)?.name ?? 'Label'"
          @change="setAlarmZone(zoneIndex, { label: ($event.target as HTMLInputElement).value })"
        />
        <button class="icon" title="Remove the zone" @click="removeAlarmZone(zoneIndex)">
          <MdiIcon name="close" :size="16" />
        </button>
      </div>
      <button :disabled="!unusedAlarmPanels.length" @click="addAlarmZone">Add a zone</button>
    </div>

    <div v-if="isCamerasPage" class="field">
      <span class="label">Cameras</span>
      <span>Choose the cameras the page lists. Tapping one plays its live stream.</span>
      <div v-for="(entry, cameraIndex) in cameraEntries" :key="entry.entityId" class="row zone">
        <select
          :value="entry.entityId"
          @change="
            setCameraEntry(cameraIndex, { entityId: ($event.target as HTMLSelectElement).value })
          "
        >
          <option
            v-for="camera in camerasForEntry(entry)"
            :key="camera.entityId"
            :value="camera.entityId"
          >
            {{ camera.name }}
          </option>
        </select>
        <input
          :value="entry.label"
          :placeholder="entityById.get(entry.entityId)?.name ?? 'Label'"
          @change="
            setCameraEntry(cameraIndex, { label: ($event.target as HTMLInputElement).value })
          "
        />
        <button class="icon" title="Remove the camera" @click="removeCameraEntry(cameraIndex)">
          <MdiIcon name="close" :size="16" />
        </button>
      </div>
      <button :disabled="!unusedCameras.length" @click="addCameraEntry">Add a camera</button>
    </div>

    <div v-if="spec?.options.length" class="field">
      <span class="label">Options</span>
      <label v-for="o in spec.options" :key="o" class="check">
        <input
          type="checkbox"
          :checked="option(o)"
          @change="setOption(o, ($event.target as HTMLInputElement).checked)"
        />
        {{ OPTION_LABELS[o] ?? o }}
      </label>
    </div>

    <div class="row actions">
      <button @click="duplicate">Duplicate</button>
      <span class="grow" />
      <button class="danger" @click="emit('remove')">Remove</button>
    </div>
  </div>
</template>

<style scoped>
.drawer {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.id {
  margin: -10px 0 0;
  font-size: 12px;
  font-family: ui-monospace, monospace;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12px;
  color: var(--muted);
}

.field > :is(input, select, textarea) {
  color: var(--ink);
  font-size: 14px;
}

.label {
  font-size: 12px;
}

.wrap {
  flex-wrap: wrap;
}

.zone > select {
  flex: 1;
  min-width: 0;
}

.zone > input {
  width: 110px;
}

.zone > :is(input, select) {
  color: var(--ink);
  font-size: 14px;
}

.check {
  color: var(--ink);
  font-size: 14px;
}

.error {
  color: var(--danger);
}

.actions {
  padding-top: 6px;
  border-top: 1px solid var(--line);
}
</style>
