# Remote Screens

Home Assistant wall screens on Android tablets, drawn with
[Remote Compose](https://developer.android.com/jetpack/androidx/releases/compose-remote).

A Home Assistant add-on lays out screens of tiles, draws each one as a Remote Compose document and streams it to
tablets running a small player app. Live state (on/off, brightness, temperatures, now playing) goes to the tablets
as updates to variables in the document, so a state change never sends the screen again. Taps go back to the
add-on, which calls Home Assistant; the tablets hold no Home Assistant token.

The idea follows [Tessera](https://github.com/MaxGramser/homeassistant_espscreen), which does this for ESP32
screens. No Tessera code or assets are used.

## What is in this repository

| Path | What |
| ---- | ---- |
| `server/` | The add-on: a Kotlin/Ktor server that talks to Home Assistant, renders documents and serves the editor |
| `player/` | The Android player app (Android 8 and later) |
| `shared/` | The layout model, the grid maths and the protocol, used by the server and the player |
| `editor/` | The Vue 3 editor, opened through ingress |
| `catalogue/` | Which tiles and cards each Home Assistant domain gets |
| `custom_components/remote_screens/` | The Home Assistant integration (HACS) for screen settings and services |
| `addon/` | The add-on's packaging, documentation and changelog |
| `docker/` | Running without the Supervisor (Home Assistant Container or Core) |

## Install

1. **Add-on.** In Home Assistant, Settings, Add-ons, Add-on store, add this repository
   (`https://github.com/sickkick/remote-screens`) and install **Remote Screens**.
2. **Player app.** Download it from the editor (**Download the player app**) or from the latest GitHub release.
3. **Integration** (optional, for automations). In HACS, add this repository as a custom repository of type
   *Integration*, install **Remote Screens** and restart Home Assistant. The add-on announces itself, so Home
   Assistant then offers to set it up.

How to pair a tablet and lay out a screen is in [addon/DOCS.md](addon/DOCS.md). Without the Supervisor, see
[docker/compose.yaml](docker/compose.yaml).

## Development

You need JDK 21, the Android SDK (for the player), Node 24 and Python 3.13.

```sh
./gradlew :shared:test :server:test        # server and shared tests
./gradlew :server:installDist              # the server, runnable as server/build/install/server/bin/server
./gradlew :player:assembleDebug            # the player app

cd editor && npm ci && npm test && npm run build

python -m pip install -r requirements_test.txt && python -m pytest   # the integration
```

A made-up home makes it possible to work without Home Assistant:

```sh
REMOTE_SCREENS_DEMO=1 REMOTE_SCREENS_DATA=/tmp/rs-demo \
REMOTE_SCREENS_STATIC=editor/dist \
REMOTE_SCREENS_INGRESS_PORT=18099 REMOTE_SCREENS_PLAYER_PORT=18100 \
server/build/install/server/bin/server
```

To hand out the player app from it, add `REMOTE_SCREENS_APK=<dir>` with a folder holding `remote-screens.apk` and a
`version.json` such as `{"versionCode": 100, "versionName": "0.1.0"}` (`tools/package_addon.sh` writes both).
The editor is then at <http://localhost:18099> (or run `npm run dev` in `editor/`, which proxies the API to it).
In the Android emulator, start the player with
`adb shell am start -n dev.remotescreens.player/.MainActivity --es server 10.0.2.2:18100`.

[docs/REMOTE_COMPOSE.md](docs/REMOTE_COMPOSE.md) lists what we learned about Remote Compose `1.0.0-alpha21`
and the workarounds the code relies on.

### Releases

`addon/VERSION` and the `version` in `addon/config.yaml` must match. Pushing a tag `v<version>` runs
[the release workflow](.github/workflows/addon.yml): it builds the signed player app, packages the add-on,
pushes `ghcr.io/<owner>/remote-screens-{amd64,aarch64}` and attaches the APK to the GitHub release.

Tablets only accept an update signed with the key the app was first installed with, so every release must use
the same key. The workflow reads it from these repository secrets:

| Secret | What |
| ------ | ---- |
| `PLAYER_KEYSTORE_BASE64` | The keystore, base64 encoded |
| `PLAYER_KEYSTORE_PASSWORD` | Its password |
| `PLAYER_KEY_ALIAS` | The key's alias |
| `PLAYER_KEY_PASSWORD` | The key's password |

## Third-party material

See [NOTICE](NOTICE).
