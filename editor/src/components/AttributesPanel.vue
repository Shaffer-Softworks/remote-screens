<script setup lang="ts">
// The attributes of the selected component, as Android Studio's Attributes pane: layout, style, content, the
// entity it shows and what a tap does.
import { computed, inject, ref, watch } from 'vue'
import { CATALOGUE } from '../generated/catalogue'
import { DESIGN } from '../model/design'
import { componentOf, isContainer } from '../model/nodes'
import { BACKGROUND_SWATCHES, INK_SWATCHES, PASTEL_KEYS, colorOf } from '../model/palette'
import { iconOf, specOf } from '../model/tiles'
import type { BindFormat, Node, Screen, TapAction, TapType } from '../model/types'
import { entityById, state } from '../store'
import ColorSwatches from './ColorSwatches.vue'
import IconPicker from './IconPicker.vue'
import MdiIcon from './MdiIcon.vue'

const props = defineProps<{ node: Node; screen: Screen; isRoot: boolean; parentType?: string }>()
const ctx = inject(DESIGN)!

const n = computed(() => props.node)
const p = computed(() => n.value.props)
const container = computed(() => isContainer(n.value))
const BINDABLE = [
  'text', 'icon', 'button', 'slider', 'image', 'badge', 'avatar', 'progress', 'graph', 'switch', 'stepper', 'choices',
  'gauge', 'iconbutton', 'keys', 'swatches', 'keypad',
]
/** Components whose own keys take the taps. */
const OWN_TAPS = ['slider', 'stepper', 'choices', 'keys', 'swatches', 'keypad']
const COLOURED = ['text', 'icon', 'button', 'divider', 'clock', 'badge', 'progress', 'graph', 'gauge', 'analog']
const BIND_NOTES: Record<string, string> = {
  slider: 'A slider sets a light, fan, cover, player or number.',
  image: 'An image shows a camera or a person.',
  avatar: "An avatar shows a person's photo, or their initials.",
  progress: "Fills with a light's brightness, a cover's position, or a number between Min and Max.",
  graph: "Draws the entity's history from the recorder.",
  switch: 'Toggles the entity: a switch, light, fan or anything that turns on and off.',
  stepper: "Steps a thermostat's target, a number, a light's brightness, a fan, a cover or a player's volume.",
  choices: "Offers a select's options, a thermostat's modes or a light's effects.",
  gauge: "Fills with a light's brightness, a cover's position, or a number between its min and max.",
  iconbutton: "Lights up in the entity's colour while it is on.",
  keys: "A player's previous, play and next; a cover's open, stop and close; a vacuum's, a lock's, an alarm's or a light's keys.",
  swatches: 'Sets a light to the colour or white tapped.',
  keypad: "Types an alarm's or a lock's code, sent with its next arm, disarm or unlock.",
}
const SHOW_WHEN = [
  { value: '', label: 'Always' },
  { value: 'on', label: 'Only while it is on' },
  { value: 'off', label: 'Only while it is off' },
]
const ANIMATIONS = [
  { value: '', label: 'Fading' },
  { value: 'up', label: 'Sliding up' },
  { value: 'down', label: 'Sliding down' },
  { value: 'left', label: 'Sliding left' },
  { value: 'right', label: 'Sliding right' },
  { value: 'none', label: 'At once' },
]
/** The size the player draws when none is set. */
const SIZES: Record<string, number> = { gauge: 140, analog: 160, iconbutton: 64, keys: 48, swatches: 40, keypad: 56 }

function setProp(key: string, value: string | undefined): void {
  if (value === undefined || value === '') delete n.value.props[key]
  else n.value.props[key] = value
}

// Sizes: wrap, fill, dp:N or weight:N.
type Mode = 'wrap' | 'fill' | 'dp' | 'weight'
const modeOf = (s: string): Mode => (s.startsWith('dp:') ? 'dp' : s.startsWith('weight:') ? 'weight' : (s as Mode))
const amountOf = (s: string): number => Number(s.split(':')[1] ?? 0)
function setMode(dim: 'width' | 'height', mode: Mode): void {
  const old = n.value.size[dim]
  n.value.size[dim] =
    mode === 'dp' ? `dp:${modeOf(old) === 'dp' ? amountOf(old) : 120}`
    : mode === 'weight' ? `weight:${modeOf(old) === 'weight' ? amountOf(old) : 1}`
    : mode
}
function setAmount(dim: 'width' | 'height', v: number): void {
  const mode = modeOf(n.value.size[dim])
  if (mode !== 'dp' && mode !== 'weight') return
  const min = mode === 'dp' ? 0 : 0.1
  n.value.size[dim] = `${mode}:${Math.max(min, Number.isFinite(v) ? v : min)}`
}

