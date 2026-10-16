<script setup lang="ts">
import { ref, watch } from 'vue'
import { api } from '../api'
import MdiIcon from './MdiIcon.vue'

const props = defineProps<{ modelValue?: string; placeholder: string }>()
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
</script>

<template>
  <div class="picker">
    <div class="row">
      <button class="current" :title="props.modelValue ?? props.placeholder" @click="open = !open">
        <MdiIcon :name="props.modelValue ?? props.placeholder" :size="22" />
        <span class="grow">{{ props.modelValue ?? `Default (${props.placeholder})` }}</span>
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
