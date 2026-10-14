<script setup lang="ts">
import { colorOf } from '../model/palette'

// A row of theme colours (role or pastel keys) to pick one from; the first button clears the choice.
withDefaults(defineProps<{ keys: string[]; clear?: string; clearTitle?: string }>(), { clear: 'A', clearTitle: 'Default' })
const model = defineModel<string | undefined>()
</script>

<template>
  <div class="swatches">
    <button type="button" class="swatch clear" :class="{ on: !model }" :title="clearTitle" @click="model = undefined">{{ clear }}</button>
    <button
      v-for="k in keys"
      :key="k"
      type="button"
      class="swatch"
      :class="{ on: model === k }"
      :style="{ background: colorOf(k) }"
      :title="k"
      @click="model = k"
    />
  </div>
</template>

<style scoped>
.swatches {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.swatch {
  width: 26px;
  height: 26px;
  padding: 0;
  border-radius: 50%;
  border: 2px solid var(--line);
  color: var(--muted);
  font-size: 11px;
}

.swatch.on {
  border-color: var(--accent);
  box-shadow: 0 0 0 2px var(--accent);
}
</style>
