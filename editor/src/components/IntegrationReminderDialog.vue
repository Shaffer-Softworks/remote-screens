<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { api } from '../api'
import { HACS_INTEGRATION_URL } from '../model/integrationInstall'
import { state } from '../store'
import MdiIcon from './MdiIcon.vue'

const emit = defineEmits<{ close: [] }>()
const dialog = ref<HTMLDialogElement>()
const saving = ref(false)
const saveError = ref('')
let trigger: HTMLElement | null = null

onMounted(() => {
  trigger = document.activeElement instanceof HTMLElement ? document.activeElement : null
  dialog.value?.showModal()
})
onBeforeUnmount(() => {
  dialog.value?.close()
  trigger?.focus()
})

function close(): void {
  emit('close')
}

function install(): void {
  window.open(HACS_INTEGRATION_URL, '_blank', 'noopener')
  close()
}

async function dismissPermanently(): Promise<void> {
  if (!state.layout || saving.value) return
  saving.value = true
  saveError.value = ''
  try {
    const saved = await api.preferences({
      ...state.layout.preferences,
      integrationReminderDismissed: true,
    })
    state.layout.preferences = saved
    close()
  } catch (error) {
    saveError.value = error instanceof Error ? error.message : 'Could not save that preference.'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <dialog
    ref="dialog"
    class="reminder-dialog"
    aria-labelledby="integration-reminder-title"
    @cancel.prevent="close"
  >
    <header class="row">
      <MdiIcon name="home-assistant" :size="22" />
      <h2 id="integration-reminder-title" class="grow">Install the Home Assistant integration</h2>
      <button class="icon" aria-label="Close" @click="close">
        <MdiIcon name="close" :size="22" />
      </button>
    </header>
    <p class="muted">
      The Remote Screens integration is optional. It adds entities and services for each screen so you
      can control brightness, standby, pages, and alerts from Home Assistant. Install it with HACS,
      restart Home Assistant, then accept the setup prompt.
    </p>
    <div class="actions">
      <button class="primary" type="button" @click="install">
        <MdiIcon name="home-assistant" :size="18" /> Install integration
      </button>
      <button type="button" :disabled="saving" @click="close">Not now</button>
      <button type="button" :disabled="saving" @click="dismissPermanently">
        {{ saving ? 'Saving…' : "Don't remind me" }}
      </button>
    </div>
    <p v-if="saveError" class="error" role="alert">{{ saveError }}</p>
  </dialog>
</template>

<style scoped>
.reminder-dialog {
  width: min(520px, calc(100vw - 32px));
  padding: 24px;
}
.reminder-dialog::backdrop {
  background: rgb(0 0 0 / 65%);
}
header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}
.muted {
  margin: 0 0 20px;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}
.actions .primary {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.error {
  margin: 12px 0 0;
  color: var(--danger);
}
</style>