const BOX_ALIGN = ['top-start', 'top-center', 'top-end', 'center-start', 'center', 'center-end', 'bottom-start', 'bottom-center', 'bottom-end']

/** Where the component sits in its parent, apart from the parent's own alignment. */
const placeChoices = computed(() =>
  props.parentType === 'column'
    ? [{ value: 'start', label: 'Left' }, { value: 'center', label: 'Center' }, { value: 'end', label: 'Right' }]
    : props.parentType === 'row'
      ? [{ value: 'top', label: 'Top' }, { value: 'center', label: 'Center' }, { value: 'bottom', label: 'Bottom' }]
      : [],
)
/** Filling across its parent leaves a component no room to move. */
const placeHasNoEffect = computed(() => {
  const cross = props.parentType === 'column' ? n.value.size.width : props.parentType === 'row' ? n.value.size.height : ''
  return cross === 'fill' || cross.startsWith('weight:')
})
// The entity a component shows.
const entityOptions = computed(() => state.entities)
const entityDraft = ref('')
watch(
  () => [n.value.id, n.value.bind?.entityId],
  () => (entityDraft.value = n.value.bind?.entityId ?? ''),
  { immediate: true },
)
function bindTo(id: string): void {
  const v = id.trim()
  if (!v) {
    delete n.value.bind
    return
  }
  if (n.value.bind) n.value.bind.entityId = v
  else {
    n.value.bind = { entityId: v, format: 'state', prefix: '', suffix: '' }
    if (n.value.type === 'badge' && n.value.props.text === 'Badge') delete n.value.props.text
  }
}
const FORMATS: { value: BindFormat; label: string }[] = [
  { value: 'state', label: 'State, in words' },
  { value: 'value', label: 'Value with unit' },
  { value: 'name', label: 'Name' },
  { value: 'attribute', label: 'An attribute' },
]

// The card of a `tile` component.
const tile = computed(() => n.value.tile)
const tileEntity = computed(() => (tile.value?.entityId ? entityById.value.get(tile.value.entityId) : undefined))
const KINDS = ['home', 'clock', 'bedside', 'nav', 'energy', 'map']
const tileEntities = computed(() => state.entities.filter((e) => e.domain in CATALOGUE))

// Tap and hold. A tile's live on its card; any other component's are its own, and absent means the default.
const TAPS: { type: TapType; label: string }[] = [
  { type: 'default', label: 'Default' },
  { type: 'toggle', label: 'Toggle' },
  { type: 'card', label: 'Open its card' },
  { type: 'action', label: 'Run an action' },
  { type: 'page', label: 'Go to a page' },
  { type: 'none', label: 'Nothing' },
]
const tapHost = computed<{ tap?: TapAction; hold?: TapAction }>(() => tile.value ?? n.value)
const tapDefault = computed(() => {
  if (n.value.type === 'switch') return 'toggle'
  const domain = (tile.value?.entityId ?? n.value.bind?.entityId)?.split('.')[0]
  const spec = tile.value ? specOf(tile.value) : domain ? CATALOGUE[domain] : undefined
  if (!spec) return 'nothing'
  return TAPS.find((t) => t.type === spec.tap)?.label.toLowerCase() ?? 'open its card'
})
function tapOf(which: 'tap' | 'hold'): TapType | '' {
  return tapHost.value[which]?.type ?? ''
}
function setTap(which: 'tap' | 'hold', type: TapType | ''): void {
  const host = tapHost.value
  if (type === '') {
    if (tile.value) host[which] = { type: which === 'tap' ? 'default' : 'card' }
    else delete host[which]
    return
  }
  const a: TapAction = { type }
  const old = host[which]
  if (type === 'action') {
    const d = (tile.value?.entityId ?? n.value.bind?.entityId)?.split('.')[0]
    a.service = old?.service ?? (d ? `${d}.${d === 'scene' || d === 'script' ? 'turn_on' : 'toggle'}` : '')
  }
  if (type === 'page') a.page = old?.page ?? props.screen.pages[0]?.id
  host[which] = a
}

