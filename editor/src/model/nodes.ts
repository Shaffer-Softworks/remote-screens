// The component tree of a free-form page: making components, finding them, and moving them about. Every change is
// in place on the tree the editor holds, like the grid's tiles.

import { newTile, newTileId } from './tiles'
import { CONTAINERS, PADDING_SIDES, type Node, type NodeType, type Page, type Tile } from './types'

export const MAX_NODES = 400
export const MAX_DEPTH = 16

export interface ComponentSpec {
  /** The palette's name for it; the node type, or a preset of one such as `card`. */
  id: string
  type: NodeType
  label: string
  icon: string
  group: 'Layouts' | 'Basic' | 'Display' | 'Controls'
}

export const COMPONENTS: ComponentSpec[] = [
  { id: 'column', type: 'column', label: 'Column', icon: 'view-sequential', group: 'Layouts' },
  { id: 'row', type: 'row', label: 'Row', icon: 'view-column', group: 'Layouts' },
  { id: 'box', type: 'box', label: 'Box', icon: 'checkbox-blank-outline', group: 'Layouts' },
  { id: 'flow', type: 'flow', label: 'Flow', icon: 'view-grid-plus-outline', group: 'Layouts' },
  { id: 'card', type: 'column', label: 'Card', icon: 'card-outline', group: 'Layouts' },
  {
    id: 'scrollcolumn',
    type: 'column',
    label: 'Scroll column',
    icon: 'arrow-up-down',
    group: 'Layouts',
  },
  { id: 'scrollrow', type: 'row', label: 'Scroll row', icon: 'arrow-left-right', group: 'Layouts' },
  { id: 'text', type: 'text', label: 'Text', icon: 'format-text', group: 'Basic' },
  { id: 'ticker', type: 'text', label: 'Ticker', icon: 'text-short', group: 'Basic' },
  { id: 'icon', type: 'icon', label: 'Icon', icon: 'star-outline', group: 'Basic' },
  { id: 'image', type: 'image', label: 'Image', icon: 'image-outline', group: 'Basic' },
  {
    id: 'spacer',
    type: 'spacer',
    label: 'Spacer',
    icon: 'arrow-expand-horizontal',
    group: 'Basic',
  },
  { id: 'divider', type: 'divider', label: 'Divider', icon: 'minus', group: 'Basic' },
  { id: 'clock', type: 'clock', label: 'Clock', icon: 'clock-outline', group: 'Display' },
  { id: 'badge', type: 'badge', label: 'Badge', icon: 'label-outline', group: 'Display' },
  {
    id: 'avatar',
    type: 'avatar',
    label: 'Avatar',
    icon: 'account-circle-outline',
    group: 'Display',
  },
  {
    id: 'progress',
    type: 'progress',
    label: 'Progress',
    icon: 'progress-helper',
    group: 'Display',
  },
  { id: 'graph', type: 'graph', label: 'Graph', icon: 'chart-line', group: 'Display' },
  { id: 'gauge', type: 'gauge', label: 'Gauge', icon: 'gauge', group: 'Display' },
  {
    id: 'analog',
    type: 'analog',
    label: 'Analog clock',
    icon: 'clock-time-four-outline',
    group: 'Display',
  },
  { id: 'button', type: 'button', label: 'Button', icon: 'gesture-tap-button', group: 'Controls' },
  {
    id: 'iconbutton',
    type: 'iconbutton',
    label: 'Icon button',
    icon: 'circle-double',
    group: 'Controls',
  },
  {
    id: 'switch',
    type: 'switch',
    label: 'Switch',
    icon: 'toggle-switch-outline',
    group: 'Controls',
  },
  { id: 'slider', type: 'slider', label: 'Slider', icon: 'tune-variant', group: 'Controls' },
  {
    id: 'stepper',
    type: 'stepper',
    label: 'Stepper',
    icon: 'plus-minus-variant',
    group: 'Controls',
  },
  {
    id: 'choices',
    type: 'choices',
    label: 'Choices',
    icon: 'format-list-bulleted-square',
    group: 'Controls',
  },
  { id: 'keys', type: 'keys', label: 'Remote keys', icon: 'remote', group: 'Controls' },
  {
    id: 'swatches',
    type: 'swatches',
    label: 'Colours',
    icon: 'palette-outline',
    group: 'Controls',
  },
  { id: 'keypad', type: 'keypad', label: 'Keypad', icon: 'dialpad', group: 'Controls' },
  { id: 'dial', type: 'dial', label: 'Dial', icon: 'circle-slice-8', group: 'Controls' },
  { id: 'countdown', type: 'countdown', label: 'Countdown', icon: 'timer-outline', group: 'Display' },
  { id: 'qr', type: 'qr', label: 'QR code', icon: 'qrcode', group: 'Display' },
  { id: 'markdown', type: 'markdown', label: 'Markdown', icon: 'language-markdown-outline', group: 'Basic' },
  { id: 'tabs', type: 'tabs', label: 'Tabs', icon: 'tab', group: 'Layouts' },
  {
    id: 'timerkeys',
    type: 'timerkeys',
    label: 'Timer controls',
    icon: 'timer-play-outline',
    group: 'Controls',
  },
  { id: 'todolist', type: 'todolist', label: 'To-do list', icon: 'checkbox-marked-outline', group: 'Controls' },
  { id: 'events', type: 'events', label: 'Events', icon: 'calendar-month', group: 'Display' },
  { id: 'mediabar', type: 'mediabar', label: 'Media bar', icon: 'play-box-outline', group: 'Controls' },
  { id: 'forecast', type: 'forecast', label: 'Forecast', icon: 'weather-partly-cloudy', group: 'Display' },
  { id: 'presence', type: 'presence', label: 'Presence', icon: 'account-group-outline', group: 'Display' },
  { id: 'led', type: 'led', label: 'LED', icon: 'circle', group: 'Display' },
]

