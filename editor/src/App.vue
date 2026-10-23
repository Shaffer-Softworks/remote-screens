<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import ScreensView from './components/ScreensView.vue'
import ScreenEditor from './components/ScreenEditor.vue'
import ThemesView from './components/ThemesView.vue'
import { dirty, editing, load, open, pollPlayers, state } from './store'
import type { Screen } from './model/types'

const theming = ref(false)

function edit(screen: Screen | null): void {
  if (screen === null && dirty.value && !confirm('Leave without saving?')) return
  open(screen)
}

function beforeUnload(e: BeforeUnloadEvent): void {
  if (dirty.value) e.preventDefault()
}

onMounted(() => {
  load()
  pollPlayers()
  window.addEventListener('beforeunload', beforeUnload)
})
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
</script>

<template>
  <div class="app">
    <div v-if="state.error" class="card failed">
      <h2>The add-on did not answer</h2>
      <p class="muted">{{ state.error }}</p>
      <button class="primary" @click="load">Try again</button>
    </div>
    <p v-else-if="!state.layout" class="muted loading">Loading…</p>
    <ScreenEditor v-else-if="editing" :key="editing.id" @close="edit(null)" />
    <ThemesView v-else-if="theming" @close="theming = false" />
    <ScreensView v-else @edit="edit" @themes="theming = true" />
    <div v-if="state.toast" class="toast">{{ state.toast }}</div>
  </div>
</template>

<style scoped>
.app {
  min-height: 100vh;
}

.failed {
  max-width: 420px;
  margin: 15vh auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.loading {
  text-align: center;
  margin-top: 20vh;
}

.toast {
  position: fixed;
  left: 50%;
  bottom: 24px;
  transform: translateX(-50%);
  background: var(--ink);
  color: var(--bg);
  padding: 10px 18px;
  border-radius: 999px;
  box-shadow: var(--shadow);
  z-index: 50;
}
</style>
