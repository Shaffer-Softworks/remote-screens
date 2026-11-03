<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { state } from '../store'

interface Device { serial: string; state: string; description: string }
interface Status { devices: Device[]; associations: Record<string, string> }
const status = ref<Status>({ devices: [], associations: {} })
const endpoint = ref('')
const pairingEndpoint = ref('')
const code = ref('')
const busy = ref(false)
const message = ref('')
const error = ref('')

async function request(operation = '', body?: unknown): Promise<Response> {
  const response = await fetch(`api/adb${operation ? '/' + operation : ''}`, {
    method: operation ? 'POST' : 'GET',
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  })
  if (!response.ok) {
    const text = await response.text()
    let detail = text
    try { detail = JSON.parse(text).error ?? text } catch { /* plain response */ }
    throw new Error(detail || response.statusText)
  }
  return response
}
async function refresh(): Promise<void> {
  status.value = await (await request()).json()
}
async function perform(operation: string, body: unknown): Promise<void> {
  if (busy.value) return
  busy.value = true
  error.value = ''
  message.value = ''
  try {
    const response = await request(operation, body)
    if (operation === 'logs' || operation === 'screenshot') {
      const url = URL.createObjectURL(await response.blob())
      const link = document.createElement('a')
      link.href = url
      link.download = operation === 'logs' ? 'tablet-logcat.txt' : 'tablet-screenshot.png'
      link.click()
      setTimeout(() => URL.revokeObjectURL(url), 1000)
    } else {
      message.value = (await response.json()).message
    }
    if (operation === 'pair') code.value = ''
    await refresh()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : String(cause)
  } finally {
    busy.value = false
  }
}
async function reload(): Promise<void> {
  busy.value = true
  error.value = ''
  try { await refresh() } catch (cause) {
    error.value = cause instanceof Error ? cause.message : String(cause)
  } finally { busy.value = false }
}
function install(serial: string): void {
  if (confirm('Install the packaged player APK on this device? It must use the same signing key as the installed app.')) {
    void perform('install', { serial })
  }
}
onMounted(reload)
</script>

<template>
  <section class="card adb-panel" aria-label="ADB tablet management" :aria-busy="busy">
    <div class="row">
      <h3 class="grow">ADB tablet management</h3>
      <button :disabled="busy" @click="reload">Refresh</button>
    </div>
    <p class="muted">
      Enable Wireless debugging on the tablet. Use its pairing address and code first, then
      connect using the separate debugging address. The tablet must be reachable from the add-on.
    </p>
    <form class="row controls" @submit.prevent="perform('pair', { endpoint: pairingEndpoint, code })">
      <label class="field">Pairing address<input v-model.trim="pairingEndpoint" placeholder="192.168.1.50:37001" required /></label>
      <label class="field">Pairing code<input v-model.trim="code" inputmode="numeric" pattern="[0-9]{6}" maxlength="6" autocomplete="off" required /></label>
      <button :disabled="busy">Pair ADB</button>
    </form>
    <form class="row controls" @submit.prevent="perform('connect', { endpoint })">
      <label class="field">Debugging address<input v-model.trim="endpoint" placeholder="192.168.1.50:41001" required /></label>
      <button :disabled="busy">Connect</button>
    </form>
    <p v-if="error" role="alert" class="error">{{ error }}</p>
    <p v-if="message" role="status">{{ message }}</p>
    <p v-if="!status.devices.length" class="muted">No ADB devices connected.</p>
    <article v-for="device in status.devices" :key="device.serial" class="device">
      <strong>{{ device.serial }}</strong> · {{ device.state }}
      <p class="muted">{{ device.description }}</p>
      <div class="row controls">
        <button :disabled="busy || device.state !== 'device'" @click="perform('restart', { serial: device.serial })">Restart player</button>
        <button :disabled="busy || device.state !== 'device'" @click="install(device.serial)">Install player APK</button>
        <button :disabled="busy || device.state !== 'device'" @click="perform('screenshot', { serial: device.serial })">Screenshot</button>
        <button :disabled="busy || device.state !== 'device'" @click="perform('logs', { serial: device.serial })">Download logs</button>
        <button :disabled="busy || !device.serial.includes(':')" @click="perform('disconnect', { serial: device.serial })">Disconnect</button>
      </div>
    </article>
    <div v-for="player in state.players" :key="player.deviceId" class="row controls">
      <label class="field grow">
        ADB device for {{ player.name }}
        <select
          :value="status.associations[player.deviceId] ?? ''" :disabled="busy"
          @change="perform('associate', { playerId: player.deviceId, serial: ($event.target as HTMLSelectElement).value })">
          <option value="">None</option>
          <option
            v-if="status.associations[player.deviceId] && !status.devices.some(d => d.serial === status.associations[player.deviceId])"
            :value="status.associations[player.deviceId]">{{ status.associations[player.deviceId] }} (disconnected)</option>
          <option v-for="device in status.devices" :key="device.serial" :value="device.serial">{{ device.serial }}</option>
        </select>
      </label>
    </div>
    <p class="muted">Disconnect closes a connection. To revoke this add-on's authorization, forget its paired computer in the tablet's Wireless debugging settings. Logs include recent system diagnostics.</p>
  </section>
</template>

<style scoped>
.adb-panel { margin-bottom: 14px; }
.controls { flex-wrap: wrap; align-items: flex-end; gap: 10px; margin-top: 12px; }
.device { border-top: 1px solid var(--line); padding: 14px 0; overflow-wrap: anywhere; }
.error { color: var(--danger); }
input { max-width: 100%; }
</style>
