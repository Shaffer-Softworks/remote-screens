<script setup lang="ts">
// One component of a free-form page drawn with HTML, as near to the add-on's Remote Compose output as a browser
// gets (server/.../render/FreeForm.kt): rows and columns are flexboxes, a box stacks its children in one cell.
import { computed, inject, ref, type CSSProperties } from 'vue'
import { CATALOGUE } from '../generated/catalogue'
import { DESIGN } from '../model/design'
import { dragged } from '../model/drag'
import { isContainer, type Where } from '../model/nodes'
import { colorOf, pastel } from '../model/palette'
import { iconOf, nameOf, stateOf } from '../model/tiles'
import type { Node } from '../model/types'
import { domainOf } from '../model/types'
import { entityById } from '../store'
import MdiIcon from './MdiIcon.vue'

defineOptions({ name: 'NodeView' })

const props = defineProps<{ node: Node; axis: 'row' | 'column' | 'box' | 'flow'; isRoot?: boolean }>()
const ctx = inject(DESIGN)!
const el = ref<HTMLElement>()

const ON_STATES = ['on', 'open', 'unlocked', 'playing', 'home', 'cleaning', 'heat', 'cool', 'heat_cool', 'auto']
const JUSTIFY: Record<string, string> = {
  start: 'flex-start',
  center: 'center',
  end: 'flex-end',
  spaceBetween: 'space-between',
  spaceEvenly: 'space-evenly',
  spaceAround: 'space-around',
}
const ALIGN: Record<string, string> = { start: 'flex-start', top: 'flex-start', center: 'center', end: 'flex-end', bottom: 'flex-end' }

const n = computed(() => props.node)
const entity = computed(() => (n.value.bind ? entityById.value.get(n.value.bind.entityId) : undefined))
const on = computed(() => !!entity.value && ON_STATES.includes(entity.value.state))
const selected = computed(() => ctx.selectedId.value === n.value.id)
const hint = computed(() => (ctx.hint.value?.targetId === n.value.id ? ctx.hint.value.where : null))

function size(spec: string, dim: 'width' | 'height'): CSSProperties {
  const main = (props.axis === 'row' && dim === 'width') || (props.axis === 'column' && dim === 'height')
  const min = dim === 'width' ? 'minWidth' : 'minHeight'
  if (spec.startsWith('dp:')) return { [dim]: `${Number(spec.slice(3))}px`, flexShrink: 0 }
  if (spec === 'fill' || spec.startsWith('weight:')) {
    if (props.axis === 'box' || props.axis === 'flow' || props.isRoot) return { [dim]: '100%' }
    if (main) return { flex: `${spec === 'fill' ? 1 : Number(spec.slice(7))} 1 0`, [min]: 0 }
    return { alignSelf: 'stretch' }
  }
  return {}
}

