#!/usr/bin/env bash
# Makes the Remote Screens player the device owner over adb, so Kiosk lock can use full lock task.
#
# QR enrollment from the editor (Screens > Enroll a tablet) needs no computer: use this script for tablets
# without the QR setup wizard (some AOSP and Fire tablets) or ones already set up.
#
# When adb says "Attempt to remove non-test admin", the only automated fix is to uninstall the package (clears
# the admin), reinstall, then set-device-owner before the first launch:
#   ./scripts/enroll-device-owner.sh --fresh path/to/remote-screens.apk
#
# Prerequisites:
#   - USB debugging on; host authorized (adb devices shows "device")
#   - No accounts and no other device owner or MDM on the tablet (often means a factory reset)
#
# Env: ANDROID_SERIAL  target one device when several are connected

set -euo pipefail

ADMIN_COMPONENT="dev.remotescreens.player/dev.remotescreens.player.receiver.LockTaskDeviceAdminReceiver"
PACKAGE="dev.remotescreens.player"

FRESH=false
RESET_ADMIN=false
APK_PATH=""

usage() {
  cat <<'EOF'
Usage:
  ./scripts/enroll-device-owner.sh --fresh PATH/to/remote-screens.apk
      Recommended when enrollment failed or remove-active-admin says "non-test admin".
      Uninstalls the app (clears the admin), installs the APK, runs set-device-owner.
      Wipes the app's data, so the tablet has to be paired again. Don't open the app until the script finishes.

  ./scripts/enroll-device-owner.sh [--reset-admin] [PATH/to/remote-screens.apk]
      With an APK: adb install -r. --reset-admin tries remove-active-admin (often fails on retail builds).
      Then set-device-owner. Use when the app isn't installed yet or only needs an update and enrollment.

  ./scripts/enroll-device-owner.sh -h|--help

Env:
  ANDROID_SERIAL   adb device serial when more than one device is connected.

EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --fresh|-f)
      FRESH=true
      shift
      ;;
    --reset-admin)
      RESET_ADMIN=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      if [[ -f "$1" ]]; then
        APK_PATH="$1"
      else
        echo "ERROR: Unknown argument or missing file: $1" >&2
        usage >&2
        exit 1
      fi
      shift
      ;;
  esac
done

if [[ "$FRESH" == true && -z "$APK_PATH" ]]; then
  echo "ERROR: --fresh requires an APK path." >&2
  usage >&2
  exit 1
fi

echo "==> Waiting for device (adb)…"
adb wait-for-device

if [[ -n "${ANDROID_SERIAL:-}" ]]; then
  echo "==> Using ANDROID_SERIAL=${ANDROID_SERIAL}"
fi

echo "==> Connected devices:"
adb devices -l

if [[ "$FRESH" == true ]]; then
  echo "==> Preparing uninstall (stop the app; may clear screen pinning on some builds)…"
  adb shell am force-stop "$PACKAGE" 2>/dev/null || true
  # Exists on some API levels; harmless if unknown.
  adb shell cmd activity stop-app-pinning 2>/dev/null || true

  echo "==> Uninstalling ${PACKAGE} (clears the device admin when adb can't remove-active-admin)…"
  set +e
  UNINSTALL_OUT="$(adb uninstall "$PACKAGE" 2>&1)"
  UNINSTALL_STATUS=$?
  set -e
  echo "$UNINSTALL_OUT"
  if [[ $UNINSTALL_STATUS -ne 0 ]]; then
    if echo "$UNINSTALL_OUT" | grep -q 'DELETE_FAILED_APP_PINNED'; then
      echo >&2
      echo "NOTE: Uninstall failed: the app is screen-pinned (DELETE_FAILED_APP_PINNED)." >&2
      echo "      On the tablet: unlock it from the player's settings with the Kiosk PIN, or hold Back + Recents" >&2
      echo "      for ~2s, or reboot and run this script again before the app opens. Then --fresh can clear the admin." >&2
      echo >&2
    else
      echo "WARN: Uninstall failed (exit $UNINSTALL_STATUS). Continuing with install; the device owner may still be set." >&2
    fi
  fi
  echo "==> Installing APK: $APK_PATH"
  adb install -r "$APK_PATH"
else
  if [[ -n "$APK_PATH" ]]; then
    echo "==> Installing APK: $APK_PATH"
    adb install -r "$APK_PATH"
  fi

  if [[ "$RESET_ADMIN" == true ]]; then
    echo "==> remove-active-admin (often fails for a non-test admin; safe to ignore errors)…"
    set +e
    adb shell dpm remove-active-admin "$ADMIN_COMPONENT" 2>&1
    set -e
  fi

  if ! adb shell pm list packages | grep -q "^package:${PACKAGE}$"; then
    echo "ERROR: ${PACKAGE} isn't installed. Pass an APK path or use --fresh PATH.apk" >&2
    exit 1
  fi
fi

echo "==> Setting device owner (${ADMIN_COMPONENT})…"
set +e
OUT="$(adb shell dpm set-device-owner "$ADMIN_COMPONENT" 2>&1)"
STATUS=$?
set -e

# When the uninstall was skipped (e.g. APP_PINNED), the owner is already us: treat that as success.
if [[ $STATUS -ne 0 ]] && echo "$OUT" | grep -qiE 'already set|already an admin'; then
  if adb shell dpm list-owners 2>/dev/null | grep -q "$PACKAGE"; then
    echo "$OUT"
    echo "==> Device owner is already ${PACKAGE}; nothing to change."
    STATUS=0
  fi
fi

if [[ $STATUS -ne 0 ]]; then
  echo "$OUT" >&2
  echo >&2
  echo "FAILED (exit $STATUS)." >&2
  echo "Automated recovery (clears a stuck admin and reinstalls):" >&2
  echo "  ./scripts/enroll-device-owner.sh --fresh /path/to/remote-screens.apk" >&2
  echo "If the uninstall showed DELETE_FAILED_APP_PINNED: unpin the app or reboot, then run --fresh again." >&2
  echo "If accounts are on the tablet: remove them (Settings > Accounts) or factory reset, then try again." >&2
  echo "Samsung/Knox: use a user with no accounts; some builds block adb device owner." >&2
  exit "$STATUS"
fi

echo "$OUT"
echo "==> Device owner status:"
adb shell dpm list-owners 2>/dev/null || true

echo "==> Done. Open the app, pair it, then turn on the screen's Kiosk lock in Home Assistant or the editor."