export function componentOf(type: NodeType): ComponentSpec | undefined {
  return COMPONENTS.find(spec => spec.type === type)
}

/** A new component from the palette entry [id], presets made up. */
export function newComponent(id: string): Node | undefined {
  const spec = COMPONENTS.find(entry => entry.id === id)
  if (!spec) return undefined
  const node = newNode(spec.type)
  if (id === 'card') {
    node.props = { gap: '8', label: 'Card' }
    node.style = { padding: 16, corner: 16, background: 'card' }
  }
  if (id === 'scrollcolumn') {
    node.props = { gap: '8', scroll: 'true', label: 'Scroll column' }
    node.size = { width: 'fill', height: 'dp:240' }
  }
  if (id === 'scrollrow') {
    node.props = { gap: '8', alignment: 'center', scroll: 'true', label: 'Scroll row' }
  }
  if (id === 'ticker') {
    node.props = {
      text: 'News that is longer than the space it has glides along',
      fontSize: '16',
      marquee: 'true',
      label: 'Ticker',
    }
    node.size = { width: 'fill', height: 'wrap' }
  }
  return node
}

export function isContainer(node: Node): boolean {
  return CONTAINERS.includes(node.type)
}

let seq = 0
export function newNodeId(): string {
  seq = (seq + 1) % 1000
  return `n${Date.now().toString(36)}${seq.toString(36)}`
}

