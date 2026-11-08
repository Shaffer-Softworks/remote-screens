<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import CrashReportingPanel from './CrashReportingPanel.vue'
import MdiIcon from './MdiIcon.vue'

const emit = defineEmits<{ close: [] }>()
const dialog = ref<HTMLDialogElement>()
let trigger: HTMLElement | null = null
onMounted(() => {
  trigger = document.activeElement instanceof HTMLElement ? document.activeElement : null
  dialog.value?.showModal()
})
onBeforeUnmount(() => {
  dialog.value?.close()
  trigger?.focus()
})
</script>

<template>
  <dialog
    ref="dialog"
    class="settings-dialog"
    aria-labelledby="settings-title"
    @cancel.prevent="emit('close')"
  >
    <header class="row">
      <h2 id="settings-title" class="grow">Settings</h2>
      <button class="icon" aria-label="Close settings" @click="emit('close')">
        <MdiIcon name="close" :size="22" />
      </button>
    </header>
    <CrashReportingPanel expanded />
  </dialog>
</template>

<style scoped>
.settings-dialog {
  width: min(780px, calc(100vw - 32px));
  max-height: calc(100dvh - 48px);
  padding: 24px;
  overflow: auto;
}
.settings-dialog::backdrop { background: rgb(0 0 0 / 65%); }
header h2 { font-size: 24px; font-weight: 600; }
.settings-dialog :deep(.diagnostics) { padding: 0; border: 0; box-shadow: none; background: transparent; }
</style>
