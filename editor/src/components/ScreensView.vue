<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { api } from '../api'
import { DENSITIES, PRESETS, clampDp, toDp } from '../model/devices'
import { reflow, suggest } from '../model/grid'
import type { PlayerStatus, Screen } from '../model/types'
import { attempt, playersOf, refreshLayout, state, toast } from '../store'
import MdiIcon from './MdiIcon.vue'

const emit = defineEmits<{ edit: [screen: Screen]; themes: [] }>()

const RELEASES = 'https://github.com/sickkick/remote-screens/releases/latest'

const screens = computed(() => state.layout?.screens ?? [])
const waiting = computed(() => state.players.filter((p) => !p.screenId))
const adopted = computed(() => state.players.filter((p) => p.screenId))

const creating = ref(false)
const newName = ref('')
const newSize = ref('0')

const customW = ref(1280)
const customH = ref(800)
/** 0 when the custom size is in dp, else the dpi of a size in pixels. */
const customDpi = ref(0)

const sizeChoices = computed(() => [
  ...waiting.value
    .filter((p) => p.widthDp > 0)
    .map((p) => ({ label: `Like ${p.name} (${p.widthDp} × ${p.heightDp})`, w: p.widthDp, h: p.heightDp })),
  ...PRESETS.map((p) => ({ label: `${p.label} (${p.w} × ${p.h})`, w: p.w, h: p.h })),
  { label: 'Custom size…', w: 0, h: 0 },
])
const custom = computed(() => Number(newSize.value) === sizeChoices.value.length - 1)

async function create(): Promise<void> {
  const choice = sizeChoices.value[Number(newSize.value)] ?? sizeChoices.value[0]
  const dp = (v: number) => (customDpi.value ? toDp(v, customDpi.value) : v)
  const size = custom.value ? { w: clampDp(dp(customW.value), 1280), h: clampDp(dp(customH.value), 800) } : choice
  const s = await attempt(() => api.createScreen(newName.value.trim() || 'Screen', { widthDp: size.w, heightDp: size.h }))
  if (!s) return
  creating.value = false
  newName.value = ''
  await refreshLayout()
  emit('edit', s)
}

async function remove(s: Screen): Promise<void> {
  if (!confirm(`Delete ${s.name}? Its tablets go back to waiting.`)) return
  await attempt(() => api.deleteScreen(s.id))
  await refreshLayout()
}

const assign = ref<Record<string, string>>({})

async function adopt(p: PlayerStatus): Promise<void> {
  const screenId = assign.value[p.deviceId] || screens.value[0]?.id
  if (!screenId) return toast('Make a screen first')
  await move(p, screenId)
}

async function move(p: PlayerStatus, screenId: string): Promise<void> {
  await attempt(() => api.adopt(p.deviceId, screenId))
  state.players = (await attempt(api.players)) ?? state.players
}

async function rename(p: PlayerStatus): Promise<void> {
  menu.value = null
  const name = prompt('Name of this tablet', p.name)?.trim()
  if (!name || name === p.name) return
  await attempt(() => api.renamePlayer(p.deviceId, name))
  state.players = (await attempt(api.players)) ?? state.players
}

function identify(p: PlayerStatus): void {
  attempt(() => api.identify(p.deviceId))
}

async function forget(p: PlayerStatus): Promise<void> {
  menu.value = null
  if (!confirm(`Unpair ${p.name}? It has to be paired again before it shows anything.`)) return
  await attempt(() => api.forgetPlayer(p.deviceId))
  state.players = (await attempt(api.players)) ?? state.players
}

/** The device id of the tablet whose ⋮ menu is open. */
const menu = ref<string | null>(null)
const closeMenu = () => (menu.value = null)
onMounted(() => window.addEventListener('click', closeMenu))
onBeforeUnmount(() => window.removeEventListener('click', closeMenu))

function tileCount(s: Screen): number {
  return s.pages.reduce((n, p) => n + p.tiles.length, 0)
}

function screenName(id?: string): string {
  return screens.value.find((s) => s.id === id)?.name ?? '—'
}

function ago(at: number): string {
  const minutes = Math.round((Date.now() - at) / 60000)
  if (minutes < 1) return 'just now'
  if (minutes < 60) return `${minutes} min ago`
  const hours = Math.round(minutes / 60)
  if (hours < 24) return `${hours} h ago`
  const days = Math.round(hours / 24)
  return days === 1 ? 'yesterday' : `${days} days ago`
}