const dataText = ref('')
const dataError = ref('')
watch(
  () => n.value.id,
  () => {
    const d = tapHost.value.tap?.data
    dataText.value = d ? JSON.stringify(d, null, 2) : ''
    dataError.value = ''
  },
  { immediate: true },
)
watch(dataText, (text) => {
  const tap = tapHost.value.tap
  if (!tap) return
  if (!text.trim()) {
    delete tap.data
    dataError.value = ''
    return
  }
  try {
    const v = JSON.parse(text)
    if (typeof v !== 'object' || v === null || Array.isArray(v)) throw new Error()
    tap.data = v
    dataError.value = ''
  } catch {
    dataError.value = 'Not a JSON object'
  }
})

const title = computed(() => (props.isRoot ? 'Page' : (p.value.label ?? componentOf(n.value.type)?.label ?? 'Tile')))
const typeIcon = computed(() => (n.value.type === 'tile' ? 'view-grid-outline' : (componentOf(n.value.type)?.icon ?? 'shape-outline')))
</script>

<template>
  <div class="attrs">
    <div class="row">
      <MdiIcon :name="typeIcon" :size="24" />
      <h2 class="grow">{{ title }}</h2>
      <button class="icon" title="Deselect (Esc)" @click="ctx.select(null)"><MdiIcon name="close" :size="18" /></button>
    </div>
    <p class="muted id">{{ n.type }} · {{ n.id }}</p>

    <div v-if="!isRoot" class="row wrap tools">
      <button class="icon" title="Move earlier" @click="ctx.shift(-1)"><MdiIcon name="arrow-up" :size="16" /></button>
      <button class="icon" title="Move later" @click="ctx.shift(1)"><MdiIcon name="arrow-down" :size="16" /></button>
      <button title="Duplicate (Ctrl+D)" @click="ctx.duplicateSelected()">Duplicate</button>
      <select aria-label="Wrap in" value="" @change="ctx.wrapSelected(($event.target as HTMLSelectElement).value as 'row'); ($event.target as HTMLSelectElement).value = ''">
        <option value="" disabled>Wrap in…</option>
        <option value="column">Column</option>
        <option value="row">Row</option>
        <option value="box">Box</option>
        <option value="flow">Flow</option>
      </select>
      <span class="grow" />
      <button class="danger" title="Remove (Delete)" @click="ctx.removeSelected()">Remove</button>
    </div>

    <details open>
      <summary>Layout</summary>
      <div v-for="dim in ['width', 'height'] as const" :key="dim" class="field">
        <span class="label">{{ dim === 'width' ? 'Width' : 'Height' }}</span>
        <div class="row">
          <select :value="modeOf(n.size[dim])" @change="setMode(dim, ($event.target as HTMLSelectElement).value as Mode)">
            <option value="wrap">Wrap content</option>
            <option value="fill">Fill</option>
            <option value="dp">Fixed (dp)</option>
            <option value="weight">Weight</option>
          </select>
          <input
            v-if="modeOf(n.size[dim]) === 'dp' || modeOf(n.size[dim]) === 'weight'"
            type="number"
            :step="modeOf(n.size[dim]) === 'dp' ? 4 : 0.5"
            min="0"
            class="num"
            :value="amountOf(n.size[dim])"
            @input="setAmount(dim, Number(($event.target as HTMLInputElement).value))"
          />
        </div>
      </div>
      <div v-if="placeChoices.length" class="field">
        <span class="label">Align in its {{ parentType }}</span>
        <div class="segments">
          <button :class="{ active: !p.place }" title="As the parent aligns its contents" @click="setProp('place', undefined)">Default</button>
          <button v-for="c in placeChoices" :key="c.value" :class="{ active: p.place === c.value }" @click="setProp('place', c.value)">
            {{ c.label }}
          </button>
        </div>
        <p v-if="p.place && placeHasNoEffect" class="muted note">
          It fills its {{ parentType }}'s {{ parentType === 'column' ? 'width' : 'height' }}, so set that to Wrap or a size to move it.
        </p>
      </div>
      <div v-else-if="parentType === 'box'" class="field">
        <span class="label">Align in its box</span>
        <div class="row">
          <div class="align-grid">
            <button v-for="a in BOX_ALIGN" :key="a" :class="{ on: p.place === a }" :title="a" @click="setProp('place', a)" />
          </div>
          <button v-if="p.place" class="link" @click="setProp('place', undefined)">Default</button>
        </div>
      </div>
      <template v-if="n.type === 'column' || n.type === 'row'">
        <label class="field">
          {{ n.type === 'row' ? 'Horizontal arrangement' : 'Vertical arrangement' }}
          <select :value="p.arrangement ?? 'start'" @change="setProp('arrangement', ($event.target as HTMLSelectElement).value)">
            <option value="start">{{ n.type === 'row' ? 'Start' : 'Top' }}</option>
            <option value="center">Center</option>
            <option value="end">{{ n.type === 'row' ? 'End' : 'Bottom' }}</option>
            <option value="spaceBetween">Space between</option>
            <option value="spaceEvenly">Space evenly</option>
            <option value="spaceAround">Space around</option>
          </select>
        </label>
        <label class="field">
          {{ n.type === 'row' ? 'Vertical alignment' : 'Horizontal alignment' }}
          <select :value="p.alignment ?? 'start'" @change="setProp('alignment', ($event.target as HTMLSelectElement).value)">
            <option value="start">{{ n.type === 'row' ? 'Top' : 'Start' }}</option>
            <option value="center">Center</option>
            <option value="end">{{ n.type === 'row' ? 'Bottom' : 'End' }}</option>
          </select>
        </label>
        <label class="field">
          Gap between (dp)
          <input type="number" min="0" step="4" :value="p.gap ?? 0" @input="setProp('gap', ($event.target as HTMLInputElement).value)" />
        </label>
        <label class="check">
          <input type="checkbox" :checked="p.scroll === 'true'" @change="setProp('scroll', ($event.target as HTMLInputElement).checked ? 'true' : undefined)" />
          Scrolls {{ n.type === 'row' ? 'sideways' : 'up and down' }} when its content overflows
        </label>
      </template>
      <template v-if="n.type === 'flow'">
        <label class="field">
          Lines start at
          <select :value="p.alignment ?? 'start'" @change="setProp('alignment', ($event.target as HTMLSelectElement).value)">
            <option value="start">Start</option>
            <option value="center">Center</option>
            <option value="end">End</option>
          </select>
        </label>
        <label class="field">
          Gap between (dp)
          <input type="number" min="0" step="4" :value="p.gap ?? 0" @input="setProp('gap', ($event.target as HTMLInputElement).value)" />
        </label>
        <p class="muted note">Places its components side by side, starting a new line when one does not fit.</p>
      </template>
      <div v-if="n.type === 'box'" class="field">
        <span class="label">Content alignment</span>
        <div class="align-grid">
          <button
            v-for="a in BOX_ALIGN"
            :key="a"
            :class="{ on: (p.alignment ?? 'top-start') === a }"
            :title="a"
            @click="setProp('alignment', a)"
          />
        </div>
      </div>
    </details>

    <details open>
      <summary>Style</summary>
      <div class="row two">
        <label class="field">Padding (dp)<input v-model.number="n.style.padding" type="number" min="0" step="2" /></label>
        <label class="field">Corner (dp)<input v-model.number="n.style.corner" type="number" min="0" step="2" /></label>
      </div>
      <div class="field">
        <span class="label">Background</span>
        <ColorSwatches v-model="n.style.background" :keys="BACKGROUND_SWATCHES" clear="∅" clear-title="None" />
      </div>
      <label class="field">
        Border (dp)
        <input
          type="number"
          min="0"
          step="1"
          :value="n.style.border ?? 0"
          @input="
            (() => {
              const v = Number(($event.target as HTMLInputElement).value)
              if (v > 0) n.style.border = v
              else delete n.style.border
            })()
          "
        />
      </label>
      <div v-if="n.style.border" class="field">
        <span class="label">Border colour</span>
        <ColorSwatches v-model="n.style.borderColor" :keys="INK_SWATCHES" />
      </div>
    </details>

    <details v-if="!container && n.type !== 'spacer'" open>
      <summary>Content</summary>
      <template v-if="n.type === 'text'">
        <label v-if="!n.bind" class="field">Text<textarea :value="p.text ?? ''" rows="2" @input="setProp('text', ($event.target as HTMLTextAreaElement).value)" /></label>
        <div class="row two">
          <label class="field">Size (sp)<input type="number" min="6" step="1" :value="p.fontSize ?? 16" @input="setProp('fontSize', ($event.target as HTMLInputElement).value)" /></label>
          <label class="field">
            Weight
            <select :value="p.weight ?? '400'" @change="setProp('weight', ($event.target as HTMLSelectElement).value)">
              <option value="300">Light</option>
              <option value="400">Regular</option>
              <option value="500">Medium</option>
              <option value="700">Bold</option>
            </select>
          </label>
        </div>
        <div class="row two">
          <label class="field">
            Align
            <select :value="p.align ?? 'start'" @change="setProp('align', ($event.target as HTMLSelectElement).value)">
              <option value="start">Start</option>
              <option value="center">Center</option>
              <option value="end">End</option>
            </select>
          </label>
          <label v-if="p.marquee !== 'true'" class="field">Max lines<input type="number" min="1" max="20" :value="p.lines ?? 1" @input="setProp('lines', ($event.target as HTMLInputElement).value)" /></label>
        </div>
        <label class="check">
          <input type="checkbox" :checked="p.marquee === 'true'" @change="setProp('marquee', ($event.target as HTMLInputElement).checked ? 'true' : undefined)" />
          Glides along when too long for one line
        </label>
      </template>
      <template v-if="n.type === 'icon'">
        <div class="field">
          <span class="label">Icon</span>
          <IconPicker :model-value="p.icon" placeholder="from the entity" @update:model-value="setProp('icon', $event)" />
        </div>
        <div v-if="n.bind" class="field">
          <span class="label">Icon while on</span>
          <IconPicker :model-value="p.iconOn" placeholder="same" @update:model-value="setProp('iconOn', $event)" />
        </div>
        <label class="field">Size (dp)<input type="number" min="8" step="4" :value="p.size ?? 32" @input="setProp('size', ($event.target as HTMLInputElement).value)" /></label>
      </template>
      <template v-if="n.type === 'button'">
        <label class="field">Label<input :value="p.text ?? ''" @input="setProp('text', ($event.target as HTMLInputElement).value)" /></label>
        <div class="field">
          <span class="label">Icon</span>
          <IconPicker :model-value="p.icon" placeholder="none" @update:model-value="setProp('icon', $event)" />
        </div>
        <div class="field">
          <span class="label">Button colour</span>
          <ColorSwatches
            :model-value="p.color"
            :keys="['key_on', 'accent', 'blue', 'danger', ...PASTEL_KEYS]"
            clear=""
            clear-title="key"
            :clear-color="colorOf('key')"
            @update:model-value="setProp('color', $event)"
          />
        </div>
      </template>
      <label v-if="n.type === 'slider'" class="field">Steps<input type="number" min="2" max="20" :value="p.steps ?? 10" @input="setProp('steps', ($event.target as HTMLInputElement).value)" /></label>
      <label v-if="n.type === 'image'" class="field">
        Fit
        <select :value="p.fit ?? 'crop'" @change="setProp('fit', ($event.target as HTMLSelectElement).value === 'whole' ? 'whole' : undefined)">
          <option value="crop">Fill and crop</option>
          <option value="whole">Show it whole</option>
        </select>
      </label>
      <template v-if="n.type === 'clock'">
        <label class="field">
          Shows
          <select :value="p.show ?? 'time'" @change="setProp('show', ($event.target as HTMLSelectElement).value)">
            <option value="time">The time</option>
            <option value="date">The date</option>
            <option value="both">The time over the date</option>
          </select>
        </label>
        <div class="row two">
          <label class="field">Size (sp)<input type="number" min="8" step="2" :value="p.fontSize ?? 44" @input="setProp('fontSize', ($event.target as HTMLInputElement).value)" /></label>
          <label class="field">
            Align
            <select :value="p.align ?? 'start'" @change="setProp('align', ($event.target as HTMLSelectElement).value)">
              <option value="start">Start</option>
              <option value="center">Center</option>
              <option value="end">End</option>
            </select>
          </label>
        </div>
      </template>
      <template v-if="n.type === 'badge'">
        <label class="field">Text<input :value="p.text ?? ''" :placeholder="n.bind ? 'the entity\'s state' : ''" @input="setProp('text', ($event.target as HTMLInputElement).value)" /></label>
        <div class="field">
          <span class="label">Icon</span>
          <IconPicker :model-value="p.icon" :placeholder="n.bind ? 'from the entity' : 'none'" @update:model-value="setProp('icon', $event)" />
        </div>
      </template>
      <template v-if="n.type === 'avatar'">
        <label class="field">Size (dp)<input type="number" min="16" step="4" :value="p.size ?? 56" @input="setProp('size', ($event.target as HTMLInputElement).value)" /></label>
        <label v-if="!n.bind" class="field">Name for the initials<input :value="p.text ?? ''" @input="setProp('text', ($event.target as HTMLInputElement).value)" /></label>
      </template>
      <div v-if="n.type === 'progress'" class="row two">
        <label class="field">Min<input type="number" :value="p.min ?? ''" placeholder="0" @input="setProp('min', ($event.target as HTMLInputElement).value)" /></label>
        <label class="field">Max<input type="number" :value="p.max ?? ''" placeholder="100" @input="setProp('max', ($event.target as HTMLInputElement).value)" /></label>
      </div>
      <label v-if="n.type === 'graph'" class="field">
        Over the last
        <select :value="p.hours ?? '24'" @change="setProp('hours', ($event.target as HTMLSelectElement).value)">
          <option value="1">Hour</option>
          <option value="6">6 hours</option>
          <option value="12">12 hours</option>
          <option value="24">Day</option>
          <option value="48">2 days</option>
          <option value="168">Week</option>
        </select>
      </label>
      <div v-if="n.type === 'switch'" class="field">
        <span class="label">Colour while on</span>
        <ColorSwatches :model-value="p.color" :keys="[...PASTEL_KEYS]" clear-title="The entity's" @update:model-value="setProp('color', $event)" />
      </div>
      <label v-if="n.type === 'stepper'" class="field">
        Step
        <input type="number" min="0" step="0.5" :value="p.step ?? ''" placeholder="from the entity" @input="setProp('step', ($event.target as HTMLInputElement).value)" />
      </label>
      <label v-if="n.type === 'choices'" class="field">
        Most shown
        <input type="number" min="2" max="8" :value="p.max ?? 4" @input="setProp('max', ($event.target as HTMLInputElement).value)" />
      </label>
      <label v-if="['gauge', 'analog', 'iconbutton', 'keys', 'swatches', 'keypad'].includes(n.type)" class="field">
        {{ n.type === 'keys' || n.type === 'keypad' ? 'Key size (dp)' : n.type === 'swatches' ? 'Swatch size (dp)' : 'Size (dp)' }}
        <input type="number" min="16" step="4" :value="p.size ?? SIZES[n.type]" @input="setProp('size', ($event.target as HTMLInputElement).value)" />
      </label>
      <template v-if="n.type === 'analog'">
        <label class="field">
          Numbers
          <select :value="p.numbers ?? ''" @change="setProp('numbers', ($event.target as HTMLSelectElement).value)">
            <option value="">None</option>
            <option value="quarters">12, 3, 6 and 9</option>
            <option value="all">All twelve</option>
          </select>
        </label>
        <label class="check">
          <input type="checkbox" :checked="p.seconds === 'true'" @change="setProp('seconds', ($event.target as HTMLInputElement).checked ? 'true' : undefined)" />
          Second hand
        </label>
      </template>
      <template v-if="n.type === 'gauge'">
        <label class="field">
          Caption under the ring
          <select
            :value="!p.caption ? '' : p.caption === '@name' ? '@name' : 'words'"
            @change="
              (() => {
                const v = ($event.target as HTMLSelectElement).value
                setProp('caption', v === 'words' ? (p.caption && p.caption !== '@name' ? p.caption : 'Caption') : v)
              })()
            "
          >
            <option value="">None</option>
            <option v-if="n.bind" value="@name">The entity's name</option>
            <option value="words">Words of my own</option>
          </select>
        </label>
        <input
          v-if="p.caption && p.caption !== '@name'"
          aria-label="Caption"
          :value="p.caption"
          @input="setProp('caption', ($event.target as HTMLInputElement).value || 'Caption')"
        />
      </template>
      <template v-if="n.type === 'iconbutton'">
        <div class="field">
          <span class="label">Icon</span>
          <IconPicker :model-value="p.icon" :placeholder="n.bind ? 'from the entity' : 'gesture-tap'" @update:model-value="setProp('icon', $event)" />
        </div>
        <div v-if="n.bind" class="field">
          <span class="label">Icon while on</span>
          <IconPicker :model-value="p.iconOn" placeholder="same" @update:model-value="setProp('iconOn', $event)" />
        </div>
        <div class="field">
          <span class="label">{{ n.bind ? 'Colour while on' : 'Colour' }}</span>
          <ColorSwatches :model-value="p.color" :keys="[...PASTEL_KEYS]" @update:model-value="setProp('color', $event)" />
        </div>
      </template>
      <label v-if="n.type === 'swatches'" class="field">
        Offers
        <select :value="p.show ?? 'colour'" @change="setProp('show', ($event.target as HTMLSelectElement).value === 'white' ? 'white' : undefined)">
          <option value="colour">Colours</option>
          <option value="white">Whites, warm to cool</option>
        </select>
      </label>
      <div v-if="COLOURED.includes(n.type)" class="field">
        <span class="label">{{ n.type === 'button' ? 'Label colour' : n.type === 'analog' ? 'Face colour' : 'Colour' }}</span>
        <ColorSwatches
          :model-value="n.type === 'button' ? p.ink : p.color"
          :keys="INK_SWATCHES"
          @update:model-value="setProp(n.type === 'button' ? 'ink' : 'color', $event)"
        />
      </div>

      <template v-if="n.type === 'tile' && tile">
        <label class="field">
          Shows
          <select
            :value="tile.entityId ? `e:${tile.entityId}` : `k:${tile.kind}`"
            @change="
              (() => {
                const v = ($event.target as HTMLSelectElement).value
                if (v.startsWith('e:')) Object.assign(tile, { entityId: v.slice(2), kind: undefined })
                else Object.assign(tile, { entityId: undefined, kind: v.slice(2) })
              })()
            "
          >
            <optgroup label="Other tiles">
              <option v-for="k in KINDS" :key="k" :value="`k:${k}`">{{ CATALOGUE[k]?.name ?? k }}</option>
            </optgroup>
            <optgroup label="Entities">
              <option v-for="e in tileEntities" :key="e.entityId" :value="`e:${e.entityId}`">{{ e.name }} ({{ e.entityId }})</option>
            </optgroup>
          </select>
        </label>
        <label class="field">
          Name
          <input :value="tile.name ?? ''" :placeholder="tileEntity?.name ?? ''" @input="tile.name = ($event.target as HTMLInputElement).value || undefined" />
        </label>
        <div class="field">
          <span class="label">Icon</span>
          <IconPicker v-model="tile.icon" :placeholder="iconOf({ ...tile, icon: undefined }, tileEntity)" />
        </div>
        <div class="field">
          <span class="label">Accent</span>
          <ColorSwatches v-model="tile.color" :keys="[...PASTEL_KEYS]" />
        </div>
      </template>
    </details>

    <details v-if="BINDABLE.includes(n.type)" open>
      <summary>Data</summary>
      <label class="field">
        Entity
        <input
          v-model="entityDraft"
          list="design-entities"
          placeholder="None: a fixed value"
          @change="bindTo(entityDraft)"
        />
        <datalist id="design-entities">
          <option v-for="e in entityOptions" :key="e.entityId" :value="e.entityId">{{ e.name }}</option>
        </datalist>
      </label>
      <template v-if="n.bind && n.type === 'text'">
        <label class="field">
          Show
          <select v-model="n.bind.format">
            <option v-for="f in FORMATS" :key="f.value" :value="f.value">{{ f.label }}</option>
          </select>
        </label>
        <label v-if="n.bind.format === 'attribute'" class="field">Attribute<input v-model="n.bind.attribute" placeholder="current_temperature" /></label>
        <div class="row two">
          <label class="field">Before<input v-model="n.bind.prefix" /></label>
          <label class="field">After<input v-model="n.bind.suffix" /></label>
        </div>
      </template>
      <p v-if="n.bind && n.type === 'icon'" class="muted note">Lights up in the entity's colour while it is on.</p>
      <p v-if="BIND_NOTES[n.type]" class="muted note">{{ BIND_NOTES[n.type] }}</p>
      <label v-if="n.bind" class="field">
        Shown
        <select :value="p.showWhen ?? ''" @change="setProp('showWhen', ($event.target as HTMLSelectElement).value)">
          <option v-for="s in SHOW_WHEN" :key="s.value" :value="s.value">{{ s.label }}</option>
        </select>
      </label>
      <label v-if="n.bind && p.showWhen" class="field">
        Comes and goes
        <select :value="p.animate ?? ''" @change="setProp('animate', ($event.target as HTMLSelectElement).value)">
          <option v-for="a in ANIMATIONS" :key="a.value" :value="a.value">{{ a.label }}</option>
        </select>
      </label>
    </details>

    <details v-if="!isRoot && n.type !== 'spacer' && !OWN_TAPS.includes(n.type)" open>
      <summary>Actions</summary>
      <label class="field">
        Tap
        <select :value="tapOf('tap') === 'default' && tile ? '' : tapOf('tap')" @change="setTap('tap', ($event.target as HTMLSelectElement).value as TapType)">
          <option value="">Default ({{ tapDefault }})</option>
          <option v-for="t in TAPS.filter((t) => t.type !== 'default')" :key="t.type" :value="t.type">{{ t.label }}</option>
        </select>
      </label>
      <template v-if="tapHost.tap?.type === 'action'">
        <label class="field">Action<input v-model="tapHost.tap.service" placeholder="light.turn_on" /></label>
        <label class="field">
          Data (JSON)
          <textarea v-model="dataText" rows="3" placeholder='{"brightness_pct": 50}' />
          <span v-if="dataError" class="error">{{ dataError }}</span>
        </label>
      </template>
      <label v-if="tapHost.tap?.type === 'page'" class="field">
        Page
        <select v-model="tapHost.tap.page">
          <option v-for="pg in screen.pages" :key="pg.id" :value="pg.id">{{ pg.title }}</option>
        </select>
      </label>
      <label class="field">
        Long press
        <select :value="tapOf('hold') === 'card' && tile ? '' : tapOf('hold')" @change="setTap('hold', ($event.target as HTMLSelectElement).value as TapType)">
          <option value="">Default ({{ tile ? 'open its card' : 'nothing' }})</option>
          <option value="card">Open its card</option>
          <option value="page">Go to a page</option>
          <option value="none">Nothing</option>
        </select>
      </label>
      <label v-if="tapHost.hold?.type === 'page'" class="field">
        Page
        <select v-model="tapHost.hold.page">
          <option v-for="pg in screen.pages" :key="pg.id" :value="pg.id">{{ pg.title }}</option>
        </select>
      </label>
    </details>
  </div>
