<script setup lang="ts">
import { computed, ref } from 'vue'
import { api } from '../api'
import { suggest } from '../model/grid'
import type { PlayerStatus, Screen } from '../model/types'
import { attempt, playersOf, refreshLayout, state, toast } from '../store'
import MdiIcon from './MdiIcon.vue'

const emit = defineEmits<{ edit: [screen: Screen] }>()

const PRESETS = [
  { label: '10" tablet, landscape (1280 × 800)', w: 1280, h: 800 },
  { label: '10" tablet, portrait (800 × 1280)', w: 800, h: 1280 },
  { label: '8" tablet, landscape (960 × 600)', w: 960, h: 600 },
  { label: 'Phone (412 × 915)', w: 412, h: 915 },
]

const screens = computed(() => state.layout?.screens ?? [])
const waiting = computed(() => state.players.filter((p) => !p.screenId))
const adopted = computed(() => state.players.filter((p) => p.screenId))

const creating = ref(false)
const newName = ref('')
const newSize = ref('0')

const sizeChoices = computed(() => [
  ...waiting.value
    .filter((p) => p.widthDp > 0)
    .map((p) => ({ label: `Like ${p.name} (${p.widthDp} × ${p.heightDp})`, w: p.widthDp, h: p.heightDp })),
  ...PRESETS,
])

async function create(): Promise<void> {
  const size = sizeChoices.value[Number(newSize.value)] ?? PRESETS[0]
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
  const name = prompt('Name of this tablet', p.name)?.trim()
  if (!name || name === p.name) return
  await attempt(() => api.renamePlayer(p.deviceId, name))
  state.players = (await attempt(api.players)) ?? state.players
}

function identify(p: PlayerStatus): void {
  attempt(() => api.identify(p.deviceId))
}

async function forget(p: PlayerStatus): Promise<void> {
  if (!confirm(`Forget ${p.name}? It has to be paired again.`)) return
  await attempt(() => api.forgetPlayer(p.deviceId))
  state.players = (await attempt(api.players)) ?? state.players
}

function grid(s: Screen): string {
  return `${s.grid.cols} × ${s.grid.rows}`
}

function screenName(id?: string): string {
  return screens.value.find((s) => s.id === id)?.name ?? '—'
}

function seen(p: PlayerStatus): string {
  if (p.online) return p.awake ? 'Online' : 'Online, in standby'
  if (!p.lastSeen) return 'Never seen'
  const minutes = Math.round((Date.now() - p.lastSeen) / 60000)
  return minutes < 60 ? `Seen ${minutes} min ago` : `Seen ${new Date(p.lastSeen).toLocaleString()}`
}

function suggested(p: PlayerStatus): string {
  const g = suggest(p.widthDp, p.heightDp)
  return `${g.cols} × ${g.rows}`
}
</script>

