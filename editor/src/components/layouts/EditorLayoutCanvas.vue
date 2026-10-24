<script setup lang="ts">
import { computed, inject } from 'vue'
import { DESIGN } from '../../model/design'
import { componentOf, parentOf } from '../../model/nodes'
import type { Node, Page, Screen, Tile, TileSource } from '../../model/types'
import AttributesPanel from '../AttributesPanel.vue'
import ComponentPalette from '../ComponentPalette.vue'
import ComponentTree from '../ComponentTree.vue'
import EntityLibrary from '../EntityLibrary.vue'
import MdiIcon from '../MdiIcon.vue'
import PagePanel from '../PagePanel.vue'
import ScreenPanel from '../ScreenPanel.vue'
import TileDrawer from '../TileDrawer.vue'
import EditorCanvasSection from './EditorCanvasSection.vue'

type RailTool = 'insert' | 'layers' | 'page' | 'screen'

const props = defineProps<{
  screen: Screen
  page: Page
  free: boolean
  device: { key: string; label: string; w: number; h: number }
  view: 'design' | 'split' | 'code'
  selectedId: string | null
  selected?: Tile
  node?: Node
  railTool: RailTool | null
  movedIds: Set<string>
  movedDropped: number
}>()

const emit = defineEmits<{
  'update:view': [value: 'design' | 'split' | 'code']
  'update:selectedId': [id: string | null]
  'update:pageId': [id: string]
  'update:railTool': [value: RailTool | null]
  move: [id: string, col: number, row: number]
  add: [source: TileSource, at?: { col: number; row: number }]
  deletePage: []
  convert: []
  removeTile: [id: string]
  addPage: [kind: 'free' | 'grid']
}>()

const design = inject(DESIGN)

const hasSelection = computed(() => !!(props.node || props.selected))

const activeTool = computed((): RailTool | null => props.railTool)

const flyoutTitle = computed(() => {
  switch (activeTool.value) {
    case 'insert':
      return props.free ? 'Add components' : 'Add tiles'
    case 'layers':
      return 'Component tree'
    case 'page':
      return 'This page'
    case 'screen':
      return 'This screen'
    default:
      return ''
  }
})

const flyoutBlurb = computed(() => {
  switch (activeTool.value) {
    case 'insert':
      return props.free
        ? 'Drag onto the preview or the tree, or click to add after the selection.'
        : 'Pick an entity or tile kind to place on the grid.'
    case 'layers':
      return 'Select a component here or on the preview. Drag to reorder.'
    case 'page':
      return 'Title, nav icon, home page, and layout for the page you are editing.'
    case 'screen':
      return 'Themes, navigation, kiosk, and other settings for the whole tablet.'
    default:
      return ''
  }
})

const selectionTitle = computed(() => {
  if (props.node) return componentOf(props.node.type)?.label ?? props.node.type
  if (props.selected) return props.selected.kind || 'Tile'
  return 'Selection'
})

const pageKind = computed(() => (props.free ? 'Free-form' : 'Grid'))

function toggleRail(tool: RailTool): void {
  if (tool === 'layers' && !props.free) return
  const next = activeTool.value === tool ? null : tool
  emit('update:railTool', next)
}

function closeFlyout(): void {
  emit('update:railTool', null)
}

function clearSelection(): void {
  emit('update:selectedId', null)
  design?.select(null)
}

function onLibraryAdd(source: TileSource): void {
  if (props.free && design) design.addEntity(source)
  else emit('add', source)
}
</script>

