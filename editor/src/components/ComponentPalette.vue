<script setup lang="ts">
import { computed, inject, ref } from 'vue'
import { DESIGN } from '../model/design'
import { dragged } from '../model/drag'
import { COMPONENTS, type ComponentSpec } from '../model/nodes'
import type { Page } from '../model/types'
import EntityLibrary from './EntityLibrary.vue'
import MdiIcon from './MdiIcon.vue'

defineProps<{ page: Page }>()
const ctx = inject(DESIGN)!

const tab = ref<'components' | 'entities'>('components')
const query = ref('')

const groups = computed(() => {
  const needle = query.value.trim().toLowerCase()
  const by = new Map<string, ComponentSpec[]>()
  for (const spec of COMPONENTS) {
    if (needle && !spec.label.toLowerCase().includes(needle)) continue
    by.set(spec.group, [...(by.get(spec.group) ?? []), spec])
  }
  return [...by.entries()]
})

function drag(e: DragEvent, spec: ComponentSpec): void {
  dragged.value = { type: 'component', component: spec.id }
  e.dataTransfer?.setData('text/plain', spec.id)
}
</script>

<template>
  <div class="palette">
    <div class="segments">
      <button :class="{ active: tab === 'components' }" @click="tab = 'components'">
        Components
      </button>
      <button :class="{ active: tab === 'entities' }" @click="tab = 'entities'">
        Home Assistant
      </button>
    </div>
    <template v-if="tab === 'components'">
      <input
        v-model="query"
        type="search"
        placeholder="Search components"
        aria-label="Search components"
      />
      <p class="muted hint">Drag onto the page or the tree, or click to add after the selection.</p>
      <section v-for="[group, items] in groups" :key="group">
        <h3>{{ group }}</h3>
        <div class="grid">
          <button
            v-for="spec in items"
            :key="spec.id"
            class="item"
            draggable="true"
            :title="`Add a ${spec.label.toLowerCase()}`"
            @dragstart="drag($event, spec)"
            @dragend="dragged = null"
            @click="ctx.addComponent(spec.id)"
          >
            <MdiIcon :name="spec.icon" :size="20" />
            <span>{{ spec.label }}</span>
          </button>
        </div>
      </section>
    </template>
    <EntityLibrary v-else :page="page" @add="ctx.addEntity($event)" />
  </div>
</template>

<style scoped>
.palette {
  display: flex;
  flex-direction: column;
  gap: 10px;
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

.hint {
  margin: 0;
  font-size: 12px;
}

section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 4px;
}

.item {
  display: flex;
  align-items: center;
  gap: 8px;
  border-radius: 10px;
  padding: 7px 8px;
  text-align: left;
  cursor: grab;
}
</style>