const DEFAULT_PROPS: Partial<Record<NodeType, Record<string, string>>> = {
  text: { text: 'Text', fontSize: '16' },
  icon: { icon: 'star', size: '32' },
  button: { text: 'Button' },
  column: { gap: '8' },
  row: { gap: '8', alignment: 'center' },
  clock: { show: 'time', fontSize: '44' },
  badge: { text: 'Badge' },
  avatar: { size: '56' },
  flow: { gap: '8' },
  gauge: { size: '140' },
  analog: { size: '160' },
  iconbutton: { size: '64' },
  dial: { size: '160' },
  countdown: { text: '0:00', fontSize: '44' },
  qr: { text: 'https://www.home-assistant.io', size: '160' },
  markdown: { text: '**Hello**\nA short note' },
  tabs: { labels: 'One,Two', gap: '8' },
  todolist: { max: '8' },
  events: { max: '3' },
  forecast: { days: '5' },
  led: { size: '16' },
}

/** A new component of [type]; a `tile` needs the card it shows. */
export function newNode(type: NodeType, tile?: Tile): Node {
  const node: Node = {
    id: newNodeId(),
    type,
    children: [],
    props: { ...(DEFAULT_PROPS[type] ?? {}) },
    size: { width: 'wrap', height: 'wrap' },
    style: { padding: 0, corner: 0 },
  }
  if (type === 'box' || type === 'column' || type === 'row' || type === 'flow')
    node.size = { width: 'fill', height: 'wrap' }
  if (type === 'slider' || type === 'progress' || type === 'choices')
    node.size = { width: 'fill', height: 'wrap' }
  if (type === 'graph') node.size = { width: 'fill', height: 'dp:120' }
  if (type === 'image') node.size = { width: 'dp:200', height: 'dp:120' }
  if (type === 'tile') {
    node.tile = tile ?? newTile({ kind: 'clock' }, 0, 0)
    node.size = { width: 'dp:200', height: 'dp:160' }
  }
  return node
}

/** What an entity dropped on the canvas becomes: its card, as on the grid. */
export function nodeForEntity(entityId: string): Node {
  return newNode('tile', newTile({ entityId }, 0, 0))
}

/** The page a free-form page starts with: a column that fills the screen. */
export function newRoot(): Node {
  const root = newNode('column')
  root.size = { width: 'fill', height: 'fill' }
  root.style = { padding: 16, corner: 0 }
  root.props = { gap: '12' }
  return root
}

export function* walk(node: Node): Generator<Node> {
  yield node
  for (const child of node.children) yield* walk(child)
}

export function count(node: Node): number {
  let i = 0
  for (const _ of walk(node)) i++
  return i
}

export function depth(node: Node): number {
  return 1 + Math.max(0, ...node.children.map(depth))
}

export function find(root: Node, id: string): Node | undefined {
  for (const node of walk(root)) if (node.id === id) return node
  return undefined
}

/** The parent of [id] and its index there, or undefined for the root or an id not in the tree. */
export function parentOf(root: Node, id: string): { parent: Node; index: number } | undefined {
  for (const node of walk(root)) {
    const index = node.children.findIndex(child => child.id === id)
    if (index >= 0) return { parent: node, index }
  }
  return undefined
}

/** The ids from the root down to [id]. */
export function pathTo(root: Node, id: string): string[] {
  if (root.id === id) return [root.id]
  for (const child of root.children) {
    const path = pathTo(child, id)
    if (path.length) return [root.id, ...path]
  }
  return []
}

/** Whether [id] is [ancestorId] or sits inside it. */
export function within(root: Node, ancestorId: string, id: string): boolean {
  const ancestor = find(root, ancestorId)
  return !!ancestor && !!find(ancestor, id)
}

/** Puts [node] into [parentId] at [index] (the end if left out). False when it cannot go there. */
export function insert(root: Node, parentId: string, node: Node, index?: number): boolean {
  const parent = find(root, parentId)
  if (!parent || !isContainer(parent)) return false
  if (count(root) + count(node) > MAX_NODES) return false
  const at =
    index === undefined
      ? parent.children.length
      : Math.max(0, Math.min(index, parent.children.length))
  parent.children.splice(at, 0, node)
  if (depth(root) > MAX_DEPTH) {
    parent.children.splice(at, 1)
    return false
  }
  return true
}