const style = computed<CSSProperties>(() => {
  const s: CSSProperties = { ...size(n.value.size.width, 'width'), ...size(n.value.size.height, 'height') }
  if (n.value.style.padding) s.padding = `${n.value.style.padding}px`
  if (n.value.style.corner) s.borderRadius = `${n.value.style.corner}px`
  const bg = colorOf(n.value.style.background)
  if (bg) s.background = bg
  if (n.value.style.border) s.border = `${n.value.style.border}px solid ${colorOf(n.value.style.borderColor ?? 'divider') ?? '#4a4a4a'}`
  const p = n.value.props
  if (n.value.bind && p.showWhen && (p.showWhen === 'on') !== on.value) s.opacity = 0.35
  switch (n.value.type) {
    case 'column':
    case 'row':
      s.display = 'flex'
      s.flexDirection = n.value.type
      s.justifyContent = JUSTIFY[p.arrangement ?? 'start']
      s.alignItems = ALIGN[p.alignment ?? 'start']
      if (p.gap) s.gap = `${Number(p.gap)}px`
      if (p.scroll === 'true') s[n.value.type === 'row' ? 'overflowX' : 'overflowY'] = 'auto'
      break
    case 'flow':
      Object.assign(s, { display: 'flex', flexWrap: 'wrap', alignItems: 'flex-start', alignContent: 'flex-start' })
      s.justifyContent = ALIGN[p.alignment ?? 'start']
      if (p.gap) s.gap = `${Number(p.gap)}px`
      break
    case 'box': {
      const [v, h] = (p.alignment ?? 'top-start').split('-')
      s.display = 'grid'
      s.alignItems = ALIGN[v] ?? 'flex-start'
      s.justifyItems = ALIGN[h ?? v] ?? 'flex-start'
      break
    }
    case 'text': {
      const lines = p.marquee === 'true' ? 1 : Number(p.lines ?? 1)
      s.fontSize = `${Number(p.fontSize ?? 16)}px`
      s.fontWeight = Number(p.weight ?? 400)
      s.color = colorOf(p.color) ?? '#ffffff'
      s.textAlign = (p.align as CSSProperties['textAlign']) ?? 'start'
      if (lines === 1) Object.assign(s, { whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' })
      else Object.assign(s, { display: '-webkit-box', WebkitLineClamp: lines, WebkitBoxOrient: 'vertical', overflow: 'hidden' })
      break
    }
    case 'spacer':
      if (n.value.size.width === 'wrap' && n.value.size.height === 'wrap') Object.assign(s, { width: '8px', height: '8px' })
      break
    case 'divider':
      s.background = colorOf(p.color ?? 'divider')
      if (n.value.size.width === 'wrap' && n.value.size.height === 'wrap') {
        Object.assign(s, props.axis === 'row' ? { width: '1px', alignSelf: 'stretch' } : { height: '1px', alignSelf: 'stretch' })
      }
      break
    case 'button':
      if (n.value.size.height === 'wrap') s.height = '48px'
      if (!n.value.style.corner) s.borderRadius = `${48 / 2.6}px`
      if (!bg) s.background = colorOf(p.color ?? 'key')
      s.color = colorOf(p.ink) ?? '#ffffff'
      if (!n.value.style.padding) s.padding = '0 12px'
      break
    case 'slider':
      if (n.value.size.height === 'wrap') s.height = '28px'
      break
    case 'image':
    case 'tile':
      if (n.value.size.width === 'wrap') s.width = '200px'
      if (n.value.size.height === 'wrap') s.height = '160px'
      break
    case 'clock':
      s.color = colorOf(p.color) ?? '#ffffff'
      s.textAlign = (p.align as CSSProperties['textAlign']) ?? 'start'
      s.alignItems = ALIGN[p.align ?? 'start']
      break
    case 'badge': {
      const explicit = colorOf(p.color)
      if (n.value.size.height === 'wrap') s.height = '32px'
      if (!n.value.style.corner) s.borderRadius = '16px'
      if (!bg) s.background = explicit ?? (on.value ? accent.value : colorOf('key'))
      s.color = explicit || on.value ? '#2f2e2e' : '#ffffff'
      if (!n.value.style.padding) s.padding = '0 12px'
      break
    }
    case 'progress':
      if (n.value.size.width === 'wrap') s.width = '240px'
      if (n.value.size.height === 'wrap') s.height = '8px'
      break
    case 'graph':
      if (n.value.size.width === 'wrap') s.width = '280px'
      if (n.value.size.height === 'wrap') s.height = '120px'
      break
    case 'choices':
      if (n.value.size.width === 'wrap') s.width = '320px'
      if (n.value.size.height === 'wrap') s.height = '44px'
      break
    case 'iconbutton': {
      const px = `${Number(p.size ?? 64)}px`
      if (n.value.size.width === 'wrap' && n.value.size.height === 'wrap') Object.assign(s, { width: px, height: px })
      if (!n.value.style.corner) s.borderRadius = `${Number(p.size ?? 64) / 2}px`
      if (!bg) s.background = n.value.bind ? (on.value ? accent.value : colorOf('key')) : (colorOf(p.color) ?? colorOf('key'))
      s.color = n.value.bind && on.value ? '#2f2e2e' : '#ffffff'
      break
    }
  }
  return s
})

const gauge = computed(() => {
  const size = Number(n.value.props.size ?? 140)
  const stroke = size * 0.1
  const r = (size - stroke) / 2
  const arc = 2 * Math.PI * r * 0.75
  const value = entity.value ? (entity.value.unit ? `${entity.value.state} ${entity.value.unit}` : stateOf(entity.value) || entity.value.state) : ''
  return { size, stroke, r, arc, fill: (arc * progress.value) / 100, value }
})

/** The hands' angles in degrees, from the editor's clock. */
const hands = computed(() => {
  const h = now.getHours() % 12
  const m = now.getMinutes()
  return { hour: h * 30 + m * 0.5, minute: m * 6, second: now.getSeconds() * 6 }
})

/** The keys the add-on gives the bound entity's remote, as in FreeForm.kt keysOf. */
const keyIcons = computed(() => {
  switch (n.value.bind?.entityId.split('.')[0]) {
    case 'media_player': return ['skip-previous', 'play-pause', 'skip-next']
    case 'cover': return ['arrow-up', 'stop', 'arrow-down']
    case 'vacuum': return ['play', 'pause', 'home-import-outline', 'map-marker-outline']
    case 'lock': return ['lock', 'lock-open-variant']
    case 'alarm_control_panel': return ['shield-home', 'shield-lock', 'shield-off']
    case 'light': return ['lightbulb-off-outline', 'lightbulb-on']
    default: return []
  }
})

const HUES = [0, 30, 50, 120, 180, 220, 270, 320]
const KELVINS = [2200, 2700, 3500, 4500, 5500, 6500]
const swatchColours = computed(() =>
  n.value.props.show === 'white'
    ? KELVINS.map((k) => {
        const t = Math.max(0, Math.min(1, (k - 2200) / 4300))
        return `rgb(255 ${Math.round(190 + 60 * t)} ${Math.round(120 + 135 * t)})`
      })
    : HUES.map((h) => `hsl(${h} 100% 72.5%)`),
)
const PAD = ['1', '2', '3', '4', '5', '6', '7', '8', '9', 'clear', '0', 'back']

/** The entity's pastel, as the add-on lights a bound component. */
const accent = computed(() => pastel(n.value.props.color, n.value.bind?.entityId.split('.')[0]).color)

const now = new Date()
const clockTime = now.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })
const clockDate = now.toLocaleDateString([], { weekday: 'long', day: 'numeric', month: 'long' })

