# Testing

## Automated

```sh
./gradlew :shared:test :server:test
./gradlew :player:testDebugUnitTest
cd editor && npm ci && npm test
python -m pip install -r requirements_test.txt && python -m pytest
```

Detekt (Kotlin style): `./gradlew :shared:detekt :server:detekt :player:detekt`

## Demo server

See [DEMO.md](DEMO.md). A made-up home is enough for most UI and render tests.

## On a device

1. Start the demo server (player port **18100** locally).
2. Install the player: `./gradlew :player:assembleDebug` then `adb install -r player/build/outputs/apk/debug/player-debug.apk`
3. Launch against the host:

```sh
# Emulator (usual case)
adb shell am start -n tech.shaffersoftworks.remotescreens.player/.MainActivity --es server 10.0.2.2:18100

# Emulator after a wipe with no route to 10.0.2.2
adb reverse tcp:18100 tcp:18100
adb shell am start -n tech.shaffersoftworks.remotescreens.player/.MainActivity --es server 127.0.0.1:18100
```

4. Pair in the editor (**Tablets**). Tap a light: the demo state should change and the tile update without a full redraw.
5. Optional: exercise kiosk enroll with a factory-reset device or `./scripts/enroll-device-owner.sh` (see [addon/DOCS.md](../addon/DOCS.md#kiosk-lock)).

The Remote Compose JVM spike is covered by `RcSpikeTest`; this section is the on-device check that taps reach the
add-on and live variables update.
