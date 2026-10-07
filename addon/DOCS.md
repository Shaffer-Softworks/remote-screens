# Remote Screens

Remote Screens turns Android tablets into Home Assistant wall screens. You lay out tiles in the editor (the
**Remote Screens** panel in the sidebar); the add-on draws each screen as a Remote Compose document and sends it
to the tablets, which show it with the Remote Screens app. Taps come back to the add-on, which calls Home
Assistant. The tablets never talk to Home Assistant themselves, and need no token.

## Getting started

1. Start the add-on and open **Remote Screens** in the sidebar.
2. Install the Remote Screens app on a tablet. In the editor, **Players** has a link to download it from the
   add-on, or use the APK from the GitHub release.
3. Open the app. It finds the add-on on your network and shows a six-digit code.
4. In the editor, **Players**, enter the code and choose a screen. The tablet shows it at once.
5. Lay out the screen: add pages, drag entities from the library onto the grid, and tap **Save & send**.

If the app does not find the add-on (mDNS blocked between VLANs, for example), enter the address of your Home
Assistant host and port 8100 in the app.

## Tiles

Every tile follows its entity live. A tap does the obvious thing for its type (toggle a light, open a cover's
card); a long press opens the card with everything else (brightness and colour, modes, the media library,
history). The editor lets you change the tap to toggle, open a card, run a service, go to a page, or nothing.

Tiles that are not an entity: clock, bedside clock, page navigation, history graph, energy flow, map, and a
favourite (a playlist on a player).

## Settings from Home Assistant

The **Remote Screens** integration (installed with HACS) adds, for each screen, entities for brightness, dark
mode, standby, night mode and the page shown, and the services `remote_screens.show_alert`,
`remote_screens.open_page` and `remote_screens.wake`. The add-on announces itself, so Home Assistant offers to
set the integration up.

Alerts can also come from an event, without the integration:

```yaml
action: event.fire
data:
  event_type: remote_screens_alert
  event_data:
    title: Someone is at the door
    camera: camera.front_door
    buttons:
      - title: Open
        service: lock.open
        data: {entity_id: lock.front_door}
```

A button fires `remote_screens_alert_action` with its `action`, so an automation can answer it.

## Network

| Port | What |
| ---- | ---- |
| 8100 | Tablets connect here (WebSocket), and the integration's API |
| 8099 | The editor, through ingress only |

The add-on uses the host network so tablets can find it over mDNS (`_remotescreens._tcp`).

## Data

Layouts and adopted players are kept in `/data/layouts.json` and included in Home Assistant backups. A player's
token is stored as a hash only. To remove a tablet, use **Forget** in the editor; it must then be adopted again.
