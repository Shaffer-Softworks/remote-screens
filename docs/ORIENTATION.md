# Screen orientation

A screen's **Orientation** is `ScreenSettings.orientation` in `shared/.../Layout.kt`: `0` follows the tablet,
`1` locks portrait, `2` locks landscape. Anything else is stored as `0`. The editor offers the same three
choices under the screen's settings.

The player applies it with `Activity.applyScreenOrientation` (`player/.../utils/WindowExtensions.kt`):

- `MainActivity.onResume` and `onConfigurationChanged`
- when the screen becomes awake again (`observeScreenOn`), because a lock does not stick across `lockNow`
- when the setting itself changes (`PlayerWindowEffects`)

If the request is already set but the window is still the other orientation, the player clears it to
`SCREEN_ORIENTATION_UNSPECIFIED` and sets it again. Some tablets ignore a request that did not change after wake.

## Large tablets ignore the lock

From smallest width about 600 dp, Android's default is `ignoreOrientationRequest true`. The activity can report
`SCREEN_ORIENTATION_LANDSCAPE` while the display stays in its natural orientation. Turning the screen off can
also force that natural orientation. Wake then stays there until the display honors the app.

Check:

```sh
adb shell wm get-ignore-orientation-request
```

Honored looks like `ignoreOrientationRequest false`. When it is true:

```sh
adb shell wm set-ignore-orientation-request false
```

`wm set-ignore-orientation-request reset` puts the large-screen default back. Do not reset a tablet that was
fixed this way.

Checked November 2026:

| Tablet | What happened |
| --- | --- |
| Galaxy Tab S7 FE, Android 16, smallest width 753 dp, natural portrait | With the override false, landscape survives screen-off and wake. With the default true, wake stays portrait even though the activity requested landscape. `setRequestedOrientation` alone does not fix it. |
| BLU M8L, Android 12, smallest width 533 dp | `ignoreOrientationRequest` is already false. Landscape survives sleep. A power-button sleep can show a portrait keyguard; bringing the player forward rotates back. |

The user-facing steps are also in [addon/DOCS.md](../addon/DOCS.md#orientation).

## Kiosk lock and auto-rotate

`KioskManager.startLockTaskMode` calls `DeviceRepository.disableSystemAutoRotate` before `startLockTask`. That
writes `Settings.System.USER_ROTATION` to the current display rotation and `ACCELEROMETER_ROTATION` to `0`.
`stopLockTaskMode` does not put auto-rotate back.

The write needs `WRITE_SETTINGS`. The manifest declares it. On the Tab S7 FE the app-op stayed at its default
and the write was rejected (`Could not disable system auto-rotate before lock task` in logcat). A manifest
line is not a grant. When the write fails, the screen's orientation lock is unchanged. When it succeeds,
auto-rotate stays off after unlock, including for a tablet that is only pinned.

A `user_rotation` pin is not enough on the Tab S7 FE: screen-off can save rotation back to natural portrait.
Wake stays landscape only when `ignoreOrientationRequest` is false and the app request is honored.

## What to read in logcat

```sh
adb logcat -d -s KioskManager:I
```

`System auto-rotate disabled (user_rotation=…)` means the settings write stuck. The warning above means it did not.
