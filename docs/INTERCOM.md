# Intercom

Call between Remote Screens tablets, the editor, and a Home Assistant dashboard on your local network.
Audio travels directly between the two ends using WebRTC; the add-on handles signaling.

## Tablet stations

Add an **Intercom** tile from **Other tiles** and make it a full-page panel. A tablet appears in the
station list only while it is showing that page. Allow microphone access on the tablet when prompted.
Select a station and press **Call**; the other tablet answers automatically. **Mute** disables your
microphone, and **Hang up** ends the call.

## Call from the editor

Open **Tablets → Intercom** in the Remote Screens panel. This control is on the Tablets view, rather
than the screen editor toolbar. Choose an online station and press **Call**. Busy stations cannot be called.
The panel adapts to narrow windows and portrait displays.

Open Home Assistant over **HTTPS** (or localhost) and allow the browser to use your microphone.
An ordinary HTTP address on your LAN does not provide browser microphone access.
If the browser blocks playback, press **Enable audio** in the intercom panel.

## Home Assistant dashboard card

Install or update the Remote Screens integration and restart Home Assistant so its bundled dashboard
card and signaling bridge are loaded. Add a manual card:

```yaml
type: custom:remote-screens-intercom
```

With no options, the card lists online stations. To select one tablet by its name:

```yaml
type: custom:remote-screens-intercom
station: Kitchen
```

Browser microphone and playback requirements are the same as in the editor. If **Enable audio** appears,
press it to retry playback. Ending a call releases the microphone and remote audio; closing the editor
panel or removing the dashboard card also cleans up its connection.

## Network and troubleshooting

- Remote Screens tablet signaling uses **8766**. The separate standalone signaling add-on keeps **8765**.
- The editor uses its ingress connection for signaling. The dashboard card uses Home Assistant's
  WebSocket connection and the integration bridge; the browser does not open port 8766 directly.
- Both ends need a direct local network path for audio. There is no relay; opening Home Assistant through
  Nabu Casa or from a phone away from home does not provide that path.
- **Microphone requires HTTPS:** open Home Assistant over HTTPS or use localhost for local development.
- **Microphone blocked:** allow microphone access in the browser's site settings, then call again.
- **No microphone found:** connect or enable a microphone. If it cannot be opened, check whether another
  app is using it and retry.
- **Connected but silent:** press **Enable audio** if offered, check output volume and mute on both ends,
  then check the network path between the callers.
- **Tablet missing:** keep its Intercom page visible and check that it is connected to the add-on.
