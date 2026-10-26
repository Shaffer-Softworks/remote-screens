// A dashboard station on the Remote Screens intercom.
// Signaling goes through Home Assistant; the audio goes directly to the tablet on the local network.

const HEARTBEAT_MS = 30000;
const CALL_TIMEOUT_MS = 30000;
const CLIENT_ID_BYTES = 16;
const RANDOM_BYTE = 256;

// crypto.randomUUID throws on the plain HTTP page Home Assistant serves on the LAN.
function intercomId() {
  const bytes = new Uint8Array(CLIENT_ID_BYTES);
  if (typeof crypto !== "undefined" && typeof crypto.getRandomValues === "function") {
    crypto.getRandomValues(bytes);
  } else {
    for (let index = 0; index < bytes.length; index += 1) {
      bytes[index] = Math.floor(Math.random() * RANDOM_BYTE);
    }
  }
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("");
}

class RemoteScreensIntercomCard extends HTMLElement {
  setConfig(config) {
    this._config = config || {};
    if (!this._root) this._root = this.attachShadow({ mode: "open" });
    this._render();
  }

  set hass(hass) {
    this._hass = hass;
    if (!this._started) {
      this._started = true;
      this._clientId = `ha-${intercomId()}`;
      this._status = "Connecting…";
      this._stations = [];
      this._busy = false;
      this._muted = false;
      this._phase = "offline";
      this._peerId = null;
      this._names = {};
      this._pendingCandidates = [];
      this._remoteIsSet = false;
      this._canSendCandidates = false;
      void this._open();
    }
  }

  getCardSize() {
    return 3;
  }

  disconnectedCallback() {
    this._stop();
  }

  async _open() {
    this._session = intercomId();
    this._unsubscribe = await this._hass.connection.subscribeMessage(
      (event) => this._handle(event.message),
      { type: "remote_screens/intercom/subscribe", session: this._session },
    );
    this._send({
      type: "register",
      clientId: this._clientId,
      displayName: this._hass.user?.name || "Home Assistant",
    });
    this._heartbeat = window.setInterval(() => this._send({ type: "heartbeat" }), HEARTBEAT_MS);
    this._render();
  }

  _stop() {
    window.clearInterval(this._heartbeat);
    window.clearTimeout(this._callTimer);
    this._endMedia(false);
    if (this._unsubscribe) this._unsubscribe();
  }

  _send(frame) {
    if (!this._hass || !this._session) return;
    void this._hass.connection.sendMessagePromise({
      type: "remote_screens/intercom/send",
      session: this._session,
      message: JSON.stringify(frame),
    });
  }

  async _handle(raw) {
    const frame = JSON.parse(raw);
    if (frame.type === "registered") {
      this._phase = "idle";
      this._status = "Online";
      this._send({ type: "getClients" });
    } else if (frame.type === "ping") {
      this._send({ type: "pong", ts: frame.ts || 0 });
    } else if (frame.type === "clientsList") {
      this._takeRoster(frame.clients || []);
    } else if (frame.type === "offer") {
      await this._onOffer(frame.sdp, frame.from);
    } else if (frame.type === "answer" && this._phase === "outgoing") {
      await this._peer.setRemoteDescription({ type: "answer", sdp: frame.sdp });
      await this._flushCandidates();
    } else if (frame.type === "candidate") {
      this._rememberCandidate(frame);
    } else if (frame.type === "hangup") {
      this._endMedia(false, "Call ended");
    } else if (frame.type === "unavailable" && frame.targetId === this._peerId) {
      this._endMedia(true, frame.reason === "busy" ? `${this._name(frame.targetId)} is busy` : "No answer");
    } else if (frame.type === "replaced" || (frame.type === "error" && frame.message === "not_registered")) {
      this._endMedia(false);
      this._phase = "offline";
      this._status = "Connecting…";
      this._send({
        type: "register",
        clientId: this._clientId,
        displayName: this._hass.user?.name || "Home Assistant",
      });
    }
    this._render();
  }

