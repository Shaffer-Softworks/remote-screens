<script setup lang="ts">
import type { Page, Screen } from '../../model/types'
import CodeView from '../CodeView.vue'
import DesignCanvas from '../DesignCanvas.vue'
import GridMockup from '../GridMockup.vue'
import MdiIcon from '../MdiIcon.vue'

withDefaults(
  defineProps<{
    screen: Screen
    page: Page
    free: boolean
    device: { key: string; label: string; w: number; h: number }
    view: 'design' | 'split' | 'code'
    selectedId: string | null
    movedIds: Set<string>
    movedDropped: number
    showViewToggle?: boolean
    showShortcutHint?: boolean
    showNote?: boolean
  }>(),
  {
    showViewToggle: true,
    showShortcutHint: false,
    showNote: true,
  },
)

const emit = defineEmits<{
  'update:view': [value: 'design' | 'split' | 'code']
  'update:selectedId': [id: string | null]
  move: [id: string, col: number, row: number]
  add: [source: { entityId?: string; kind?: string }, at?: { col: number; row: number }]
}>()

function onMove(id: string, col: number, row: number): void {
  emit('move', id, col, row)
}

function onAdd(
  source: { entityId?: string; kind?: string },
  at?: { col: number; row: number },
): void {
  emit('add', source, at)
}
</script>

<template>
  <section class="canvas">
    <div v-if="free && showViewToggle" class="toolbar">
      <div class="segments views-toggle">
        <button
          :class="{ active: view === 'design' }"
          title="Design"
          @click="emit('update:view', 'design')"
        >
          <MdiIcon name="palette-outline" :size="16" /> Design
        </button>
        <button
          :class="{ active: view === 'split' }"
          title="Split"
          @click="emit('update:view', 'split')"
        >
          <MdiIcon name="view-split-vertical" :size="16" /> Split
        </button>
        <button
          :class="{ active: view === 'code' }"
          title="Code"
          @click="emit('update:view', 'code')"
        >
          <MdiIcon name="code-json" :size="16" /> Code
        </button>
      </div>
      <span class="grow" />
      <span v-if="showShortcutHint" class="muted keys"
        >Arrows move the selection · Alt+arrows reorder · Ctrl+D duplicates · Delete removes</span
      >
    </div>
    <template v-if="free">
      <div class="views" :class="view">
        <DesignCanvas v-if="view !== 'code'" :screen="screen" :page="page" :device="device" />
        <CodeView v-if="view !== 'design'" :page="page" />
      </div>
      <p v-if="showNote" class="muted note">
        The canvas is close to the tablet, not exact; Preview on device shows the real thing.
      </p>
    </template>
    <template v-else>
      <GridMockup
        :screen="screen"
        :page="page"
        :device="device"
        :selected-id="selectedId"
        :moved="movedIds"
        @select="emit('update:selectedId', $event)"
        @move="onMove"
        @add="onAdd"
      />
      <p v-if="movedIds.size || movedDropped" class="warn">
        <MdiIcon name="alert-outline" :size="16" />
        <template v-if="movedDropped"
          >{{ movedDropped }} tile(s) do not fit this grid and are not shown.
        </template>
        <template v-if="movedIds.size"
          >{{ movedIds.size }} tile(s) were moved or shrunk to fit; drag them to place
          them.</template
        >
      </p>
    </template>
  </section>
</template>

<style scoped>
.canvas {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
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

.views-toggle button {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex: none;
  padding: 5px 12px;
}

.keys {
  font-size: 12px;
}

.views {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 12px;
}

.views.split > * {
  flex: 1 1 0;
}

.note {
  margin: 0;
  font-size: 12px;
}

.warn {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  color: #9a6b00;
}

@media (max-width: 1200px) {
  .keys {
    display: none;
  }
}
</style>
