# Remote Screens

Remote Screens turns Android tablets into Home Assistant wall screens. You lay out tiles in the editor (the
**Remote Screens** panel in the sidebar); the add-on draws each screen as a Remote Compose document and sends it
to the tablets, which show it with the Remote Screens app. Taps come back to the add-on, which calls Home
Assistant. The tablets never talk to Home Assistant themselves, and need no token.

## Getting started

1. Start the add-on and open **Remote Screens** in the sidebar.
2. Make a screen with **New screen**. Pick the size of your tablet, or make it after the tablet has shown up and
   pick that.
3. Install the Remote Screens app on the tablet: **Download the player app** in the editor, or the APK from the
   GitHub release. Android asks you to allow installing apps from your browser.
4. Open the app. It finds the add-on on your network and shows a six-digit code.
5. In the editor, the tablet appears under **Tablets** with its code. Check that the codes match, choose the screen
   and press **Pair**. The tablet shows the screen at once.
6. Open the screen in the editor: add pages, drag entities from the library onto the grid, and press
   **Save & send**. **Preview on device** shows your changes on a tablet without saving them.

If the app does not find the add-on (mDNS blocked between VLANs, for example), tap **Enter address** and give the
address of your Home Assistant host with port 8100.

### A tablet on the wall

- Make Remote Screens the tablet's **home app** (Android settings, Apps, Default apps, Home app). It then opens when
  the tablet starts and after every update, and the Home button cannot leave it.
- If you would rather keep your launcher, open the app's settings (long press the top bar) and choose **Open by
  itself after a restart or update**. Android calls this permission "Display over other apps".
- Keep the tablet plugged in. The screen dims on its own after the standby time and at night.

## Tiles

Every tile follows its entity live. A tap does the obvious thing for its type (toggle a light, open a cover's
card); a long press opens the card with everything else (brightness and colour, modes, the media library,
history). The editor lets you change the tap and the long press to: toggle, open the card, run a service, go to a
page, or nothing.

Tiles that are not an entity: clock, bedside clock, page navigation, history graph, energy flow, map, and a
favourite (a playlist on a player).

A page can be hidden: it has no page dot and is only reached from a navigation tile, with a Back key.

## Themes

A screen wears a theme: RPI dark (the default), Light, OLED black, High contrast, or one of your own. In the
screen's settings, pick its **Theme** and its **Dark theme**; the dark theme is worn in dark mode and, with
**Dark at night**, during the night hours. The editor's Day / Dark switch previews both.

To make your own, open **Themes** on the editor's home page, duplicate any theme and change its colours. Each
colour you leave alone follows the theme it was made from. Saving sends the change to every tablet wearing it;
deleting it puts those screens back on RPI dark.

## Languages

The screens follow Home Assistant's language, or the one set in the editor. The words for states ("Open", "Heat",
"Armed away") come from Home Assistant itself, so they match its own dashboards in every language. The add-on's
own texts are in English, Dutch, German, French, Spanish, Italian, Polish and Portuguese, and fall back to
English in other languages.

## Settings from Home Assistant

The **Remote Screens** integration (installed with HACS) adds, for each screen, entities for brightness, dark
mode, standby, night mode, swipe, back-to-home and the page shown, and the services
`remote_screens.show_alert`, `remote_screens.open_page`, `remote_screens.wake` and `remote_screens.close`. The
add-on announces itself, so Home Assistant offers to set the integration up.

Alerts can also come from an event, without the integration. In a script or automation:

```yaml
- event: remote_screens_alert
  event_data:
    title: Someone is at the door
    level: warning
    camera: camera.front_door
    timeout: 60
    buttons:
      - title: Open
        service: lock.open
        data: {entity_id: lock.front_door}
      - title: Ignore
```

A button fires `remote_screens_alert_action` with its `action` (its title when it has none), so an automation can
answer it. The events `remote_screens_open_page` (with `page`), `remote_screens_wake` and `remote_screens_close`
work the same way.

## Updating the tablets

When the add-on has a newer player app than a tablet, the tablet offers to install it. Android asks once for
permission to install apps from Remote Screens, then asks you to confirm each update.

## Network

| Port | What |
| ---- | ---- |
| 8100 | Tablets connect here (WebSocket), and the integration's API |
| (free port) | The editor, through ingress only; the Supervisor picks a free port so it never clashes |

The add-on uses the host network so tablets can find it over mDNS (`_remotescreens._tcp`).

## Data

Layouts and paired tablets are kept in `/data/layouts.json` and included in Home Assistant backups. A tablet's
token is stored as a hash only. To remove a tablet, use **Forget** in the editor; it must then be paired again.

## Options

| Option | What |
| ------ | ---- |
| `map_tiles` | Where map tiles come from, as a URL with `{z}`, `{x}` and `{y}`. Empty is OpenStreetMap, whose tile servers ask for light use: the add-on caches tiles and only fetches what a map shows. With many tablets, or for another map style, use a tile provider you have a key for. |
