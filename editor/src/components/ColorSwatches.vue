<script setup lang="ts">
import { computed } from 'vue'
import { colorOf, isHex, packHex, rgbOf } from '../model/palette'
import ColorInput from './ColorInput.vue'

// A row of theme colours (role or pastel keys) to pick one from; the first button clears the choice and the last
// picks any colour, stored as `#RRGGBB` or `#AARRGGBB`.
const props = withDefaults(defineProps<{ keys: string[]; clear?: string; clearTitle?: string; clearColor?: string }>(), {
  clear: 'A',
  clearTitle: 'Default',
  clearColor: undefined,
})
const model = defineModel<string | undefined>()

/** The custom picker's hex: the model when it is hex, else the resolved colour at full opacity to start a pick from. */
const custom = computed({
  get: () => (isHex(model.value) ? model.value! : packHex(rgbOf(model.value))),
  set: (v: string) => {
    model.value = v
  },
})
</script>

<template>
  <div class="swatches">
    <button
      type="button"
      class="swatch clear"
      :class="{ on: !model }"
      :style="props.clearColor ? { background: props.clearColor } : {}"
      :title="clearTitle"
      @click="model = undefined"
    >
      {{ clear }}
    </button>
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
    <ColorInput v-model="custom" class="custom" :class="{ on: isHex(model) }" compact />
  </div>
</template>

<style scoped>
.swatches {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
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

.swatch.on,
.custom.on :deep(.swatch) {
  border-color: var(--accent);
  box-shadow: 0 0 0 2px var(--accent);
}

.custom:not(.on) :deep(.board) {
  background: conic-gradient(red, yellow, lime, cyan, blue, magenta, red);
}
</style>
