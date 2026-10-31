<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { api } from '../api'
import { state } from '../store'
import { notificationError, type NotificationRequest, type NotificationResult } from '../model/notifications'
import MdiIcon from './MdiIcon.vue'

const props = defineProps<{ initialTargets: string[] }>()
const emit = defineEmits<{ close: [] }>()
const dialog = ref<HTMLDialogElement>()
const recipients = computed(() => state.players.filter(player => player.screenId))
const draft = reactive<NotificationRequest>({
  title: '', message: '', targets: [...props.initialTargets], all: false,
  presentation: 'banner', level: 'info', timeout: 10, wake: false,
})
const buttons = reactive<{ title: string; service: string; action: string; data: string }[]>([])
const untilAcknowledged = ref(false)
const sending = ref(false)
const error = ref('')
const result = ref<NotificationResult>()
const acknowledged = computed(() => draft.presentation === 'alert' && untilAcknowledged.value)
watch(() => draft.presentation, presentation => {
  draft.timeout = presentation === 'banner' ? 10 : 30
  untilAcknowledged.value = false
  draft.camera = undefined
})
watch(() => draft.all, all => { if (all) draft.targets = [] })
onMounted(() => dialog.value?.showModal())

function close(): void {
  if (!sending.value) emit('close')
}

async function send(): Promise<void> {
  if (sending.value) return
  error.value = ''
  result.value = undefined
  let request: NotificationRequest
  try {
    request = {
      ...draft, title: draft.title.trim(), timeout: acknowledged.value ? 0 : draft.timeout,
      buttons: buttons.map(button => {
        const data: unknown = button.data.trim() ? JSON.parse(button.data) : undefined
        if (data !== undefined && (data === null || typeof data !== 'object' || Array.isArray(data)))
          throw new Error('Button data must be a JSON object.')
        return { title: button.title.trim(), service: button.service.trim() || undefined,
          action: button.action.trim() || undefined, data: data as Record<string, unknown> | undefined }
      }),
    }
    const invalid = notificationError(request)
    if (invalid) throw new Error(invalid)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'Check the notification fields.'
    return
  }
  sending.value = true
  try {
    result.value = await api.sendNotification(request)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'Could not send. Your draft is still here.'
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <dialog ref="dialog" class="notification-dialog" aria-labelledby="notification-title" @cancel.prevent="close">
    <form @submit.prevent="send">
      <header class="row">
        <h2 id="notification-title" class="grow">Send notification</h2>
        <button type="button" :disabled="sending" @click="close">Close</button>
      </header>
      <p class="muted">Send immediately to connected tablets. Offline tablets are skipped.</p>
      <fieldset :disabled="sending">
        <legend>Recipients</legend>
        <label class="check"><input v-model="draft.all" type="checkbox" /> All paired tablets</label>
        <div v-if="!draft.all" class="recipient-list">
          <label v-for="player in recipients" :key="player.deviceId" class="check">
            <input v-model="draft.targets" type="checkbox" :value="player.deviceId" />
            {{ player.name }} <span class="muted">{{ player.online ? 'Online' : 'Offline · will be skipped' }}</span>
          </label>
          <details v-if="state.layout?.screens.length">
            <summary>Target screens instead</summary>
            <label v-for="screen in state.layout.screens" :key="screen.id" class="check">
              <input v-model="draft.targets" type="checkbox" :value="screen.id" /> {{ screen.name }} · all its tablets
            </label>
          </details>
        </div>
      </fieldset>
      <fieldset :disabled="sending" class="fields">
        <legend>Message</legend>
        <label class="field">Title<input v-model="draft.title" required maxlength="100" placeholder="Laundry is ready" /></label>
        <label class="field">Message<textarea v-model="draft.message" maxlength="500" rows="3" placeholder="The washing machine has finished." /></label>
        <div class="row wrap">
          <label class="field grow">Presentation
            <select v-model="draft.presentation"><option value="banner">Banner</option><option value="alert">Alert</option></select>
          </label>
          <label class="field grow">Level
            <select v-model="draft.level"><option value="info">Information</option><option value="warning">Warning</option><option value="danger">Danger</option></select>
          </label>
          <label v-if="!acknowledged" class="field">Duration (seconds)
            <input v-model.number="draft.timeout" type="number" :min="draft.presentation === 'banner' ? 1 : 0" max="3600" required />
          </label>
        </div>
        <label v-if="draft.presentation === 'alert'" class="check">
          <input v-model="untilAcknowledged" type="checkbox" /> Until acknowledged
        </label>
        <label class="check"><input v-model="draft.wake" type="checkbox" /> Wake screen</label>
        <details>
          <summary>Camera, icon, actions and tag</summary>
          <label class="field">Icon<input v-model="draft.icon" placeholder="mdi:bell-ring" /></label>
          <label v-if="draft.presentation === 'alert'" class="field">Camera entity<input v-model="draft.camera" placeholder="camera.front_door" /></label>
          <label class="field">Tag<input v-model="draft.tag" placeholder="laundry" /></label>
          <p class="muted">Buttons fire remote_screens_alert_action and can optionally call a Home Assistant service.</p>
          <section v-for="(button, index) in buttons" :key="index" class="button-fields">
            <label class="field">Button title<input v-model="button.title" required maxlength="60" /></label>
            <label class="field">Action identifier<input v-model="button.action" placeholder="acknowledge" /></label>
            <label class="field">Service (optional)<input v-model="button.service" placeholder="lock.open" /></label>
            <label class="field">Service data (JSON object)<textarea v-model="button.data" rows="2" placeholder='{"entity_id":"lock.front_door"}' /></label>
            <button type="button" @click="buttons.splice(index, 1)">Remove button</button>
          </section>
          <button v-if="buttons.length < 2" type="button" @click="buttons.push({ title: '', service: '', action: '', data: '' })">Add action button</button>
        </details>
      </fieldset>
      <section class="preview" :class="[draft.presentation, draft.level]" aria-label="Notification preview">
        <MdiIcon :name="draft.icon || (draft.level === 'info' ? 'bell-ring' : 'alert')" :size="28" />
        <strong>{{ draft.title || 'Notification title' }}</strong>
        <p>{{ draft.message || 'Your message appears here.' }}</p>
        <div class="row wrap">
          <button v-for="(button, index) in buttons" :key="index" type="button" disabled>{{ button.title || 'Action' }}</button>
          <button v-if="acknowledged && !buttons.length" type="button" disabled>Acknowledge</button>
          <span v-if="draft.presentation === 'alert'" class="muted">Close</span>
        </div>
      </section>
      <p v-if="error" role="alert" class="error">{{ error }}</p>
      <section v-if="result" aria-live="polite">
        <p v-if="!result.devices.length">No paired tablets match these recipients.</p>
        <p v-else>Dispatch results (sent means sent to the connection, not read):</p>
        <ul>
          <li v-for="device in result.devices" :key="device.deviceId">
            {{ device.name }}: {{ device.status === 'unsupported' ? 'Unsupported · update the player or use an alert with wake enabled' : device.status }}
          </li>
        </ul>
      </section>
      <footer class="row">
        <span class="grow muted">Silent · local delivery</span>
        <button class="primary" :disabled="sending || (!draft.all && !draft.targets.length)">
          {{ sending ? 'Sending…' : 'Send notification' }}
        </button>
      </footer>
    </form>
  </dialog>
</template>

<style scoped>
.notification-dialog { width: min(680px, calc(100vw - 32px)); max-height: calc(100dvh - 32px); overflow-y: auto; padding: 24px; color: var(--ink); background: var(--card); border: 1px solid var(--line); border-radius: 18px; }
.notification-dialog::backdrop { background: rgb(0 0 0 / 45%); }
form, .fields { display: grid; gap: 16px; }
fieldset { min-width: 0; border: 1px solid var(--line); border-radius: 12px; padding: 16px; }
legend { font-weight: 600; padding: 0 6px; }
.check { display: flex; align-items: center; gap: 8px; padding: 6px 0; flex-wrap: wrap; }
.check input { width: auto; }
.recipient-list { max-height: 220px; overflow-y: auto; }
.wrap { flex-wrap: wrap; }
summary { cursor: pointer; }
details .field, .button-fields { margin-top: 12px; }
.button-fields { padding: 12px; border: 1px solid var(--line); border-radius: 10px; }
.preview { padding: 20px; background: var(--bg); border: 1px solid var(--line); border-left: 4px solid var(--accent); border-radius: 14px; overflow-wrap: anywhere; }
.preview.alert { text-align: center; }
.preview.warning { border-left-color: #b7791f; }
.preview.danger, .error { color: var(--danger); }
.preview strong { display: block; margin-top: 8px; }
.preview p { white-space: pre-wrap; }
.preview .row { justify-content: center; }
header h2 { margin: 0; }
</style>