/** Takes [id] out of the tree and gives it back; the root cannot be taken. */
export function remove(root: Node, id: string): Node | undefined {
  const parent = parentOf(root, id)
  if (!parent) return undefined
  return parent.parent.children.splice(parent.index, 1)[0]
}

/** Moves [id] into [parentId] at [index], counted as if it were already gone. False if it would go inside itself. */
export function move(root: Node, id: string, parentId: string, index: number): boolean {
  if (within(root, id, parentId)) return false
  const target = find(root, parentId)
  if (!target || !isContainer(target)) return false
  const from = parentOf(root, id)
  if (!from) return false
  const [node] = from.parent.children.splice(from.index, 1)
  const at = Math.max(0, Math.min(index, target.children.length))
  target.children.splice(at, 0, node)
  if (depth(root) > MAX_DEPTH) {
    target.children.splice(at, 1)
    from.parent.children.splice(from.index, 0, node)
    return false
  }
  return true
}

export type Where = 'before' | 'after' | 'inside'

/** Where a drop on [targetId] puts a component: into it, or beside it in its parent. */
export function dropSpot(
  root: Node,
  targetId: string,
  where: Where,
): { parentId: string; index: number } | undefined {
  if (where === 'inside') {
    const target = find(root, targetId)
    return target && isContainer(target)
      ? { parentId: target.id, index: target.children.length }
      : undefined
  }
  const parent = parentOf(root, targetId)
  return parent
    ? { parentId: parent.parent.id, index: parent.index + (where === 'after' ? 1 : 0) }
    : undefined
}

/** Moves [id] to a spot counted with it still in place, as [dropSpot] gives. */
export function moveTo(root: Node, id: string, spot: { parentId: string; index: number }): boolean {
  const from = parentOf(root, id)
  if (!from) return false
  const index =
    from.parent.id === spot.parentId && from.index < spot.index ? spot.index - 1 : spot.index
  return move(root, id, spot.parentId, index)
}

/** A copy of [n] with new ids throughout, its cards' ids too. */
export function clone(node: Node): Node {
  const copy = JSON.parse(JSON.stringify(node)) as Node
  for (const child of walk(copy)) {
    child.id = newNodeId()
    if (child.tile) child.tile.id = newTileId()
  }
  return copy
}

/** Puts a copy of [id] just after it, and returns the copy. */
export function duplicate(root: Node, id: string): Node | undefined {
  const parent = parentOf(root, id)
  const node = find(root, id)
  if (!parent || !node) return undefined
  const copy = clone(node)
  if (count(root) + count(copy) > MAX_NODES) return undefined
  parent.parent.children.splice(parent.index + 1, 0, copy)
  return copy
}

/** Wraps [id] in a new container of [type] in its place. */
export function wrap(
  root: Node,
  id: string,
  type: 'column' | 'row' | 'box' | 'flow',
): Node | undefined {
  const parent = parentOf(root, id)
  if (!parent) return undefined
  const box = newNode(type)
  box.children = [parent.parent.children[parent.index]]
  parent.parent.children.splice(parent.index, 1, box)
  return box
}

/** The sibling before or after [id], for moving through the tree with the arrow keys. */
export function sibling(root: Node, id: string, step: -1 | 1): Node | undefined {
  const parent = parentOf(root, id)
  if (!parent) return undefined
  return parent.parent.children[parent.index + step]
}

/** [n]'s padding on each side, start, top, end and bottom: its own for the side, else its `padding`. */
export function paddingOf(node: Node): [number, number, number, number] {
  const style = node.style
  return PADDING_SIDES.map(side => style[side] ?? style.padding) as [number, number, number, number]
}