const initials = computed(() => {
  const name = entity.value?.name ?? n.value.props.text ?? '?'
  return name.split(' ').filter(Boolean).slice(0, 2).map((w) => w[0].toUpperCase()).join('') || '?'
})

/** How full a progress bar is, from 0 to 100: a number between its limits, or a lit entity half way. */
const progress = computed(() => {
  const e = entity.value
  if (!e) return n.value.bind ? 0 : 50
  const v = Number(e.state)
  if (Number.isFinite(v)) {
    const lo = Number(n.value.props.min ?? 0)
    const hi = Number(n.value.props.max ?? 100)
    return hi > lo ? Math.max(0, Math.min(100, ((v - lo) / (hi - lo)) * 100)) : 0
  }
  return on.value ? 60 : 0
})

const stepperValue = computed(() => {
  const e = entity.value
  if (!n.value.bind) return '–'
  if (!e) return `‹${n.value.bind.entityId}›`
  if (e.domain === 'climate') return 'Target'
  return e.unit ? `${e.state} ${e.unit}` : (stateOf(e) || e.state)
})

/** The add-on lists the entity's own options; the editor does not know them, so it shows stand-ins. */
const choiceWords = computed(() => {
  const d = n.value.bind?.entityId.split('.')[0]
  const max = Number(n.value.props.max ?? 4)
  const words = d === 'climate' ? ['Off', 'Heat', 'Cool', 'Auto'] : d === 'light' ? ['Effect 1', 'Effect 2', 'Effect 3'] : ['Option 1', 'Option 2', 'Option 3']
  return words.slice(0, max)
})
const chosenWord = computed(() => {
  const s = entity.value?.state.toLowerCase()
  return choiceWords.value.find((w) => w.toLowerCase() === s) ?? choiceWords.value[0]
})

const text = computed(() => {
  const b = n.value.bind
  if (!b) return n.value.props.text ?? ''
  const e = entity.value
  let v: string
  if (!e) v = `‹${b.entityId}›`
  else if (b.format === 'name') v = e.name
  else if (b.format === 'value') v = e.unit ? `${e.state} ${e.unit}` : e.state
  else if (b.format === 'attribute') v = `‹${b.attribute ?? 'attribute'}›`
  else v = stateOf(e) || e.state
  return b.prefix + v + b.suffix
})

