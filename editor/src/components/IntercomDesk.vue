<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { IntercomBrowser, intercomClientId, intercomSignalingUrl } from '../model/intercomCall'
import type { IntercomDeskState } from '../model/intercomCall'

const state = ref<IntercomDeskState>({
  status: 'Connecting…',
  isMuted: false,
  isBusy: false,
  playbackBlocked: false,
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
      <button v-if="state.playbackBlocked" @click="browser.resumeAudio()">Enable audio</button>
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
  height: 420px;
  min-height: 0;
  background: var(--rc-page, var(--bg));
}

.side {
  display: flex;
  flex-direction: column;
  gap: 16px;
  flex: 0 0 240px;
  box-sizing: border-box;
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
  flex-shrink: 0;
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
  min-width: 0;
  min-height: 0;
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
  min-height: 100px;
  flex-shrink: 0;
  gap: 12px;
  padding: 0 20px;
  background: var(--rc-strip, var(--card));
}

.station div {
  min-width: 0;
  overflow-wrap: anywhere;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.empty {
  color: var(--rc-ink, var(--ink));
}
@media (max-width: 640px) {
  .desk {
    flex-direction: column;
    height: 520px;
  }

  .side {
    flex: none;
    width: 100%;
    padding: 16px;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
  }

  .side .bar,
  .side h2,
  .side p {
    grid-column: 1 / -1;
  }

  .side button {
    width: 100%;
  }

  .stations {
    padding: 12px;
  }

  .station {
    padding: 0 12px;
  }

  .station button {
    width: 72px;
  }
}
</style>
