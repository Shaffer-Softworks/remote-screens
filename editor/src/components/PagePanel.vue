<script setup lang="ts">
import { computed } from 'vue'
import { api } from '../api'
import type { Page, Screen } from '../model/types'
import { attempt } from '../store'
import IconPicker from './IconPicker.vue'
import MdiIcon from './MdiIcon.vue'

const props = defineProps<{ screen: Screen; page: Page }>()
const emit = defineEmits<{
  delete: []
  convert: []
  addPage: [kind: 'free' | 'grid']
}>()

const atPageLimit = computed(() => props.screen.pages.length >= 9)

const index = computed(() => props.screen.pages.indexOf(props.page))

function shift(by: number): void {
  const pages = props.screen.pages
  const i = index.value
  const targetIndex = i + by
  if (targetIndex < 0 || targetIndex >= pages.length) return
  ;[pages[i], pages[targetIndex]] = [pages[targetIndex], pages[i]]
}

const backgroundUrl = computed(() => {
  const token = props.page.backgroundImage
  if (!token) return ''
  const screenId = encodeURIComponent(props.screen.id)
  const pageId = encodeURIComponent(props.page.id)
  return `api/screens/${screenId}/pages/${pageId}/background?v=${encodeURIComponent(token)}`
})

async function chooseBackground(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  const saved = await attempt(() =>
    api.uploadPageBackground(props.screen.id, props.page.id, file),
  )
  if (saved) props.page.backgroundImage = saved.backgroundImage
}

function removeBackground(): void {
  delete props.page.backgroundImage
}

const isHome = computed({
  get: () => props.screen.homePage === props.page.id,
  set: on => {
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
    <div class="field">
      <span class="label">Background image</span>
      <p class="muted note">
        A JPEG or PNG behind this page. The bars stay on top. Exporting the screen does not include
        the file.
      </p>
      <img v-if="page.backgroundImage" class="shot" :src="backgroundUrl" alt="" />
      <div class="add-row">
        <label class="file">
          {{ page.backgroundImage ? 'Replace' : 'Choose image' }}
          <input type="file" accept="image/jpeg,image/png" @change="chooseBackground" />
        </label>
        <button v-if="page.backgroundImage" type="button" @click="removeBackground">Remove</button>
      </div>
    </div>
    <label class="check"
      ><input v-model="isHome" type="checkbox" :disabled="isHome" /> Home page</label
    >
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
            : "Grid: tiles placed on the screen's columns and rows."
        }}
      </p>
      <button @click="emit('convert')">
        {{ page.root ? 'Back to a grid page' : 'Convert to free-form' }}
      </button>
    </div>
    <div class="field">
      <span class="label">Add a page</span>
      <div class="add-row">
        <button
          type="button"
          :disabled="atPageLimit"
          :title="atPageLimit ? 'At most 9 pages' : 'Add a grid page'"
          @click="emit('addPage', 'grid')"
        >
          <MdiIcon name="view-grid-outline" :size="16" />
          Grid
        </button>
        <button
          type="button"
          :disabled="atPageLimit"
          :title="atPageLimit ? 'At most 9 pages' : 'Add a free-form page'"
          @click="emit('addPage', 'free')"
        >
          <MdiIcon name="vector-square" :size="16" />
          Free-form
        </button>
      </div>
    </div>
    <div class="row">
      <button class="icon" title="Move left" :disabled="index === 0" @click="shift(-1)">
        <MdiIcon name="chevron-left" :size="18" />
      </button>
      <button
        class="icon"
        title="Move right"
        :disabled="index === screen.pages.length - 1"
        @click="shift(1)"
      >
        <MdiIcon name="chevron-right" :size="18" />
      </button>
      <span class="grow" />
      <button class="danger" :disabled="screen.pages.length === 1" @click="emit('delete')">
        Delete page
      </button>
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

.add-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.add-row > button,
.file {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--ink);
}

.file {
  position: relative;
  overflow: hidden;
  align-self: flex-start;
  padding: 6px 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
  cursor: pointer;
}

.file input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}

.shot {
  width: 100%;
  height: 72px;
  object-fit: cover;
  border-radius: 8px;
  background: var(--bg);
}
</style>
