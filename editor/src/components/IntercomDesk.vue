<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { IntercomBrowser, intercomClientId, intercomSignalingUrl } from '../model/intercomCall'
import type { IntercomDeskState } from '../model/intercomCall'

const state = ref<IntercomDeskState>({
  status: 'Connecting…',
  isMuted: false,
  isBusy: false,
  stations: [],
})

const browser = new IntercomBrowser(intercomClientId('ha'), 'Home Assistant', next => {
  state.value = next
})
browser.start(intercomSignalingUrl())
onBeforeUnmount(() => browser.stop())
</script>

<template>
  <section class="desk">
    <div class="side">
      <i class="bar" />
      <h2>INTERCOM</h2>
      <p>{{ state.status }}</p>
      <button @click="browser.toggleMute()">{{ state.isMuted ? 'Muted' : 'Mute' }}</button>
      <button @click="browser.hangUp()">Hang up</button>
    </div>
    <div class="stations">
      <p v-if="state.stations.length === 0" class="empty">No other stations are online.</p>
      <article v-for="station in state.stations" :key="station.clientId" class="station">
        <div>
          <strong>{{ station.displayName }}</strong>
          <small>{{ station.inCall ? 'In Call' : 'Online' }}</small>
        </div>
        <button
          :disabled="state.isBusy || station.inCall"
          @click="browser.call(station.clientId)"
        >
          Call
        </button>
      </article>
    </div>
  </section>
</template>

<style scoped>
.desk {
  display: flex;
  flex: 1;
  min-height: 0;
  background: var(--rc-page, var(--bg));
}

.side {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 350px;
  padding: 28px 20px;
  background: var(--rc-strip, var(--card));
}

.bar {
  width: 120px;
  height: 10px;
  background: var(--rc-accent-dark, var(--accent));
}

h2,
p,
strong,
small {
  margin: 0;
  color: var(--rc-ink, var(--ink));
}

button {
  width: 150px;
  height: 50px;
  border: 0;
  background: var(--rc-accent-dark, var(--accent));
  color: var(--rc-ink, var(--ink));
  cursor: pointer;
}

button:disabled {
  opacity: 0.4;
  cursor: default;
}

.stations {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 20px;
  overflow: auto;
}

.station {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 100px;
  padding: 0 20px;
  background: var(--rc-strip, var(--card));
}

.station div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.empty {
  color: var(--rc-ink, var(--ink));
}
</style>
