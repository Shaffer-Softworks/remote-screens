# Remote Screens

Remote Screens turns Android tablets into Home Assistant wall screens. You lay out tiles in the editor (the
**Remote Screens** panel in the sidebar); the add-on draws each screen as a Remote Compose document and sends it
to the tablets, which show it with the Remote Screens app. Taps come back to the add-on, which calls Home
Assistant. The tablets never talk to Home Assistant themselves, and need no token.

The full guide (editor, free-form components, architecture, troubleshooting) is on the
[GitHub Wiki](https://github.com/Shaffer-Softworks/remote-screens/wiki).

## Getting started

1. Start the add-on and open **Remote Screens** in the sidebar.
2. Make a screen with **New screen**. Pick the size of your tablet, or make it after the tablet has shown up and
   pick that.
3. Install the Remote Screens app on the tablet: **Download app** in the editor, or the APK from the
   GitHub release. Android asks you to allow installing apps from your browser.
4. Open the app. It finds the add-on on your network and shows a six-digit code.
5. In the editor, the tablet appears under **Tablets** with its code. Check that the codes match, choose the screen
   and press **Pair**. The tablet shows the screen at once.
6. Open the screen in the editor: add pages, drag entities from the library onto the grid, and press
   **Save & send**. **Preview on device** shows your changes on a tablet without saving them.

**Export** on a screen downloads that screen as a file. **Import** adds the file as a new screen, and
**Duplicate** makes the same copy without a file. Tablets stay on the original screen.

If the app does not find the add-on (mDNS blocked between VLANs, for example), tap **Enter address** and give the
address of your Home Assistant host with port 8100.

### A tablet on the wall

- Make Remote Screens the tablet's **home app** (Android settings, Apps, Default apps, Home app). It then opens when
  the tablet starts and after every update, and the Home button cannot leave it.
- If you would rather keep your launcher, open the app's settings (long press the top bar) and choose **Open by
  itself after a restart or update**. Android calls this permission "Display over other apps".
- Keep the tablet plugged in. The screen dims on its own after the standby time and at night.
- To turn the screen fully off in standby instead of dimming it, tick **Turn the screen off in standby** for the
  screen, then on the tablet open the app's settings and choose **Allow turning the screen off in standby** (Android
  asks to activate Remote Screens as a device admin). A screen that is off does not wake on a touch: press the power
  button, or call `remote_screens.wake` from Home Assistant.

## Waking from standby

Besides a touch (when the panel is only dimmed) or the power button, a screen can wake from:

1. **Home Assistant** — call `remote_screens.wake` (or fire `remote_screens_wake`). This is the reliable way to wake
   when someone enters the room: use a PIR, mmWave, ultrasonic, or occupancy `binary_sensor` in an automation.
2. **Proximity** (optional) — tick **Wake on proximity** for the screen. Uses the tablet's proximity sensor when it
   has one. On many tablets that is only a few centimetres, so it is not a substitute for a room sensor. Ignored if
   there is no sensor. Can keep **Turn the screen off in standby**.
3. **Face** (optional) — tick **Wake on face**. Uses the front camera and on-device face detection while the screen is
   asleep. The tablet asks for camera permission; Android shows its privacy indicator while the camera runs. Needs a
   front camera and is skipped on low-RAM (Android Go) tablets. When face wake is active, standby keeps a black screen
   instead of turning the panel fully off (so the camera can keep running). Proximity and face can both be on at once;
   each wakes the screen on its own.

Example: wake screens when a hallway motion sensor trips.

```yaml
automation:
  - alias: Wake screens on motion
    triggers:
      - trigger: state
        entity_id: binary_sensor.hallway_motion
        to: "on"
    actions:
      - action: remote_screens.wake
      # Or pick devices under screens, or fire event: remote_screens_wake
```

## Kiosk lock

