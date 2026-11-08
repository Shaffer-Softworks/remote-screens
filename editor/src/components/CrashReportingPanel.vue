<script setup lang="ts">
import { computed, ref } from 'vue'
import { api } from '../api'
import SentryMappingsPanel from './SentryMappingsPanel.vue'
import type { SentrySettings } from '../model/types'
import { state, toast } from '../store'

defineProps<{ expanded?: boolean }>()

const settings = ref<SentrySettings>({
  enabled: false,
  dsn: '',
  environment: 'production',
  anrEnabled: true,
  ...state.layout?.preferences.sentry,
})
const saving = ref(false)
const saveMessage = ref('')
const saveError = ref('')
const testError = ref('')
const testing = ref(false)
const testTablet = ref('')
const testResult = ref<{ status: string; eventId?: string } | null>(null)
const tablets = computed(() => state.players.filter(player => player.screenId && player.online))
const statusText: Record<string, string> = {
  accepted: 'Sentry accepted the test event. Search for its event ID in your Sentry project.',
  disabled: 'Sentry is disabled on this tablet. Save the configuration and retry.',
  unsupported: 'Update this tablet’s player app to use the test action.',
  offline: 'The tablet disconnected. Reconnect it and retry.',
  busy: 'A test is already running on this tablet.',
  dropped: 'The SDK dropped the test event. Check the Sentry configuration.',
  failed: 'Delivery failed. Check the DSN, network access, and Sentry quota.',
  timeout: 'No delivery confirmation arrived. The event may be queued; check Sentry before retrying.',
}

async function test(): Promise<void> {
  if (!testTablet.value || testing.value) return
  testing.value = true
  testResult.value = null
  testError.value = ''
  try {
    testResult.value = await api.testSentry(testTablet.value)
  } catch (error) {
    testError.value = error instanceof Error ? error.message : 'Could not send the test event.'
  } finally {
    testing.value = false
  }
}

async function save(): Promise<void> {
  if (!state.layout || saving.value) return
  saving.value = true
  saveMessage.value = ''
  saveError.value = ''
  try {
    const saved = await api.preferences({
        ...state.layout!.preferences,
        sentry: {
          ...settings.value,
          dsn: settings.value.dsn.trim(),
          environment: settings.value.environment.trim() || 'production',
        },
      })
    state.layout.preferences = saved
    settings.value = saved.sentry ?? settings.value
    saveMessage.value = 'Crash reporting saved. Connected tablets receive it now; offline tablets on reconnect.'
    toast(saveMessage.value)
  } catch (error) {
    saveError.value = error instanceof Error ? error.message : 'Could not save crash reporting.'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <details class="card diagnostics" :open="expanded">
    <summary>Sentry crash reporting</summary>
    <form @submit.prevent="save">
      <p class="muted">
        Optional reporting for all paired tablets. Sends app crashes, native crashes and, when enabled,
        app-not-responding reports to your Sentry project. Reports include stack traces, app and device
        details, and may include error messages. No screenshots, session replay or performance tracing.
      </p>
      <label class="check">
        <input v-model="settings.enabled" type="checkbox" /> Enable Sentry on paired tablets
      </label>
      <label>
        Public DSN
        <input
          v-model="settings.dsn"
          type="text"
          placeholder="https://public-key@o123.ingest.sentry.io/456"
          :required="settings.enabled"
          maxlength="2048"
          autocomplete="off"
          spellcheck="false"
        />
      </label>
      <label>
        Environment
        <input v-model="settings.environment" maxlength="64" placeholder="production" />
      </label>
      <label class="check">
        <input v-model="settings.anrEnabled" type="checkbox" /> Report app-not-responding events (ANRs)
      </label>
      <p class="muted">
        Tablets remember this setting across restarts and queue reports while offline. Disabling takes
        effect when each tablet receives the update. A full device freeze or power loss may leave no report.
        Requires a player version with Sentry support.
      </p>
      <button class="primary" type="submit" :disabled="saving">
        {{ saving ? 'Saving…' : 'Save crash reporting' }}
      </button>
      <p v-if="saveError" class="error" role="alert">{{ saveError }}</p>
      <p v-if="saveMessage" role="status">{{ saveMessage }}</p>
    </form>
    <SentryMappingsPanel />
    <div class="test">
      <h3>Verify a tablet</h3>
      <p class="muted">
        Sends one labeled, nonfatal event with app/device details to the tablet’s saved Sentry DSN.
        Save settings first. “Accepted” confirms the ingestion endpoint acknowledged the event;
        it may take time to appear in Sentry.
      </p>
      <label>
        Tablet
        <select v-model="testTablet" :disabled="testing">
          <option value="" disabled>Choose a connected tablet…</option>
          <option v-for="tablet in tablets" :key="tablet.deviceId" :value="tablet.deviceId">
            {{ tablet.name }}
          </option>
        </select>
      </label>
      <button type="button" :disabled="testing || !testTablet" @click="test">
        {{ testing ? 'Testing…' : 'Send test event' }}
      </button>
      <p v-if="testError" class="error" role="alert">{{ testError }}</p>
      <p v-if="testResult" role="status">
        {{ statusText[testResult.status] ?? 'Unexpected result. Check the player version.' }}
        <br v-if="testResult.eventId" />
        <code v-if="testResult.eventId">Event ID: {{ testResult.eventId }}</code>
      </p>
    </div>
  </details>
</template>

<style scoped>
.diagnostics {
  margin-block: 20px;
}
.error { color: var(--danger); }
summary {
  cursor: pointer;
  font-weight: 600;
}
form,
.test {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 16px;
  max-width: 720px;
}
label:not(.check) {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.check {
  display: flex;
  align-items: center;
  gap: 8px;
}
button {
  align-self: flex-start;
}
</style>
