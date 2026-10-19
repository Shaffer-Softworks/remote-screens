# Demo home

Remote Screens can run without Home Assistant. Set `REMOTE_SCREENS_DEMO=1` and the add-on (or a local
`server` build) invents a small home, entities and a screen named **Living room tablet**. That is what the
editor screenshots and most development workflows use.

## Start it

You need a built server (`./gradlew :server:installDist`) and a built editor (`cd editor && npm ci && npm run build`).

```sh
REMOTE_SCREENS_DEMO=1 REMOTE_SCREENS_DATA=/tmp/rs-demo \
REMOTE_SCREENS_STATIC=editor/dist \
REMOTE_SCREENS_INGRESS_PORT=18099 REMOTE_SCREENS_PLAYER_PORT=18100 \
server/build/install/server/bin/server
```

| Variable | Purpose |
| -------- | ------- |
| `REMOTE_SCREENS_DEMO=1` | Use the made-up home instead of a Home Assistant token |
| `REMOTE_SCREENS_DATA` | Where `layouts.json` is stored (wipe the dir to reset) |
| `REMOTE_SCREENS_STATIC` | Editor UI files (`editor/dist`) |
| `REMOTE_SCREENS_INGRESS_PORT` | Editor HTTP port (default in Supervisor is chosen for you) |
| `REMOTE_SCREENS_PLAYER_PORT` | WebSocket port for tablets (Supervisor uses **8100**) |
| `REMOTE_SCREENS_APK` | Optional folder with `remote-screens.apk` + `version.json` so the editor can download / enroll |

Editor: <http://localhost:18099>  
Players: `host:18100` (Android emulator usually `10.0.2.2:18100`; if that route is missing after a wipe, use
`adb reverse tcp:18100 tcp:18100` and point the player at `127.0.0.1:18100`).

## What the demo screen has

The fixture is [`DemoLayout`](../server/src/main/kotlin/tech/shaffersoftworks/remotescreens/server/data/homeassistant/demo/DemoLayout.kt)
(`id` = `demo`):

| Page | Kind |
| ---- | ---- |
| Home | Full home page (weather, next event, presence) |
| Living room | Grid of lights, climate, media, lock, scenes |
| Rooms | Covers, fan, graphs, … |
| More | Extra grid tiles |
| Extras | Newer domains (calendar, todo, timer, humidifier, …) |
| Compose | Free-form page (dial, countdown, QR, tabs, media bar, …) |
| Weather | Weather-focused page |
| Thermostat | Full climate / RPI-style thermostat page |

Themes and the entity library come from the same demo home.

## Pair a tablet or emulator

1. Install the debug player: `./gradlew :player:installDebug` (or `adb install` the APK).
2. Start it with the demo address, e.g.  
   `adb shell am start -n tech.shaffersoftworks.remotescreens.player/.MainActivity --es server 10.0.2.2:18100`
3. In the editor under **Tablets**, match the six-digit code and **Pair** to **Living room tablet**.

See [SCREENSHOTS.md](SCREENSHOTS.md) to retake documentation images from this setup.
