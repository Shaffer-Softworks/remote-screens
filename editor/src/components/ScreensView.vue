<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { api } from '../api'
import { DENSITIES, PRESETS, clampDp, toDp } from '../model/devices'
import { isScreenFile, screenFile, screenFileName, type ScreenFile } from '../model/screenFile'
import type { PlayerStatus, Screen } from '../model/types'
import { attempt, playersOf, refreshLayout, refreshThemes, state, toast } from '../store'
import EnrollTabletPanel from './EnrollTabletPanel.vue'
import MdiIcon from './MdiIcon.vue'
import NotificationComposer from './NotificationComposer.vue'
import IntercomDesk from './IntercomDesk.vue'
import AdbPanel from './AdbPanel.vue'
import ScreenPreview from './ScreenPreview.vue'
import SettingsDialog from './SettingsDialog.vue'
import DeviceInfoDialog from './DeviceInfoDialog.vue'
import brandIcon from '../../../addon/icon.png'
import './dashboard.css'

const emit = defineEmits<{ edit: [screen: Screen]; themes: [] }>()

const RELEASES = 'https://github.com/Shaffer-Softworks/remote-screens/releases/latest'

const screens = computed(() => state.layout?.screens ?? [])
const waiting = computed(() => state.players.filter(player => !player.screenId))
const adopted = computed(() => state.players.filter(player => player.screenId))

const settingsOpen = ref(false)
const infoDeviceId = ref<string | null>(null)
const screenMenu = ref<string | null>(null)
const creating = ref(false)
const enrolling = ref(false)
const intercomOpen = ref(false)
const adbOpen = ref(false)
const notificationTargets = ref<string[] | null>(null)
function notifyTablet(player: PlayerStatus): void {
  menu.value = null
  notificationTargets.value = [player.deviceId]
}
const newName = ref('')
const newSize = ref('0')

const customW = ref(1280)
const customH = ref(800)
/** 0 when the custom size is in dp, else the dpi of a size in pixels. */
const customDpi = ref(0)

const sizeChoices = computed(() => [
  ...waiting.value
    .filter(player => player.widthDp > 0)
    .map(player => ({
      label: `Like ${player.name} (${player.widthDp} × ${player.heightDp})`,
      w: player.widthDp,
      h: player.heightDp,
    })),
  ...PRESETS.map(preset => ({
    label: `${preset.label} (${preset.w} × ${preset.h})`,
    w: preset.w,
    h: preset.h,
  })),
  { label: 'Custom size…', w: 0, h: 0 },
])
const custom = computed(() => Number(newSize.value) === sizeChoices.value.length - 1)

async function create(): Promise<void> {
  const choice = sizeChoices.value[Number(newSize.value)] ?? sizeChoices.value[0]
  const toDesignDp = (value: number) => (customDpi.value ? toDp(value, customDpi.value) : value)
  const size = custom.value
    ? {
        w: clampDp(toDesignDp(customW.value), 1280),
        h: clampDp(toDesignDp(customH.value), 800),
      }
    : choice
  const created = await attempt(() =>
    api.createScreen(newName.value.trim() || 'Screen', { widthDp: size.w, heightDp: size.h }),
  )
  if (!created) return
  creating.value = false
  newName.value = ''
  await refreshLayout()
  emit('edit', created)
}

async function remove(screen: Screen): Promise<void> {
  if (!confirm(`Delete ${screen.name}? Its tablets go back to waiting.`)) return
  await attempt(() => api.deleteScreen(screen.id))
  await refreshLayout()
}

const fileInput = ref<HTMLInputElement | null>(null)

function chooseFile(): void {
  const input = fileInput.value
  if (!input) return
  input.value = ''
  input.click()
}

async function importChosen(event: Event): Promise<void> {
  const input = event.target
  if (!(input instanceof HTMLInputElement)) return
  const chosen = input.files?.[0]
  input.value = ''
  if (!chosen) return
  let parsed: unknown
  try {
    parsed = JSON.parse(await chosen.text())
  } catch {
    toast('That file is not a screen')
    return
  }
  if (!isScreenFile(parsed)) {
    toast('That file is not a screen')
    return
  }
  await addCopy(parsed)
}

async function duplicate(screen: Screen): Promise<void> {
  await addCopy(screenFile(screen, state.layout?.themes ?? []))
}