  async _call(clientId) {
    if (this._phase !== "idle") return;
    this._peerId = clientId;
    try {
      await this._openPeer();
      const offer = await this._peer.createOffer();
      await this._peer.setLocalDescription(offer);
      const sdp = await this._gatheredSdp();
      if (!sdp) throw new Error("No offer");
      this._phase = "outgoing";
      this._canSendCandidates = true;
      this._busy = true;
      this._status = `Calling ${this._name(clientId)}`;
      this._send({ type: "offer", to: clientId, sdp });
      this._armTimeout();
    } catch (ignored) {
      this._endMedia(true, "Call failed");
    }
    this._render();
  }

  async _onOffer(sdp, from) {
    if (this._phase === "outgoing" && this._clientId < from) return;
    if (this._phase !== "idle" && this._phase !== "outgoing") return;
    this._peerId = from;
    try {
      if (this._phase === "outgoing") await this._peer.setLocalDescription({ type: "rollback" });
      else await this._openPeer();
      await this._peer.setRemoteDescription({ type: "offer", sdp });
      await this._flushCandidates();
      const answer = await this._peer.createAnswer();
      await this._peer.setLocalDescription(answer);
      const answerSdp = await this._gatheredSdp();
      if (!answerSdp) throw new Error("No answer");
      this._phase = "incoming";
      this._canSendCandidates = true;
      this._busy = true;
      this._status = `Incoming from ${this._name(from)}`;
      this._send({ type: "answer", to: from, sdp: answerSdp });
      this._armTimeout();
    } catch (ignored) {
      this._endMedia(true, "Call failed");
    }
  }

  async _openPeer() {
    if (typeof RTCPeerConnection !== "function") throw new Error("WebRTC is unavailable");
    const peer = new RTCPeerConnection({ iceServers: [] });
    this._peer = peer;
    this._canSendCandidates = false;
    this._stream = await this._captureMicrophone();
    if (this._stream) {
      this._stream.getTracks().forEach((track) => peer.addTrack(track, this._stream));
    } else {
      peer.addTransceiver("audio", { direction: "recvonly" });
    }
    peer.onicecandidate = (event) => {
      if (!event.candidate || !this._peerId || !this._canSendCandidates) return;
      this._send({
        type: "candidate",
        to: this._peerId,
        candidate: event.candidate.candidate,
        sdpMid: event.candidate.sdpMid,
        sdpMLineIndex: event.candidate.sdpMLineIndex,
      });
    };
    peer.oniceconnectionstatechange = () => {
      if (peer.iceConnectionState === "connected" || peer.iceConnectionState === "completed") {
        this._phase = "live";
        this._status = `Connected to ${this._name(this._peerId)}`;
        window.clearTimeout(this._callTimer);
        this._render();
      } else if (peer.iceConnectionState === "failed") {
        this._endMedia(true, "Call failed");
        this._render();
      }
    };
  }

  _takeRoster(clients) {
    const pinned = this._config.station;
    this._stations = clients
      .filter((client) => client.clientId !== this._clientId)
      .filter((client) => !pinned || client.displayName === pinned)
      .map((client) => {
        this._names[client.clientId] = client.displayName || client.clientId;
        return client;
      });
    if (this._phase === "idle" && (this._status === "Connecting…" || this._status === "Online")) {
      this._status = "Online";
    }
  }

  _rememberCandidate(frame) {
    const init = {
      candidate: frame.candidate || "",
      sdpMid: frame.sdpMid || null,
      sdpMLineIndex: frame.sdpMLineIndex || 0,
    };
    if (!this._remoteIsSet || !this._peer) {
      this._pendingCandidates.push(init);
      return;
    }
    void this._peer.addIceCandidate(init).catch(() => undefined);
  }

  async _flushCandidates() {
    this._remoteIsSet = true;
    const queued = this._pendingCandidates;
    this._pendingCandidates = [];
    for (const init of queued) {
      await this._peer.addIceCandidate(init).catch(() => undefined);
    }
  }

