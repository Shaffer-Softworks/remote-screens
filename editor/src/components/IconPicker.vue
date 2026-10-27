<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { api } from '../api'
import MdiIcon from './MdiIcon.vue'

const props = defineProps<{
  modelValue?: string
  placeholder: string
  /** Icon drawn for the default, when it is not the placeholder words (an entity's own icon). */
  preview?: string
}>()
const emit = defineEmits<{ 'update:modelValue': [value: string | undefined] }>()

const query = ref('')
const results = ref<string[]>([])
const open = ref(false)
let timer: ReturnType<typeof setTimeout> | undefined

watch(query, searchText => {
  clearTimeout(timer)
  timer = setTimeout(async () => {
    results.value =
      searchText.trim().length < 2 ? [] : await api.searchIcons(searchText.trim()).catch(() => [])
  }, 200)
})

function pick(name: string | undefined): void {
  emit('update:modelValue', name)
  open.value = false
  query.value = ''
}

/** The glyph for a blank value: the resolved default, else the placeholder when that is itself an icon. */
const displayedIcon = computed(() => props.modelValue ?? props.preview ?? props.placeholder)

/** The words beside that glyph. An entity's icon replaces the placeholder words. */
const defaultCaption = computed((): string => {
  const iconName = props.preview
  if (!iconName) return props.placeholder
  return iconName.replace(/^mdi:/, '')
})
</script>

<template>
  <div class="picker">
    <div class="row">
      <button class="current" :title="displayedIcon" @click="open = !open">
        <MdiIcon :name="displayedIcon" :size="22" />
        <span class="grow">{{ props.modelValue ?? `Default (${defaultCaption})` }}</span>
      </button>
      <button v-if="props.modelValue" class="icon" title="Use the default" @click="pick(undefined)">
        <MdiIcon name="close" :size="16" />
      </button>
    </div>
    <div v-if="open" class="panel">
      <input v-model="query" type="search" placeholder="Search icons, such as lamp" autofocus />
      <div class="grid">
        <button v-for="n in results" :key="n" class="icon" :title="n" @click="pick(n)">
          <MdiIcon :name="n" :size="22" />
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.picker {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.current {
  display: flex;
  align-items: center;
  gap: 8px;
  border-radius: 10px;
  flex: 1;
  text-align: left;
}

.panel {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(38px, 1fr));
  gap: 4px;
  max-height: 200px;
  overflow: auto;
}

.grid button {
  justify-content: center;
}
</style>
