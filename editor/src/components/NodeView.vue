<script setup lang="ts">
// One component of a free-form page drawn with HTML, as near to the add-on's Remote Compose output as a browser
// gets (server/.../render/FreeForm.kt): rows and columns are flexboxes, a box stacks its children in one cell.
import QRCode from 'qrcode'
import { computed, inject, ref, watchEffect, type CSSProperties } from 'vue'
import { CATALOGUE } from '../generated/catalogue'
import { DESIGN } from '../model/design'
import { dragged } from '../model/drag'
import { isContainer, paddingOf, type Where } from '../model/nodes'
import { colorOf, pastel } from '../model/palette'
import { iconOf, nameOf, stateOf } from '../model/tiles'
import type { EntityInfo, Node } from '../model/types'
import { domainOf } from '../model/types'
import { entityById, state } from '../store'
import MdiIcon from './MdiIcon.vue'

defineOptions({ name: 'NodeView' })

const props = defineProps<{
  node: Node
  axis: 'row' | 'column' | 'box' | 'flow'
  isRoot?: boolean
}>()
const ctx = inject(DESIGN)!
const el = ref<HTMLElement>()

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
const JUSTIFY: Record<string, string> = {
  start: 'flex-start',
  center: 'center',
  end: 'flex-end',
  spaceBetween: 'space-between',
  spaceEvenly: 'space-evenly',
  spaceAround: 'space-around',
}
const ALIGN: Record<string, string> = {
  start: 'flex-start',
  top: 'flex-start',
  center: 'center',
  end: 'flex-end',
  bottom: 'flex-end',
}

const viewNode = computed(() => props.node)
const entity = computed(() =>
  viewNode.value.bind ? entityById.value.get(viewNode.value.bind.entityId) : undefined,
)
const on = computed(() => !!entity.value && ON_STATES.includes(entity.value.state))
const selected = computed(() => ctx.selectedId.value === viewNode.value.id)
const hint = computed(() =>
  ctx.hint.value?.targetId === viewNode.value.id ? ctx.hint.value.where : null,
)