/** Fills in what the add-on would take as defaults, so typed JSON may leave them out. */
export function normalize(value: unknown): unknown {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return value
  const obj = value as Record<string, unknown>
  const size = (obj.size ?? {}) as Record<string, unknown>
  const style = (obj.style ?? {}) as Record<string, unknown>
  return {
    ...obj,
    children: Array.isArray(obj.children)
      ? obj.children.map(normalize)
      : obj.children === undefined
        ? []
        : obj.children,
    props: obj.props ?? {},
    size: { width: size.width ?? 'wrap', height: size.height ?? 'wrap' },
    style: { ...style, padding: style.padding ?? 0, corner: style.corner ?? 0 },
  }
}

/** What is wrong with a tree the user typed, or undefined when it is fine. Mirrors the add-on's own check. */
export function problems(root: unknown): string | undefined {
  const seen = new Set<string>()
  const sizes = /^(wrap|fill|dp:\d+(\.\d+)?|weight:\d+(\.\d+)?)$/
  const types = new Set([...CONTAINERS, ...COMPONENTS.map(spec => spec.type), 'tile'])
  let total = 0
  function check(value: unknown, level: number): string | undefined {
    if (!value || typeof value !== 'object') return 'A component must be an object'
    const node = value as Partial<Node>
    if (typeof node.id !== 'string' || !node.id) return 'Every component needs an id'
    if (seen.has(node.id)) return `The id ${node.id} is used twice`
    seen.add(node.id)
    if (!node.type || !types.has(node.type)) return `Unknown component type ${String(node.type)}`
    if (++total > MAX_NODES) return `At most ${MAX_NODES} components`
    if (level > MAX_DEPTH) return `Components nest at most ${MAX_DEPTH} deep`
    if (!Array.isArray(node.children)) return `${node.id}: children must be a list`
    if (node.children.length && !CONTAINERS.includes(node.type))
      return `A ${node.type} holds no components`
    if (!node.size || !sizes.test(node.size.width) || !sizes.test(node.size.height))
      return `${node.id}: sizes are wrap, fill, dp:N or weight:N`
    if (!node.style || typeof node.style.padding !== 'number')
      return `${node.id}: style needs padding and corner`
    if (!node.props || typeof node.props !== 'object') return `${node.id}: props must be an object`
    if (node.type === 'tile' && !node.tile) return `${node.id}: a tile needs its card`
    for (const child of node.children) {
      const problem = check(child, level + 1)
      if (problem) return problem
    }
    return undefined
  }
  return check(root, 1)
}

/**
 * A grid page as a free-form one: a column of rows, each row the grid's row of cards, weighted by their widths
 * so the page keeps its look.
 */
export function fromGrid(page: Page, cols: number, rows: number): Node {
  const root = newRoot()
  root.props = { gap: '8' }
  root.style = { padding: 8, corner: 0 }
  const placed = [...page.tiles].sort(
    (tileA, tileB) => tileA.row - tileB.row || tileA.col - tileB.col,
  )
  for (let rowIndex = 0; rowIndex < rows; rowIndex++) {
    const inRow = placed.filter(tile => tile.row === rowIndex && !tile.full)
    if (!inRow.length) continue
    const row = newNode('row')
    row.size = { width: 'fill', height: `weight:${Math.max(...inRow.map(tile => tile.h))}` }
    row.props = { gap: '8' }
    let col = 0
    for (const tile of inRow) {
      if (tile.col > col)
        row.children.push({
          ...newNode('spacer'),
          size: { width: `weight:${tile.col - col}`, height: 'wrap' },
        })
      const node = newNode('tile', tile)
      node.size = { width: `weight:${tile.w}`, height: 'fill' }
      row.children.push(node)
      col = tile.col + tile.w
    }
    if (col < cols)
      row.children.push({
        ...newNode('spacer'),
        size: { width: `weight:${cols - col}`, height: 'wrap' },
      })
    root.children.push(row)
  }
  const full = page.tiles.find(tile => tile.full)
  if (full) {
    const node = newNode('tile', full)
    node.size = { width: 'fill', height: 'weight:1' }
    root.children.push(node)
  }
  return root
}
