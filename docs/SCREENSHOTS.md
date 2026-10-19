# Screenshots

Product images live in [`docs/screenshots/`](screenshots/). The README and the
[GitHub Wiki](https://github.com/Shaffer-Softworks/remote-screens/wiki) embed them.

## Inventory

| File | Source | Shows |
| ---- | ------ | ----- |
| `screens.png` | Editor | Screens + tablets home |
| `editor.png` | Editor | Living room tablet home page |
| `editor-room.png` | Editor | Living room grid page |
| `editor-tile.png` | Editor | Tile inspector |
| `editor-extras.png` | Editor | Extras domain tiles |
| `editor-compose.png` | Editor | Free-form Compose page |
| `editor-thermostat.png` | Editor | Thermostat page |
| `editor-card.png` | Editor | Tile / card inspector on Extras |
| `editor-enroll.png` | Editor | Enroll a tablet (needs `REMOTE_SCREENS_APK`) |
| `themes.png` | Editor | Themes page |
| `player-pairing.png` | Player | Six-digit pairing code |
| `player-home.png` | Player | Home page on device |
| `player-living.png` | Player | Living room grid |
| `player-extras.png` | Player | Extras page |
| `player-compose.png` | Player | Free-form Compose page |
| `player-thermostat.png` | Player | Thermostat page |
| `player-card.png` | Player | Detail card open |

Do not commit concept mockups; capture live editor and player shots instead.

## Retake editor shots

1. Start the [demo server](DEMO.md) (include `REMOTE_SCREENS_APK` if you want `editor-enroll.png`).
2. Install Playwright once: `npm i --no-save playwright && npx playwright install chromium`
3. Run:

```sh
node tools/capture_screenshots.mjs http://localhost:18099
```

## Retake player shots (Pixel Tablet)

1. Start the demo server as above.
2. Boot the **Pixel_Tablet** AVD (Android Studio Device Manager is fine).
3. Install the debug app: `./gradlew :player:installDebug`
4. If `adb shell ping -c 1 10.0.2.2` fails (common after a wipe):

```sh
adb reverse tcp:18100 tcp:18100
SERIAL=emulator-5554 PLAYER_HOST=127.0.0.1 ./tools/capture_player_screenshots.sh
```

Otherwise:

```sh
SERIAL=emulator-5554 PLAYER_HOST=10.0.2.2 ./tools/capture_player_screenshots.sh
```

The script pairs to the demo screen, dismisses Android’s full-screen tip when present, and taps the
bottom nav (coordinates tuned for the 2560×1600 Pixel Tablet skin).