const icon = computed(() => {
  const p = n.value.props
  if (on.value && p.iconOn) return p.iconOn
  if (p.icon) return p.icon
  const d = n.value.bind?.entityId.split('.')[0]
  return entity.value?.icon ?? (d && CATALOGUE[d]?.icon) ?? 'help-circle-outline'
})

const iconColor = computed(() => {
  const explicit = colorOf(n.value.props.color)
  if (explicit) return explicit
  if (!n.value.bind) return '#ffffff'
  return on.value ? pastel(undefined, n.value.bind.entityId.split('.')[0]).color : '#939393'
})

const sliderFill = computed(() => {
  const e = entity.value
  if (!e) return 50
  return on.value ? 60 : 0
})

const card = computed(() => {
  const t = n.value.tile
  if (!t) return undefined
  const e = t.entityId ? entityById.value.get(t.entityId) : undefined
  const lit = !!e && ON_STATES.includes(e.state)
  return { name: nameOf(t, e), icon: iconOf(t, e), state: stateOf(e), accent: lit ? pastel(t.color, domainOf(t)).color : undefined }
})

const empty = computed(() => isContainer(n.value) && n.value.children.length === 0)
const childAxis = computed(() => (n.value.type === 'row' || n.value.type === 'column' || n.value.type === 'flow' ? n.value.type : 'box'))

function where(e: DragEvent): Where {
  if (props.isRoot) return 'inside'
  const r = el.value!.getBoundingClientRect()
  const f = props.axis === 'row' || props.axis === 'flow' ? (e.clientX - r.left) / r.width : (e.clientY - r.top) / r.height
  if (isContainer(n.value)) return f < 0.2 ? 'before' : f > 0.8 ? 'after' : 'inside'
  return f < 0.5 ? 'before' : 'after'
}

function over(e: DragEvent): void {
  if (!dragged.value) return
  e.stopPropagation()
  let w = where(e)
  if (!ctx.canDrop(n.value.id, w)) {
    if (isContainer(n.value) && ctx.canDrop(n.value.id, 'inside')) w = 'inside'
    else return
  }
  e.preventDefault()
  ctx.hint.value = { targetId: n.value.id, where: w }
}

function drop(e: DragEvent): void {
  e.stopPropagation()
  const h = ctx.hint.value
  if (h?.targetId === n.value.id) ctx.drop(n.value.id, h.where)
}

function start(e: DragEvent): void {
  e.stopPropagation()
  e.dataTransfer?.setData('text/plain', n.value.id)
  const id = n.value.id
  setTimeout(() => {
    dragged.value = { type: 'node', id }
    ctx.select(id)
  })
}

function end(): void {
  dragged.value = null
  ctx.hint.value = null
}
</script>

