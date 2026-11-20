<script setup lang="ts">
// The free-form page as ESPHome LVGL, and Save & send of that page to a panel.
import { onBeforeUnmount, ref, watch } from 'vue'
import { ApiError, api } from '../api'
import type { Page, Screen } from '../model/types'

const REFRESH_MS = 400
const DEVICE_KEY = 'rs.esphomeDevice'

const props = defineProps<{
  screen: Screen
  page: Page
  device: { w: number; h: number }
  themeId: string
}>()

const yaml = ref('')
const payload = ref('')
const skipped = ref<string[]>([])
const status = ref('Preparing the panel layout…')
const failed = ref(false)
const sending = ref(false)
const deviceName = ref(localStorage.getItem(DEVICE_KEY) ?? '')

let timer = 0

watch(
  () =>
    JSON.stringify({
      root: props.page.root ?? null,
      themeId: props.themeId,
      width: props.device.w,
      height: props.device.h,
      screenId: props.screen.id,
      pageId: props.page.id,
    }),
  () => {
    window.clearTimeout(timer)
    timer = window.setTimeout(() => {
      void refresh()
    }, REFRESH_MS)
  },
  { immediate: true },
)

onBeforeUnmount(() => window.clearTimeout(timer))

async function refresh(): Promise<void> {
  if (!props.page.root) {
    yaml.value = ''
    payload.value = ''
    skipped.value = []
    failed.value = true
    status.value = 'This page has no free-form layout.'
    return
  }
  try {
    const document = await api.exportEspHome({
      root: props.page.root,
      themeId: props.themeId,
      widthPx: props.device.w,
      heightPx: props.device.h,
      screenId: props.screen.id,
      pageId: props.page.id,
    })
    yaml.value = document.yaml
    payload.value = document.payload
    skipped.value = document.skipped
    failed.value = false
    status.value = document.skipped.length
      ? 'Some components stay on the tablet. The rest can go to the panel.'
      : 'Paste this into ESPHome, or send it to a panel that is already running.'
  } catch (error) {
    failed.value = true
    status.value = error instanceof ApiError ? error.message : 'The layout could not be prepared.'
  }
}

function rememberDevice(value: string): void {
  deviceName.value = value
  localStorage.setItem(DEVICE_KEY, value)
}

async function send(): Promise<void> {
  const name = deviceName.value.trim()
  if (!name || !payload.value || sending.value) return
  sending.value = true
  try {
    const result = await api.sendEspHome({ device: name, payload: payload.value })
    failed.value = false
    status.value = `Sent to ${result.service}.`
  } catch (error) {
    failed.value = true
    status.value = error instanceof ApiError ? error.message : 'The panel did not accept the layout.'
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <div class="esphome">
    <textarea :value="yaml" readonly spellcheck="false" aria-label="ESPHome LVGL YAML" />
    <ul v-if="skipped.length" class="skipped">
      <li v-for="item in skipped" :key="item">{{ item }}</li>
    </ul>
    <div class="send">
      <label>
        ESPHome device
        <input
          :value="deviceName"
          spellcheck="false"
          placeholder="kitchen_panel"
          aria-label="ESPHome device name"
          @input="rememberDevice(($event.target as HTMLInputElement).value)"
        />
      </label>
      <button class="primary" type="button" :disabled="sending || !deviceName.trim() || !payload" @click="send">
        Save &amp; send
      </button>
    </div>
    <p class="status" :class="{ bad: failed }">{{ status }}</p>
  </div>
</template>

<style scoped>
.esphome {
  flex: 1;
  min-height: 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

textarea {
  flex: 1;
  min-height: 200px;
  resize: none;
  font-family: ui-monospace, 'SF Mono', Menlo, monospace;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre;
}

.skipped {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
  color: var(--muted);
}

.send {
  display: flex;
  align-items: flex-end;
  gap: 8px;
}

label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: var(--muted);
}

input {
  min-width: 220px;
}

.status {
  margin: 0;
  font-size: 12px;
  color: var(--muted);
}

.status.bad {
  color: var(--danger);
}
</style>
