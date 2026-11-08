<script setup lang="ts">
import QRCode from 'qrcode'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { api } from '../api'
import type { Enrollment, EnrollmentRequest } from '../model/types'
import { state } from '../store'
import MdiIcon from './MdiIcon.vue'

const emit = defineEmits<{ close: [] }>()
const dialog = ref<HTMLDialogElement>()
const available = computed(() => !!state.info?.playerApp)
const busy = ref(false)
const error = ref('')
let closed = false
let trigger: HTMLElement | null = null
onMounted(() => {
  trigger = document.activeElement instanceof HTMLElement ? document.activeElement : null
  dialog.value?.showModal()
})
onBeforeUnmount(() => {
  closed = true
  wifiPassword.value = ''
  enrollment.value = null
  qrImage.value = ''
  dialog.value?.close()
  trigger?.focus()
})

const wifiSsid = ref('')
const wifiPassword = ref('')
const wifiSecurity = ref<NonNullable<EnrollmentRequest['wifiSecurity']>>('WPA')
const host = ref('')

const enrollment = ref<Enrollment | null>(null)
const qrImage = ref('')

const expiresAt = computed(() =>
  enrollment.value
    ? new Date(enrollment.value.expiresAt).toLocaleTimeString([], {
        hour: '2-digit',
        minute: '2-digit',
      })
    : '',
)

async function showCode(): Promise<void> {
  if (!available.value || busy.value) return
  busy.value = true
  error.value = ''
  const request: EnrollmentRequest = {
    wifiSsid: wifiSsid.value.trim() || undefined,
    wifiPassword: wifiPassword.value || undefined,
    wifiSecurity: wifiSecurity.value,
    host: host.value.trim() || undefined,
  }
  try {
    const created = await api.enrollment(request)
    if (closed) return
    const image = await QRCode.toDataURL(created.provisioning, {
      errorCorrectionLevel: 'M',
      margin: 2,
      width: 320,
    })
    if (closed) return
    qrImage.value = image
    enrollment.value = created
    wifiPassword.value = ''
  } catch (cause) {
    if (!closed) error.value = cause instanceof Error ? cause.message : 'Could not create an enrollment code.'
  } finally {
    busy.value = false
  }
}

/** The Wi-Fi password lives only in the code: forget both once it has been scanned. */
function done(): void {
  closed = true
  wifiPassword.value = ''
  enrollment.value = null
  qrImage.value = ''
  emit('close')
}
</script>

<template>
  <dialog ref="dialog" class="enroll-dialog" aria-labelledby="enrollment-title" @cancel.prevent="done">
  <div class="enroll">
    <div class="row">
      <MdiIcon name="qrcode-scan" :size="22" />
      <h2 id="enrollment-title" class="grow">Enroll a tablet as a kiosk</h2>
      <button class="icon quiet" aria-label="Close enrollment" @click="done">
        <MdiIcon name="close" :size="18" />
      </button>
    </div>
    <p class="muted">
      An enrolled tablet runs only Remote Screens: with <strong>Kiosk lock</strong> on, nobody can
      leave the screen on the tablet. It has to be factory reset first, with no Google account
      added.
    </p>

    <p v-if="!available" class="availability" role="status">
      QR enrollment requires the player APK to be hosted by the add-on.
      {{ state.info?.demo ? 'This sample preview does not provide an installable APK.' : 'The add-on is not currently serving a player APK.' }}
      You can still download the app from GitHub and pair it manually.
    </p>

    <template v-if="!enrollment">
      <fieldset :disabled="busy || !available" class="enrollment-fields">
      <div class="row fields">
        <label class="field grow"
          >Wi-Fi name (optional)<input v-model="wifiSsid" autocomplete="off"
        /></label>
        <label class="field">
          Security
          <select v-model="wifiSecurity" :disabled="!wifiSsid.trim()">
            <option value="WPA">WPA / WPA2 / WPA3</option>
            <option value="WEP">WEP</option>
            <option value="NONE">Open</option>
          </select>
        </label>
        <label class="field grow">
          Wi-Fi password
          <input
            v-model="wifiPassword"
            type="password"
            autocomplete="new-password"
            :disabled="!wifiSsid.trim() || wifiSecurity === 'NONE'"
          />
        </label>
      </div>
      <label class="field">
        Add-on address (optional, when tablets can't reach the one the add-on finds)
        <input v-model="host" placeholder="192.168.1.10" autocomplete="off" />
      </label>
      <p class="muted small">The Wi-Fi password only goes into the code. It is not saved.</p>
      <div class="row">
        <span class="grow" />
        <button class="primary" :disabled="busy || !available" @click="showCode">{{ busy ? 'Creating code…' : 'Show QR code' }}</button>
      </div>
      </fieldset>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
    </template>

    <div v-else class="row result">
      <img :src="qrImage" alt="Enrollment QR code" width="320" height="320" />
      <ol class="steps">
        <li>Factory reset the tablet, or start a new one.</li>
        <li>
          On the first Welcome screen, tap the same spot six times. A QR scanner opens (some tablets
          ask for Wi-Fi first).
        </li>
        <li>
          Scan this code. The tablet joins Wi-Fi, downloads Remote Screens from the add-on and
          finishes setting up.
        </li>
        <li>
          Pair it with a screen as usual, and turn on that screen's <strong>Kiosk lock</strong>.
        </li>
        <li class="muted">The code works for several tablets until {{ expiresAt }}.</li>
      </ol>
    </div>
    <div v-if="enrollment" class="row">
      <span class="grow" />
      <button class="primary" @click="done">Done</button>
    </div>
  </div>
  </dialog>
</template>

<style scoped>
.enroll-dialog {
  width: min(900px, calc(100vw - 32px));
  max-height: calc(100dvh - 48px);
  overflow: auto;
  padding: 24px;
}
.enroll-dialog::backdrop { background: rgb(0 0 0 / 65%); }
.enrollment-fields { border: 0; padding: 0; margin: 0; min-width: 0; display: flex; flex-direction: column; gap: 14px; }
.availability { border: 1px solid var(--line); padding: 14px; border-radius: 8px; color: var(--muted); }
.error { color: var(--danger); }
.enroll {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.enroll p {
  margin: 0;
}

.fields {
  flex-wrap: wrap;
  align-items: flex-end;
}

.small {
  font-size: 12px;
}

.result {
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 20px;
}

.result img {
  max-width: 100%;
  height: auto;
  background: #fff;
  border-radius: 10px;
  flex: none;
}

.steps {
  margin: 0;
  padding-left: 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: 460px;
}

button.quiet {
  color: var(--muted);
  border-color: transparent;
  background: transparent;
}
</style>