<template>
  <div class="page">
    <header class="row">
      <MdiIcon name="tablet-dashboard" :size="28" />
      <h1 class="grow">Remote Screens</h1>
      <span v-if="state.info?.demo" class="badge">Demo</span>
      <span class="muted">{{ state.info?.locationName }}</span>
      <span class="dot" :class="{ on: state.info?.connected }" :title="state.info?.connected ? 'Connected to Home Assistant' : 'Not connected to Home Assistant'" />
    </header>

    <section>
      <div class="row heading">
        <h3 class="grow">Screens</h3>
        <button class="primary" @click="creating = !creating">New screen</button>
      </div>

      <div v-if="creating" class="card create">
        <label class="field grow">Name<input v-model="newName" placeholder="Kitchen" @keyup.enter="create" /></label>
        <label class="field grow">
          Sized for
          <select v-model="newSize">
            <option v-for="(c, i) in sizeChoices" :key="c.label" :value="String(i)">{{ c.label }}</option>
          </select>
        </label>
        <div class="row">
          <button @click="creating = false">Cancel</button>
          <button class="primary" @click="create">Create</button>
        </div>
      </div>

      <p v-if="screens.length === 0 && !creating" class="muted">No screens yet. Make one, then pair a tablet with it.</p>

      <div class="screens">
        <div v-for="s in screens" :key="s.id" class="card screen" @click="emit('edit', s)">
          <div class="row">
            <h2 class="grow">{{ s.name }}</h2>
            <button class="icon danger" title="Delete" @click.stop="remove(s)"><MdiIcon name="delete-outline" :size="18" /></button>
          </div>
          <p class="muted">
            {{ s.pages.length }} {{ s.pages.length === 1 ? 'page' : 'pages' }} · grid {{ grid(s) }} ·
            {{ s.pages.reduce((n, p) => n + p.tiles.length, 0) }} tiles
          </p>
          <div class="row tablets">
            <span v-for="p in playersOf(s.id)" :key="p.deviceId" class="chip" :class="{ on: p.online }">
              <MdiIcon name="tablet" :size="14" /> {{ p.name }}
            </span>
            <span v-if="playersOf(s.id).length === 0" class="muted">No tablet shows it yet</span>
          </div>
        </div>
      </div>
    </section>

    <section>
      <div class="row heading">
        <h3 class="grow">Tablets</h3>
        <a href="api/player.apk" class="muted">Download the player app</a>
      </div>

      <div v-if="waiting.length" class="card waiting">
        <p>
          <strong>Waiting to be paired.</strong>
          <span class="muted"> Check the code matches the one on the tablet, then choose its screen.</span>
        </p>
        <div v-for="p in waiting" :key="p.deviceId" class="row player">
          <span class="code">{{ p.pairingCode ?? '—' }}</span>
          <span class="grow">
            {{ p.name }}
            <span class="muted"> · {{ p.widthDp }} × {{ p.heightDp }} dp, suits {{ suggested(p) }}</span>
          </span>
          <select v-model="assign[p.deviceId]">
            <option value="" disabled>Screen…</option>
            <option v-for="s in screens" :key="s.id" :value="s.id">{{ s.name }}</option>
          </select>
          <button class="primary" :disabled="screens.length === 0" @click="adopt(p)">Pair</button>
        </div>
      </div>

      <p v-if="state.players.length === 0" class="muted">
        Install the player app on a tablet on the same network. It finds the add-on by itself and shows a code here.
      </p>

      <div v-if="adopted.length" class="card">
        <div v-for="p in adopted" :key="p.deviceId" class="row player">
          <span class="dot" :class="{ on: p.online }" />
          <span class="grow">
            {{ p.name }}
            <span class="muted"> · {{ seen(p) }}<template v-if="p.battery != null"> · {{ p.battery }}%</template></span>
          </span>
          <select :value="p.screenId" @change="move(p, ($event.target as HTMLSelectElement).value)">
            <option v-for="s in screens" :key="s.id" :value="s.id">{{ s.name }}</option>
            <option v-if="!screens.some((s) => s.id === p.screenId)" :value="p.screenId">{{ screenName(p.screenId) }}</option>
          </select>
          <button class="icon" title="Show its name on the tablet" :disabled="!p.online" @click="identify(p)">
            <MdiIcon name="eye-outline" :size="18" />
          </button>
          <button class="icon" title="Rename" @click="rename(p)"><MdiIcon name="pencil-outline" :size="18" /></button>
          <button class="icon danger" title="Forget" @click="forget(p)"><MdiIcon name="link-off" :size="18" /></button>
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
}

.badge {
  background: var(--accent);
  color: var(--accent-ink);
  border-radius: 999px;
  padding: 2px 10px;
  font-size: 12px;
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--line);
  flex: none;
}

.dot.on {
  background: #3da866;
}

.create {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  flex-wrap: wrap;
  margin-bottom: 12px;
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
}

.screen:hover {
  outline: 2px solid var(--accent);
}

.screen p {
  margin: 0;
}

.tablets {
  flex-wrap: wrap;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 1px solid var(--line);
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 12px;
  color: var(--muted);
}

.chip.on {
  color: var(--ink);
  border-color: #3da866;
}

.waiting {
  margin-bottom: 12px;
}

.waiting p {
  margin: 0 0 10px;
}

.player {
  padding: 6px 0;
  flex-wrap: wrap;
}

.player + .player {
  border-top: 1px solid var(--line);
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