<template>
  <main class="body">
    <nav class="rail" aria-label="Editor tools">
      <button
        type="button"
        class="rail-btn"
        :class="{ active: activeTool === 'insert' }"
        :aria-pressed="activeTool === 'insert'"
        @click="toggleRail('insert')"
      >
        <MdiIcon name="plus" :size="18" />
        <span>Add</span>
      </button>
      <button
        type="button"
        class="rail-btn"
        :class="{ active: activeTool === 'layers' }"
        :aria-pressed="activeTool === 'layers'"
        :disabled="!free"
        :title="free ? 'Component tree' : 'Only on free-form pages'"
        @click="toggleRail('layers')"
      >
        <MdiIcon name="file-tree-outline" :size="18" />
        <span>Tree</span>
      </button>
      <button
        type="button"
        class="rail-btn"
        :class="{ active: activeTool === 'page' }"
        :aria-pressed="activeTool === 'page'"
        @click="toggleRail('page')"
      >
        <MdiIcon name="file-document-outline" :size="18" />
        <span>Page</span>
      </button>
      <span class="grow" />
      <button
        type="button"
        class="rail-btn"
        :class="{ active: activeTool === 'screen' }"
        :aria-pressed="activeTool === 'screen'"
        @click="toggleRail('screen')"
      >
        <MdiIcon name="cog-outline" :size="18" />
        <span>Screen</span>
      </button>
    </nav>

    <aside v-if="activeTool" class="flyout card" :aria-label="flyoutTitle">
      <header class="panel-head">
        <div>
          <strong>{{ flyoutTitle }}</strong>
          <p class="muted blurb">{{ flyoutBlurb }}</p>
        </div>
        <button type="button" class="icon" title="Close panel" @click="closeFlyout">
          <MdiIcon name="close" :size="18" />
        </button>
      </header>
      <div class="panel-body">
        <ComponentPalette v-if="activeTool === 'insert' && free" :page="page" />
        <EntityLibrary v-else-if="activeTool === 'insert'" :page="page" @add="onLibraryAdd" />
        <ComponentTree v-else-if="activeTool === 'layers' && free" />
        <PagePanel
          v-else-if="activeTool === 'page'"
          :screen="screen"
          :page="page"
          @delete="emit('deletePage')"
          @convert="emit('convert')"
          @add-page="kind => emit('addPage', kind)"
        />
        <ScreenPanel v-else-if="activeTool === 'screen'" :screen="screen" :device-width="device.w" />
      </div>
    </aside>

    <div class="center">
      <div class="page-row">
        <label class="row">
          <span class="muted">Editing</span>
          <select
            :value="page.id"
            aria-label="Page"
            @change="emit('update:pageId', ($event.target as HTMLSelectElement).value)"
          >
            <option v-for="entry in screen.pages" :key="entry.id" :value="entry.id">
              {{ entry.title }}
            </option>
          </select>
          <span class="kind muted">{{ pageKind }}</span>
        </label>
        <span class="grow" />
        <button
          type="button"
          class="quiet"
          title="Add a free-form page"
          @click="emit('addPage', 'free')"
        >
          <MdiIcon name="vector-square" :size="16" />
          Add free-form
        </button>
        <button
          type="button"
          class="quiet"
          title="Add a grid page"
          @click="emit('addPage', 'grid')"
        >
          <MdiIcon name="view-grid-outline" :size="16" />
          Add grid
        </button>
      </div>

      <div class="canvas-wrap">
        <EditorCanvasSection
          :screen="screen"
          :page="page"
          :free="free"
          :device="device"
          :view="view"
          :selected-id="selectedId"
          :moved-ids="movedIds"
          :moved-dropped="movedDropped"
          :show-note="false"
          @update:view="emit('update:view', $event)"
          @update:selected-id="emit('update:selectedId', $event)"
          @move="(id, col, row) => emit('move', id, col, row)"
          @add="(source, at) => emit('add', source, at)"
        />

        <p v-if="!hasSelection && !activeTool" class="canvas-tip muted">
          Click something on the tablet to edit it. Use <strong>Add</strong> on the left to insert
          {{ free ? 'components' : 'tiles' }}.
        </p>

        <aside v-if="hasSelection" class="overlay card" aria-label="Selection properties">
          <header class="panel-head">
            <div>
              <span class="muted tiny">Selected</span>
              <strong>{{ selectionTitle }}</strong>
            </div>
            <button type="button" class="icon" title="Deselect (Esc)" @click="clearSelection">
              <MdiIcon name="close" :size="18" />
            </button>
          </header>
          <div class="panel-body">
            <AttributesPanel
              v-if="node"
              :node="node"
              :screen="screen"
              :is-root="node.id === page.root?.id"
              :parent-type="page.root ? parentOf(page.root, node.id)?.parent.type : undefined"
            />
            <TileDrawer
              v-else-if="selected"
              :tile="selected"
              :page="page"
              :screen="screen"
              @close="clearSelection"
              @remove="emit('removeTile', selected.id)"
            />
          </div>
        </aside>
      </div>
    </div>
  </main>
</template>

<style scoped>
.body {
  flex: 1;
  display: flex;
  gap: 8px;
  padding: 12px 16px 16px;
  min-height: 0;
}

.rail {
  width: 56px;
  flex: none;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 6px;
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--radius);
}

.rail-btn {
  width: 100%;
  height: auto;
  min-height: 44px;
  padding: 6px 2px;
  border-radius: 10px;
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  font-size: 10px;
  font-weight: 500;
  line-height: 1.1;
}

.rail-btn.active {
  background: var(--ink);
  color: var(--bg);
  border-color: var(--ink);
}

.flyout {
  width: 300px;
  flex: none;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 0;
  min-height: 0;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  padding-bottom: 10px;
  margin-bottom: 10px;
  border-bottom: 1px solid var(--line);
  flex: none;
}

.panel-head strong {
  display: block;
  font-weight: 500;
}

.blurb {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.35;
}

.tiny {
  display: block;
  font-size: 11px;
  margin-bottom: 2px;
}

.panel-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.center {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.page-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: none;
  flex-wrap: wrap;
}

.kind {
  font-size: 12px;
  padding: 2px 8px;
  border: 1px solid var(--line);
  border-radius: 999px;
}

.quiet {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  padding: 5px 10px;
}

.canvas-wrap {
  position: relative;
  flex: 1;
  min-height: 0;
  display: flex;
}

.canvas-wrap :deep(.canvas) {
  flex: 1;
}

.canvas-tip {
  position: absolute;
  left: 50%;
  bottom: 16px;
  transform: translateX(-50%);
  z-index: 3;
  margin: 0;
  padding: 8px 14px;
  max-width: min(420px, 90%);
  text-align: center;
  font-size: 12px;
  line-height: 1.4;
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: 999px;
  box-shadow: var(--shadow);
  pointer-events: none;
}

.canvas-tip strong {
  color: var(--ink);
  font-weight: 500;
}

.overlay {
  position: absolute;
  top: 8px;
  right: 8px;
  bottom: 8px;
  width: min(340px, 42%);
  z-index: 5;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 12px 14px;
  box-shadow: var(--shadow);
}

@media (max-width: 900px) {
  .body {
    flex-direction: column;
  }

  .rail {
    width: auto;
    flex-direction: row;
    flex-wrap: wrap;
  }

  .rail-btn {
    width: auto;
    min-width: 56px;
    flex-direction: row;
    gap: 6px;
    padding: 6px 10px;
  }

  .flyout {
    width: auto;
  }

  .overlay {
    width: auto;
    left: 8px;
  }
}
</style>
