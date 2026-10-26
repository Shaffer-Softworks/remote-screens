<script setup lang="ts">
import { DENSITIES } from '../model/devices'
import { MAX_CELLS, MIN_CELLS } from '../model/grid'
import type { Screen } from '../model/types'
import MdiIcon from './MdiIcon.vue'

export interface ToolbarDevice {
  key: string
  label: string
  w: number
  h: number
}

defineProps<{
  screen: Screen
  canUndo: boolean
  canRedo: boolean
  previewDark: boolean
  sameTheme: boolean
  free: boolean
  deviceKey: string
  devices: ToolbarDevice[]
  customDpi: number
  device: ToolbarDevice
  shownSize: (dp: number) => number
  online: { deviceId: string; name: string }[]
  previewOn: string
  dirty: boolean
  intercomOpen: boolean
}>()

const emit = defineEmits<{
  close: []
  undo: []
  redo: []
  'update:previewDark': [value: boolean]
  chooseDarkTheme: []
  pickDevice: [key: string]
  'update:customDpi': [value: number]
  setCustom: [dim: 'widthDp' | 'heightDp', value: number]
  rotateCustom: []
  resize: [cols: number, rows: number]
  'update:previewOn': [value: string]
  preview: []
  save: []
  toggleIntercom: []
}>()

const CUSTOM = 'custom'
</script>

<template>
  <header class="bar">
    <button class="icon" title="Back to screens" @click="emit('close')">
      <MdiIcon name="arrow-left" />
    </button>
    <input v-model="screen.name" class="name" aria-label="Screen name" />
    <span class="row">
      <button class="icon" title="Undo (Ctrl+Z)" :disabled="!canUndo" @click="emit('undo')">
        <MdiIcon name="undo" :size="18" />
      </button>
      <button class="icon" title="Redo (Ctrl+Shift+Z)" :disabled="!canRedo" @click="emit('redo')">
        <MdiIcon name="redo" :size="18" />
      </button>
    </span>
    <span class="grow" />
    <div class="segments" title="Preview the screen's day theme or its dark theme">
      <button :class="{ active: !previewDark }" @click="emit('update:previewDark', false)">
        <MdiIcon name="white-balance-sunny" :size="16" /> Day
      </button>
      <button :class="{ active: previewDark }" @click="emit('update:previewDark', true)">
        <MdiIcon name="weather-night" :size="16" /> Dark
      </button>
    </div>
    <button
      v-if="sameTheme"
      class="icon muted"
      title="Day and dark use the same theme, so they look alike. Choose a dark theme in the Screen settings."
      @click="emit('chooseDarkTheme')"
    >
      <MdiIcon name="information-outline" :size="18" />
    </button>
    <label class="row muted">
      <MdiIcon name="tablet" :size="18" />
      <select
        :value="deviceKey"
        aria-label="Device"
        @change="emit('pickDevice', ($event.target as HTMLSelectElement).value)"
      >
        <option v-for="d in devices" :key="d.key" :value="d.key">
          {{ d.label }} ({{ d.w }} × {{ d.h }})
        </option>
      </select>
    </label>
    <span
      v-if="deviceKey === CUSTOM"
      class="row custom-size"
      title="Width × height, in dp or in pixels at a density"
    >
      <input
        type="number"
        aria-label="Width"
        step="10"
        :value="shownSize(device.w)"
        @change="emit('setCustom', 'widthDp', Number(($event.target as HTMLInputElement).value))"
      />
      ×
      <input
        type="number"
        aria-label="Height"
        step="10"
        :value="shownSize(device.h)"
        @change="emit('setCustom', 'heightDp', Number(($event.target as HTMLInputElement).value))"
      />
      <select
        :value="customDpi"
        aria-label="Units"
        @change="emit('update:customDpi', Number(($event.target as HTMLSelectElement).value))"
      >
        <option :value="0">dp</option>
        <option v-for="d in DENSITIES" :key="d" :value="d">px at {{ d }} dpi</option>
      </select>
      <button class="icon" title="Swap width and height" @click="emit('rotateCustom')">
        <MdiIcon name="phone-rotate-landscape" :size="18" />
      </button>
    </span>
    <span v-if="!free" class="row stepper" title="Columns × rows">
      <button
        class="icon"
        :disabled="screen.grid.cols <= MIN_CELLS"
        @click="emit('resize', screen.grid.cols - 1, screen.grid.rows)"
      >
        −
      </button>
      {{ screen.grid.cols }}
      <button
        class="icon"
        :disabled="screen.grid.cols >= MAX_CELLS"
        @click="emit('resize', screen.grid.cols + 1, screen.grid.rows)"
      >
        +
      </button>
      ×
      <button
        class="icon"
        :disabled="screen.grid.rows <= MIN_CELLS"
        @click="emit('resize', screen.grid.cols, screen.grid.rows - 1)"
      >
        −
      </button>
      {{ screen.grid.rows }}
      <button
        class="icon"
        :disabled="screen.grid.rows >= MAX_CELLS"
        @click="emit('resize', screen.grid.cols, screen.grid.rows + 1)"
      >
        +
      </button>
    </span>
    <span class="row">
      <select
        v-if="online.length > 1"
        :value="previewOn"
        aria-label="Preview on"
        @change="emit('update:previewOn', ($event.target as HTMLSelectElement).value)"
      >
        <option value="">First tablet</option>
        <option v-for="p in online" :key="p.deviceId" :value="p.deviceId">{{ p.name }}</option>
      </select>
      <button
        :disabled="online.length === 0"
        title="Show this version on a tablet without saving"
        @click="emit('preview')"
      >
        Preview
      </button>
      <button
        :class="{ active: intercomOpen }"
        title="Call a tablet from this browser"
        @click="emit('toggleIntercom')"
      >
        Intercom
      </button>
      <button class="primary" :disabled="!dirty" @click="emit('save')">Save</button>
    </span>
  </header>
</template>

<style scoped>
.bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  background: var(--card);
  border-bottom: 1px solid var(--line);
  flex-wrap: wrap;
}

.name {
  font-size: 18px;
  border-color: transparent;
  background: transparent;
  max-width: 260px;
}

.name:hover {
  border-color: var(--line);
}

.custom-size input {
  width: 76px;
}

.stepper button {
  width: 26px;
  height: 26px;
  justify-content: center;
  align-items: center;
  padding: 0;
}

.segments {
  display: flex;
  gap: 4px;
}

.segments button {
  flex: 1;
  padding: 6px 4px;
}

.segments button.active {
  background: var(--ink);
  color: var(--bg);
  border-color: var(--ink);
}
</style>