function size(spec: string, dim: 'width' | 'height'): CSSProperties {
  const main =
    (props.axis === 'row' && dim === 'width') || (props.axis === 'column' && dim === 'height')
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
  const css: CSSProperties = {
    ...size(viewNode.value.size.width, 'width'),
    ...size(viewNode.value.size.height, 'height'),
  }
  const place = viewNode.value.props.place
  if (place && !props.isRoot && props.axis !== 'flow' && !css.alignSelf) {
    if (props.axis === 'box') {
      const [vertical, horizontal] = place.split('-')
      Object.assign(css, {
        alignSelf: ALIGN[vertical] ?? 'flex-start',
        justifySelf: ALIGN[horizontal ?? vertical] ?? 'flex-start',
      })
    } else {
      css.alignSelf = ALIGN[place] ?? 'flex-start'
    }
  }
  const [ps, pt, pe, pb] = paddingOf(viewNode.value)
  const padded = ps > 0 || pt > 0 || pe > 0 || pb > 0
  if (padded) css.padding = `${pt}px ${pe}px ${pb}px ${ps}px`
  if (viewNode.value.style.corner) css.borderRadius = `${viewNode.value.style.corner}px`
  const bg = colorOf(viewNode.value.style.background)
  if (bg) css.background = bg
  if (viewNode.value.style.border)
    css.border = `${viewNode.value.style.border}px solid ${colorOf(viewNode.value.style.borderColor ?? 'divider') ?? colorOf('divider')}`
  const nodeProps = viewNode.value.props
  if (viewNode.value.bind && nodeProps.showWhen && (nodeProps.showWhen === 'on') !== on.value)
    css.opacity = 0.35
  switch (viewNode.value.type) {
    case 'column':
    case 'row':
      css.display = 'flex'
      css.flexDirection = viewNode.value.type
      css.justifyContent = JUSTIFY[nodeProps.arrangement ?? 'start']
      css.alignItems = ALIGN[nodeProps.alignment ?? 'start']
      if (nodeProps.gap) css.gap = `${Number(nodeProps.gap)}px`
      if (nodeProps.scroll === 'true')
        css[viewNode.value.type === 'row' ? 'overflowX' : 'overflowY'] = 'auto'
      break
    case 'flow':
      Object.assign(css, {
        display: 'flex',
        flexWrap: 'wrap',
        alignItems: 'flex-start',
        alignContent: 'flex-start',
      })
      css.justifyContent = ALIGN[nodeProps.alignment ?? 'start']
      if (nodeProps.gap) css.gap = `${Number(nodeProps.gap)}px`
      break
    case 'box': {
      const [vertical, horizontal] = (nodeProps.alignment ?? 'top-start').split('-')
      css.display = 'grid'
      css.alignItems = ALIGN[vertical] ?? 'flex-start'
      css.justifyItems = ALIGN[horizontal ?? vertical] ?? 'flex-start'
      break
    }
    case 'text': {
      const lines = nodeProps.marquee === 'true' ? 1 : Number(nodeProps.lines ?? 1)
      css.fontSize = `${Number(nodeProps.fontSize ?? 16)}px`
      css.fontWeight = Number(nodeProps.weight ?? 400)
      css.color = colorOf(nodeProps.color) ?? colorOf('ink')
      css.textAlign = (nodeProps.align as CSSProperties['textAlign']) ?? 'start'
      if (lines === 1)
        Object.assign(css, { whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' })
      else
        Object.assign(css, {
          display: '-webkit-box',
          WebkitLineClamp: lines,
          WebkitBoxOrient: 'vertical',
          overflow: 'hidden',
        })
      break
    }
    case 'spacer':
      if (viewNode.value.size.width === 'wrap' && viewNode.value.size.height === 'wrap')
        Object.assign(css, { width: '8px', height: '8px' })
      break
    case 'divider':
      css.background = colorOf(nodeProps.color ?? 'divider')
      if (viewNode.value.size.width === 'wrap' && viewNode.value.size.height === 'wrap') {
        Object.assign(
          css,
          props.axis === 'row'
            ? { width: '1px', alignSelf: 'stretch' }
            : { height: '1px', alignSelf: 'stretch' },
        )
      }
      break
    case 'button':
      if (viewNode.value.size.height === 'wrap') css.height = '48px'
      if (!viewNode.value.style.corner) css.borderRadius = `${48 / 2.6}px`
      if (!bg) css.background = colorOf(nodeProps.color ?? 'key')
      css.color = colorOf(nodeProps.ink) ?? colorOf('ink')
      if (!padded) css.padding = '0 12px'
      break
    case 'slider':
      if (viewNode.value.size.height === 'wrap') css.height = '28px'
      break
    case 'image':
    case 'tile':
      if (viewNode.value.size.width === 'wrap') css.width = '200px'
      if (viewNode.value.size.height === 'wrap') css.height = '160px'
      break
    case 'clock':
      css.color = colorOf(nodeProps.color) ?? colorOf('ink')
      css.textAlign = (nodeProps.align as CSSProperties['textAlign']) ?? 'start'
      css.alignItems = ALIGN[nodeProps.align ?? 'start']
      break
    case 'badge': {
      const explicit = colorOf(nodeProps.color)
      if (viewNode.value.size.height === 'wrap') css.height = '32px'
      if (!viewNode.value.style.corner) css.borderRadius = '16px'
      if (!bg) css.background = explicit ?? (on.value ? accent.value : colorOf('key'))
      css.color = explicit || on.value ? colorOf('key_on_ink') : colorOf('ink')
      if (!padded) css.padding = '0 12px'
      break
    }
    case 'progress':
      if (viewNode.value.size.width === 'wrap') css.width = '240px'
      if (viewNode.value.size.height === 'wrap') css.height = '8px'
      break
    case 'graph':
      if (viewNode.value.size.width === 'wrap') css.width = '280px'
      if (viewNode.value.size.height === 'wrap') css.height = '120px'
      break
    case 'choices':
      if (viewNode.value.size.width === 'wrap') css.width = '320px'
      if (viewNode.value.size.height === 'wrap') css.height = '44px'
      break
    case 'gauge': {
      const gaugeSize = `${Number(nodeProps.size ?? 140)}px`
      if (viewNode.value.size.width === 'wrap' && viewNode.value.size.height === 'wrap')
        Object.assign(css, { width: gaugeSize, height: gaugeSize })
      break
    }
    case 'analog': {
      const analogSize = `${Number(nodeProps.size ?? 160)}px`
      if (viewNode.value.size.width === 'wrap' && viewNode.value.size.height === 'wrap')
        Object.assign(css, { width: analogSize, height: analogSize })
      break
    }
    case 'keys': {
      const key = Number(nodeProps.size ?? 48)
      if (viewNode.value.size.width === 'wrap') css.width = `${key * 3 + 20}px`
      if (viewNode.value.size.height === 'wrap') css.height = `${key}px`
      break
    }
    case 'swatches': {
      const swatch = Number(nodeProps.size ?? 40)
      const count = nodeProps.show === 'white' ? 6 : 8
      if (viewNode.value.size.width === 'wrap') css.width = `${swatch * count + 10 * (count - 1)}px`
      if (viewNode.value.size.height === 'wrap') css.height = `${swatch}px`
      break
    }
    case 'keypad': {
      const key = Number(nodeProps.size ?? 56)
      if (viewNode.value.size.width === 'wrap') css.width = `${key * 1.5 * 3 + 24}px`
      if (viewNode.value.size.height === 'wrap') css.height = `${56 + 3 * (key + 10)}px`
      break
    }
    case 'iconbutton': {
      const px = `${Number(nodeProps.size ?? 64)}px`
      if (viewNode.value.size.width === 'wrap' && viewNode.value.size.height === 'wrap')
        Object.assign(css, { width: px, height: px })
      if (!viewNode.value.style.corner) css.borderRadius = `${Number(nodeProps.size ?? 64) / 2}px`
      if (!bg)
        css.background = viewNode.value.bind
          ? on.value
            ? accent.value
            : colorOf('key')
          : (colorOf(nodeProps.color) ?? colorOf('key'))
      css.color = viewNode.value.bind && on.value ? colorOf('key_on_ink') : colorOf('ink')
      break
    }
    case 'dial': {
      const dial = Number(nodeProps.size ?? 160)
      if (viewNode.value.size.width === 'wrap') css.width = `${dial}px`
      if (viewNode.value.size.height === 'wrap') css.height = `${dial + 54}px`
      break
    }
    case 'countdown':
      css.color = colorOf(nodeProps.color) ?? colorOf('ink')
      css.fontSize = `${Number(nodeProps.fontSize ?? 44)}px`
      css.fontWeight = 300
      break
    case 'qr': {
      const qr = Number(nodeProps.size ?? 160)
      if (viewNode.value.size.width === 'wrap') css.width = `${qr}px`
      if (viewNode.value.size.height === 'wrap') css.height = `${qr}px`
      if (!bg) css.background = colorOf('ink')
      if (!viewNode.value.style.corner) css.borderRadius = '12px'
      break
    }
    case 'markdown':
      css.color = colorOf(nodeProps.color) ?? colorOf('ink')
      css.fontSize = `${Number(nodeProps.fontSize ?? 16)}px`
      break
    case 'tabs':
      if (viewNode.value.size.width === 'wrap') css.width = '320px'
      if (viewNode.value.size.height === 'wrap') css.height = '160px'
      break
    case 'timerkeys':
      if (viewNode.value.size.width === 'wrap') css.width = '320px'
      if (viewNode.value.size.height === 'wrap') css.height = '56px'
      break
    case 'todolist': {
      const rows = Number(nodeProps.max ?? 8)
      if (viewNode.value.size.width === 'wrap') css.width = '280px'
      if (viewNode.value.size.height === 'wrap') css.height = `${Math.max(rows, 1) * 50}px`
      break
    }
    case 'events': {
      const rows = Number(nodeProps.max ?? 3)
      if (viewNode.value.size.width === 'wrap') css.width = '280px'
      if (viewNode.value.size.height === 'wrap') css.height = `${Math.max(rows, 1) * 64}px`
      break
    }
    case 'mediabar':
      if (viewNode.value.size.width === 'wrap') css.width = '360px'
      if (viewNode.value.size.height === 'wrap') css.height = '72px'
      break
    case 'forecast': {
      const days = Number(nodeProps.days ?? 5)
      if (viewNode.value.size.width === 'wrap') css.width = `${days * 56}px`
      if (viewNode.value.size.height === 'wrap') css.height = '72px'
      css.color = colorOf(nodeProps.color) ?? colorOf('ink')
      break
    }
    case 'presence':
      if (viewNode.value.size.width === 'wrap') css.width = '320px'
      if (viewNode.value.size.height === 'wrap') css.height = '72px'
      break
    case 'led': {
      const led = `${Number(nodeProps.size ?? 16)}px`
      if (viewNode.value.size.width === 'wrap' && viewNode.value.size.height === 'wrap')
        Object.assign(css, { width: led, height: led })
      if (!viewNode.value.style.corner) css.borderRadius = '50%'
      if (!bg) css.background = on.value ? (colorOf(nodeProps.color) ?? accent.value) : colorOf('ink_dim')
      break
    }
  }
  return css
})

const gauge = computed(() => {
  const size = Number(viewNode.value.props.size ?? 140)
  const stroke = size * 0.1
  const radius = (size - stroke) / 2
  const arc = 2 * Math.PI * radius * 0.75
  const value = entity.value
    ? entity.value.unit
      ? `${entity.value.state} ${entity.value.unit}`
      : stateOf(entity.value) || entity.value.state
    : ''
  const captionProp = viewNode.value.props.caption
  const caption = !captionProp
    ? ''
    : captionProp === '@name'
      ? (entity.value?.name ?? viewNode.value.bind?.entityId ?? '')
      : captionProp
  return { size, stroke, radius, arc, fill: (arc * progress.value) / 100, value, caption }
})

const clockNumbers = computed(() =>
  viewNode.value.props.numbers === 'all'
    ? [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]
    : viewNode.value.props.numbers === 'quarters'
      ? [3, 6, 9, 12]
      : [],
)

/** The hands' angles in degrees, from the editor's clock. */
const hands = computed(() => {
  const hours = now.getHours() % 12
  const minutes = now.getMinutes()
  return { hour: hours * 30 + minutes * 0.5, minute: minutes * 6, second: now.getSeconds() * 6 }
})

/** The keys the add-on gives the bound entity's remote, as in FreeForm.kt keysOf. */
const keyIcons = computed(() => {
  switch (viewNode.value.bind?.entityId.split('.')[0]) {
    case 'media_player':
      return ['skip-previous', 'play-pause', 'skip-next']
    case 'cover':
      return ['arrow-up', 'stop', 'arrow-down']
    case 'vacuum':
      return ['play', 'pause', 'home-import-outline', 'map-marker-outline']
    case 'lock':
      return ['lock', 'lock-open-variant']
    case 'alarm_control_panel':
      return ['shield-home', 'shield-lock', 'shield-off']
    case 'light':
      return ['lightbulb-off-outline', 'lightbulb-on']
    default:
      return []
  }
})

const HUES = [0, 30, 50, 120, 180, 220, 270, 320]
const KELVINS = [2200, 2700, 3500, 4500, 5500, 6500]
const swatchColours = computed(() =>
  viewNode.value.props.show === 'white'
    ? KELVINS.map(kelvin => {
        const warmth = Math.max(0, Math.min(1, (kelvin - 2200) / 4300))
        return `rgb(255 ${Math.round(190 + 60 * warmth)} ${Math.round(120 + 135 * warmth)})`
      })
    : HUES.map(hue => `hsl(${hue} 100% 72.5%)`),
)
const PAD = ['1', '2', '3', '4', '5', '6', '7', '8', '9', 'clear', '0', 'back']

/** The entity's pastel, as the add-on lights a bound component. */
const accent = computed(
  () => pastel(viewNode.value.props.color, viewNode.value.bind?.entityId.split('.')[0]).color,
)

const now = new Date()
const clockTime = now.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })
const clockDate = now.toLocaleDateString([], { weekday: 'long', day: 'numeric', month: 'long' })

