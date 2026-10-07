#!/usr/bin/env python3
"""A pretend tablet, to try the add-on without one.

    tools/fake_player.py ws://127.0.0.1:8100/player [--adopt http://127.0.0.1:8099]

Says hello, prints what it is sent, and with --adopt pairs itself to the first screen through the
editor API, then taps the first tile it finds and waits for the values to change.
"""
import argparse
import asyncio
import base64
import json
import urllib.request

import websockets


def api(base: str, path: str, body=None):
    req = urllib.request.Request(base + path, method="POST" if body is not None else "GET",
                                 data=json.dumps(body).encode() if body is not None else None,
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req) as r:
        raw = r.read()
        return json.loads(raw) if raw else None


async def main(url: str, adopt: str | None, seconds: float) -> None:
    async with websockets.connect(url, max_size=None) as ws:
        await ws.send(json.dumps({"type": "hello", "deviceId": "fake-1", "deviceName": "Fake tablet",
                                  "widthDp": 1280, "heightDp": 800, "density": 1.5, "appVersion": 1}))
        docs = {}
        tapped = False
        end = asyncio.get_event_loop().time() + seconds
        while asyncio.get_event_loop().time() < end:
            try:
                msg = json.loads(await asyncio.wait_for(ws.recv(), 1))
            except asyncio.TimeoutError:
                continue
            t = msg["type"]
            if t == "welcome":
                print("welcome", {k: v for k, v in msg.items() if k != "type"})
                if msg.get("pairingCode") and adopt:
                    screens = api(adopt, "/api/layout")["screens"]
                    api(adopt, "/api/pair", {"code": msg["pairingCode"], "screenId": screens[0]["id"]})
            elif t == "document":
                size = len(base64.b64decode(msg["data"]))
                docs[msg["slot"]] = msg
                print(f"document {msg['slot']} rev {msg['rev']}: {size} bytes, {len(msg.get('vars', []))} values, "
                      f"{len(msg.get('predictions', {}))} predictions, {msg.get('pageCount')} pages")
                if msg["slot"] == "main" and not tapped and msg.get("predictions"):
                    meta = next(iter(msg["predictions"]))
                    print("tap", meta, "->", msg["predictions"][meta])
                    await ws.send(json.dumps({"type": "action", "slot": "main", "rev": msg["rev"], "metadata": meta}))
                    tapped = True
            elif t == "vars":
                print("vars", [(v["name"], v["value"]) for v in msg["vars"]][:8])
            elif t == "bitmap":
                print("bitmap", msg["name"], len(base64.b64decode(msg["data"])), "bytes")
            else:
                print(t, {k: v for k, v in msg.items() if k != "type"})


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("url")
    p.add_argument("--adopt")
    p.add_argument("--seconds", type=float, default=8)
    a = p.parse_args()
    asyncio.run(main(a.url, a.adopt, a.seconds))
