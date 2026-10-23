<script setup lang="ts">
import { computed } from 'vue'
import { alphaOf, cssColor, packHex, rgbOf } from '../model/palette'

// A colour plus opacity: stores `#RRGGBB` when opaque, else `#AARRGGBB` (the add-on's order).
withDefaults(defineProps<{ disabled?: boolean; compact?: boolean }>(), {
  disabled: false,
  compact: false,
})
const model = defineModel<string | undefined>({ required: true })

const rgb = computed(() => rgbOf(model.value))
const alpha = computed(() => alphaOf(model.value))
const pct = computed(() => Math.round((alpha.value / 255) * 100))
const preview = computed(() => cssColor(packHex(rgb.value, alpha.value)))

function setRgb(e: Event) {
  model.value = packHex((e.target as HTMLInputElement).value, alpha.value)
}

function setAlpha(e: Event) {
  model.value = packHex(rgb.value, Number((e.target as HTMLInputElement).value))
}
</script>

<template>
  <div class="color-input" :class="{ compact, disabled }">
    <label class="swatch" :title="model || 'Colour'">
      <span class="board" :style="{ '--c': preview }" />
      <input type="color" :value="rgb" :disabled="disabled" @input="setRgb" />
    </label>
    <input
      v-range
      class="alpha"
      type="range"
      min="0"
      max="255"
      :value="alpha"
      :disabled="disabled"
      :title="`Opacity ${pct}%`"
      @input="setAlpha"
    />
    <span v-if="!compact" class="pct">{{ pct }}%</span>
  </div>
</template>

<style scoped>
.color-input {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.color-input.disabled {
  opacity: 0.55;
  pointer-events: none;
}

.swatch {
  position: relative;
  flex: none;
  width: 34px;
  height: 26px;
  border-radius: 6px;
  border: 1px solid var(--line);
  overflow: hidden;
  cursor: pointer;
}

.compact .swatch {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  border-width: 2px;
}

.board {
  display: block;
  width: 100%;
  height: 100%;
  background:
    linear-gradient(var(--c, transparent), var(--c, transparent)),
    repeating-conic-gradient(#c8c8c8 0% 25%, #ffffff 0% 50%) 0 0 / 8px 8px;
}

.swatch input {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  opacity: 0;
  cursor: pointer;
  border: 0;
  padding: 0;
}

.alpha {
  flex: 1;
  min-width: 48px;
  max-width: 120px;
}

.compact .alpha {
  width: 72px;
  flex: none;
  max-width: none;
}

.pct {
  flex: none;
  width: 3.2em;
  font-size: 12px;
  color: var(--muted);
  font-variant-numeric: tabular-nums;
  text-align: right;
}
</style>