With **Kiosk lock** on, a screen's tablet stays on Remote Screens: Home, Recents, the notifications and the power
menu cannot leave it. Turn it on with the screen's **Kiosk lock** switch in Home Assistant, or **Lock the tablet to
this screen** in the editor. The lock is strongest on a tablet enrolled as a kiosk (Android calls this the *device
owner*); any other tablet gets Android's screen pinning instead.

### Enroll with a QR code

No computer is needed. The tablet must be factory reset (or new), with no Google account added.

1. In the editor, under **Tablets**, choose **Enroll a tablet**. Optionally give the Wi-Fi the tablet should join,
   then **Show QR code**. The Wi-Fi password only goes into the code; the add-on does not keep it.
2. On the tablet's first Welcome screen, tap the same spot six times. A QR scanner opens (some tablets ask for Wi-Fi
   first).
3. Scan the code. The tablet joins Wi-Fi, downloads the player app from the add-on and sets itself up as a kiosk.
4. Pair it with a screen as usual.

The code works for an hour, for as many tablets as you like. An enrolled tablet starts locked and stays locked until
it is paired; from then on it follows its screen's **Kiosk lock**, so turn that on (before pairing, to keep it
locked throughout).

### Enroll with adb

For tablets without the QR scanner (some AOSP and Fire tablets) or ones already set up, connect the tablet with USB
debugging on and run, from this repository:

```sh
./scripts/enroll-device-owner.sh --fresh remote-screens.apk
```

`--fresh` reinstalls the app, which clears its data, so pair the tablet again afterwards. Android only accepts this
on a tablet with no accounts (Settings, Accounts), so remove them or factory reset first. Run the script with
`--help` for the other options.

### Without enrolling

A tablet that is not enrolled pins the app instead. Android asks to confirm the first time. Pinning keeps the
navigation bar, and holding Back and Recents unpins the app; turn on Android's **Ask for PIN before unpinning**
(Settings, Security, App pinning) so only you can. The app pins itself again when it comes back to the front.

### Unlocking on the tablet

Give the screen a **Kiosk PIN** (4 to 8 digits) in Home Assistant or the editor. Long pressing the top bar then
asks for the PIN, and the settings offer **Unlock tablet**; **Lock again** undoes it. The tablet stays unlocked,
even after a restart, until it is locked again there or the screen's Kiosk lock is turned off and on. Without a PIN,
the tablet can only be unlocked from Home Assistant or the editor.

### Stop managing a tablet

To give an enrolled tablet back its normal use, unlock it (turn Kiosk lock off, or use the PIN), open the settings
and choose **Stop managing this tablet**. The app can then be uninstalled like any other. A factory reset does the
same.

## Top bar

Per screen, the top bar can show the clock, date, outdoor temperature, people home count, Wi‑Fi signal and
battery level. Turn each on in the editor under the screen’s settings.

## Navigation

Each screen has a button per visible page, then Settings. They can be in the bottom bar, a navigation drawer, or
both. Choose under the screen’s settings, **Navigation drawer**:

- **Menu that slides over the page**: a menu key in the top bar opens a panel from the left or right. It takes no
  room from the grid. Choosing a page, tapping outside the panel or pressing Back closes it. Without the top bar,
  the key sits in the page’s top corner.

With a drawer on, **Show the bottom bar** can be turned off to give the grid its height. Without a drawer, the
bottom bar always shows, as it is the only way to the other pages and Settings.

## Tiles

Every tile follows its entity live. A tap does the obvious thing for its type (toggle a light, open a cover's
card); a long press opens the card with everything else (brightness and colour, modes, the media library,
history). The editor lets you change the tap and the long press to: toggle, open the card, run a service, go to a
page, open a URL on the tablet, fire a Home Assistant event, or nothing.

Tiles that are not an entity: clock, bedside clock, page navigation, history graph, energy flow, map, and a
favourite (a playlist on a player). Dedicated domain tiles cover the usual Home Assistant domains (lights,
covers, climate, media, cameras, calendars, timers, humidifiers, and more); entities without a dedicated tile
still appear in the library as a generic on/off tile (`homeassistant.toggle`).

