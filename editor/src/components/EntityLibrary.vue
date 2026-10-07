<script setup lang="ts">
import { computed, ref } from 'vue'
import { CATALOGUE } from '../generated/catalogue'
import { dragged } from '../model/drag'
import { newTile } from '../model/tiles'
import type { EntityInfo, Page } from '../model/types'
import { state } from '../store'
import MdiIcon from './MdiIcon.vue'

const props = defineProps<{ page: Page }>()
const emit = defineEmits<{ add: [source: { entityId?: string; kind?: string }] }>()

const query = ref('')
const onPage = computed(() => new Set(props.page.tiles.map((t) => t.entityId).filter(Boolean)))

/** Tiles that are no entity. History and favourite music are an entity shown another way (see the tile drawer). */
const KINDS = ['home', 'clock', 'bedside', 'nav', 'energy', 'map']

/** Entity types with a tile; the rest are left out. */
const supported = (e: EntityInfo) => e.domain in CATALOGUE

const groups = computed(() => {
  const q = query.value.trim().toLowerCase()
  const match = (e: EntityInfo) => !q || e.name.toLowerCase().includes(q) || e.entityId.includes(q)
  const areaName = new Map(state.areas.map((a) => [a.id, a.name]))
  const by = new Map<string, EntityInfo[]>()
  for (const e of state.entities) {
    if (!supported(e) || !match(e)) continue
    const key = (e.area && areaName.get(e.area)) || 'No area'
    by.set(key, [...(by.get(key) ?? []), e])
  }
  return [...by.entries()].sort(([a], [b]) => (a === 'No area' ? 1 : b === 'No area' ? -1 : a.localeCompare(b)))
})

const kinds = computed(() =>
  Object.entries(CATALOGUE)
    .filter(([key]) => KINDS.includes(key))
    .filter(([, s]) => !query.value || s.name.toLowerCase().includes(query.value.toLowerCase())),
)

const open = ref<Record<string, boolean>>({})
const isOpen = (area: string) => query.value.trim() !== '' || (open.value[area] ?? groups.value.length <= 3)

function drag(e: DragEvent, source: { entityId?: string; kind?: string }): void {
  const t = newTile(source, 0, 0)
  dragged.value = { type: 'new', ...source, w: t.full ? 1 : t.w, h: t.full ? 1 : t.h }
  e.dataTransfer?.setData('text/plain', source.entityId ?? source.kind ?? '')
}

function icon(e: EntityInfo): string {
  return e.icon ?? CATALOGUE[e.domain]?.icon ?? 'help-circle-outline'
}
</script>

<template>
  <div class="library">
    <input v-model="query" type="search" placeholder="Search entities" aria-label="Search entities" />
    <p class="muted hint">Drag onto the page, or click to add at the first free spot.</p>

    <section v-if="kinds.length">
      <h3>Other tiles</h3>
      <button
        v-for="[key, s] in kinds"
        :key="key"
        class="item"
        draggable="true"
        @dragstart="drag($event, { kind: key })"
        @dragend="dragged = null"
        @click="emit('add', { kind: key })"
      >
        <MdiIcon :name="s.icon" :size="20" />
        <span class="grow">{{ s.name }}</span>
      </button>
    </section>

    <section v-for="[area, entities] in groups" :key="area">
      <button class="area" @click="open[area] = !isOpen(area)">
        <MdiIcon :name="isOpen(area) ? 'chevron-down' : 'chevron-right'" :size="18" />
        <h3 class="grow">{{ area }}</h3>
        <span class="muted">{{ entities.length }}</span>
      </button>
      <template v-if="isOpen(area)">
        <button
          v-for="e in entities"
          :key="e.entityId"
          class="item"
          :class="{ used: onPage.has(e.entityId) }"
          draggable="true"
          :title="e.entityId"
          @dragstart="drag($event, { entityId: e.entityId })"
          @dragend="dragged = null"
          @click="emit('add', { entityId: e.entityId })"
        >
          <MdiIcon :name="icon(e)" :size="20" />
          <span class="grow name">{{ e.name }}</span>
          <span class="muted state">{{ e.unit ? `${e.state} ${e.unit}` : e.state }}</span>
        </button>
      </template>
    </section>

    <p v-if="groups.length === 0 && kinds.length === 0" class="muted">Nothing matches.</p>
  </div>
</template>

<style scoped>
.library {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.hint {
  margin: 0;
  font-size: 12px;
}

section {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.area {
  display: flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: none;
  padding: 6px 0;
  border-radius: 0;
  width: 100%;
  text-align: left;
}

.item {
  display: flex;
  align-items: center;
  gap: 10px;
  border: none;
  background: none;
  border-radius: 10px;
  padding: 7px 8px;
  text-align: left;
  cursor: grab;
}

.item:hover {
  background: var(--bg);
}

.item.used .name::after {
  content: ' ·  on this page';
  color: var(--muted);
  font-size: 12px;
}

.name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.state {
  font-size: 12px;
  max-width: 90px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
