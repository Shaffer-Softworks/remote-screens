<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { api, type MappingUploadStatus } from '../api'

const status = ref<MappingUploadStatus | null>(null)
const organization = ref('')
const token = ref('')
const busy = ref(false)
const error = ref('')
let timer: ReturnType<typeof setInterval> | undefined

async function refresh(): Promise<void> {
  try {
    status.value = await api.sentryMappings()
    if (!organization.value) organization.value = status.value.organization
  } catch {
    error.value = 'Could not load mapping upload status.'
  }
}

async function run(action: 'save' | 'retry' | 'clear'): Promise<void> {
  busy.value = true
  error.value = ''
  try {
    status.value = action === 'save'
      ? await api.saveSentryMappings(organization.value.trim(), token.value)
      : action === 'retry' ? await api.retrySentryMappings() : await api.clearSentryMappings()
    token.value = ''
    organization.value = status.value.organization
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'Could not update mapping uploads.'
  } finally {
    busy.value = false
  }
}

onMounted(() => {
  void refresh()
  timer = setInterval(() => { if (!busy.value) void refresh() }, 5000)
})
onUnmounted(() => clearInterval(timer))
</script>

<template>
  <section class="mappings" aria-label="Crash deobfuscation">
    <h3>Readable crash reports</h3>
    <p class="muted">
      Uploads the bundled player's R8 mapping to your Sentry Cloud project so crash reports show
      original class and method names. After setup, mappings upload automatically when crash reporting
      is enabled and when an add-on update includes a new APK. Previously bundled mappings are retained.
    </p>
    <form @submit.prevent="run('save')">
      <label>
        Sentry organization slug
        <input v-model="organization" required maxlength="100" placeholder="shaffer-softworks" autocomplete="off" />
      </label>
      <label>
        Sentry upload token
        <input
          v-model="token" type="password" :required="!status?.tokenConfigured"
          maxlength="4096" autocomplete="new-password"
          :placeholder="status?.tokenConfigured ? 'Token saved; leave blank to keep it' : 'Sentry organization upload token (org:ci)'"
        />
      </label>
      <p class="muted">
        Use a Sentry organization token with the org:ci upload scope for the project in your saved DSN.
        The token is stored only on this add-on server and is never sent to tablets or included in layout exports.
        Only JVM/Kotlin mappings are uploaded; native symbols are not included.
      </p>
      <div class="actions">
        <button type="submit" :disabled="busy">{{ busy ? 'Working…' : 'Save and upload mappings' }}</button>
        <button type="button" :disabled="busy || !status?.tokenConfigured" @click="run('retry')">Retry upload</button>
        <button type="button" :disabled="busy || !status?.tokenConfigured" @click="run('clear')">Remove saved token</button>
      </div>
    </form>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="status" role="status">{{ status.message }}</p>
    <ul v-if="status?.builds.length">
      <li v-for="build in status.builds" :key="build.version + build.uuid">
        {{ build.version }} — {{ build.uploaded ? 'Uploaded' : 'Not uploaded' }}
      </li>
    </ul>
    <p class="muted">Older APKs built before mapping support need a player update before their reports can be deobfuscated automatically.</p>
  </section>
</template>

<style scoped>
.mappings { margin-top: 24px; max-width: 720px; }
form, label { display: flex; flex-direction: column; gap: 8px; }
form { gap: 14px; }
.actions { display: flex; flex-wrap: wrap; gap: 8px; }
.error { color: var(--danger); }
</style>
