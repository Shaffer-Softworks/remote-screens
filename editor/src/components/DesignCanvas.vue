<script setup lang="ts">
// The design surface of a free-form page: the device as the grid mockup draws it, with the page's components in
// its content area instead of the grid.
import { inject } from 'vue'
import { DESIGN } from '../model/design'
import type { Page, Screen } from '../model/types'
import GridMockup from './GridMockup.vue'
import NodeView from './NodeView.vue'

defineProps<{ screen: Screen; page: Page; device: { w: number; h: number } }>()
const ctx = inject(DESIGN)!
const NONE = new Set<string>()
</script>

<template>
  <GridMockup
    :screen="screen"
    :page="page"
    :device="device"
    :selected-id="null"
    :moved="NONE"
    class="design"
    @select="ctx.select(null)"
  >
    <NodeView v-if="page.root" :node="page.root" axis="box" is-root />
  </GridMockup>
</template>
