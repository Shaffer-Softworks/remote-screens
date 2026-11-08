<script setup lang="ts">
import { computed, provide, ref } from 'vue'
import { DESIGN, type DesignContext } from '../model/design'
import { PREVIEW_THEME, themeById, themeVars } from '../model/palette'
import { previewDevice, previewPage, previewThemeId } from '../model/screenPreview'
import type { Screen } from '../model/types'
import { state } from '../store'
import GridMockup from './GridMockup.vue'
import NodeView from './NodeView.vue'

const props = defineProps<{ screen: Screen }>()
const page = computed(() => previewPage(props.screen))
const device = computed(() => previewDevice(props.screen, state.players))
const theme = computed(() => themeById(previewThemeId(props.screen)))
provide(PREVIEW_THEME, theme)

// Thumbnails render saved data only. They must never attach the editor's mutating designer.
const noop = () => {}
const context: DesignContext = {
  root: computed(() => page.value?.root),
  selectedId: ref(null),
  selected: computed(() => undefined),
  hint: ref(null),
  select: noop, canDrop: () => false, drop: noop, add: noop,
  addComponent: noop, addEntity: noop, removeSelected: noop,
  duplicateSelected: noop, wrapSelected: noop, step: noop, shift: noop,
}
provide(DESIGN, context)
const moved = new Set<string>()
</script>

<template>
  <div class="screen-preview" :style="themeVars(theme)" aria-hidden="true" inert>
    <GridMockup
      v-if="page"
      class="thumbnail"
      :screen="screen"
      :page="page"
      :device="device"
      :selected-id="null"
      :moved="moved"
      thumbnail
    >
      <NodeView v-if="page.root" :node="page.root" axis="box" is-root />
    </GridMockup>
    <span v-else class="empty-preview">Empty screen — open to add a page</span>
  </div>
</template>

<style scoped>
.screen-preview {
  position: relative;
  display: flex;
  height: clamp(190px, 16.5vw, 300px);
  overflow: hidden;
  border-radius: 8px;
  background: var(--rc-page, #1b1c1e);
  color: var(--rc-ink, #fff);
  pointer-events: none;
  contain: layout paint;
}
.thumbnail { width: 100%; }
.thumbnail :deep(.device) {
  margin: 0;
  border-radius: 8px;
  box-shadow: none;
}
.thumbnail :deep(.cell) { display: none; }
.empty-preview { margin: auto; font-size: 13px; color: #b9bbbe; }
@media (max-width: 650px) {
  .screen-preview { height: clamp(190px, 52vw, 320px); }
}
</style>