const initials = computed(() => {
  const name = entity.value?.name ?? viewNode.value.props.text ?? '?'
  return (
    name
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map(w => w[0].toUpperCase())
      .join('') || '?'
  )
})

/** How full a progress bar is, from 0 to 100: a number between its limits, or a lit entity half way. */
const progress = computed(() => {
  const ent = entity.value
  if (!ent) return viewNode.value.bind ? 0 : 50
  const numeric = Number(ent.state)
  if (Number.isFinite(numeric)) {
    const lo = Number(viewNode.value.props.min ?? 0)
    const hi = Number(viewNode.value.props.max ?? 100)
    return hi > lo ? Math.max(0, Math.min(100, ((numeric - lo) / (hi - lo)) * 100)) : 0
  }
  return on.value ? 60 : 0
})

const stepperValue = computed(() => {
  const e = entity.value
  if (!viewNode.value.bind) return '–'
  if (!e) return `‹${viewNode.value.bind.entityId}›`
  if (e.domain === 'climate') return 'Target'
  return e.unit ? `${e.state} ${e.unit}` : stateOf(e) || e.state
})

/** The add-on lists the entity's own options; the editor does not know them, so it shows stand-ins. */
const choiceWords = computed(() => {
  const domain = viewNode.value.bind?.entityId.split('.')[0]
  const max = Number(viewNode.value.props.max ?? 4)
  const words =
    domain === 'climate'
      ? ['Off', 'Heat', 'Cool', 'Auto']
      : domain === 'light'
        ? ['Effect 1', 'Effect 2', 'Effect 3']
        : ['Option 1', 'Option 2', 'Option 3']
  return words.slice(0, max)
})
const chosenWord = computed(() => {
  const stateLower = entity.value?.state.toLowerCase()
  return choiceWords.value.find(word => word.toLowerCase() === stateLower) ?? choiceWords.value[0]
})

