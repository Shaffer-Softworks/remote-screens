<script setup lang="ts">
import { computed } from 'vue'
import { navBar as defaultNavBar, TOP_BAR } from '../model/grid'
import { BACKGROUND_SWATCHES, DEFAULT_THEME, INK_SWATCHES, themes } from '../model/palette'
import type { NavBar, Screen } from '../model/types'
import { state } from '../store'
import ColorSwatches from './ColorSwatches.vue'

const props = defineProps<{ screen: Screen; deviceWidth?: number }>()

if (!props.screen.navBar) props.screen.navBar = {}
const nav = computed<NavBar>(() => props.screen.navBar!)

/** An optional number that is absent for Auto: the checkbox clears it, or sets it to [fallback] to start from. */
function auto<T extends object>(target: () => T, key: keyof T & string, fallback: () => number) {
  return computed({
    get: () => (target() as Record<string, unknown>)[key] === undefined,
    set: (on: boolean) => {
      const bar = target() as Record<string, unknown>
      if (on) delete bar[key]
      else bar[key] = fallback()
    },
  })
}

const topAuto = auto(
  () => props.screen.topBar,
  'height',
  () => TOP_BAR,
)
const clockAuto = auto(
  () => props.screen.topBar,
  'clockWidth',
  () => 42,
)
const navAuto = auto(
  () => nav.value,
  'height',
  () => defaultNavBar(props.deviceWidth ?? 1280),
)

const temperatures = computed(() =>
  state.entities.filter(
    e => (e.domain === 'sensor' && /°|K$/.test(e.unit ?? '')) || e.domain === 'weather',
  ),
)

const night = computed({
  get: () => props.screen.settings.nightStart != null,
  set: on => {
    const settings = props.screen.settings
    if (on) {
      settings.nightStart = settings.nightStart ?? '22:00'
      settings.nightEnd = settings.nightEnd ?? '07:00'
    } else {
      delete settings.nightStart
      delete settings.nightEnd
    }
  },
})

function num(e: Event, min: number, max: number): number {
  const value = Number((e.target as HTMLInputElement).value)
  return Math.min(Math.max(Number.isFinite(value) ? Math.round(value) : min, min), max)
}

/** The kiosk PIN typed in, or null when empty or not 4 to 8 digits (the add-on drops those too). */
function pin(e: Event): string | null {
  const value = (e.target as HTMLInputElement).value.trim()
  return /^\d{4,8}$/.test(value) ? value : null
}
</script>

