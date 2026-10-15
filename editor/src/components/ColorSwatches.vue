<script setup lang="ts">
import { colorOf, isHex, rgbOf } from '../model/palette'

// A row of theme colours (role or pastel keys) to pick one from; the first button clears the choice and the last
// picks any colour, stored as `#RRGGBB`.
const props = withDefaults(defineProps<{ keys: string[]; clear?: string; clearTitle?: string; clearColor?: string }>(), {
  clear: 'A',
  clearTitle: 'Default',
  clearColor: undefined,
})
const model = defineModel<string | undefined>()

function pick(e: Event) {
  model.value = (e.target as HTMLInputElement).value.toUpperCase()
}
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
    <label class="swatch custom" :class="{ on: isHex(model) }" :style="isHex(model) ? { background: colorOf(model) } : {}" :title="isHex(model) ? model : 'Any colour'">
      <input type="color" :value="rgbOf(model)" @input="pick" />
    </label>
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

.custom {
  position: relative;
  box-sizing: border-box;
  cursor: pointer;
  background: conic-gradient(red, yellow, lime, cyan, blue, magenta, red);
}

.custom input {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  opacity: 0;
  cursor: pointer;
}
</style>
