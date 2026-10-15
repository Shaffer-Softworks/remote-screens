# Changelog

## 0.1.8

- Faster live camera start: begin Home Assistant's HLS stream while the card or alert builds, reuse a warm
  stream when the same camera is opened again, and keep the player closer to the live edge.

## 0.1.7

- Restore live camera video when the overlay could not resolve the image's bounds (it stayed invisible and only
  the snapshot showed). The overlay sits above the document again and falls back if the library location fails.

## 0.1.6

- Fix the camera card's double preview: live video is hosted inside the document player (same coordinates as
  clicks), fills the image, and covers the snapshot so it no longer floats over the title.

## 0.1.5

- Fix the camera card's live video sitting over the header: the overlay now uses the document's own bounds
  (with padding), so it covers the snapshot instead of showing a second, shifted preview.

## 0.1.4

- Fix live camera video: Home Assistant's low-latency HLS parts (`segment/1.0.m4s`) are relayed to the tablet
  instead of returning 404, so the card's stream plays instead of sticking on the snapshot.

## 0.1.3

- Live camera video on cards and alerts. Tablets that can play video get Home Assistant's HLS stream over the
  snapshot; without a stream, or on an older player, the snapshot stays.
- Player app updates: bump this version whenever the APK changes so tablets are offered the new build. The app
  id is `tech.shaffersoftworks.remotescreens.player`; tablets still on `dev.remotescreens.player` must uninstall
  (kiosk tablets: factory reset and enroll again).

## 0.1.2

- Themes. Screens come in RPI dark (as before), Light, OLED black and High contrast, and you can make your own
  in the editor's new Themes page: duplicate a theme, pick its colours, and see them on a live preview.
- Each screen has a theme and a dark theme. Dark mode, and the night schedule's "dark at night", now switch the
  screen to its dark theme, so the integration's dark mode switch changes what the screen wears.
- The editor's canvas draws in the screen's theme, with a Day / Dark switch to preview both.
- The map follows the theme: light on light themes, dark on dark ones.

## 0.1.1

- Free-form pages: a layout builder like Android Studio's, with columns, rows, boxes, flows and scrolling lists,
  and components for text, icons, buttons, sliders, images, cards, clocks, analog clocks, badges, avatars,
  progress bars, gauges, graphs, switches, steppers, choices, icon buttons, remote keys, colour swatches and a
  keypad. Borders on any component; text that glides along; components shown only while an entity is on or off,
  fading or sliding in and out.
- Remote Compose 1.0.0-alpha21. Tablets on this player app receive their screens compressed, well under half
  the size.
- A clearer home page in the editor: tablets waiting to pair at the top, a preview of each screen, and a tablet
  list with its status, the screen it shows, and plainly named actions.

## 0.1.0

- First release: screens of tiles on Android tablets, drawn with Remote Compose, edited in Home Assistant.
- Tiles for lights, switches, fans, covers, locks, climate, media players, weather, cameras, alarms, selects,
  numbers, vacuums, people, sensors, scenes and scripts, plus clock, bedside clock, navigation, history graph,
  energy flow, map and favourite music.
- Several pages per screen, hidden pages, a top bar with clock, temperature and who is home.
- Cards on long press; alerts with a camera and buttons; light and dark theme with a night schedule.
- An editor in the sidebar: drag entities onto a mockup of the tablet, choose sizes, colours, icons and tap
  actions, then save and send, or preview on a tablet without saving.
- Pairing with a six-digit code; players found over mDNS.
- The tablets update their app from the add-on.
- Texts in English, Dutch, German, French, Spanish, Italian, Polish and Portuguese, with Home Assistant's own
  words for states in every language.
- A Home Assistant integration (HACS) with entities for each screen's settings and services for alerts, pages
  and waking the screen.