<template>
  <div class="panel">
    <h3>Top bar</h3>
    <label class="check"
      ><input v-model="screen.topBar.show" type="checkbox" /> Show the top bar</label
    >
    <template v-if="screen.topBar.show">
      <label class="check"><input v-model="topAuto" type="checkbox" /> Default height</label>
      <label v-if="!topAuto" class="field">
        Height {{ screen.topBar.height }} dp
        <input
          v-model.number="screen.topBar.height"
          v-range
          type="range"
          min="32"
          max="120"
          step="2"
        />
      </label>
      <label class="check"><input v-model="screen.topBar.clock" type="checkbox" /> Clock</label>
      <template v-if="screen.topBar.clock">
        <label class="check sub">
          <input
            :checked="screen.topBar.date !== false"
            type="checkbox"
            @change="screen.topBar.date = ($event.target as HTMLInputElement).checked"
          />
          Date (on screens 600 dp wide or more)
        </label>
        <label class="check sub"
          ><input v-model="clockAuto" type="checkbox" /> Default clock width</label
        >
        <label v-if="!clockAuto" class="field sub">
          Clock width {{ screen.topBar.clockWidth }}% of the screen
          <input v-model.number="screen.topBar.clockWidth" v-range type="range" min="15" max="60" />
        </label>
      </template>
      <label class="check"
        ><input v-model="screen.topBar.people" type="checkbox" /> People at home</label
      >
      <label class="check">
        <input
          :checked="!!screen.topBar.wifi"
          type="checkbox"
          @change="screen.topBar.wifi = ($event.target as HTMLInputElement).checked"
        />
        Wi‑Fi signal
      </label>
      <label class="check">
        <input
          :checked="!!screen.topBar.battery"
          type="checkbox"
          @change="screen.topBar.battery = ($event.target as HTMLInputElement).checked"
        />
        Battery
      </label>
      <label class="field">
        Temperature
        <select
          :value="screen.topBar.temperatureEntity ?? ''"
          @change="
            screen.topBar.temperatureEntity =
              ($event.target as HTMLSelectElement).value || undefined
          "
        >
          <option value="">None</option>
          <option v-for="e in temperatures" :key="e.entityId" :value="e.entityId">
            {{ e.name }}
          </option>
        </select>
      </label>
      <div class="field">
        Background
        <ColorSwatches
          v-model="screen.topBar.background"
          :keys="BACKGROUND_SWATCHES"
          clear-title="The theme's"
        />
      </div>
      <div v-if="screen.topBar.clock" class="field">
        Clock background
        <ColorSwatches
          v-model="screen.topBar.clockBackground"
          :keys="BACKGROUND_SWATCHES"
          clear-title="The theme's"
        />
      </div>
    </template>

    <h3>Bottom bar</h3>
    <label class="check"><input v-model="navAuto" type="checkbox" /> Default height</label>
    <label v-if="!navAuto" class="field">
      Height {{ nav.height }} dp
      <input v-model.number="nav.height" v-range type="range" min="48" max="160" step="2" />
    </label>
    <label class="field">
      Buttons show
      <select
        :value="nav.labels ?? 'both'"
        @change="nav.labels = ($event.target as HTMLSelectElement).value as NavBar['labels']"
      >
        <option value="both">Icons and names</option>
        <option value="icons">Icons only</option>
        <option value="text">Names only</option>
      </select>
    </label>
    <label class="field">
      Layout
      <select
        :value="nav.layout ?? 'spread'"
        @change="nav.layout = ($event.target as HTMLSelectElement).value as NavBar['layout']"
      >
        <option value="spread">Spread out</option>
        <option value="center">Centred</option>
        <option value="fill">Fill the width</option>
      </select>
    </label>
    <div class="field">
      Background
      <ColorSwatches
        v-model="nav.background"
        :keys="BACKGROUND_SWATCHES"
        clear-title="The theme's"
      />
    </div>
    <div class="field">
      Line
      <ColorSwatches v-model="nav.line" :keys="INK_SWATCHES" clear-title="The theme's" />
    </div>
    <div class="field">
      Current page
      <ColorSwatches v-model="nav.active" :keys="BACKGROUND_SWATCHES" clear-title="The theme's" />
    </div>

    <h3>Display</h3>
    <label class="field">
      Brightness {{ screen.settings.brightness }}%
      <input v-model.number="screen.settings.brightness" v-range type="range" min="1" max="100" />
    </label>
    <label class="field">
      Theme
      <select
        :value="screen.settings.theme ?? DEFAULT_THEME"
        @change="screen.settings.theme = ($event.target as HTMLSelectElement).value"
      >
        <option v-for="theme in themes" :key="theme.id" :value="theme.id">{{ theme.name }}</option>
      </select>
    </label>
    <label class="field">
      Dark theme (in dark mode and at night)
      <select
        :value="screen.settings.darkTheme ?? DEFAULT_THEME"
        @change="screen.settings.darkTheme = ($event.target as HTMLSelectElement).value"
      >
        <option v-for="theme in themes" :key="theme.id" :value="theme.id">{{ theme.name }}</option>
      </select>
    </label>
    <label class="check"
      ><input v-model="screen.settings.darkMode" type="checkbox" /> Dark mode</label
    >
    <label class="check"
      ><input v-model="screen.settings.swipe" type="checkbox" /> Swipe between pages</label
    >
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
      <input
        :value="screen.settings.backToHomeSeconds"
        type="number"
        min="0"
        max="3600"
        @change="screen.settings.backToHomeSeconds = num($event, 0, 3600)"
      />
    </label>

    <h3>Standby</h3>
    <label class="field">
      Dim after (minutes, 0 is never)
      <input
        :value="screen.settings.standbyMinutes"
        type="number"
        min="0"
        max="240"
        @change="screen.settings.standbyMinutes = num($event, 0, 240)"
      />
    </label>
    <label class="field">
      Standby brightness {{ screen.settings.standbyBrightness }}%
      <input
        v-model.number="screen.settings.standbyBrightness"
        v-range
        type="range"
        min="0"
        max="100"
      />
    </label>
    <label class="check"
      ><input v-model="screen.settings.standbyScreenOff" type="checkbox" /> Turn the screen off in
      standby</label
    >
    <p v-if="screen.settings.standbyScreenOff" class="note muted">
      The tablet asks once to let Remote Screens lock it (Settings on the tablet). A touch can't
      wake a screen that is off: use the power button or the Wake action in Home Assistant.
    </p>
    <label class="check"
      ><input v-model="screen.settings.wakeOnProximity" type="checkbox" /> Wake on proximity</label
    >
    <p class="note muted">
      Uses the tablet's proximity sensor when it has one (often only a few centimetres). Ignored if
      there is no sensor. For someone walking into the room, use a motion or occupancy sensor in
      Home Assistant with the Wake action.
    </p>
    <label class="check"
      ><input v-model="screen.settings.wakeOnFace" type="checkbox" /> Wake on face</label
    >
    <p class="note muted">
      Uses the front camera and on-device face detection while the screen is in standby. The tablet
      asks for camera permission; Android shows its privacy indicator while the camera runs. Needs a
      front camera and is skipped on low-RAM tablets. When this is on and in use, standby keeps a
      black screen instead of turning the panel fully off. Can be used together with proximity.
    </p>

    <h3>Night</h3>
    <label class="check"><input v-model="night" type="checkbox" /> Night mode</label>
    <template v-if="night">
      <div class="row">
        <label class="field grow"
          >From<input v-model="screen.settings.nightStart" type="time"
        /></label>
        <label class="field grow"
          >Until<input v-model="screen.settings.nightEnd" type="time"
        /></label>
      </div>
      <label class="field">
        Night brightness {{ screen.settings.nightBrightness }}%
        <input
          v-model.number="screen.settings.nightBrightness"
          v-range
          type="range"
          min="0"
          max="100"
        />
      </label>
      <label class="check"
        ><input v-model="screen.settings.nightDark" type="checkbox" /> Dark at night</label
      >
    </template>

    <h3>Kiosk</h3>
    <label class="check"
      ><input v-model="screen.settings.kiosk" type="checkbox" /> Lock the tablet to this
      screen</label
    >
    <template v-if="screen.settings.kiosk">
      <label class="field">
        PIN to unlock on the tablet (4 to 8 digits, empty unlocks only from here or Home Assistant)
        <input
          :value="screen.settings.kioskPin ?? ''"
          type="password"
          inputmode="numeric"
          autocomplete="off"
          maxlength="8"
          @change="screen.settings.kioskPin = pin($event)"
        />
      </label>
      <p class="note muted">
        Enrolled tablets (Players, Enroll a tablet) can't be left at all. Others use screen pinning,
        which holding Back and Overview undoes.
      </p>
    </template>
    <p class="muted note">
      Home Assistant can change these too, with the Remote Screens integration's entities.
    </p>
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

.sub {
  margin-left: 22px;
}

.note {
  font-size: 12px;
  margin: 0;
}
</style>