<template>
  <div
    v-if="!node.hidden"
    ref="el"
    class="node"
    :class="[
      `t-${node.type}`,
      `in-${axis}`,
      { selected, empty, root: isRoot, [`drop-${hint}`]: hint, dragging: dragged?.type === 'node' && dragged.id === node.id },
    ]"
    :style="style"
    :draggable="!isRoot"
    :title="node.props.label ?? node.type"
    @click.stop="ctx.select(node.id)"
    @dragstart="start"
    @dragend="end"
    @dragover="over"
    @drop="drop"
  >
    <template v-if="isContainer(node)">
      <NodeView v-for="c in node.children" :key="c.id" :node="c" :axis="childAxis" />
      <span v-if="empty" class="placeholder">Drop components here</span>
    </template>
    <span v-else-if="node.type === 'text' && node.props.marquee === 'true'" class="marquee">{{ text }}</span>
    <template v-else-if="node.type === 'text'">{{ text }}</template>
    <MdiIcon
      v-else-if="node.type === 'icon'"
      :name="icon"
      :size="Number(node.props.size ?? 32)"
      :style="{ color: iconColor }"
    />
    <template v-else-if="node.type === 'button'">
      <MdiIcon v-if="node.props.icon" :name="node.props.icon" :size="node.props.text ? 20 : 24" />
      <span v-if="node.props.text">{{ node.props.text }}</span>
    </template>
    <div v-else-if="node.type === 'slider'" class="track">
      <i :style="{ width: `${sliderFill}%`, background: pastel(node.props.color, node.bind?.entityId.split('.')[0]).color }" />
      <span v-if="!node.bind" class="hint">Bind an entity</span>
    </div>
    <div v-else-if="node.type === 'image'" class="picture">
      <MdiIcon :name="node.bind?.entityId.startsWith('camera.') ? 'cctv' : 'image-outline'" :size="36" />
      <span>{{ node.bind ? (entity?.name ?? node.bind.entityId) : 'Bind a camera or person' }}</span>
    </div>
    <div v-else-if="node.type === 'tile' && card" class="card">
      <div class="head">{{ card.name }}</div>
      <i class="line" :style="{ background: card.accent }" />
      <div class="body">
        <MdiIcon :name="card.icon" :size="30" :style="{ color: card.accent ?? '#939393' }" />
        <span class="grow" />
        <span class="state">{{ card.state }}</span>
      </div>
    </div>
    <template v-else-if="node.type === 'clock'">
      <span v-if="node.props.show !== 'date'" class="time" :style="{ fontSize: `${Number(node.props.fontSize ?? 44)}px` }">{{ clockTime }}</span>
      <span
        v-if="node.props.show === 'date' || node.props.show === 'both'"
        :style="node.props.show === 'date' ? { fontSize: `${Number(node.props.fontSize ?? 44)}px` } : { fontSize: `${Math.max(Number(node.props.fontSize ?? 44) * 0.33, 14)}px`, color: '#cacaca' }"
      >{{ clockDate }}</span>
    </template>
    <template v-else-if="node.type === 'badge'">
      <MdiIcon v-if="node.props.icon || node.bind" :name="icon" :size="18" />
      <span>{{ node.bind && !node.props.text ? (entity ? stateOf(entity) || entity.state : `‹${node.bind.entityId}›`) : node.props.text }}</span>
    </template>
    <span
      v-else-if="node.type === 'avatar'"
      class="avatar"
      :style="{ width: `${Number(node.props.size ?? 56)}px`, height: `${Number(node.props.size ?? 56)}px`, fontSize: `${Number(node.props.size ?? 56) * 0.38}px` }"
    >{{ initials }}</span>
    <div v-else-if="node.type === 'progress'" class="track">
      <i :style="{ width: `${progress}%`, background: accent }" />
    </div>
    <div v-else-if="node.type === 'graph'" class="graph">
      <svg v-if="node.bind" viewBox="0 0 100 40" preserveAspectRatio="none">
        <polyline
          points="0,30 10,26 20,28 30,18 40,20 50,12 60,16 70,10 80,14 90,8 100,12"
          fill="none"
          :stroke="colorOf(node.props.color) ?? '#0986db'"
          stroke-width="2"
          vector-effect="non-scaling-stroke"
        />
      </svg>
      <span v-else class="hint">Bind a sensor</span>
    </div>
    <span v-else-if="node.type === 'switch'" class="switch" :class="{ on }" :style="on ? { background: accent } : undefined"><i /></span>
    <template v-else-if="node.type === 'stepper'">
      <span class="round"><MdiIcon name="minus" :size="22" /></span>
      <span class="value">{{ stepperValue }}</span>
      <span class="round"><MdiIcon name="plus" :size="22" /></span>
    </template>
    <template v-else-if="node.type === 'choices'">
      <template v-if="node.bind">
        <span v-for="w in choiceWords" :key="w" class="choice" :class="{ on: w === chosenWord }">{{ w }}</span>
      </template>
      <span v-else class="hint">Bind a select, thermostat or light</span>
    </template>
    <div v-else-if="node.type === 'gauge'" class="gauge" :style="{ width: `${gauge.size}px`, height: `${gauge.size}px` }">
      <svg :viewBox="`0 0 ${gauge.size} ${gauge.size}`">
        <g :transform="`rotate(135 ${gauge.size / 2} ${gauge.size / 2})`" fill="none" :stroke-width="gauge.stroke" stroke-linecap="round">
          <circle :cx="gauge.size / 2" :cy="gauge.size / 2" :r="gauge.r" stroke="#1b1b1b" :stroke-dasharray="`${gauge.arc} 9999`" />
          <circle
            v-if="node.bind && gauge.fill > 0"
            :cx="gauge.size / 2"
            :cy="gauge.size / 2"
            :r="gauge.r"
            :stroke="colorOf(node.props.color) ?? accent"
            :stroke-dasharray="`${gauge.fill} 9999`"
          />
        </g>
      </svg>
      <span class="value" :style="{ fontSize: `${Math.max(gauge.size * 0.16, 12)}px` }">{{ node.bind ? gauge.value : '' }}</span>
    </div>
    <svg
      v-else-if="node.type === 'analog'"
      viewBox="0 0 100 100"
      :width="Number(node.props.size ?? 160)"
      :height="Number(node.props.size ?? 160)"
      stroke-linecap="round"
    >
      <circle cx="50" cy="50" r="50" :fill="colorOf(node.props.color ?? 'card')" />
      <line v-for="i in 12" :key="i" x1="50" :y1="(i - 1) % 3 === 0 ? 13 : 9" x2="50" y2="5" stroke="#939393" stroke-width="2" :transform="`rotate(${(i - 1) * 30} 50 50)`" />
      <line x1="50" y1="50" x2="50" y2="25" stroke="#fff" stroke-width="5" :transform="`rotate(${hands.hour} 50 50)`" />
      <line x1="50" y1="50" x2="50" y2="12.5" stroke="#fff" stroke-width="3.5" :transform="`rotate(${hands.minute} 50 50)`" />
      <line v-if="node.props.seconds === 'true'" x1="50" y1="50" x2="50" y2="9" stroke="#14e1ba" stroke-width="1.2" :transform="`rotate(${hands.second} 50 50)`" />
      <circle cx="50" cy="50" r="4" fill="#fff" />
    </svg>
    <MdiIcon
      v-else-if="node.type === 'iconbutton'"
      :name="node.props.icon || (node.bind ? icon : 'gesture-tap')"
      :size="Number(node.props.size ?? 64) * 0.45"
    />
    <template v-else-if="node.type === 'keys'">
      <template v-if="keyIcons.length">
        <span
          v-for="k in keyIcons"
          :key="k"
          class="key"
          :class="{ round: node.size.width === 'wrap' }"
          :style="{ height: `${Number(node.props.size ?? 48)}px`, minWidth: `${Number(node.props.size ?? 48)}px` }"
        ><MdiIcon :name="k" :size="Number(node.props.size ?? 48) * 0.45" /></span>
      </template>
      <span v-else class="key-stub" :style="{ width: `${Number(node.props.size ?? 48) * 3 + 20}px`, height: `${Number(node.props.size ?? 48)}px` }">Bind a player, cover, vacuum, lock or alarm</span>
    </template>
    <template v-else-if="node.type === 'swatches'">
      <i
        v-for="c in swatchColours"
        :key="c"
        class="dot"
        :style="{ background: c, width: `${Number(node.props.size ?? 40)}px`, height: `${Number(node.props.size ?? 40)}px` }"
      />
    </template>
    <template v-else-if="node.type === 'keypad'">
      <div class="display">{{ node.bind ? '• • •' : '' }}</div>
      <div class="pad" :style="{ gridTemplateColumns: `repeat(3, ${Number(node.props.size ?? 56) * 1.5}px)` }">
        <span v-for="k in PAD" :key="k" class="key" :style="{ height: `${Number(node.props.size ?? 56)}px` }">
          <MdiIcon v-if="k === 'clear' || k === 'back'" :name="k === 'clear' ? 'close' : 'backspace-outline'" :size="24" />
          <template v-else>{{ k }}</template>
        </span>
      </div>
    </template>
  </div>
