<script setup lang="ts">
import { ref } from 'vue'
import { api } from '../api'
import type { SentrySettings } from '../model/types'
import { attempt, state, toast } from '../store'

const settings = ref<SentrySettings>({
  enabled: false,
  dsn: '',
  environment: 'production',
  anrEnabled: true,
  ...state.layout?.preferences.sentry,
})
const saving = ref(false)

async function save(): Promise<void> {
  if (!state.layout || saving.value) return
  saving.value = true
  try {
    const saved = await attempt(() =>
      api.preferences({
        ...state.layout!.preferences,
        sentry: {
          ...settings.value,
          dsn: settings.value.dsn.trim(),
          environment: settings.value.environment.trim() || 'production',
        },
      }),
    )
    if (!saved) return
    state.layout.preferences = saved
    settings.value = saved.sentry ?? settings.value
    toast('Crash reporting saved. Connected tablets receive it now; offline tablets on reconnect.')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <details class="card diagnostics">
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
    </form>
  </details>
</template>

<style scoped>
.diagnostics {
  margin-block: 20px;
}
summary {
  cursor: pointer;
  font-weight: 600;
}
form {
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
