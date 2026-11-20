<script setup lang="ts">
// The design surface of a free-form page. The page fills the device; the tablet's top and bottom bars stay on
// grid pages and on the dashboard thumbnails.
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
    :show-bars="false"
    class="design"
    @select="ctx.select(null)"
  >
    <NodeView v-if="page.root" :node="page.root" axis="box" is-root />
  </GridMockup>
</template>
