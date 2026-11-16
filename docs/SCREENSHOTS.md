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
| `editor-lights.png` | Editor | Lights page |
| `editor-cameras.png` | Editor | Cameras page |
| `editor-blinds.png` | Editor | Blinds page |
| `editor-security.png` | Editor | Security page |
| `editor-vacuum.png` | Editor | Vacuum page |
| `editor-energy-dashboard.png` | Editor | Energy dashboard page |
| `editor-enroll.png` | Editor | Enroll a tablet (needs `REMOTE_SCREENS_APK`) |
| `themes.png` | Editor | Themes page |
| `player-pairing.png` | Player | Six-digit pairing code |
| `player-home.png` | Player | Home page on device |
| `player-living.png` | Player | Living room grid |
| `player-extras.png` | Player | Extras page |
| `player-compose.png` | Player | Free-form Compose page |
| `player-thermostat.png` | Player | Thermostat page |
| `player-lights.png` | Player | Lights page |
| `player-cameras.png` | Player | Cameras page |
| `player-blinds.png` | Player | Blinds page |
| `player-security.png` | Player | Security page |
| `player-vacuum.png` | Player | Vacuum page |
| `player-energy-dashboard.png` | Player | Energy dashboard page |
| `player-card.png` | Player | Detail card open |

Do not commit concept mockups; capture live editor and player shots instead.
Local design mockups may live under `docs/mockups/` (gitignored).

## Retake editor shots

1. Start the [demo server](DEMO.md) (include `REMOTE_SCREENS_APK` if you want `editor-enroll.png`).
   Wipe `REMOTE_SCREENS_DATA` when the demo layout changes so the new pages load.
2. Install Playwright once: `npm i --no-save playwright && npx playwright install chromium`
3. Run:

```sh
node tools/capture_screenshots.mjs http://localhost:18099
```

## Retake player shots (prefer a real tablet)

A full demo document (many pages including free-form Compose) can trip Remote Compose
`ConcurrentModificationException` during paint — the player shows a red triangle while
accessibility still has the content. Prefer a physical tablet (e.g. BLU M8L) and capture
**one panel page at a time**.

1. Start the demo server as above.
2. Point the tablet at the demo (`adb reverse tcp:18100 tcp:18100` and server `127.0.0.1:18100`).
3. Pair to the demo screen.
4. For each panel:

```sh
python3 tools/docs_demo_layout.py one lights   # or cameras, blinds, security, vacuum, energy_dashboard
# open that page on the tablet, then:
adb exec-out screencap -p > docs/screenshots/player-lights.png
```

Or run `./tools/capture_player_screenshots.sh` after `python3 tools/docs_demo_layout.py one …`
when the nav only has Home + that panel (avoids the paint crash).

`python3 tools/docs_demo_layout.py panels` is fine for **editor** Playwright shots (Vue mockups);
do not leave that many pages on a paired player.