function status(p: PlayerStatus): string {
  if (p.online) return p.awake ? 'Online' : 'Standby'
  return p.lastSeen ? `Offline · ${ago(p.lastSeen)}` : 'Never connected'
}

function suggested(p: PlayerStatus): string {
  const g = suggest(p.widthDp, p.heightDp)
  return `${g.cols} × ${g.rows}`
}

/** The shape the preview is drawn in: the screen's design size, else its first tablet's. */
function aspect(s: Screen): string {
  if (s.designSize) return `${s.designSize.widthDp} / ${s.designSize.heightDp}`
  const p = playersOf(s.id).find((p) => p.widthDp > 0)
  return p ? `${p.widthDp} / ${p.heightDp}` : '16 / 10'
}

function preview(s: Screen) {
  const page = s.pages.find((p) => p.id === s.homePage) ?? s.pages[0]
  if (!page || page.root) return { free: page?.root !== undefined, tiles: [] }
  const { cols, rows } = s.grid
  return {
    free: false,
    tiles: reflow(page.tiles, s.grid).map((t) => ({
      id: t.tile.id,
      style: {
        left: `${(t.col / cols) * 100}%`,
        top: `${(t.row / rows) * 100}%`,
        width: `${(t.w / cols) * 100}%`,
        height: `${(t.h / rows) * 100}%`,
      },
    })),
  }
}
</script>

