const { test } = require("node:test");
const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const vm = require("node:vm");

const source = readFileSync(require("node:path").join(__dirname, "intercom-card.js"), "utf8");

function setup(secure = true) {
  let Card;
  let stopped = false;
  let removed = false;
  const track = { enabled: true, stop() { stopped = true; } };
  const stream = { getTracks: () => [track], getAudioTracks: () => [track] };
  const received = [];
  const audio = {
    play: async () => {}, pause() {}, remove() { removed = true; },
  };
  const peer = { addTrack() {}, close() {} };
  const context = {
    HTMLElement: class { appendChild() {} },
    customElements: { define(name, constructor) { Card = constructor; } },
    window: { isSecureContext: secure, clearTimeout, clearInterval, setTimeout, setInterval },
    navigator: { mediaDevices: { getUserMedia: async () => stream } },
    document: { createElement: () => audio },
    RTCPeerConnection: function () { return peer; },
    MediaStream: function () { return { addTrack: track => received.push(track) }; },
    crypto: require("node:crypto").webcrypto,
    Uint8Array,
  };
  vm.runInNewContext(source, context);
  const card = new Card();
  card._render = () => {};
  card._pendingCandidates = [];
  return { card, audio, peer, received, context, stopped: () => stopped, removed: () => removed };
}

test("plays remote audio, retries blocked playback, and cleans up on hangup", async () => {
  const { card, audio, peer, received, stopped, removed } = setup();
  card._muted = true;
  await card._openPeer();
  assert.equal(card._stream.getAudioTracks()[0].enabled, false);
  audio.play = async () => { throw new Error("Autoplay blocked"); };
  peer.ontrack({ track: "remote" });
  await new Promise(resolve => setImmediate(resolve));
  assert.deepEqual(received, ["remote"]);
  assert.equal(card._playbackBlocked, true);
  audio.play = async () => {};
  await card._resumeAudio();
  assert.equal(card._playbackBlocked, false);
  card._endMedia(false);
  assert.equal(stopped(), true);
  assert.equal(removed(), true);
  assert.equal(audio.srcObject, null);
});

test("reports insecure and denied microphone access", async () => {
  const insecure = setup(false);
  await assert.rejects(insecure.card._openPeer(), /Microphone requires HTTPS/);
  insecure.card._endMedia(false);
  const denied = setup();
  denied.context.navigator.mediaDevices.getUserMedia = async () => {
    throw Object.assign(new Error("Denied"), { name: "NotAllowedError" });
  };
  await assert.rejects(denied.card._openPeer(), /Allow microphone access/);
  denied.card._endMedia(false);
});

test("subscribes again when the dashboard reattaches the card", async () => {
  const { card } = setup();
  let subscriptions = 0;
  let unsubscribed = 0;
  card.hass = { connection: {
    subscribeMessage: async () => { subscriptions++; return () => { unsubscribed++; }; },
    sendMessagePromise: async () => {},
  } };
  await new Promise(resolve => setImmediate(resolve));
  card.disconnectedCallback();
  assert.equal(unsubscribed, 1);
  card.connectedCallback();
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(subscriptions, 2);
  card.disconnectedCallback();
});

test("displays subscription failures", async () => {
  const { card } = setup();
  card.hass = { connection: {
    subscribeMessage: async () => { throw new Error("Add-on unavailable"); },
  } };
  await new Promise(resolve => setImmediate(resolve));
  assert.match(card._status, /Could not connect to intercom: Add-on unavailable/);
  card.disconnectedCallback();
});
