# Testing

## Automated

```sh
./gradlew :shared:test :server:test
./gradlew :player:testDebugUnitTest
cd editor && npm ci && npm test
python -m pip install -r requirements_test.txt && python -m pytest
```

The Home Assistant dashboard intercom card has its own browser-mock regression suite:

```sh
node --test custom_components/remote_screens/frontend/intercom-card.test.cjs
```

Detekt (Kotlin style): `./gradlew :shared:detekt :server:detekt :player:detekt`

## Server UI previews

```sh
./gradlew :preview:assembleDebug
```

Open a gallery file under `preview/src/main/kotlin` in Android Studio and select **Split** or **Design**.
The [preview guide](../preview/README.md) lists the galleries and fixture sources.
Rebuild after changing server code or fixtures; refreshing the preview alone does not regenerate documents.
Use `-PpreviewFilter=climate` to focus on a feature, then rebuild without the property to restore all cases.

Generation checks catalogue tile sizes/cards and the free-form node registry, and parses every generated document.
`PreviewDocumentsTest` checks that named values are baked correctly without modifying production document bytes.
These checks establish scenario representation and document validity; they do not verify every visual branch or pixel.

For avatar changes, open `PeoplePreviews.kt` and inspect **Photo** and **Initials** at each tile size in both themes.
Both avatars have a one-physical-pixel black ring. The photo cases use synthetic artwork; the standard demo person
has no photo and uses initials. `PanelsPreviews.kt` also includes **Home · person photo**.

Run the Android checks alongside the server checks:

```sh
./gradlew :shared:detekt :server:detekt :player:detekt
./gradlew :shared:test :server:test :player:testDebugUnitTest
./gradlew :player:assembleDebug :preview:assembleDebug :player:lintDebug :preview:lintDebug
```

`:server:detekt` includes `:server:detektPreview`; it can also be run directly while editing fixtures.
Inspect representative previews after a successful build. Preview generation is not an automated screenshot
comparison, and previews do not execute actions, stream media, or mount map/search overlays.
Use the device checks below for live behavior.

## Demo server

See [DEMO.md](DEMO.md). A made-up home is enough for most UI and render tests.

## On a device

1. Start the demo server (player port **18100** locally).
2. Install the player: `./gradlew :player:assembleDebug` then `adb install -r player/build/outputs/apk/debug/player-debug.apk`.
   [ADB.md](ADB.md) lists the Gradle tasks that deploy, reverse-forward, screenshot, and save logs for one
   serial. **Tablets → ADB** in the editor does the same kind of actions when that server can reach the tablet.
3. Launch against the host:

```sh
# Emulator (usual case)
adb shell am start -n tech.shaffersoftworks.remotescreens.player/.MainActivity --es server 10.0.2.2:18100

# Emulator after a wipe with no route to 10.0.2.2
adb reverse tcp:18100 tcp:18100
adb shell am start -n tech.shaffersoftworks.remotescreens.player/.MainActivity --es server 127.0.0.1:18100
```

4. Pair in the editor (**Tablets**). Tap a light: the demo state should change and the tile update without a full redraw.
5. In **Tablets → Send notification**, send a banner to one tablet, then an alert. Verify the
   banner leaves dashboard controls usable, the alert hides it, and Close/Back restores the page.
   Repeat in portrait, landscape, light/dark mode, large text, and kiosk lock. Check long text and both action buttons.
6. Put the tablet in standby: a notification with Wake off must leave it asleep; Wake on must wake it.
   Disconnect one tablet and target its screen: verify per-tablet sent/offline results, with no replay after reconnect.
7. Send a persistent alert with Acknowledge and a tag. Verify only pressing Acknowledge produces
   the action event; Close, outside tap, and Back must not. Replace a banner before its expiry and check the old timer
   cannot dismiss its replacement. Repeat while camera and calendar overlays and intercom are active.
8. Optional: exercise kiosk enroll with a factory-reset device or `./scripts/enroll-device-owner.sh` (see [addon/DOCS.md](../addon/DOCS.md#kiosk-lock)).

The Remote Compose JVM spike is covered by `RcSpikeTest`; this section is the on-device check that taps reach the
add-on and live variables update.

## Intercom checks

Use two connected tablets on the local network, both showing an Intercom page. Call between them,
check audio in both directions, mute/unmute, and hang up from each end. Repeat from **Tablets → Intercom**
and a Home Assistant dashboard card over HTTPS, allowing microphone access.

Check denied microphone permission, a missing microphone, and blocked autoplay (**Enable audio**).
End the call and close/reopen the panel or card: the microphone should stop after closing, and the
station list and audio should work again after reopening. Check narrow and wide editor windows.
The demo home alone cannot verify real microphone permissions or peer-to-peer audio.