function exportScreen(screen: Screen): void {
  const file = screenFile(screen, state.layout?.themes ?? [])
  const blob = new Blob([`${JSON.stringify(file, null, 2)}\n`], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${screenFileName(screen.name)}.json`
  link.click()
  URL.revokeObjectURL(url)
}

async function addCopy(file: ScreenFile): Promise<void> {
  const created = await attempt(() => api.importScreen(file))
  if (!created) return
  await refreshLayout()
  await refreshThemes()
  emit('edit', created)
}

const assign = ref<Record<string, string>>({})

async function adopt(player: PlayerStatus): Promise<void> {
  const screenId = assign.value[player.deviceId] || screens.value[0]?.id
  if (!screenId) return toast('Make a screen first')
  await move(player, screenId)
}

async function move(player: PlayerStatus, screenId: string): Promise<void> {
  await attempt(() => api.adopt(player.deviceId, screenId))
  state.players = (await attempt(api.players)) ?? state.players
}

async function rename(player: PlayerStatus): Promise<void> {
  menu.value = null
  const name = prompt('Name of this tablet', player.name)?.trim()
  if (!name || name === player.name) return
  await attempt(() => api.renamePlayer(player.deviceId, name))
  state.players = (await attempt(api.players)) ?? state.players
}

function identify(player: PlayerStatus): void {
  attempt(() => api.identify(player.deviceId))
}

async function forget(player: PlayerStatus): Promise<void> {
  menu.value = null
  if (!confirm(`Unpair ${player.name}? It has to be paired again before it shows anything.`)) return
  await attempt(() => api.forgetPlayer(player.deviceId))
  state.players = (await attempt(api.players)) ?? state.players
}

/** The device id of the tablet whose ⋮ menu is open. */
const menu = ref<string | null>(null)
const closeMenu = () => {
  menu.value = null
  screenMenu.value = null
}
const menuKey = (event: KeyboardEvent) => {
  if (event.key === 'Escape') closeMenu()
}
onMounted(() => {
  window.addEventListener('click', closeMenu)
  window.addEventListener('keydown', menuKey)
})
onBeforeUnmount(() => {
  window.removeEventListener('click', closeMenu)
  window.removeEventListener('keydown', menuKey)
})

function tileCount(screen: Screen): number {
  return screen.pages.reduce((count, page) => count + page.tiles.length, 0)
}

function screenName(id?: string): string {
  return screens.value.find(screen => screen.id === id)?.name ?? '—'
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

function status(player: PlayerStatus): string {
  if (player.online) return player.awake ? 'Online' : 'Standby'
  return player.lastSeen ? `Offline · ${ago(player.lastSeen)}` : 'Never connected'
}

</script>

<template>
  <div class="dashboard">
    <NotificationComposer v-if="notificationTargets !== null" :initial-targets="notificationTargets" @close="notificationTargets = null" />
    <SettingsDialog v-if="settingsOpen" @close="settingsOpen = false" />
    <DeviceInfoDialog v-if="infoDeviceId" :device-id="infoDeviceId" @close="infoDeviceId = null" />
    <EnrollTabletPanel v-if="enrolling" @close="enrolling = false" />

    <header class="dashboard-header">
      <div class="brand"><img class="brand-icon" :src="brandIcon" alt="" width="30" height="30" /><span>Remote Screens</span></div>
      <nav aria-label="Main navigation">
        <button class="nav-tab selected" aria-current="page"><MdiIcon name="view-grid" :size="22" /> Screens</button>
        <button class="nav-tab" @click="emit('themes')"><MdiIcon name="palette-outline" :size="22" /> Themes</button>
      </nav>
      <div class="connection" role="status">
        <MdiIcon name="home-assistant" :size="23" />
        <span class="dot" :class="{ on: state.info?.connected }" />
        <span>{{ state.info?.connected ? 'Connected to' : 'Not connected to' }} {{ state.info?.locationName || 'Home Assistant' }}</span>
      </div>
      <span v-if="state.info?.demo" class="demo-badge">Demo</span>
      <button class="settings-button" aria-label="Settings" title="Settings" @click="settingsOpen = true"><MdiIcon name="cog-outline" :size="24" /></button>
    </header>

    <main class="dashboard-content">
      <section aria-labelledby="screens-title">
        <div class="section-heading screens-heading">
          <div>
            <h1 id="screens-title">Your screens</h1>
            <p>Design and manage your Remote Screens dashboards for your wall tablets.</p>
          </div>
          <div class="screen-actions" role="group" aria-label="Screen actions">
            <button class="secondary-action" title="Add a screen from a file" @click="chooseFile"><MdiIcon name="download" :size="18" /> Import</button>
            <button class="primary" :aria-expanded="creating" @click="creating = !creating"><MdiIcon name="plus" :size="20" /> Create screen</button>
          </div>
          <input ref="fileInput" class="file" type="file" accept=".json,application/json" @change="importChosen" />
        </div>

      <div v-if="creating" class="card create">
        <label class="field grow"
          >Name<input v-model="newName" placeholder="Kitchen" @keyup.enter="create"
        /></label>
        <label class="field grow">
          Sized for
          <select v-model="newSize">
            <option v-for="(c, i) in sizeChoices" :key="c.label" :value="String(i)">
              {{ c.label }}
            </option>
          </select>
        </label>
        <template v-if="custom">
          <label class="field"
            >Width<input v-model.number="customW" type="number" min="1" step="10"
          /></label>
          <label class="field"
            >Height<input v-model.number="customH" type="number" min="1" step="10"
          /></label>
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


        <p v-if="!screens.length && !creating" class="card empty">No screens yet. Make one, then pair a tablet with it.</p>
        <div class="screens">
          <article v-for="s in screens" :key="s.id" class="screen">
            <button class="screen-open" :aria-label="'Edit ' + s.name" @click="emit('edit', s)" />
            <ScreenPreview :screen="s" />
            <div class="screen-footer">
              <div class="screen-description">
                <h2>{{ s.name }}</h2>
                <p><MdiIcon name="layers-outline" :size="17" /> {{ s.pages.length }} {{ s.pages.length === 1 ? 'page' : 'pages' }} · {{ tileCount(s) }} {{ tileCount(s) === 1 ? 'tile' : 'tiles' }}</p>
              </div>
              <div class="screen-tablets">
                <span v-for="p in playersOf(s.id)" :key="p.deviceId" class="chip" :title="status(p)"><MdiIcon name="tablet" :size="17" /> {{ p.name }}</span>
                <span v-if="!playersOf(s.id).length" class="unassigned">No tablet assigned</span>
              </div>
              <div class="menu">
                <button class="icon quiet" :aria-label="'Actions for ' + s.name" :aria-expanded="screenMenu === s.id" @click.stop="screenMenu = screenMenu === s.id ? null : s.id"><MdiIcon name="dots-vertical" :size="22" /></button>
                <div v-if="screenMenu === s.id" class="card popup" @click.stop>
                  <button @click="screenMenu = null; duplicate(s)"><MdiIcon name="content-copy" :size="18" /> Duplicate</button>
                  <button @click="screenMenu = null; exportScreen(s)"><MdiIcon name="download" :size="18" /> Export</button>
                  <button class="danger" @click="screenMenu = null; remove(s)"><MdiIcon name="delete-outline" :size="18" /> Delete</button>
                </div>
              </div>
            </div>
          </article>
        </div>
      </section>

      <section class="tablet-section" aria-labelledby="tablets-title">
        <div class="section-heading">
          <div>
            <h2 id="tablets-title">Tablets</h2>
            <p>Manage your Android tablets, assign screens and run device tools.</p>
          </div>
          <div class="segmented" role="group" aria-label="Tablet actions">
            <button :class="{ active: intercomOpen }" :aria-expanded="intercomOpen" aria-controls="intercom-desk" @click="intercomOpen = !intercomOpen"><MdiIcon name="microphone-outline" :size="20" /> Intercom</button>
            <button :class="{ active: adbOpen }" :aria-expanded="adbOpen" aria-controls="adb-panel" @click="adbOpen = !adbOpen"><MdiIcon name="console" :size="20" /> ADB</button>
            <button :disabled="!adopted.length" @click="notificationTargets = []"><MdiIcon name="bell-outline" :size="20" /> Send notification</button>
          </div>
        </div>

        <div v-if="waiting.length" class="pairing" aria-label="Tablets waiting to pair">
          <div v-for="p in waiting" :key="p.deviceId" class="pairing-row">
            <MdiIcon class="pairing-icon" name="tablet" :size="42" />
            <div class="pairing-copy">
              <h3>{{ waiting.length === 1 ? 'Tablet waiting to pair' : p.name + ' waiting to pair' }}</h3>
              <p>Check this code matches your tablet.</p>
              <small>{{ p.name }} · {{ p.widthDp }} × {{ p.heightDp }} dp</small>
            </div>
            <div class="pairing-code"><span>Pairing code</span><strong>{{ p.pairingCode ?? '—' }}</strong></div>
            <label class="pairing-select">Assign screen
              <select :value="assign[p.deviceId] || screens[0]?.id || ''" :disabled="!screens.length" @change="assign[p.deviceId] = ($event.target as HTMLSelectElement).value">
                <option v-if="!screens.length" value="">Create a screen first</option>
                <option v-for="s in screens" :key="s.id" :value="s.id">{{ s.name }}</option>
              </select>
            </label>
            <button class="primary pair-button" :disabled="!screens.length" @click="adopt(p)">Pair</button>
          </div>
        </div>

        <IntercomDesk v-if="intercomOpen" id="intercom-desk" class="intercom-panel" />
        <AdbPanel v-if="adbOpen" id="adb-panel" />

        <div class="tablet-setup">
          <p><MdiIcon name="plus-circle-outline" :size="18" /> Add a tablet: install the player app, or enroll a factory-reset tablet as a kiosk.</p>
          <div class="setup-actions">
            <a v-if="state.info?.playerApp" href="api/player.apk"><MdiIcon name="download" :size="18" /> Download app ({{ state.info.playerApp }})</a>
            <a v-else :href="RELEASES" target="_blank" rel="noopener"><MdiIcon name="download" :size="18" /> Download app <span class="sr-only">from GitHub releases</span></a>
            <button aria-haspopup="dialog" :aria-expanded="enrolling" @click="enrolling = true"><MdiIcon name="qrcode-scan" :size="18" /> Enroll a tablet</button>
          </div>
        </div>

        <div class="device-list" role="table" aria-label="Paired tablets">
          <div v-if="adopted.length" class="device-row labels" role="row">
            <span role="columnheader">Device</span><span role="columnheader">State</span><span role="columnheader">Battery</span><span role="columnheader">Assigned screen</span><span role="columnheader" class="sr-only">Actions</span>
          </div>
          <div v-for="p in adopted" :key="p.deviceId" class="device-row" role="row">
            <div class="device-name" role="cell">
              <MdiIcon name="tablet" :size="27" />
              <div><button class="device-info-link" :data-device-info="p.deviceId" :aria-label="p.name + ' device info'" aria-haspopup="dialog" @click="infoDeviceId = p.deviceId">{{ p.name }}</button><small>{{ p.widthDp }} × {{ p.heightDp }} dp
                <span v-if="p.kiosk === 'locked' || p.kiosk === 'pinned'" class="kiosk-status" :title="p.online ? (p.kiosk === 'locked' ? 'Kiosk locked' : 'Kiosk pinned') : 'Last reported: kiosk ' + p.kiosk">
                  <MdiIcon :name="p.kiosk === 'locked' ? 'lock' : 'pin'" :size="15" />
                  <span class="sr-only">{{ p.online ? '' : 'Last reported: ' }}{{ p.kiosk === 'locked' ? 'Kiosk locked' : 'Kiosk pinned' }}</span>
                </span>
              </small></div>
            </div>
            <span class="device-status" role="cell" :class="p.online ? (p.awake ? 'online' : 'standby') : 'offline'" :title="status(p)">
              <span class="dot" /> {{ p.online ? (p.awake ? 'Online' : 'Standby') : 'Offline' }}
            </span>
            <span class="battery" role="cell" :aria-label="p.battery != null ? p.battery + ' percent battery' : 'Battery unknown'" :class="{ charging: p.charging }"><MdiIcon :name="p.battery == null ? 'battery-unknown' : p.charging ? 'battery-charging' : 'battery-outline'" :size="22" /> {{ p.battery != null ? p.battery + '%' : '—' }}</span>
            <div role="cell" class="assignment-cell"><select class="screen-assignment" :value="p.screenId" :aria-label="'Screen shown on ' + p.name" @change="move(p, ($event.target as HTMLSelectElement).value)">
              <option v-for="s in screens" :key="s.id" :value="s.id">{{ s.name }}</option>
              <option v-if="!screens.some(s => s.id === p.screenId)" :value="p.screenId">{{ screenName(p.screenId) }}</option>
            </select></div>
            <div class="device-actions" role="cell">
              <button class="identify" :disabled="!p.online" title="Flash this tablet's name on its display" @click="identify(p)"><MdiIcon name="crosshairs-gps" :size="18" /> Identify</button>
              <div class="menu">
                <button class="icon quiet" :aria-label="'Actions for ' + p.name" :aria-expanded="menu === p.deviceId" @click.stop="menu = menu === p.deviceId ? null : p.deviceId"><MdiIcon name="dots-vertical" :size="22" /></button>
                <div v-if="menu === p.deviceId" class="card popup" @click.stop>
                  <button @click="menu = null; infoDeviceId = p.deviceId"><MdiIcon name="information-outline" :size="18" /> Device info</button>
                  <button @click="notifyTablet(p)"><MdiIcon name="bell-outline" :size="18" /> Send notification</button>
                  <button @click="rename(p)"><MdiIcon name="pencil-outline" :size="18" /> Rename</button>
                  <button class="danger" @click="forget(p)"><MdiIcon name="link-off" :size="18" /> Unpair</button>
                </div>
              </div>
            </div>
          </div>
          <p v-if="!adopted.length" class="empty">No paired tablets yet. Install the player app on a tablet to get started.</p>
        </div>


      </section>
    </main>
  </div>
</template>
