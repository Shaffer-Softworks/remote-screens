// The add-on's layout model (shared/src/main/kotlin/.../Layout.kt) as JSON. Nulls are left out of the JSON.

export interface GridSize {
  cols: number
  rows: number
}

export type TapType = 'default' | 'toggle' | 'card' | 'action' | 'page' | 'none'

export interface TapAction {
  type: TapType
  service?: string
  data?: Record<string, unknown>
  page?: string
}

export interface Tile {
  id: string
  entityId?: string
  kind?: string
  col: number
  row: number
  w: number
  h: number
  full?: boolean
  name?: string
  icon?: string
  color?: string
  borderColor?: string
  tap: TapAction
  hold: TapAction
  options: Record<string, string>
}

export interface Page {
  id: string
  title: string
  tiles: Tile[]
  hidden?: boolean
  icon?: string
  /** A free-form page: a tree of components. When set, the grid's tiles are not drawn. */
  root?: Node
}

export type NodeType =
  | 'column'
  | 'row'
  | 'box'
  | 'text'
  | 'icon'
  | 'button'
  | 'spacer'
  | 'divider'
  | 'slider'
  | 'image'
  | 'tile'
  | 'clock'
  | 'badge'
  | 'avatar'
  | 'progress'
  | 'graph'
  | 'switch'
  | 'stepper'
  | 'choices'
  | 'flow'
  | 'gauge'
  | 'analog'
  | 'iconbutton'
  | 'keys'
  | 'swatches'
  | 'keypad'

export const CONTAINERS: readonly NodeType[] = ['column', 'row', 'box', 'flow']

/** `wrap`, `fill`, `dp:N` or `weight:N`, as in Compose. */
export type SizeSpec = string

export interface NodeSize {
  width: SizeSpec
  height: SizeSpec
}

export interface NodeStyle {
  padding: number
  background?: string
  corner: number
  /** An outline this many dp wide; absent or 0 for none. */
  border?: number
  borderColor?: string
}

export type BindFormat = 'state' | 'value' | 'name' | 'attribute'

export interface NodeBinding {
  entityId: string
  format: BindFormat
  attribute?: string
  prefix: string
  suffix: string
}

/** One component of a free-form page (shared/.../Layout.kt `Node`). */
export interface Node {
  id: string
  type: NodeType
  children: Node[]
  props: Record<string, string>
  size: NodeSize
  style: NodeStyle
  bind?: NodeBinding
  tap?: TapAction
  hold?: TapAction
  tile?: Tile
  hidden?: boolean
}

export interface ScreenSettings {
  brightness: number
  darkMode: boolean
  standbyMinutes: number
  standbyBrightness: number
  nightStart?: string
  nightEnd?: string
  nightBrightness: number
  nightDark: boolean
  backToHomeSeconds: number
  swipe: boolean
  orientation: number
  /** Theme id, built-in or custom; absent is `rpi`. */
  theme?: string
  /** The theme worn while dark; absent is `rpi`. */
  darkTheme?: string
}

/** A custom theme as stored: hex colours laid over a built-in [base]. */
export interface Theme {
  id: string
  name: string
  base: string
  roles: Record<string, string>
  pastels: Record<string, string>
}

/** A theme as `GET /themes` serves it: every role and pastel resolved to hex. */
export interface ResolvedTheme {
  id: string
  name: string
  base: string
  builtin: boolean
  /** Whether its page is dark, so previews and the map pick a matching style. */
  dark: boolean
  roles: Record<string, string>
  pastels: Record<string, string>
  /** The custom theme's own overrides; empty for built-ins. */
  overrides: { roles: Record<string, string>; pastels: Record<string, string> }
}

export interface TopBar {
  show: boolean
  clock: boolean
  /** The date beside the time; absent is shown. */
  date?: boolean
  temperatureEntity?: string
  people: boolean
  /** dp; absent is the default. */
  height?: number
  /** The clock block's share of the width in percent; absent is the default. */
  clockWidth?: number
  /** Theme role or pastel key; absent is the theme's header colour. */
  background?: string
  clockBackground?: string
}

export type NavLabels = 'both' | 'icons' | 'text'
export type NavLayout = 'spread' | 'center' | 'fill'

export interface NavBar {
  /** dp; absent is the default, smaller on a narrow device. */
  height?: number
  labels?: NavLabels
  layout?: NavLayout
  background?: string
  line?: string
  active?: string
}

export interface Screen {
  id: string
  name: string
  grid: GridSize
  pages: Page[]
  homePage: string
  settings: ScreenSettings
  topBar: TopBar
  /** Absent in screens saved before the bottom bar had settings; the editor fills it in when it opens one. */
  navBar?: NavBar
  /** The size the editor draws the screen at until a tablet shows it. */
  designSize?: DeviceSize
}

export interface DeviceSize {
  widthDp: number
  heightDp: number
}

export interface Preferences {
  language?: string
  clock24h?: boolean
}

export interface LayoutData {
  schemaVersion: number
  screens: Screen[]
  preferences: Preferences
  themes?: Theme[]
}

export interface PlayerStatus {
  deviceId: string
  name: string
  screenId?: string
  online: boolean
  pairingCode?: string
  widthDp: number
  heightDp: number
  appVersion: number
  lastSeen: number
  awake: boolean
  page: number
  battery?: number
}

export interface EntityInfo {
  entityId: string
  name: string
  state: string
  domain: string
  area?: string
  icon?: string
  unit?: string
}

export interface AreaInfo {
  id: string
  name: string
}

export interface Info {
  version: string
  demo: boolean
  connected: boolean
  language: string
  haVersion: string
  locationName: string
  /** The player app version the add-on hands out; absent when it has none. */
  playerApp?: string
}

export function domainOf(tile: Pick<Tile, 'entityId'>): string | undefined {
  const d = tile.entityId?.split('.')[0]
  return d || undefined
}

export function kindOf(tile: Pick<Tile, 'entityId' | 'kind'>): string {
  return tile.kind ?? domainOf(tile) ?? 'empty'
}
