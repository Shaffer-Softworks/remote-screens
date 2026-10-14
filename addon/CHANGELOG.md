# Changelog

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