const text = computed(() => {
  const bind = viewNode.value.bind
  if (!bind) return viewNode.value.props.text ?? ''
  const ent = entity.value
  let shown: string
  if (!ent) shown = `‹${bind.entityId}›`
  else if (bind.format === 'name') shown = ent.name
  else if (bind.format === 'value') shown = ent.unit ? `${ent.state} ${ent.unit}` : ent.state
  else if (bind.format === 'attribute') shown = `‹${bind.attribute ?? 'attribute'}›`
  else shown = stateOf(ent) || ent.state
  return bind.prefix + shown + bind.suffix
})

const icon = computed(() => {
  const nodeProps = viewNode.value.props
  if (on.value && nodeProps.iconOn) return nodeProps.iconOn
  if (nodeProps.icon) return nodeProps.icon
  const domain = viewNode.value.bind?.entityId.split('.')[0]
  return entity.value?.icon ?? (domain && CATALOGUE[domain]?.icon) ?? 'help-circle-outline'
})

const iconColor = computed(() => {
  const explicit = colorOf(viewNode.value.props.color)
  if (explicit) return explicit
  if (!viewNode.value.bind) return colorOf('ink')
  return on.value
    ? pastel(undefined, viewNode.value.bind.entityId.split('.')[0]).color
    : colorOf('ink_dim')
})

const sliderFill = computed(() => {
  const e = entity.value
  if (!e) return 50
  return on.value ? 60 : 0
})

const card = computed(() => {
  const tile = viewNode.value.tile
  if (!tile) return undefined
  const ent = tile.entityId ? entityById.value.get(tile.entityId) : undefined
  const lit = !!ent && ON_STATES.includes(ent.state)
  return {
    name: nameOf(tile, ent),
    icon: iconOf(tile, ent),
    state: stateOf(ent),
    accent: lit ? pastel(tile.color, domainOf(tile)).color : undefined,
  }
})

const empty = computed(() => isContainer(viewNode.value) && viewNode.value.children.length === 0)
const childAxis = computed(() =>
  viewNode.value.type === 'row' ||
  viewNode.value.type === 'column' ||
  viewNode.value.type === 'flow'
    ? viewNode.value.type
    : 'box',
)

/** Entity ids from a comma-separated prop, else the bound entity, else every entity of [domain]. */
function listedEntities(prop: string, domain: string): EntityInfo[] {
  const fromProp = viewNode.value.props[prop]
    ?.split(',')
    .map(id => id.trim())
    .filter(Boolean)
  if (fromProp?.length)
    return fromProp.map(
      entityId =>
        entityById.value.get(entityId) ?? {
          entityId,
          name: entityId.split('.')[1] ?? entityId,
          state: 'unknown',
          domain,
        },
    )
  const bound = viewNode.value.bind?.entityId
  if (bound?.startsWith(`${domain}.`)) {
    const entity = entityById.value.get(bound)
    return entity
      ? [entity]
      : [{ entityId: bound, name: bound.split('.')[1] ?? bound, state: 'unknown', domain }]
  }
  return state.entities.filter(entity => entity.domain === domain)
}

const presencePeople = computed(() => listedEntities('people', 'person'))
const eventCalendars = computed(() => listedEntities('calendars', 'calendar'))

const countdownText = computed(() => {
  if (!viewNode.value.bind) return viewNode.value.props.text ?? '0:00'
  const ent = entity.value
  if (!ent) return `‹${viewNode.value.bind.entityId}›`
  return ent.unit ? `${ent.state} ${ent.unit}` : stateOf(ent) || ent.state
})

const qrPayload = computed(() => {
  if (viewNode.value.bind)
    return entity.value?.state || viewNode.value.bind.entityId
  return viewNode.value.props.text || 'https://www.home-assistant.io'
})
const qrDataUrl = ref('')
watchEffect(() => {
  const payload = qrPayload.value
  const size = Math.max(64, Number(viewNode.value.props.size ?? 160) - 16)
  void QRCode.toDataURL(payload, { errorCorrectionLevel: 'M', margin: 1, width: size }).then(
    url => (qrDataUrl.value = url),
    () => (qrDataUrl.value = ''),
  )
})

