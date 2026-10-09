<script setup lang="ts">
// The page's component tree as JSON, editable. A change is applied once it parses and passes the add-on's checks.
import { ref, watch } from 'vue'
import { normalize, problems } from '../model/nodes'
import type { Node, Page } from '../model/types'

const props = defineProps<{ page: Page }>()

const text = ref('')
const error = ref('')
let applied = ''

watch(
  () => JSON.stringify(props.page.root ?? null, null, 2),
  (json) => {
    if (json === applied) return
    applied = json
    text.value = json
    error.value = ''
  },
  { immediate: true },
)

function edit(value: string): void {
  text.value = value
  let parsed: unknown
  try {
    parsed = normalize(JSON.parse(value))
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Not JSON'
    return
  }
  const wrong = problems(parsed)
  if (wrong) {
    error.value = wrong
    return
  }
  error.value = ''
  applied = JSON.stringify(parsed, null, 2)
  props.page.root = parsed as Node
}

function indent(e: KeyboardEvent): void {
  const t = e.target as HTMLTextAreaElement
  const { selectionStart: s, selectionEnd: end } = t
  t.setRangeText('  ', s, end, 'end')
  edit(t.value)
}
</script>

<template>
  <div class="code">
    <textarea
      :value="text"
      spellcheck="false"
      aria-label="Component tree as JSON"
      @input="edit(($event.target as HTMLTextAreaElement).value)"
      @keydown.tab.prevent="indent"
    />
    <p class="status" :class="{ bad: error }">{{ error || 'Applied. Edits here and on the canvas stay in step.' }}</p>
  </div>
</template>

<style scoped>
.code {
  flex: 1;
  min-height: 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

textarea {
  flex: 1;
  min-height: 200px;
  resize: none;
  font-family: ui-monospace, 'SF Mono', Menlo, monospace;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre;
  tab-size: 2;
}

.status {
  margin: 0;
  font-size: 12px;
  color: var(--muted);
}

.status.bad {
  color: var(--danger);
}
</style>