<template>
  <div class="page">
    <header class="row">
      <MdiIcon name="tablet-dashboard" :size="28" />
      <h1 class="grow">Remote Screens</h1>
      <button title="Colours the screens wear" @click="emit('themes')"><MdiIcon name="palette-outline" :size="16" /> Themes</button>
      <span v-if="state.info?.demo" class="badge">Demo</span>
      <span class="pill">
        <span class="dot" :class="{ on: state.info?.connected }" />
        {{ state.info?.connected ? 'Connected to' : 'Not connected to' }} {{ state.info?.locationName || 'Home Assistant' }}
      </span>
    </header>

    <section v-if="waiting.length" class="card pairing">
      <div class="row">
        <MdiIcon name="tablet" :size="22" />
        <h2 class="grow">{{ waiting.length === 1 ? 'A tablet wants' : `${waiting.length} tablets want` }} to pair</h2>
      </div>
      <p class="muted">Check the code matches the one on the tablet, then pick the screen it should show.</p>
      <div v-for="p in waiting" :key="p.deviceId" class="row player">
        <span class="code">{{ p.pairingCode ?? '—' }}</span>
        <span class="grow">
          {{ p.name }}
          <span class="muted"> · {{ p.widthDp }} × {{ p.heightDp }} dp, suits a {{ suggested(p) }} grid</span>
        </span>
        <select v-model="assign[p.deviceId]" :disabled="screens.length === 0">
          <option value="" disabled>Show screen…</option>
          <option v-for="s in screens" :key="s.id" :value="s.id">{{ s.name }}</option>
        </select>
        <button class="primary" :disabled="screens.length === 0" @click="adopt(p)">Pair</button>
      </div>
      <p v-if="screens.length === 0" class="muted">Make a screen below first.</p>
    </section>

    <section>
      <div class="row heading">
        <div class="grow">
          <h3>Screens</h3>
          <p class="muted hint">What the tablets display. Click one to edit its pages and tiles.</p>
        </div>
        <button class="primary" @click="creating = !creating">+ New screen</button>
      </div>

      <div v-if="creating" class="card create">
        <label class="field grow">Name<input v-model="newName" placeholder="Kitchen" @keyup.enter="create" /></label>
        <label class="field grow">
          Sized for
          <select v-model="newSize">
            <option v-for="(c, i) in sizeChoices" :key="c.label" :value="String(i)">{{ c.label }}</option>
          </select>
        </label>
        <template v-if="custom">
          <label class="field">Width<input v-model.number="customW" type="number" min="1" step="10" /></label>
          <label class="field">Height<input v-model.number="customH" type="number" min="1" step="10" /></label>
          <label class="field">
            In
            <select v-model.number="customDpi">
              <option :value="0">dp</option>
              <option v-for="d in DENSITIES" :key="d" :value="d">px at {{ d }} dpi</option>
            </select>
          </label>
        </template>
        <div class="row">
          <button @click="creating = false">Cancel</button>
          <button class="primary" @click="create">Create</button>
        </div>
      </div>

      <p v-if="screens.length === 0 && !creating" class="card muted empty">No screens yet. Make one, then pair a tablet with it.</p>

      <div class="screens">
        <article v-for="s in screens" :key="s.id" class="card screen" @click="emit('edit', s)">
          <div class="frame">
            <div class="thumb" :style="{ aspectRatio: aspect(s) }">
              <div class="cells">
                <span v-for="t in preview(s).tiles" :key="t.id" class="tile" :style="t.style" />
              </div>
              <span v-if="preview(s).free" class="empty-thumb"><MdiIcon name="vector-square" :size="28" /></span>
              <span v-else-if="preview(s).tiles.length === 0" class="empty-thumb">Empty — click to add tiles</span>
              <span class="nav" />
            </div>
          </div>

          <div class="row">
            <h2 class="grow">{{ s.name }}</h2>
            <button class="icon quiet" title="Delete screen" @click.stop="remove(s)"><MdiIcon name="delete-outline" :size="18" /></button>
          </div>
          <p class="muted">
            {{ s.pages.length }} {{ s.pages.length === 1 ? 'page' : 'pages' }} · {{ s.grid.cols }} × {{ s.grid.rows }} grid ·
            {{ tileCount(s) }} {{ tileCount(s) === 1 ? 'tile' : 'tiles' }}
          </p>

          <div class="row tablets">
            <template v-if="playersOf(s.id).length">
              <span class="muted">On</span>
              <span v-for="p in playersOf(s.id)" :key="p.deviceId" class="chip" :title="status(p)">
                <span class="dot small" :class="{ on: p.online }" /> {{ p.name }}
              </span>
            </template>
            <span v-else class="muted">Not on any tablet yet</span>
          </div>
        </article>
      </div>
    </section>

    <section>
      <div class="row heading">
        <div class="grow">
          <h3>Tablets</h3>
          <p class="muted hint">Each paired tablet shows one screen. Change it here at any time.</p>
        </div>
      </div>

      <div class="card list">
        <div v-if="adopted.length" class="row player labels muted">
          <span class="grow">Tablet</span>
          <span class="status">Status</span>
          <span class="shows">Shows screen</span>
          <span class="actions" />
        </div>

        <div v-for="p in adopted" :key="p.deviceId" class="row player">
          <span class="grow name">
            <MdiIcon name="tablet" :size="20" class="muted" />
            <span>
              {{ p.name }}
              <template v-if="p.widthDp > 0"><br /><span class="muted small">{{ p.widthDp }} × {{ p.heightDp }} dp</span></template>
            </span>
          </span>
          <span class="status" :class="{ online: p.online }">
            <span class="dot small" :class="{ on: p.online }" /> {{ status(p) }}
            <template v-if="p.battery != null"> · {{ p.battery }}%</template>
          </span>
          <select class="shows" :value="p.screenId" :title="`Screen shown on ${p.name}`" @change="move(p, ($event.target as HTMLSelectElement).value)">
            <option v-for="s in screens" :key="s.id" :value="s.id">{{ s.name }}</option>
            <option v-if="!screens.some((s) => s.id === p.screenId)" :value="p.screenId">{{ screenName(p.screenId) }}</option>
          </select>
          <span class="actions row">
            <button class="quiet" :disabled="!p.online" title="Flash this tablet's name on its display" @click="identify(p)">Identify</button>
            <span class="menu">
              <button class="icon quiet" :class="{ open: menu === p.deviceId }" title="More" @click.stop="menu = menu === p.deviceId ? null : p.deviceId">
                <MdiIcon name="dots-vertical" :size="18" />
              </button>
              <div v-if="menu === p.deviceId" class="card popup" @click.stop>
                <button class="item" @click="rename(p)"><MdiIcon name="pencil-outline" :size="16" /> Rename</button>
                <button class="item danger" @click="forget(p)"><MdiIcon name="link-off" :size="16" /> Unpair</button>
              </div>
            </span>
          </span>
        </div>

        <div class="row add" :class="{ alone: adopted.length === 0 }">
          <MdiIcon name="plus-circle-outline" :size="20" class="muted" />
          <span class="grow muted">
            <strong>Add a tablet:</strong> install the player app on a tablet on the same network. It finds the add-on by itself
            and a pairing code appears here.
          </span>
          <a v-if="state.info?.playerApp" href="api/player.apk" class="link">Download app ({{ state.info.playerApp }})</a>
          <a v-else :href="RELEASES" target="_blank" rel="noopener" class="link">Get the app on GitHub</a>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.page {
  max-width: 1040px;
  margin: 0 auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 28px;
}

