# Device notifications

Send a silent banner or modal alert from **Tablets → Send notification**, a tablet's menu,
or Home Assistant's `remote_screens.send_notification` action. Delivery uses the existing local
player connection; no cloud account or Android notification permission is needed.

Choose individual tablets, all tablets showing selected screens, or explicitly **All paired tablets**.
Selecting one tablet reaches only that tablet, even when others show the same screen. Overlapping
recipients are deduplicated. Offline tablets are skipped and messages are not replayed on reconnect.

## Install the updated components first

This feature changes three separately installed components:

1. Deploy the updated Remote Screens server/editor app.
2. Update HA's `/config/custom_components/remote_screens/` with this checkout's
   `custom_components/remote_screens/` directory, then restart Home Assistant to load the new Python code.
3. Install the updated player APK on tablets for banners and alerts with wake disabled.

`tools/deploy_ha_dev.sh` deploys the server/editor/APK bundle only; it does not update the HA custom integration.
An app rebuild or integration reload alone does not load newly changed Python modules.
After restarting HA, verify `remote_screens.send_notification` appears in Tools → Actions.
If HA reports “Action not found,” check that the updated custom integration has been installed and HA restarted.

## Appearance

- **Banner:** dashboard stays interactive outside the banner. Default duration: 10 seconds.
- **Alert:** modal above the dashboard, with optional camera and up to two buttons. Default duration: 30 seconds.
- **Until acknowledged:** alert timeout 0. Without custom buttons, an Acknowledge button is added.
  Close, outside tap, and Back dismiss without acknowledging.
- **Wake screen:** off by default. Enable to wake a connected tablet from standby. Notifications are silent.

One banner and one alert can exist on a tablet. New messages replace the previous message of the
same presentation. An alert hides a banner; the banner's expiry continues while hidden.
Reconnection clears banners. No history or offline queue is retained.

## Home Assistant

Choose screen/tablet devices in the action editor. For example:

```yaml
action: remote_screens.send_notification
data:
  screens:
    - YOUR_TABLET_DEVICE_REGISTRY_ID
  title: Laundry is ready
  message: The washing machine has finished.
  presentation: banner
  timeout: 10
  wake: true
response_variable: notification_result
```

To broadcast, omit `screens` and set `all: true`. An empty selection alone never broadcasts
with this new action. A screen selection reaches all its paired tablets.

A persistent alert with an action:

```yaml
action: remote_screens.send_notification
data:
  all: true
  title: Garage door is open
  message: Please check the garage before leaving.
  presentation: alert
  level: warning
  timeout: 0
  wake: true
  tag: garage
  buttons:
    - title: Acknowledge
      action: acknowledge
```

Buttons fire the existing `remote_screens_alert_action` event with `action`, optional `tag`,
`screen`, and `device`. An optional `service: domain.service` and `data` object use the
existing server-side Home Assistant action handling. Camera entities are supported on alerts only.

The response contains `devices`, with `deviceId`, `name`, and `status` for each recipient:
`sent`, `offline`, `unsupported`, or `failed`. Sent means dispatched to the connection,
not displayed or read. An empty screen can produce an empty result.

## API and compatibility

The editor uses `POST /api/notifications` through ingress. The integration uses
`POST /integration/notifications` with its existing bearer API key. Both accept:

```json
{
  "title": "Laundry is ready",
  "message": "The washing machine has finished.",
  "targets": ["tablet-device-id"],
  "presentation": "banner",
  "timeout": 10,
  "wake": false
}
```

Targets can be screen ids or player device ids. Use `all: true` instead of targets for broadcast.
Title is required (100 characters maximum); message is limited to 500. Levels are `info`,
`warning`, and `danger`. Duration is 1–3600 seconds for banners and 0–3600 for alerts.
Optional fields: `icon`, `tag`, `camera`, and `buttons` (up to two).

Players advertise the `notifications` capability. Older players report unsupported for banners
and alerts with wake disabled, because they cannot honor silent display without waking.
Wake-enabled modal alerts continue to work on older players.

Existing `remote_screens.show_alert`, `/alert` APIs, and alert events keep their defaults and
wake behavior. Legacy empty recipient lists still broadcast. Selecting a tablet in legacy HA
services now correctly targets that tablet.