  async _captureMicrophone() {
    const devices = navigator.mediaDevices;
    if (!devices || typeof devices.getUserMedia !== "function") return null;
    try {
      return await devices.getUserMedia({ audio: true, video: false });
    } catch (ignored) {
      return null;
    }
  }

  _gatheredSdp() {
    const peer = this._peer;
    if (!peer) return Promise.resolve("");
    const ready =
      peer.iceGatheringState === "complete"
        ? Promise.resolve()
        : new Promise((resolve) => {
            const finish = () => {
              window.clearTimeout(timer);
              peer.removeEventListener("icegatheringstatechange", onChange);
              resolve();
            };
            const onChange = () => {
              if (peer.iceGatheringState === "complete") finish();
            };
            const timer = window.setTimeout(finish, 1500);
            peer.addEventListener("icegatheringstatechange", onChange);
          });
    return ready.then(() => (peer.localDescription && peer.localDescription.sdp) || "");
  }

  _hangUp() {
    this._endMedia(true, "Call ended");
    this._render();
  }

  _toggleMute() {
    this._muted = !this._muted;
    (this._stream ? this._stream.getAudioTracks() : []).forEach((track) => {
      track.enabled = !this._muted;
    });
    this._render();
  }

  _endMedia(notify, status) {
    window.clearTimeout(this._callTimer);
    if (notify && this._peerId) this._send({ type: "hangup", to: this._peerId });
    if (this._peer) this._peer.close();
    (this._stream ? this._stream.getTracks() : []).forEach((track) => track.stop());
    this._peer = null;
    this._stream = null;
    this._pendingCandidates = [];
    this._remoteIsSet = false;
    this._canSendCandidates = false;
    this._peerId = null;
    this._phase = "idle";
    this._busy = false;
    if (status) this._status = status;
  }

  _armTimeout() {
    window.clearTimeout(this._callTimer);
    this._callTimer = window.setTimeout(() => {
      this._endMedia(true, "No answer");
      this._render();
    }, CALL_TIMEOUT_MS);
  }

  _name(clientId) {
    return this._names[clientId] || clientId;
  }

  _render() {
    if (!this._root) return;
    const stations = this._stations || [];
    const pinnedMissing = this._config && this._config.station && stations.length === 0;
    const rows = stations
      .map((station) => {
        const state = station.inCall ? "In Call" : "Online";
        const disabled = this._busy || station.inCall ? "disabled" : "";
        return `<article class="station"><div><strong>${escapeText(station.displayName)}</strong><small>${state}</small></div>
          <button data-call="${escapeText(station.clientId)}" ${disabled}>Call</button></article>`;
      })
      .join("");
    this._root.innerHTML = `
      <style>
        :host { display: block; }
        .card { display: flex; gap: 16px; padding: 16px; }
        .side, .station { display: flex; flex-direction: column; gap: 8px; }
        .station { flex-direction: row; align-items: center; justify-content: space-between; }
        button { min-width: 96px; }
        .empty { opacity: 0.7; }
      </style>
      <ha-card>
        <div class="card">
          <div class="side">
            <strong>INTERCOM</strong>
            <span>${escapeText(this._status || "")}</span>
            <button data-mute>${this._muted ? "Muted" : "Mute"}</button>
            <button data-hangup>Hang up</button>
          </div>
          <div class="stations">
            ${pinnedMissing ? `<p class="empty">${escapeText(this._config.station)} is offline</p>` : rows || '<p class="empty">No stations online</p>'}
          </div>
        </div>
      </ha-card>`;
    this._root.querySelector("[data-mute]")?.addEventListener("click", () => this._toggleMute());
    this._root.querySelector("[data-hangup]")?.addEventListener("click", () => this._hangUp());
    this._root.querySelectorAll("[data-call]").forEach((button) => {
      button.addEventListener("click", () => this._call(button.getAttribute("data-call")));
    });
  }
}

function escapeText(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

customElements.define("remote-screens-intercom", RemoteScreensIntercomCard);
window.customCards = window.customCards || [];
window.customCards.push({
  type: "remote-screens-intercom",
  name: "Remote Screens Intercom",
  description: "Call a Remote Screens tablet",
});
