# Tessera call hook for Remote Screens

Remote Screens can place and take a Tessera call only when Tessera Screen Manager treats a Remote Screens station as a screen. This is the hook to add in `screen_manager/app/server.py` (`call_screens` and `call_send`) for Tessera 0.4.99 / firmware 0.62.0. Remote Screens does not copy Tessera's call code. It speaks these events, and the PCM links Tessera already gives a screen.

Until this hook answers, the Remote Screens add-on publishes peers and then stays off the intercom roster.

## Peers

Remote Screens fires `remote_screens_intercom_peers`. The event replaces the whole external set. Each peer is one station on the Remote Screens intercom (a tablet or a dashboard card), never a Tessera screen.

```json
{
  "peers": [
    {"node": "station-TABLET", "name": "Kitchen tablet", "online": true, "hears": true, "speaks": true}
  ]
}
```

`call_screens()` merges these in beside real screens. `node` is the id used as `screen` on `esphome.screen_call`. It has no `_`, because that event rewrites `_` to `-` before the lookup. `hears` and `speaks` are true. `films` and `shows` stay false, so Tessera offers the peer sound only. `online` false, or a peer left out of the next event, drops it.

## Messages for a peer

`esphome.screen_call` stays the way a screen dials, answers, declines, and ends. `calls.py` still rings, misses after 45 seconds, and refuses a busy or Do not disturb screen.

When `call_send()` would `send_auxiliary` a `call` or `contacts` message to one of these peers, it fires `esp_screens_call_peer` instead. The payload is that message plus `node` (the peer it is for) and `peer` (the other end's node, the same value `Say.peer` already has). `l` and `tk` are included whenever the live message has them.

A ring of tablet `station-TABLET` from screen `kitchen`:

```json
{"node": "station-TABLET", "peer": "kitchen", "op": "call", "k": "ab12cd34", "s": "ring", "n": "Kitchen", "vd": 0}
```

Live, after the tablet answers:

```json
{
  "node": "station-TABLET",
  "peer": "kitchen",
  "op": "call",
  "k": "ab12cd34",
  "s": "live",
  "n": "Kitchen",
  "vd": 0,
  "l": "http://HOST/camera/TOKEN.mjpeg",
  "tk": "http://HOST/camera/TOKEN/talk"
}
```

`s` `end` keeps `w` (`hung_up`, `busy`, `declined`, `quiet`, `gone`, `missed`, `no_answer`). Contacts use the same `op`, `r`, `p`, and `more` as a screen's list, with `node` set to the peer that asked.

## What Remote Screens does with the links

On `live` it reads `audio/x-s16le` parts from `l` and POSTs 16 kHz 16-bit mono PCM, in 20 ms parts, to `tk`. That is the voice path `talk_bridge.Voice` already bridges between two screens. There is no video in this pass.