</template>

<style scoped>
.node {
  position: relative;
  box-sizing: border-box;
  min-width: 0;
  cursor: default;
}

.node:not(.root):hover {
  outline: 1px dashed rgb(20 225 186 / 60%);
  outline-offset: -1px;
}

.node.selected {
  outline: 2px solid #14e1ba !important;
  outline-offset: -2px;
  z-index: 1;
}

.node.dragging {
  opacity: 0.4;
}

.in-box {
  grid-area: 1 / 1;
}

.empty:not(.root) {
  min-width: 60px;
  min-height: 44px;
  border: 1px dashed rgb(255 255 255 / 25%);
}

.placeholder {
  color: rgb(255 255 255 / 45%);
  font-size: 12px;
  padding: 8px;
  pointer-events: none;
}

.drop-inside {
  outline: 3px solid var(--accent) !important;
  outline-offset: -3px;
  background-color: rgb(61 127 214 / 15%);
}

.drop-before::before,
.drop-after::after {
  content: '';
  position: absolute;
  background: var(--accent);
  border-radius: 2px;
  z-index: 2;
  pointer-events: none;
}

.in-column.drop-before::before,
.in-box.drop-before::before {
  left: 0;
  right: 0;
  top: -3px;
  height: 4px;
}

.in-column.drop-after::after,
.in-box.drop-after::after {
  left: 0;
  right: 0;
  bottom: -3px;
  height: 4px;
}

