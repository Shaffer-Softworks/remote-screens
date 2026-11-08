<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { api } from '../api'
import { deviceInfoSections, infoTimestamp } from '../model/deviceInfo'
import { state } from '../store'
import MdiIcon from './MdiIcon.vue'

const props = defineProps<{ deviceId: string }>()
const emit = defineEmits<{ close: [] }>()
const dialog = ref<HTMLDialogElement>()
const refreshing = ref(false)
const error = ref('')
const player = computed(() => state.players.find(player => player.deviceId === props.deviceId))
const screen = computed(() => state.layout?.screens.find(screen => screen.id === player.value?.screenId))
const sections = computed(() => player.value ? deviceInfoSections(player.value, screen.value) : [])
let trigger: HTMLElement | null = null

onMounted(() => {
  trigger = document.activeElement instanceof HTMLElement ? document.activeElement : null
  dialog.value?.showModal()
})
onBeforeUnmount(() => {
  dialog.value?.close()
  // An overflow action may no longer be mounted; return to the device's persistent info button.
  const fallback = Array.from(document.querySelectorAll<HTMLButtonElement>('[data-device-info]'))
    .find(button => button.dataset.deviceInfo === props.deviceId)
  if (trigger?.isConnected) trigger.focus()
  else fallback?.focus()
})

async function refresh(): Promise<void> {
  if (refreshing.value) return
  refreshing.value = true
  error.value = ''
  try {
    state.players = await api.players()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'Could not refresh device information.'
  } finally {
    refreshing.value = false
  }
}
</script>

<template>
  <dialog
    ref="dialog"
    class="device-info-dialog"
    aria-labelledby="device-info-title"
    @cancel.prevent="emit('close')"
  >
    <header class="info-heading">
      <div>
        <h2 id="device-info-title">{{ player?.name ?? 'Device info' }}</h2>
        <p>Device information</p>
      </div>
      <button :disabled="refreshing" @click="refresh">
        <MdiIcon name="refresh" :size="18" /> {{ refreshing ? 'Refreshing…' : 'Refresh' }}
      </button>
      <button class="icon" aria-label="Close device info" @click="emit('close')">
        <MdiIcon name="close" :size="22" />
      </button>
    </header>

    <p v-if="error" class="info-error" role="alert">{{ error }}</p>
    <template v-if="player">
      <div class="info-notice" role="status">
        <span class="dot" :class="{ on: player.online }" />
        <div>
          <strong>{{ player.online ? (player.awake ? 'Online · Awake' : 'Online · Standby') : 'Offline' }}</strong>
          <p v-if="!player.online">Live readings are unavailable while offline. Any values shown are last reported.</p>
          <p v-else-if="!player.telemetry">
            No sensor report available yet. This can happen when reporting is disabled, the player has just connected, or the app needs an update.
          </p>
          <p v-else>Sensor report: {{ infoTimestamp(player.telemetryAt) }}</p>
        </div>
      </div>
      <p class="info-caption">Updates automatically with the device list. Not reported means the tablet has not supplied a value.</p>
      <div class="info-sections">
        <section v-for="section in sections" :key="section.title" class="info-section">
          <h3><MdiIcon :name="section.icon" :size="20" /> {{ section.title }}</h3>
          <p v-if="section.title === 'Location'" class="section-note">Only reported when location sharing is enabled and Android supplies a fix.</p>
          <dl>
            <div v-for="row in section.rows" :key="row.label" class="info-row">
              <dt>{{ row.label }}</dt>
              <dd :class="{ missing: row.value === 'Not reported' }">{{ row.value }}</dd>
            </div>
          </dl>
        </section>
      </div>
    </template>
    <p v-else class="info-notice" role="status">This tablet is no longer in the device list.</p>
  </dialog>
</template>

<style scoped>
.device-info-dialog {
  width: min(1080px, calc(100vw - 32px));
  max-height: calc(100dvh - 48px);
  padding: 0 24px 24px;
  overflow: auto;
}
.device-info-dialog::backdrop { background: rgb(0 0 0 / 65%); }
.info-heading {
  position: sticky;
  top: 0;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 22px 0 16px;
  border-bottom: 1px solid var(--line);
  background: var(--bg);
}
.info-heading > div { flex: 1; min-width: 0; }
.info-heading h2 { font-size: 24px; font-weight: 650; overflow-wrap: anywhere; }
.info-heading p { margin: 5px 0 0; color: var(--muted); }
.info-notice {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  margin: 18px 0 12px;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: #1b232e;
}
.info-notice .dot { margin-top: 4px; }
.info-notice p { margin: 5px 0 0; color: var(--muted); font-size: 13px; line-height: 1.5; }
.info-caption { color: var(--muted); font-size: 12px; margin: 0 0 18px; line-height: 1.5; }
.info-error { color: var(--danger); }
.info-sections { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); align-items: start; gap: 16px; }
.info-section { border: 1px solid var(--line); border-radius: 10px; background: var(--card); padding: 16px; min-width: 0; }
.info-section h3 { display: flex; gap: 9px; align-items: center; color: var(--ink); font-size: 16px; font-weight: 600; text-transform: none; letter-spacing: 0; }
.info-section h3 svg { color: var(--accent); }
.section-note { color: var(--muted); font-size: 12px; line-height: 1.5; margin: 10px 0 0; }
dl { margin: 12px 0 0; }
.info-row { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.15fr); gap: 14px; padding: 9px 0; font-size: 13px; }
.info-row + .info-row { border-top: 1px solid #383c42; }
dt { color: var(--muted); }
dd { margin: 0; text-align: right; overflow-wrap: anywhere; font-variant-numeric: tabular-nums; }
dd.missing { color: var(--muted); }
@media (max-width: 700px) {
  .device-info-dialog { padding-inline: 16px; }
  .info-sections { grid-template-columns: minmax(0, 1fr); }
  .info-heading { flex-wrap: wrap; gap: 8px; }
  .info-heading h2 { font-size: 20px; }
  .info-heading > div { flex-basis: calc(100% - 60px); }
  .info-heading > button:first-of-type { order: 3; width: 100%; }
}
</style>
