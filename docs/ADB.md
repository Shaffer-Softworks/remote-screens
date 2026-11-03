# ADB development and tablet management

## Android Studio and local development

The player module exposes tasks in the **adb** Gradle group. Use the Android SDK's
Platform Tools installation; no ADB library is embedded in the player.

```sh
./gradlew :player:adbDevices
./gradlew :player:adbReverse -PadbSerial=DEVICE_SERIAL
./gradlew :player:adbDeploy -PadbSerial=DEVICE_SERIAL
./gradlew :player:adbLaunch -PadbSerial=DEVICE_SERIAL -PadbServer=192.168.1.10:8100
./gradlew :player:adbScreenshot -PadbSerial=DEVICE_SERIAL
./gradlew :player:adbLogs -PadbSerial=DEVICE_SERIAL
```

Every operation except listing requires an explicit serial, including when only one device is
connected. Deploy builds the debug APK, installs it on that device, and launches the player.
An existing release signed with another key cannot be updated with the debug APK.

For a server on your development computer, the defaults are player port **18100** and
server address **127.0.0.1:18100**. Run reverse before launch/deploy; repeat it after reconnecting
the device. Override the forwarded port with `-PadbPort=8100` and set
`-PadbServer=127.0.0.1:8100` when using another local port.
The server itself must already be running.

Screenshots and the latest 500 logcat lines are saved under `player/build/adb/`.
The logs contain system diagnostics, including messages from other apps.

## Editor

Open **Tablets → ADB**. The add-on image includes Android Platform Tools.

1. On an Android 11+ tablet, enable Developer options and Wireless debugging.
2. Choose **Pair device with pairing code** on the tablet.
3. Enter its pairing host:port and six-digit code in the editor, then select **Pair ADB**.
4. Enter the separate debugging host:port from the tablet's main Wireless debugging screen,
   then select **Connect**.
5. Refresh to see device state. Associate a serial with a registered tablet to retain the mapping.

The browser sends requests to the add-on; the add-on must be able to reach the tablet on the
local network. This works through Home Assistant ingress. Pairing and connection ports differ,
and debugging ports can change after wireless debugging or the tablet restarts. Reconnect with
the new address when needed. Associations are labels; actions explicitly target the displayed serial.

Older tablets normally require initial USB authorization and enabling TCP debugging from a
computer. The editor does not enable debugging, provision device ownership, or bypass tablet
authorization. USB devices appear only when available inside the server container.

Available actions are restart player, install the packaged player APK, screenshot, download
logs, and disconnect. Installation uses the same APK offered by **Download app** and requires
compatible signing. Install errors are displayed without uninstalling or clearing app data.
Disconnect closes the network connection; revoke trust using **Forget** in the tablet's
Wireless debugging paired-computer list.

ADB routes exist only on the editor server, under its existing ingress protection.
The player and integration ports do not expose ADB actions. ADB operations are predefined;
there is no arbitrary shell-command endpoint. Commands have a two-minute timeout and a
16 MiB output cap.

## Standalone server

Install `adb` on the server host or set `REMOTE_SCREENS_ADB` to its absolute executable path.
The server uses a separate local ADB server on port **5038**. Keys and tablet associations
live under `<REMOTE_SCREENS_DATA>/adb`; persist this directory across container replacement.
The add-on's existing `/data` storage provides persistence automatically.

Authorization keys grant access to paired tablets. To revoke one tablet, forget this
computer on that tablet. Android debugging must remain enabled for ADB management to work.
