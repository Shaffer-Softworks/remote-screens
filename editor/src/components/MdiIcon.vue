<script setup lang="ts">
import { computed } from 'vue'
import { iconPath } from '../model/icons'

const props = withDefaults(defineProps<{ name: string; size?: number }>(), { size: 24 })
const d = computed(() => iconPath(props.name))
</script>

<template>
  <svg :width="size" :height="size" viewBox="0 0 24 24" aria-hidden="true">
    <path v-if="d" :d="d" fill="currentColor" />
  </svg>
</template>