const markdownLines = computed(() =>
  (viewNode.value.props.text ?? '')
    .replace(/\r\n/g, '\n')
    .replace(/\r/g, '\n')
    .split('\n')
    .map(raw => {
      if (raw.startsWith('**') && raw.endsWith('**') && raw.length >= 4)
        return { text: raw.slice(2, -2), bold: true, italic: false }
      if (raw.startsWith('*') && raw.endsWith('*') && raw.length >= 2 && !raw.startsWith('**'))
        return { text: raw.slice(1, -1), bold: false, italic: true }
      return { text: raw, bold: false, italic: false }
    }),
)

const tabLabels = computed(() => {
  const fromProp = viewNode.value.props.labels
    ?.split(',')
    .map(label => label.trim())
    .filter(Boolean)
  const children = viewNode.value.children.filter(child => !child.hidden)
  if (fromProp?.length) return fromProp.slice(0, Math.max(children.length, fromProp.length))
  return children.map(
    (child, index) => child.props.label ?? child.props.text ?? `Tab ${index + 1}`,
  )
})
const tabIndex = ref(0)
const tabChild = computed(() => {
  const children = viewNode.value.children.filter(child => !child.hidden)
  if (!children.length) return undefined
  return children[Math.min(tabIndex.value, children.length - 1)]
})

const FORECAST_DAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
const forecastDays = computed(() => {
  const count = Number(viewNode.value.props.days ?? 5)
  return FORECAST_DAYS.slice(0, Math.max(1, Math.min(7, count))).map((day, index) => ({
    day,
    high: 19 - index,
    low: 10 - Math.floor(index / 2),
  }))
})

const todoItems = computed(() => {
  const max = Number(viewNode.value.props.max ?? 8)
  if (!viewNode.value.bind) return []
  return Array.from({ length: Math.min(max, 3) }, (_, index) => `To-do ${index + 1}`)
})

const eventItems = computed(() => {
  const max = Number(viewNode.value.props.max ?? 3)
  if (!eventCalendars.value.length) return []
  return eventCalendars.value.slice(0, max).map(calendar => ({
    title: calendar.name,
    when: calendar.state === 'unknown' ? 'No upcoming events' : stateOf(calendar) || calendar.state,
  }))
})

const mediaTitle = computed(() => {
  if (!viewNode.value.bind) return '—'
  return entity.value?.name ?? viewNode.value.bind.entityId
})
const mediaArtist = computed(() => (entity.value ? stateOf(entity.value) || entity.value.state : ''))

function whereWord(person: EntityInfo): string {
  if (person.state === 'home') return 'HOME'
  if (person.state === 'not_home') return 'AWAY'
  return person.state.replace(/_/g, ' ').toUpperCase() || 'AWAY'
}

function where(event: DragEvent): Where {
  if (props.isRoot) return 'inside'
  const bounds = el.value!.getBoundingClientRect()
  const fraction =
    props.axis === 'row' || props.axis === 'flow'
      ? (event.clientX - bounds.left) / bounds.width
      : (event.clientY - bounds.top) / bounds.height
  if (isContainer(viewNode.value))
    return fraction < 0.2 ? 'before' : fraction > 0.8 ? 'after' : 'inside'
  return fraction < 0.5 ? 'before' : 'after'
}

function over(e: DragEvent): void {
  if (!dragged.value) return
  e.stopPropagation()
  let w = where(e)
  if (!ctx.canDrop(viewNode.value.id, w)) {
    if (isContainer(viewNode.value) && ctx.canDrop(viewNode.value.id, 'inside')) w = 'inside'
    else return
  }
  e.preventDefault()
  ctx.hint.value = { targetId: viewNode.value.id, where: w }
}

function drop(e: DragEvent): void {
  e.stopPropagation()
  const h = ctx.hint.value
  if (h?.targetId === viewNode.value.id) ctx.drop(viewNode.value.id, h.where)
}