</template>

<style scoped>
.attrs {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.id {
  margin: -8px 0 0;
  font-size: 12px;
  font-family: ui-monospace, monospace;
}

.tools {
  padding-bottom: 10px;
  border-bottom: 1px solid var(--line);
}

.wrap {
  flex-wrap: wrap;
}

details {
  display: flex;
  flex-direction: column;
  gap: 10px;
  border-bottom: 1px solid var(--line);
  padding-bottom: 12px;
}

details[open] > :not(summary) {
  margin-top: 10px;
}

summary {
  font-size: 13px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--muted);
  cursor: pointer;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12px;
  color: var(--muted);
}

.field :is(input, select, textarea) {
  color: var(--ink);
  font-size: 14px;
}

.label {
  font-size: 12px;
}

.check {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.two > .field {
  flex: 1;
  min-width: 0;
}

.num {
  width: 90px;
}

.align-grid {
  display: grid;
  grid-template-columns: repeat(3, 28px);
  gap: 4px;
}

.align-grid button {
  width: 28px;
  height: 28px;
  padding: 0;
  border-radius: 6px;
}

.align-grid button.on {
  background: var(--accent);
  border-color: var(--accent);
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

.link {
  align-self: center;
  border: none;
  background: none;
  color: var(--accent);
  padding: 4px;
}

.note {
  margin: 0;
  font-size: 12px;
}

.error {
  color: var(--danger);
}
</style>