.in-row.drop-before::before,
.in-flow.drop-before::before {
  top: 0;
  bottom: 0;
  left: -3px;
  width: 4px;
}

.in-row.drop-after::after,
.in-flow.drop-after::after {
  top: 0;
  bottom: 0;
  right: -3px;
  width: 4px;
}

.t-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 500;
}

.t-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.t-spacer:hover {
  background: rgb(255 255 255 / 6%);
}

.t-slider {
  width: 240px;
}

.t-clock {
  display: flex;
  flex-direction: column;
}

.t-clock .time {
  font-weight: 300;
  line-height: 1.2;
  white-space: nowrap;
}

.t-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
}

.t-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #5c5c5c;
  font-weight: 600;
}

.graph {
  position: relative;
  width: 100%;
  height: 100%;
  border-radius: inherit;
  background: #1b1b1b;
}

.graph svg {
  width: 100%;
  height: 100%;
  display: block;
}

.graph .hint,
.t-choices .hint {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: rgb(255 255 255 / 50%);
}

.t-switch {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.switch {
  position: relative;
  width: 52px;
  height: 30px;
  border-radius: 15px;
  background: #5c5c5c;
  flex: none;
}

.switch i {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #fff;
}

.switch.on i {
  left: 25px;
}

.t-stepper {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.t-stepper .round {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: #5c5c5c;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}

.t-stepper .value {
  flex: 1;
  min-width: 112px;
  text-align: center;
  font-size: 22px;
  font-weight: 500;
}

.t-choices {
  display: flex;
  gap: 8px;
  position: relative;
}

.choice {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 17px;
  background: #5c5c5c;
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
}

.choice.on {
  background: #14e1ba;
  color: #2f2e2e;
}

.track {
  position: relative;
  width: 100%;
  height: 100%;
  border-radius: 999px;
  background: #1b1b1b;
  overflow: hidden;
}

.track i {
  position: absolute;
  inset: 0 auto 0 0;
}

.track .hint {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: rgb(255 255 255 / 50%);
}

.picture {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  background: #1b1b1b;
  color: rgb(255 255 255 / 60%);
  font-size: 12px;
  border-radius: inherit;
}

.card {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  border: 2px solid #000;
  border-radius: 10px;
  background: #333;
  overflow: hidden;
}

.card .head {
  background: #494949;
  padding: 0 14px;
  height: 36px;
  line-height: 36px;
  font-size: 15px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: none;
}

.card .line {
  height: 3px;
  background: #494949;
  flex: none;
}

.card .body {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 12px 14px;
  min-height: 0;
}

.t-text:has(.marquee) {
  text-overflow: clip !important;
  mask-image: linear-gradient(to right, #000 85%, transparent);
}

.t-gauge,
.t-analog,
.t-iconbutton {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.gauge {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.gauge svg {
  position: absolute;
  inset: 0;
}

.gauge .value {
  position: relative;
  font-weight: 500;
}

.t-keys {
  display: flex;
  align-items: center;
  gap: 10px;
}

.t-keys .key {
  flex: 1;
}

.t-keys .key.round {
  flex: none;
  border-radius: 50%;
}

.key {
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  background: #5c5c5c;
  font-size: 22px;
  font-weight: 500;
}

.key-stub {
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: #1b1b1b;
  font-size: 11px;
  color: rgb(255 255 255 / 50%);
  text-align: center;
}

.t-swatches {
  display: flex;
  align-items: center;
  gap: 10px;
}

.dot {
  border-radius: 50%;
  flex: none;
}

.t-keypad {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.t-keypad .display {
  height: 56px;
  display: flex;
  align-items: center;
  font-size: 28px;
}

.pad {
  display: grid;
  column-gap: 12px;
  row-gap: 10px;
}

.card .state {
  font-size: 18px;
  font-weight: 500;
  color: #cacaca;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