function start(e: DragEvent): void {
  e.stopPropagation()
  e.dataTransfer?.setData('text/plain', viewNode.value.id)
  const id = viewNode.value.id
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
      {
        selected,
        empty,
        root: isRoot,
        [`drop-${hint}`]: hint,
        dragging: dragged?.type === 'node' && dragged.id === node.id,
      },
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
    <template v-if="node.type === 'tabs'">
      <div class="tab-strip">
        <button
          v-for="(label, index) in tabLabels"
          :key="`${label}-${index}`"
          type="button"
          class="tab"
          :class="{ on: index === Math.min(tabIndex, Math.max(tabLabels.length - 1, 0)) }"
          @click.stop="tabIndex = index"
        >
          {{ label }}
        </button>
      </div>
      <NodeView v-if="tabChild" :node="tabChild" axis="box" />
      <span v-else class="placeholder">Drop a panel into each tab</span>
    </template>
    <template v-else-if="isContainer(node)">
      <NodeView v-for="c in node.children" :key="c.id" :node="c" :axis="childAxis" />
      <span v-if="empty" class="placeholder">Drop components here</span>
    </template>
    <span v-else-if="node.type === 'text' && node.props.marquee === 'true'" class="marquee">{{
      text
    }}</span>
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
      <i
        :style="{
          width: `${sliderFill}%`,
          background: pastel(node.props.color, node.bind?.entityId.split('.')[0]).color,
        }"
      />
      <span v-if="!node.bind" class="hint">Bind an entity</span>
    </div>
    <div v-else-if="node.type === 'image'" class="picture">
      <MdiIcon
        :name="node.bind?.entityId.startsWith('camera.') ? 'cctv' : 'image-outline'"
        :size="36"
      />
      <span>{{
        node.bind ? (entity?.name ?? node.bind.entityId) : 'Bind a camera or person'
      }}</span>
    </div>
    <div v-else-if="node.type === 'tile' && card" class="card">
      <div class="head">{{ card.name }}</div>
      <i class="line" :style="{ background: card.accent }" />
      <div class="body">
        <MdiIcon
          :name="card.icon"
          :size="30"
          :style="{ color: card.accent ?? colorOf('ink_dim') }"
        />
        <span class="grow" />
        <span class="state">{{ card.state }}</span>
      </div>
    </div>
    <template v-else-if="node.type === 'clock'">
      <span
        v-if="node.props.show !== 'date'"
        class="time"
        :style="{ fontSize: `${Number(node.props.fontSize ?? 44)}px` }"
        >{{ clockTime }}</span
      >
      <span
        v-if="node.props.show === 'date' || node.props.show === 'both'"
        :style="
          node.props.show === 'date'
            ? { fontSize: `${Number(node.props.fontSize ?? 44)}px` }
            : {
                fontSize: `${Math.max(Number(node.props.fontSize ?? 44) * 0.33, 14)}px`,
                color: colorOf('ink_soft'),
              }
        "
        >{{ clockDate }}</span
      >
    </template>
    <template v-else-if="node.type === 'badge'">
      <MdiIcon v-if="node.props.icon || node.bind" :name="icon" :size="18" />
      <span>{{
        node.bind && !node.props.text
          ? entity
            ? stateOf(entity) || entity.state
            : `‹${node.bind.entityId}›`
          : node.props.text
      }}</span>
    </template>
    <span
      v-else-if="node.type === 'avatar'"
      class="avatar"
      :style="{
        width: `${Number(node.props.size ?? 56)}px`,
        height: `${Number(node.props.size ?? 56)}px`,
        fontSize: `${Number(node.props.size ?? 56) * 0.38}px`,
      }"
      >{{ initials }}</span
    >
    <div v-else-if="node.type === 'progress'" class="track">
      <i :style="{ width: `${progress}%`, background: accent }" />
    </div>
    <div v-else-if="node.type === 'graph'" class="graph">
      <svg v-if="node.bind" viewBox="0 0 100 40" preserveAspectRatio="none">
        <polyline
          points="0,30 10,26 20,28 30,18 40,20 50,12 60,16 70,10 80,14 90,8 100,12"
          fill="none"
          :stroke="colorOf(node.props.color) ?? colorOf('blue')"
          stroke-width="2"
          vector-effect="non-scaling-stroke"
        />
      </svg>
      <span v-else class="hint">Bind a sensor</span>
    </div>
    <span
      v-else-if="node.type === 'switch'"
      class="switch"
      :class="{ on }"
      :style="on ? { background: accent } : undefined"
      ><i
    /></span>
    <template v-else-if="node.type === 'stepper'">
      <span class="round"><MdiIcon name="minus" :size="22" /></span>
      <span class="value">{{ stepperValue }}</span>
      <span class="round"><MdiIcon name="plus" :size="22" /></span>
    </template>
    <template v-else-if="node.type === 'choices'">
      <template v-if="node.bind">
        <span v-for="w in choiceWords" :key="w" class="choice" :class="{ on: w === chosenWord }">{{
          w
        }}</span>
      </template>
      <span v-else class="hint">Bind a select, thermostat or light</span>
    </template>
    <div
      v-else-if="node.type === 'gauge'"
      class="gauge"
      :style="{ width: `${gauge.size}px`, height: `${gauge.size}px` }"
    >
      <svg :viewBox="`0 0 ${gauge.size} ${gauge.size}`">
        <g
          :transform="`rotate(135 ${gauge.size / 2} ${gauge.size / 2})`"
          fill="none"
          :stroke-width="gauge.stroke"
          stroke-linecap="round"
        >
          <circle
            :cx="gauge.size / 2"
            :cy="gauge.size / 2"
            :r="gauge.radius"
            :stroke="colorOf('track')"
            :stroke-dasharray="`${gauge.arc} 9999`"
          />
          <circle
            v-if="node.bind && gauge.fill > 0"
            :cx="gauge.size / 2"
            :cy="gauge.size / 2"
            :r="gauge.radius"
            :stroke="colorOf(node.props.color) ?? accent"
            :stroke-dasharray="`${gauge.fill} 9999`"
          />
        </g>
      </svg>
      <span class="value" :style="{ fontSize: `${Math.max(gauge.size * 0.16, 12)}px` }">{{
        node.bind ? gauge.value : ''
      }}</span>
      <span v-if="gauge.caption" class="caption" :style="{ fontSize: `${gauge.size * 0.085}px` }">{{
        gauge.caption
      }}</span>
    </div>
    <svg
      v-else-if="node.type === 'analog'"
      viewBox="0 0 100 100"
      :width="Number(node.props.size ?? 160)"
      :height="Number(node.props.size ?? 160)"
      stroke-linecap="round"
    >
      <circle cx="50" cy="50" r="50" :fill="colorOf(node.props.color ?? 'card')" />
      <line
        v-for="i in 12"
        :key="i"
        x1="50"
        :y1="(i - 1) % 3 === 0 ? 13 : 9"
        x2="50"
        y2="5"
        :stroke="colorOf('ink_dim')"
        stroke-width="2"
        :transform="`rotate(${(i - 1) * 30} 50 50)`"
      />
      <text
        v-for="h in clockNumbers"
        :key="h"
        :x="50 + Math.sin((h * Math.PI) / 6) * 30"
        :y="50 - Math.cos((h * Math.PI) / 6) * 30"
        :fill="colorOf('ink')"
        font-size="10"
        text-anchor="middle"
        dominant-baseline="central"
      >
        {{ h }}
      </text>
      <line
        x1="50"
        y1="50"
        x2="50"
        y2="25"
        :stroke="colorOf('ink')"
        stroke-width="5"
        :transform="`rotate(${hands.hour} 50 50)`"
      />
      <line
        x1="50"
        y1="50"
        x2="50"
        y2="12.5"
        :stroke="colorOf('ink')"
        stroke-width="3.5"
        :transform="`rotate(${hands.minute} 50 50)`"
      />
      <line
        v-if="node.props.seconds === 'true'"
        x1="50"
        y1="50"
        x2="50"
        y2="9"
        :stroke="colorOf('accent')"
        stroke-width="1.2"
        :transform="`rotate(${hands.second} 50 50)`"
      />
      <circle cx="50" cy="50" r="4" :fill="colorOf('ink')" />
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
          :style="{
            height: `${Number(node.props.size ?? 48)}px`,
            minWidth: `${Number(node.props.size ?? 48)}px`,
          }"
          ><MdiIcon :name="k" :size="Number(node.props.size ?? 48) * 0.45"
        /></span>
      </template>
      <span
        v-else
        class="key-stub"
        :style="{
          width: `${Number(node.props.size ?? 48) * 3 + 20}px`,
          height: `${Number(node.props.size ?? 48)}px`,
        }"
        >Bind a player, cover, vacuum, lock or alarm</span
      >
    </template>
    <template v-else-if="node.type === 'swatches'">
      <i
        v-for="c in swatchColours"
        :key="c"
        class="dot"
        :style="{
          background: c,
          width: `${Number(node.props.size ?? 40)}px`,
          height: `${Number(node.props.size ?? 40)}px`,
        }"
      />
    </template>
    <template v-else-if="node.type === 'keypad'">
      <div class="display">{{ node.bind ? '• • •' : '' }}</div>
      <div
        class="pad"
        :style="{ gridTemplateColumns: `repeat(3, ${Number(node.props.size ?? 56) * 1.5}px)` }"
      >
        <span
          v-for="k in PAD"
          :key="k"
          class="key"
          :style="{ height: `${Number(node.props.size ?? 56)}px` }"
        >
          <MdiIcon
            v-if="k === 'clear' || k === 'back'"
            :name="k === 'clear' ? 'close' : 'backspace-outline'"
            :size="24"
          />
          <template v-else>{{ k }}</template>
        </span>
      </div>
    </template>
    <div v-else-if="node.type === 'dial'" class="dial" :style="{ width: `${gauge.size}px` }">
      <div class="gauge" :style="{ width: `${gauge.size}px`, height: `${gauge.size}px` }">
        <svg :viewBox="`0 0 ${gauge.size} ${gauge.size}`">
          <g
            :transform="`rotate(135 ${gauge.size / 2} ${gauge.size / 2})`"
            fill="none"
            :stroke-width="gauge.stroke"
            stroke-linecap="round"
          >
            <circle
              :cx="gauge.size / 2"
              :cy="gauge.size / 2"
              :r="gauge.radius"
              :stroke="colorOf('track')"
              :stroke-dasharray="`${gauge.arc} 9999`"
            />
            <circle
              v-if="node.bind && gauge.fill > 0"
              :cx="gauge.size / 2"
              :cy="gauge.size / 2"
              :r="gauge.radius"
              :stroke="colorOf(node.props.color) ?? accent"
              :stroke-dasharray="`${gauge.fill} 9999`"
            />
          </g>
        </svg>
        <span class="value" :style="{ fontSize: `${Math.max(gauge.size * 0.16, 12)}px` }">{{
          node.bind ? gauge.value || '–' : '–'
        }}</span>
      </div>
      <div class="dial-keys">
        <span class="round"><MdiIcon name="minus" :size="22" /></span>
        <span class="round"><MdiIcon name="plus" :size="22" /></span>
      </div>
    </div>
    <span v-else-if="node.type === 'countdown'">{{ countdownText }}</span>
    <img
      v-else-if="node.type === 'qr'"
      class="qr"
      :src="qrDataUrl"
      :alt="qrPayload"
      :width="Math.max(48, Number(node.props.size ?? 160) - 16)"
      :height="Math.max(48, Number(node.props.size ?? 160) - 16)"
    />
    <div v-else-if="node.type === 'markdown'" class="markdown">
      <span
        v-for="(line, index) in markdownLines"
        :key="index"
        :style="{ fontWeight: line.bold ? 700 : 400, fontStyle: line.italic ? 'italic' : 'normal' }"
        >{{ line.text || ' ' }}</span
      >
    </div>
    <template v-else-if="node.type === 'timerkeys'">
      <span class="key grow"><MdiIcon name="play" :size="20" />Start</span>
      <span class="key grow"><MdiIcon name="pause" :size="20" />Pause</span>
      <span class="key grow"><MdiIcon name="close" :size="20" />Cancel</span>
    </template>
    <template v-else-if="node.type === 'todolist'">
      <template v-if="todoItems.length">
        <span v-for="item in todoItems" :key="item" class="todo"
          ><MdiIcon name="checkbox-blank-outline" :size="18" />{{ item }}</span
        >
      </template>
      <span v-else class="hint">Bind a to-do list</span>
    </template>
    <template v-else-if="node.type === 'events'">
      <template v-if="eventItems.length">
        <div v-for="item in eventItems" :key="item.title" class="event">
          <strong>{{ item.title }}</strong>
          <span>{{ item.when }}</span>
        </div>
      </template>
      <span v-else class="hint">Bind a calendar</span>
    </template>
    <template v-else-if="node.type === 'mediabar'">
      <span class="art"><MdiIcon name="music" :size="28" /></span>
      <span class="meta">
        <strong>{{ mediaTitle }}</strong>
        <span>{{ mediaArtist }}</span>
      </span>
      <span class="round"><MdiIcon name="skip-previous" :size="20" /></span>
      <span class="round on"><MdiIcon name="play" :size="20" /></span>
      <span class="round"><MdiIcon name="skip-next" :size="20" /></span>
    </template>
    <template v-else-if="node.type === 'forecast'">
      <span v-for="day in forecastDays" :key="day.day" class="day">
        <span>{{ day.day }}</span>
        <MdiIcon name="weather-partly-cloudy" :size="28" />
        <span>{{ day.high }}° / {{ day.low }}°</span>
      </span>
    </template>
    <template v-else-if="node.type === 'presence'">
      <template v-if="presencePeople.length">
        <span v-for="person in presencePeople" :key="person.entityId" class="person">
          <span class="photo">{{ (person.name || '?').slice(0, 1).toUpperCase() }}</span>
          <span class="who">
            <strong>{{ person.name }}</strong>
            <span class="where">{{ whereWord(person) }}</span>
          </span>
        </span>
      </template>
      <span v-else class="hint">No people</span>
    </template>
    <span v-else-if="node.type === 'led'" class="led" />
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
  background: var(--rc-key);
  font-weight: 600;
}

