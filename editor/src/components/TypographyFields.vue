<script setup lang="ts">
/** Size, weight and align for free-form text-drawing components. Presets match server Type. */
withDefaults(
  defineProps<{
    fontSize?: string
    weight?: string
    align?: string
    defaultFontSize?: number
    defaultWeight?: string
    defaultAlign?: string
    showAlign?: boolean
    showWeight?: boolean
    minSize?: number
  }>(),
  {
    fontSize: undefined,
    weight: undefined,
    align: undefined,
    defaultFontSize: 16,
    defaultWeight: '400',
    defaultAlign: 'start',
    showAlign: true,
    showWeight: true,
    minSize: 6,
  },
)

const emit = defineEmits<{
  'update:fontSize': [value: string | undefined]
  'update:weight': [value: string | undefined]
  'update:align': [value: string | undefined]
}>()

const SIZE_PRESETS: { label: string; value: string }[] = [
  { label: 'Caption', value: '12' },
  { label: 'Body', value: '14' },
  { label: 'Title', value: '16' },
  { label: 'Headline', value: '22' },
  { label: 'Value', value: '28' },
  { label: 'Display', value: '44' },
  { label: 'Huge', value: '96' },
]

function setSize(value: string): void {
  emit('update:fontSize', value === '' ? undefined : value)
}

function setWeight(value: string): void {
  emit('update:weight', value === '' ? undefined : value)
}

function setAlign(value: string): void {
  emit('update:align', value === '' ? undefined : value)
}
</script>

<template>
  <div class="typography">
    <div class="field">
      <span class="label">Size</span>
      <div class="presets">
        <button
          v-for="preset in SIZE_PRESETS"
          :key="preset.value"
          type="button"
          :class="{ on: String(fontSize ?? defaultFontSize) === preset.value }"
          :title="`${preset.label} ${preset.value}sp`"
          @click="setSize(preset.value)"
        >
          {{ preset.label }}
        </button>
      </div>
    </div>
    <div class="row two">
      <label class="field"
        >Size (sp)<input
          type="number"
          :min="minSize"
          step="1"
          :value="fontSize ?? defaultFontSize"
          @input="setSize(($event.target as HTMLInputElement).value)"
      /></label>
      <label v-if="showWeight" class="field">
        Weight
        <select :value="weight ?? defaultWeight" @change="setWeight(($event.target as HTMLSelectElement).value)">
          <option value="300">Light</option>
          <option value="400">Regular</option>
          <option value="500">Medium</option>
          <option value="600">Semi-bold</option>
          <option value="700">Bold</option>
        </select>
      </label>
      <label v-else-if="showAlign" class="field">
        Align
        <select
          :value="align ?? defaultAlign"
          @change="setAlign(($event.target as HTMLSelectElement).value)"
        >
          <option value="start">Start</option>
          <option value="center">Center</option>
          <option value="end">End</option>
        </select>
      </label>
    </div>
    <label v-if="showAlign && showWeight" class="field">
      Align
      <select
        :value="align ?? defaultAlign"
        @change="setAlign(($event.target as HTMLSelectElement).value)"
      >
        <option value="start">Start</option>
        <option value="center">Center</option>
        <option value="end">End</option>
      </select>
    </label>
  </div>
</template>

<style scoped>
.typography {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12px;
  color: var(--muted);
}

.field :is(input, select) {
  color: var(--ink);
  font-size: 14px;
}

.label {
  font-size: 12px;
}

.presets {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.presets button {
  padding: 4px 8px;
  font-size: 11px;
  border-radius: 6px;
}

.presets button.on {
  background: var(--accent);
  border-color: var(--accent);
  color: var(--accent-ink);
}

.row.two {
  display: flex;
  gap: 10px;
}

.row.two > .field {
  flex: 1;
  min-width: 0;
}
</style>
