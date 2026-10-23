<script setup lang="ts">
import { computed } from 'vue'
import { COLORS, VIEWPORT, weatherPieces } from '../model/haWeatherIcons'
import MdiIcon from './MdiIcon.vue'

const props = withDefaults(
  defineProps<{
    condition: string
    size?: number
    nightTime?: boolean
    fallback?: string
  }>(),
  { size: 28, nightTime: false, fallback: 'weather-partly-cloudy' },
)

const pieces = computed(() => weatherPieces(props.condition, props.nightTime))
</script>

<template>
  <svg
    v-if="pieces"
    :width="size"
    :height="size"
    :viewBox="`0 0 ${VIEWPORT} ${VIEWPORT}`"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
  >
    <path
      v-for="(piece, index) in pieces"
      :key="index"
      :d="piece.path"
      :fill="COLORS[piece.layer]"
    />
  </svg>
  <MdiIcon v-else :name="fallback" :size="size" />
</template>