header h1 {
  font-size: 22px;
}

.heading {
  margin-bottom: 12px;
  align-items: flex-end;
}

.hint {
  margin: 4px 0 0;
}

.badge {
  background: var(--accent);
  color: var(--accent-ink);
  border-radius: 999px;
  padding: 2px 10px;
  font-size: 12px;
}

.pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--line);
  border-radius: 999px;
  padding: 3px 10px;
  font-size: 12px;
  color: var(--muted);
}

.dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--muted);
  opacity: 0.6;
  flex: none;
}

.dot.small {
  width: 8px;
  height: 8px;
}

.dot.on {
  background: #3da866;
  opacity: 1;
}

.pairing {
  outline: 2px solid var(--accent);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.pairing p {
  margin: 0;
}

.create {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.empty {
  margin: 0;
  text-align: center;
  padding: 28px;
}

.screens {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
}

.screen {
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 6px;
  outline: 2px solid transparent;
}

.screen:hover {
  outline-color: var(--accent);
}

.screen p {
  margin: 0;
}

.frame {
  height: 160px;
  margin-bottom: 6px;
  display: flex;
  justify-content: center;
}

.thumb {
  position: relative;
  height: 100%;
  max-width: 100%;
  border-radius: 10px;
  background: #2f2e2e;
  padding: 6px 6px 0;
  overflow: hidden;
}

.cells {
  position: relative;
  height: 86%;
}

.tile {
  position: absolute;
  border: 2px solid #2f2e2e;
  border-radius: 5px;
  background: #555;
}

.screen:hover .tile {
  background: #3e7f72;
}

.nav {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 12%;
  background: #262525;
  border-top: 2px solid #03dac5;
}

.empty-thumb {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: #8a8a8a;
  font-size: 12px;
  text-align: center;
}

button.quiet {
  color: var(--muted);
  border-color: transparent;
  background: transparent;
}

.screen button.quiet:hover {
  color: var(--danger);
}

.tablets {
  flex-wrap: wrap;
  margin-top: 2px;
  font-size: 12px;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: 1px solid var(--line);
  border-radius: 999px;
  padding: 2px 8px;
}

.list {
  padding: 4px 16px;
}

.player {
  padding: 10px 0;
  flex-wrap: wrap;
  gap: 12px;
}

.player + .player,
.add {
  border-top: 1px solid var(--line);
}

.labels {
  font-size: 12px;
  padding: 8px 0 6px;
}

.name {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 180px;
}

.small {
  font-size: 12px;
}

.status {
  width: 170px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--muted);
}

.status.online {
  color: var(--ink);
}

.shows {
  width: 150px;
}

.actions {
  width: 130px;
  justify-content: flex-end;
}

.menu {
  position: relative;
}

.menu button.open {
  background: var(--line);
  color: var(--ink);
}

.popup {
  position: absolute;
  right: 0;
  top: calc(100% + 4px);
  z-index: 10;
  padding: 6px;
  display: flex;
  flex-direction: column;
  min-width: 150px;
  outline: 1px solid var(--line);
}

.popup .item {
  display: flex;
  align-items: center;
  gap: 8px;
  border: none;
  border-radius: 8px;
  background: transparent;
}

.popup .item:hover {
  background: var(--bg);
}

.add {
  padding: 12px 0;
  gap: 10px;
}

.add.alone {
  border-top: none;
}

.add strong {
  color: var(--ink);
  font-weight: 500;
}

.link {
  color: var(--accent);
  white-space: nowrap;
}

.code {
  font-family: ui-monospace, monospace;
  font-size: 18px;
  letter-spacing: 0.15em;
  background: var(--bg);
  border-radius: 8px;
  padding: 4px 10px;
}
</style>
