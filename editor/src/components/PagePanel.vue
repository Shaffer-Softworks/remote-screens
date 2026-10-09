<script setup lang="ts">
import { computed } from 'vue'
import type { Page, Screen } from '../model/types'
import IconPicker from './IconPicker.vue'
import MdiIcon from './MdiIcon.vue'

const props = defineProps<{ screen: Screen; page: Page }>()
const emit = defineEmits<{ delete: []; convert: [] }>()

const index = computed(() => props.screen.pages.indexOf(props.page))

function shift(by: number): void {
  const pages = props.screen.pages
  const i = index.value
  const j = i + by
  if (j < 0 || j >= pages.length) return
  ;[pages[i], pages[j]] = [pages[j], pages[i]]
}

const isHome = computed({
  get: () => props.screen.homePage === props.page.id,
  set: (on) => {
    if (on) props.screen.homePage = props.page.id
  },
})
</script>

<template>
  <div class="panel">
    <label class="field">Title<input v-model="page.title" maxlength="40" /></label>
    <div v-if="!page.hidden" class="field">
      <span class="label">Navigation bar icon</span>
      <IconPicker v-model="page.icon" :placeholder="isHome ? 'home' : 'view-dashboard'" />
    </div>
    <label class="check"><input v-model="isHome" type="checkbox" :disabled="isHome" /> Home page</label>
    <label class="check">
      <input v-model="page.hidden" type="checkbox" :disabled="isHome" />
      Hidden: no button in the navigation bar, opened from a "Go to page" tile, with a Back key
    </label>
    <div class="field">
      <span class="label">Layout</span>
      <p class="muted note">
        {{
          page.root
            ? 'Free-form: components laid out in rows, columns and boxes, like Compose.'
            : 'Grid: tiles placed on the screen\'s columns and rows.'
        }}
      </p>
      <button @click="emit('convert')">{{ page.root ? 'Back to a grid page' : 'Convert to free-form' }}</button>
    </div>
    <div class="row">
      <button class="icon" title="Move left" :disabled="index === 0" @click="shift(-1)">
        <MdiIcon name="chevron-left" :size="18" />
      </button>
      <button class="icon" title="Move right" :disabled="index === screen.pages.length - 1" @click="shift(1)">
        <MdiIcon name="chevron-right" :size="18" />
      </button>
      <span class="grow" />
      <button class="danger" :disabled="screen.pages.length === 1" @click="emit('delete')">Delete page</button>
    </div>
  </div>
</template>

<style scoped>
.panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12px;
  color: var(--muted);
}

.field > button {
  align-self: flex-start;
  color: var(--ink);
}

.note {
  margin: 0;
}
</style>