The **map card** (person, device tracker, or the map tile's detail) is an interactive OpenStreetMap on current
players: pinch to zoom and drag to pan. Older players still get a still image drawn by the add-on. Grid map tiles
stay as still images.

### Full-page panels

**Other tiles** in the library includes pages that fill the tablet on their own. Drop one onto an empty page.
The editor refuses it on a page that already has tiles ("A full-page tile needs a page of its own").

Panels that follow an entity — alarm, weather, thermostat, music, and calendar — use the first entity of that
domain. They stay unavailable until Home Assistant has one, and the tile's settings change which entity they
follow. Home, lights, cameras, intercom, and the bedside clock do not need an entity.

- **Home** shows the first weather entity, the next event across every calendar, and who is home.
- **Lights** lists Home Assistant areas. A light, switch, or cover uses its own area, or its device's area when
  it has none. Choose a room, then a row, to switch it, set brightness, or tilt a cover. A light with more
  controls opens its detail from the row. The count under a room is lights that are on; switches and covers
  are not included.
- **Cameras** lists only the cameras you add under **Cameras** in the tile's settings, with an optional label
  each. The right side stays blank until a row is tapped, then plays that camera's live stream. The first
  picture can take several seconds while Home Assistant starts the stream; if the stream fails, the page shows
  a still. Leaving the page stops the video. A camera card or an alert uses the same decoder, so the page
  pauses and resumes when that closes. An older player app stays on the spinner: install the current app.
- **Alarm panel** puts status and zones on the left and a keypad on the right. The tile's own panel is **All
  Zones**; **Zones** in the tile's settings adds other alarm panels, such as Alarmo areas, and arms the one you
  pick. Arming and disarming need a four-digit PIN. An empty or shorter PIN is refused. When Alarmo reports
  open sensors, arming is refused and disarming still works. Switching zone clears the PIN.
- **Weather** is the current conditions and forecast for its weather entity. When the page is taller than it
  is wide, that same content stacks vertically and the forecast becomes a scrolling list of days.
- **Thermostat** adjusts its climate entity. Heat/Cool, and Auto when the entity has a low and a high, uses
  two setpoints. Heat, Cool, and Off use one. Temperature moves in steps with **+** and **−**. Mode and fan
  are buttons on the page.
- **Music** is the Music Assistant page for the media player on the tile: now playing, browse, search, and
  queue. Pick a Music Assistant player. While a track plays, the progress bar advances from the last position
  Home Assistant reported; pausing leaves it there. A new position arrives when playback starts, pauses, seeks,
  or the track changes.
- **Calendar** lists upcoming events. On a current player, an add button creates an event when that calendar
  allows it, and holding a row deletes the event when it can be deleted.

The intercom is one of these pages too. See [Intercom](#intercom).

If the library groups every entity under **No area**, refresh the editor. Areas are loaded once, and a load
while Home Assistant's area list is unavailable (including just after the add-on restarts) comes back empty.

Besides the grid, a page can be **free-form**: convert it in the page settings, then drag components from the
palette (columns, rows, dials, tabs, markdown, QR codes, thermostat, weather, and the rest). Bound components
can use `showWhen` and `animate`. The wiki’s **Free-form components** page lists every type and its props. The
demo screen includes a Compose page that shows several of them.

A page can be hidden: it has no page dot and is only reached from a navigation tile, with a Back key.

A page can have a JPEG or PNG behind its tiles. Exporting or duplicating a screen embeds those images in the screen file and restores them on import.

## Themes

A screen wears a theme: RPI dark (the default), Light, OLED black, High contrast, or one of your own. In the
screen's settings, pick its **Theme** and its **Dark theme**; the dark theme is worn in dark mode and, with
**Dark at night**, during the night hours. The editor's Day / Dark switch previews both.

To make your own, open **Themes** on the editor's home page, duplicate any theme and change its colours. Each
colour you leave alone follows the theme it was made from. The Themes page groups roles (chrome, surfaces, ink,
accents, keys, and so on) and says where each colour lands on the tablet; the preview above the list shows the
main bands. Saving sends the change to every tablet wearing it; deleting it puts those screens back on RPI dark.

## Languages

The screens follow Home Assistant's language, or the one set in the editor. The words for states ("Open", "Heat",
"Armed away") come from Home Assistant itself, so they match its own dashboards in every language. The add-on's
own texts are in English, Dutch, German, French, Spanish, Italian, Polish and Portuguese, and fall back to
English in other languages.

## Settings from Home Assistant

The **Remote Screens** integration (installed with HACS) adds, for each screen, entities for brightness, dark
mode, standby, night mode, back-to-home, the page shown, kiosk lock, and wake-on-proximity /
wake-on-face, and the services `remote_screens.show_alert`, `remote_screens.open_page`, `remote_screens.wake`,
`remote_screens.close`, and `remote_screens.send_notification`. The add-on announces itself, so Home Assistant
offers to set the integration up.

### Device notifications

Use **Tablets → Send notification**, a tablet's menu, or `remote_screens.send_notification` to send a silent
banner or modal alert. Select individual tablets, screens (all tablets showing them), or explicitly all paired
tablets. Offline paired tablets are queued in the add-on and flushed when they reconnect (within the
notification duration, or up to one hour for until-acknowledged alerts). The queue is in memory only.
**Wake screen** is off by default.

Update the add-on, player APK, and Home Assistant integration for full support. Restart Home Assistant after
updating the integration. Delivery results (`sent`, `queued`, `unsupported`, `failed`) describe dispatch to
the tablet connection, not whether someone read the message. The [notification guide](https://github.com/Shaffer-Softworks/remote-screens/wiki/Device-notifications)
has automation examples, persistent alerts, action buttons, and compatibility details.

### Tablet sensors

Each tablet is also a device of its own, shown under the screen it plays. Entities appear once the tablet reports
them, and only for the hardware it has:

- **Sensors:** illuminance, pressure, ambient temperature, humidity, orientation and last motion.
- **Binary sensors:** online, charging, awake, moving (held on for a minute after the tablet is moved or knocked)
  and proximity.
- **Diagnostics:** battery level, temperature and health, Wi‑Fi network and signal, storage free, memory
  available, Android and app version, kiosk mode and the time of the last report. Battery power and voltage,
  charger, battery cycles, BSSID, IP address, link speed, frequency, uptime and security patch are there too,
  disabled until you enable them.
- **Location:** a `device_tracker` for each tablet, only when **Share tablet location** is on.

Two switches per screen control this: **Report tablet sensors** (on by default) and **Share tablet location**
(off by default). The editor has the same checkboxes under **Tablet sensors**. Tablets report every minute, and at
once when the light changes a lot, something comes near, the tablet moves, or its location changes. Home Assistant
polls every 30 seconds.

Location needs Android's location permission. The tablet asks for it when sharing is turned on, or from its
settings (long press the top bar). Without it, the Wi‑Fi network name is hidden too, as Android only gives it
to apps with location permission. A managed tablet also turns location services on, then asks once to turn on
Location accuracy, which is what finds it indoors. A new position is reported at most every 5 minutes, after
moving 50 m.
Tablets need a player app with tablet sensors; older players only report battery, charging and kiosk mode.

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
permission to install apps from Remote Screens, then asks you to confirm each update. To check manually,
long press the top bar to open settings and choose **Check for updates**. Tablets accept only APKs signed
with the same key as the installed app.

The player app's ID changed from `dev.remotescreens.player` to `tech.shaffersoftworks.remotescreens.player`, so a
tablet running the old app cannot update to it. Uninstall the old app, install the new one and pair the tablet again.
A tablet enrolled as a kiosk has to be factory reset and enrolled again.

## Sentry crash reporting

Open **Sentry crash reporting** on the editor's main page. Enter your Sentry Android project's public
DSN (Project Settings → Client Keys), choose an environment, enable reporting, then save. This applies
to all paired tablets running a player build with Sentry support; older players ignore the setting.
The public DSN is not an auth token. Upload credentials belong only in **Readable crash reports** below.

To check delivery, choose a connected tablet under **Verify a tablet**, then **Send test event**.
This explicitly sends one nonfatal connectivity event with app/device metadata to that tablet's
saved DSN. **Accepted** means Sentry's ingestion endpoint acknowledged the event; use the returned
event ID to find it in Sentry after indexing. A timeout does not prove failure: the report may be
queued. Offline, disabled, unsupported, dropped, and failed results are shown separately.
Both the server and tablet need a version that supports the test action.

Connected tablets apply changes immediately. Offline tablets receive them on reconnect. The player
caches the configuration, so reporting starts again after an app restart even without the add-on.
Disabling closes Sentry when the tablet receives the update; unpairing also disables it.

Reporting covers uncaught JVM exceptions, native crashes, and optional ANRs, including release builds.
Sentry queues reports while offline. Reports may contain exception messages, stack traces, app version,
and device/OS details. Default PII collection, breadcrumbs, screenshots, view hierarchy attachments,
session replay, session tracking, and performance tracing are disabled. A full device freeze, abrupt
power loss, or an early crash before SDK initialization may not produce a report.

On Android 11 and newer, the player also reports why its previous run ended when it starts again.
Low-memory kills, ANRs, JVM crashes, and native crashes become Sentry warning events tagged
`diagnostic:previous-process-exit`, with the exit time, reason, and memory use. The event's app
version is the build that reported it, which may be newer than the one that exited, and the first
start after updating can include older exits Android still remembers. Later starts skip exits
already reported.
The same diagnostics go to Logcat under the tag `ProcessExitDiagnostics` and to the add-on log,
even when Sentry is disabled. Only exits Android recorded are covered, and only after the app
restarts; a freeze while the app keeps running is not detected.

Player warnings and errors also appear in **Settings → Apps → Remote Screens → Log** in Home
Assistant (called Add-ons on older HA versions), prefixed with the tablet ID and original timestamp.
This works in release builds and without Sentry, once the tablet is paired, and needs a player and
add-on that both support it. While the add-on is unreachable, the tablet keeps up to 1,000 entries,
including across restarts, and sends them when it reconnects; when that fills, the oldest are dropped,
except process-exit diagnostics, which are kept.
An entry can occasionally appear twice after the add-on restarts, and the last few can be lost if the
app is killed abruptly. Messages and stack traces are cut at 4,000 characters and shown on one line.

Release builds use R8. Under **Readable crash reports**, enter your Sentry Cloud organization slug
and a Sentry organization upload token with the `org:ci` scope for the project in your saved DSN. Choose **Save and
upload mappings**. The server stores the token separately from layouts in a file readable only by
its owner; it never returns the token to the editor, sends it to tablets, or includes it in exports.
Add-on backups include this credential file, so protect your backups. **Remove saved token** stops
future uploads without disabling tablet reporting or deleting files already uploaded to Sentry.

The matching R8 mapping is bundled with each new release APK. Sentry's build plugin embeds its
mapping identifier in that APK. The add-on verifies the APK checksum, retains the mapping in its
persistent data, and uploads it when reporting is enabled or a new bundled APK appears (within
30 seconds). Status lists retained builds; **Retry upload** retries failures. Already uploaded
mappings are remembered per organization and project. Save settings before retrying after a DSN
change. Uploads currently support Sentry Cloud only; self-hosted DSNs still receive crash events.

This applies to APKs built with mapping support. Earlier APKs need an update; rebuilding an old
version does not recover its exact mapping. New events include the mapping identifier required for
deobfuscation. Native-symbol uploads are not included. For local manual retracing, keep the exact
build's `player/build/outputs/mapping/release/mapping.txt`.
This integration reports the Android player, not the add-on server or browser editor.

## Managing a tablet with ADB

**Tablets → ADB** in the editor can pair a tablet over the network, restart the player, install the packaged
player app, take a screenshot, and download recent logs. The add-on image includes Android's platform tools.
The browser only talks to the add-on, so the add-on must be able to reach the tablet on the local network.
This works through Home Assistant ingress. These actions are not on the player port or the integration port,
and there is no free-form shell command.

On Android 11 or newer:

1. On the tablet, turn on Developer options and **Wireless debugging**.
2. Choose **Pair device with pairing code**.
3. In the editor, enter that pairing address (`host:port`) and the six-digit code, then **Pair ADB**.
4. Enter the separate debugging address from the main Wireless debugging screen, then **Connect**. Pairing
   and debugging use different ports. The debugging port can change after wireless debugging or the tablet
   restarts; connect again with the new address.
5. **Refresh** to see the device. **ADB device for** a paired tablet remembers which serial belongs to it.
   That label does not change where an action goes: each button uses the serial shown on the device.

**Install player APK** uses the same app as **Download app**. The tablet accepts it only when it is
signed with the same key as the installed app. A failed install leaves the current app in place. **Restart
player** stops and starts Remote Screens on that tablet. **Screenshot** downloads a PNG, and **Download logs**
downloads the latest 500 logcat lines, which include messages from other apps. **Disconnect** closes the network
connection. To revoke this add-on's access, use **Forget** in the tablet's Wireless debugging paired-computer
list. Android debugging has to stay on for any of this to work.

An older tablet usually needs a USB connection and TCP debugging turned on from a computer first. The editor
does not enable debugging or skip the tablet's own authorization. A USB device appears here only when the
add-on can see it. Each command stops after two minutes.

## Network

| Port | What |
| ---- | ---- |
| 8100 | Tablets connect here (WebSocket), and the integration's API |
| 8766 | Intercom signaling for Remote Screens tablets. The standalone signaling add-on keeps 8765 |
| (free port) | The editor, through ingress only; the Supervisor picks a free port so it never clashes |

The add-on uses the host network so tablets can find it over mDNS (`_remotescreens._tcp`).

## Intercom

Add a full-page **Intercom** tile from **Other tiles**. Tablets join the station list while showing that
page and answer incoming calls automatically. Allow microphone access when prompted. **Mute** disables
your microphone; **Hang up** ends the call.

From the editor, open **Tablets → Intercom**, choose a station and press **Call**. A Home Assistant dashboard
can join with a `custom:remote-screens-intercom` card. Browser calls require HTTPS (or localhost) and microphone
permission. If playback is blocked, press **Enable audio**.

Audio needs a direct local network path between callers; there is no relay for off-site callers or Nabu Casa.
Remote Screens signaling uses port 8766; the standalone signaling add-on keeps 8765.
See the [intercom guide](https://github.com/Shaffer-Softworks/remote-screens/wiki/Intercom) for card setup and
troubleshooting.

## Data

Layouts and paired tablets are kept in `/data/layouts.json` and included in Home Assistant backups. A tablet's
token is stored as a hash only. To remove a tablet, use **Forget** in the editor; it must then be paired again.

## Options

| Option | What |
| ------ | ---- |
| `map_tiles` | Where map tiles come from, as a URL with `{z}`, `{x}` and `{y}`. Empty is OpenStreetMap, whose tile servers ask for light use: the add-on caches tiles for still map images on the grid (and on older players). Interactive map cards on current players fetch tiles on the tablet using the same URL. With many tablets, or for another map style, use a tile provider you have a key for. |