.graph {
  position: relative;
  width: 100%;
  height: 100%;
  border-radius: inherit;
  background: var(--rc-track);
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
  background: var(--rc-key);
  flex: none;
}

.switch i {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--rc-ink);
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
  background: var(--rc-key);
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
  background: var(--rc-key);
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
}

.choice.on {
  background: var(--rc-key-on);
  color: var(--rc-key-on-ink);
}

.track {
  position: relative;
  width: 100%;
  height: 100%;
  border-radius: 999px;
  background: var(--rc-track);
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
  background: var(--rc-track);
  color: var(--rc-ink-dim);
  font-size: 12px;
  border-radius: inherit;
}

.card {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  border: 2px solid var(--rc-card-edge);
  border-radius: 10px;
  background: var(--rc-card);
  overflow: hidden;
}

.card .head {
  background: var(--rc-card-header);
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
  background: var(--rc-card-header);
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

.gauge .caption {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 1%;
  text-align: center;
  color: var(--rc-ink-soft);
  white-space: nowrap;
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
  background: var(--rc-key);
  font-size: 22px;
  font-weight: 500;
}

.key-stub {
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: var(--rc-track);
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
  color: var(--rc-ink-soft);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.t-tabs {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 0;
}

.tab-strip {
  display: flex;
  flex: none;
  align-items: flex-end;
  gap: 12px;
}

.tab {
  padding: 0 0 4px;
  border: none;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: var(--rc-ink-dim);
  font-size: 14px;
  cursor: pointer;
}

.tab.on {
  color: var(--rc-ink);
  font-weight: 600;
  border-bottom-color: var(--rc-accent);
}

.t-dial,
.t-countdown,
.t-qr {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.dial {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.dial-keys {
  display: flex;
  gap: 20px;
}

.dial-keys .round,
.t-mediabar .round {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: var(--rc-key);
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}

.t-mediabar .round.on {
  background: var(--rc-key-on);
  color: var(--rc-key-on-ink);
}

.t-countdown {
  white-space: nowrap;
  line-height: 1.2;
}

.qr {
  display: block;
  border-radius: 4px;
  background: #fff;
}

.t-markdown {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.markdown span {
  display: block;
  white-space: pre-wrap;
}

.t-timerkeys {
  display: flex;
  align-items: center;
  gap: 10px;
}

.t-timerkeys .key.grow {
  flex: 1;
  gap: 6px;
  height: 52px;
  font-size: 14px;
}

.t-todolist,
.t-events {
  display: flex;
  flex-direction: column;
  gap: 6px;
  position: relative;
  width: 100%;
  height: 100%;
}

.todo {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  padding: 0 12px;
  border-radius: 12px;
  background: var(--rc-key);
  font-size: 14px;
}

.event {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.event strong {
  font-size: 14px;
  font-weight: 600;
}

.event span {
  font-size: 12px;
  color: var(--rc-ink-soft);
}

.t-mediabar {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}

.art {
  width: 56px;
  height: 56px;
  border-radius: 8px;
  background: var(--rc-track);
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
  color: var(--rc-ink-dim);
}

.meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.meta strong,
.meta span {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.meta span {
  font-size: 12px;
  color: var(--rc-ink-soft);
}

.t-forecast {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.day {
  width: 56px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  font-size: 12px;
}

.t-presence {
  display: flex;
  align-items: center;
  justify-content: space-evenly;
  gap: 12px;
  width: 100%;
  height: 100%;
}

.person {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px;
}

.photo {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--rc-key);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  flex: none;
}

.who {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.who strong {
  font-size: 14px;
  white-space: nowrap;
}

.who .where {
  font-size: 12px;
  color: var(--rc-accent-soft, var(--rc-accent));
}

.t-led {
  flex: none;
}

.t-todolist .hint,
.t-events .hint,
.t-presence .hint {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: rgb(255 255 255 / 50%);
}
</style>
