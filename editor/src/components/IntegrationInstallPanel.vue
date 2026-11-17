<script setup lang="ts">
import { computed } from 'vue'
import { HACS_INTEGRATION_URL, MANUAL_INTEGRATION_DOCS_URL } from '../model/integrationInstall'
import { state } from '../store'
import MdiIcon from './MdiIcon.vue'

defineProps<{ expanded?: boolean }>()

const isInstalled = computed(() => state.info?.integrationInstalled === true)
</script>

<template>
  <details class="card integration" :open="expanded">
    <summary>Home Assistant integration</summary>
    <div class="body">
      <p class="muted">
        Optional entities and services for each screen (brightness, standby, alerts, pages, and more).
        Install with HACS, restart Home Assistant, then accept the setup prompt the add-on announces.
      </p>
      <p v-if="isInstalled" class="status" role="status">
        <MdiIcon name="check-circle" :size="18" /> Installed
      </p>
      <a
        v-else
        class="primary install"
        :href="HACS_INTEGRATION_URL"
        target="_blank"
        rel="noopener"
      >
        <MdiIcon name="home-assistant" :size="18" /> Install integration
      </a>
      <p class="muted fallback">
        Without HACS, copy
        <code>custom_components/remote_screens/</code>
        into Home Assistant’s config folder, then restart. See
        <a :href="MANUAL_INTEGRATION_DOCS_URL" target="_blank" rel="noopener">Install</a>.
      </p>
    </div>
  </details>
</template>

<style scoped>
.integration {
  margin-block: 20px;
}
summary {
  cursor: pointer;
  font-weight: 600;
}
.body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 16px;
  max-width: 720px;
}
.status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  color: #9bf0bc;
}
a.install {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  align-self: flex-start;
  padding: 8px 14px;
  min-height: 36px;
  font: inherit;
  color: var(--accent-ink);
  background: var(--accent);
  border: 1px solid var(--accent);
  border-radius: 10px;
  text-decoration: none;
}
a.install:hover {
  filter: brightness(1.08);
}
.fallback {
  margin: 0;
  font-size: 13px;
}
.fallback a {
  color: inherit;
  text-decoration: underline;
}
code {
  font-size: 0.92em;
}
</style>
