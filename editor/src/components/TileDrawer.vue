<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { firstFree, fitsAt } from '../model/grid'
import { PASTELS, pastel } from '../model/palette'
import { iconOf, nameOf, newTileId, sizesOf, specOf } from '../model/tiles'
import type { Page, Screen, TapAction, TapType, Tile } from '../model/types'
import { domainOf } from '../model/types'
import { entityById, state, toast } from '../store'
import IconPicker from './IconPicker.vue'
import MdiIcon from './MdiIcon.vue'

const props = defineProps<{ tile: Tile; page: Page; screen: Screen }>()
const emit = defineEmits<{ close: []; remove: [] }>()

const entity = computed(() => (props.tile.entityId ? entityById.value.get(props.tile.entityId) : undefined))
const spec = computed(() => specOf(props.tile))
const domain = computed(() => domainOf(props.tile))
const others = computed(() => props.page.tiles.filter((t) => t.id !== props.tile.id))

/** An entity may be shown another way: a sensor as a graph, a player as its favourite music. */
const SHOW_AS: Record<string, { kind: string; label: string }[]> = {
  sensor: [{ kind: 'history', label: 'History graph' }],
  media_player: [{ kind: 'favourite', label: 'Favourite music' }],
}
const showAs = computed(() => (domain.value ? SHOW_AS[domain.value] ?? [] : []))

const sizes = computed(() => sizesOf(props.tile))
const isSize = (s: { w: number; h: number; full: boolean }) =>
  s.full ? !!props.tile.full : !props.tile.full && props.tile.w === s.w && props.tile.h === s.h

function setSize(s: { w: number; h: number; full: boolean }): void {
  const t = props.tile
  if (s.full) {
    if (others.value.length) return toast('A full-page tile needs a page of its own')
    Object.assign(t, { full: true, col: 0, row: 0, w: 1, h: 1 })
    return
  }
  const spot = fitsAt(others.value, props.screen.grid, t.col, t.row, s.w, s.h)
    ? { col: t.col, row: t.row }
    : firstFree(others.value, props.screen.grid, s.w, s.h)
  if (!spot) return toast(`No room for ${s.w} × ${s.h} on this page`)
  Object.assign(t, { full: undefined, w: s.w, h: s.h, ...spot })
}

function setKind(kind: string | undefined): void {
  props.tile.kind = kind
  if (!sizes.value.some(isSize)) setSize(sizes.value.find((s) => !s.full) ?? sizes.value[0])
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
const defaultTap = computed(() => TAPS.find((t) => t.type === spec.value?.tap)?.label.toLowerCase() ?? 'card')
const taps = computed(() => TAPS.filter((t) => t.type !== 'toggle' || spec.value?.toggle))

function setTap(which: 'tap' | 'hold', type: TapType): void {
  const a: TapAction = { type }
  if (type === 'action') a.service = props.tile[which].service ?? defaultService()
  if (type === 'page') a.page = props.tile[which].page ?? props.screen.pages.find((p) => p.id !== props.page.id)?.id
  props.tile[which] = a
}

function defaultService(): string {
  const d = domain.value
  return d ? `${d}.${d === 'scene' || d === 'script' ? 'turn_on' : 'toggle'}` : ''
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
watch(dataText, (text) => {
  if (!text.trim()) {
    delete props.tile.tap.data
    dataError.value = ''
    return
  }
  try {
    const v = JSON.parse(text)
    if (typeof v !== 'object' || v === null || Array.isArray(v)) throw new Error()
    props.tile.tap.data = v
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
  const t = props.tile
  const spot = firstFree(props.page.tiles, props.screen.grid, t.w, t.h)
  if (t.full || !spot) return toast('No room for a copy on this page')
  props.page.tiles.push({ ...JSON.parse(JSON.stringify(t)), id: newTileId(), ...spot })
}

const entities = computed(() => state.entities.filter((e) => e.domain === domain.value))
const pageTitle = (id: string) => props.screen.pages.find((p) => p.id === id)?.title
</script>

<template>
  <div class="drawer">
    <div class="row">
      <MdiIcon :name="iconOf(tile, entity)" :size="26" />
      <h2 class="grow">{{ nameOf(tile, entity, pageTitle) }}</h2>
      <button class="icon" title="Close" @click="emit('close')"><MdiIcon name="close" :size="18" /></button>
    </div>
    <p class="muted id">{{ tile.entityId ?? spec?.name ?? tile.kind }}</p>

    <label v-if="tile.entityId && entities.length > 1" class="field">
      Entity
      <select v-model="tile.entityId">
        <option v-for="e in entities" :key="e.entityId" :value="e.entityId">{{ e.name }} ({{ e.entityId }})</option>
      </select>
    </label>

    <label v-if="showAs.length" class="field">
      Show as
      <select :value="tile.kind ?? ''" @change="setKind(($event.target as HTMLSelectElement).value || undefined)">
        <option value="">{{ specOf({ entityId: tile.entityId })?.name ?? 'Tile' }}</option>
        <option v-for="k in showAs" :key="k.kind" :value="k.kind">{{ k.label }}</option>
      </select>
    </label>

    <label v-if="tile.kind === 'nav'" class="field">
      Page
      <select v-model="tile.options.page">
        <option v-for="p in screen.pages.filter((p) => p.id !== page.id)" :key="p.id" :value="p.id">{{ p.title }}</option>
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
      <div class="swatches">
        <button
          class="swatch auto"
          :class="{ on: !tile.color }"
          :style="{ background: pastel(undefined, domain).light }"
          title="Default"
          @click="tile.color = undefined"
        >
          A
        </button>
        <button
          v-for="p in PASTELS"
          :key="p.key"
          class="swatch"
          :class="{ on: tile.color === p.key }"
          :style="{ background: p.light }"
          :title="p.key"
          @click="tile.color = p.key"
        />
      </div>
    </div>

    <div class="field">
      <span class="label">Size</span>
      <div class="row wrap">
        <button v-for="s in sizes" :key="s.label" :class="{ primary: isSize(s) }" @click="setSize(s)">{{ s.label }}</button>
      </div>
    </div>

    <label class="field">
      Tap
      <select :value="tile.tap.type" @change="setTap('tap', ($event.target as HTMLSelectElement).value as TapType)">
        <option v-for="t in taps" :key="t.type" :value="t.type">{{ t.type === 'default' ? `Default (${defaultTap})` : t.label }}</option>
      </select>
    </label>
    <template v-if="tile.tap.type === 'action'">
      <label class="field">Action<input v-model="tile.tap.service" placeholder="light.turn_on" /></label>
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
      <select :value="tile.hold.type" @change="setTap('hold', ($event.target as HTMLSelectElement).value as TapType)">
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

    <div v-if="spec?.options.length" class="field">
      <span class="label">Options</span>
      <label v-for="o in spec.options" :key="o" class="check">
        <input type="checkbox" :checked="option(o)" @change="setOption(o, ($event.target as HTMLInputElement).checked)" />
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

.swatches {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.swatch {
  width: 30px;
  height: 30px;
  padding: 0;
  border-radius: 50%;
  border: 2px solid transparent;
  color: #333;
  font-size: 11px;
}

.swatch.on {
  border-color: var(--ink);
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
