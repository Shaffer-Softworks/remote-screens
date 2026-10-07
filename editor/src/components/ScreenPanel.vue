<script setup lang="ts">
import { computed } from 'vue'
import type { Screen } from '../model/types'
import { state } from '../store'

const props = defineProps<{ screen: Screen }>()

const temperatures = computed(() =>
  state.entities.filter((e) => (e.domain === 'sensor' && /°|K$/.test(e.unit ?? '')) || e.domain === 'weather'),
)

const night = computed({
  get: () => props.screen.settings.nightStart != null,
  set: (on) => {
    const s = props.screen.settings
    if (on) {
      s.nightStart = s.nightStart ?? '22:00'
      s.nightEnd = s.nightEnd ?? '07:00'
    } else {
      delete s.nightStart
      delete s.nightEnd
    }
  },
})

function num(e: Event, min: number, max: number): number {
  const v = Number((e.target as HTMLInputElement).value)
  return Math.min(Math.max(Number.isFinite(v) ? Math.round(v) : min, min), max)
}
</script>

<template>
  <div class="panel">
    <h3>Top bar</h3>
    <label class="check"><input v-model="screen.topBar.show" type="checkbox" /> Show the top bar</label>
    <template v-if="screen.topBar.show">
      <label class="check"><input v-model="screen.topBar.clock" type="checkbox" /> Clock</label>
      <label class="check"><input v-model="screen.topBar.people" type="checkbox" /> People at home</label>
      <label class="field">
        Temperature
        <select
          :value="screen.topBar.temperatureEntity ?? ''"
          @change="screen.topBar.temperatureEntity = ($event.target as HTMLSelectElement).value || undefined"
        >
          <option value="">None</option>
          <option v-for="e in temperatures" :key="e.entityId" :value="e.entityId">{{ e.name }}</option>
        </select>
      </label>
    </template>

    <h3>Display</h3>
    <label class="field">
      Brightness {{ screen.settings.brightness }}%
      <input v-model.number="screen.settings.brightness" type="range" min="1" max="100" />
    </label>
    <label class="check"><input v-model="screen.settings.darkMode" type="checkbox" /> Dark mode</label>
    <label class="check"><input v-model="screen.settings.swipe" type="checkbox" /> Swipe between pages</label>
    <label class="field">
      Orientation
      <select v-model.number="screen.settings.orientation">
        <option :value="0">Follow the tablet</option>
        <option :value="1">Portrait</option>
        <option :value="2">Landscape</option>
      </select>
    </label>
    <label class="field">
      Back to the home page after (seconds, 0 is never)
      <input :value="screen.settings.backToHomeSeconds" type="number" min="0" max="3600" @change="screen.settings.backToHomeSeconds = num($event, 0, 3600)" />
    </label>

    <h3>Standby</h3>
    <label class="field">
      Dim after (minutes, 0 is never)
      <input :value="screen.settings.standbyMinutes" type="number" min="0" max="240" @change="screen.settings.standbyMinutes = num($event, 0, 240)" />
    </label>
    <label class="field">
      Standby brightness {{ screen.settings.standbyBrightness }}%
      <input v-model.number="screen.settings.standbyBrightness" type="range" min="0" max="100" />
    </label>

    <h3>Night</h3>
    <label class="check"><input v-model="night" type="checkbox" /> Night mode</label>
    <template v-if="night">
      <div class="row">
        <label class="field grow">From<input v-model="screen.settings.nightStart" type="time" /></label>
        <label class="field grow">Until<input v-model="screen.settings.nightEnd" type="time" /></label>
      </div>
      <label class="field">
        Night brightness {{ screen.settings.nightBrightness }}%
        <input v-model.number="screen.settings.nightBrightness" type="range" min="0" max="100" />
      </label>
      <label class="check"><input v-model="screen.settings.nightDark" type="checkbox" /> Dark at night</label>
    </template>
    <p class="muted note">Home Assistant can change these too, with the Remote Screens integration's entities.</p>
  </div>
</template>

<style scoped>
.panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

h3 {
  margin-top: 6px;
}

.note {
  font-size: 12px;
  margin: 0;
}
</style>
