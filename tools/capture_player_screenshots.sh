#!/usr/bin/env bash
# Capture player-app screenshots against a demo server (see docs/SCREENSHOTS.md).
#
# Prerequisites:
#   - Demo server on PLAYER_PORT (default 18100) with REMOTE_SCREENS_DEMO=1
#   - Debug APK installed on SERIAL (Pixel_Tablet recommended)
#   - If the emulator has no route to 10.0.2.2 (fresh wipe), use:
#       adb reverse tcp:18100 tcp:18100
#       PLAYER_HOST=127.0.0.1
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/docs/screenshots"
mkdir -p "$OUT"

SERIAL="${SERIAL:-}"
PLAYER_HOST="${PLAYER_HOST:-10.0.2.2}"
PLAYER_PORT="${PLAYER_PORT:-18100}"
INGRESS="${INGRESS:-http://127.0.0.1:18099}"
SCREEN_ID="${SCREEN_ID:-demo}"
PACKAGE="${PACKAGE:-tech.shaffersoftworks.remotescreens.player}"

if [[ -z "$SERIAL" ]]; then
  SERIAL="$(adb devices | awk '/emulator-.*\tdevice$/{print $1; exit}')"
fi
if [[ -z "$SERIAL" ]]; then
  SERIAL="$(adb devices | awk '/\tdevice$/{print $1; exit}')"
fi
if [[ -z "$SERIAL" ]]; then
  echo "No adb device. Set SERIAL=..." >&2
  exit 1
fi

adb() { command adb -s "$SERIAL" "$@"; }

shot() {
  local name="$1"
  adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  sleep 1.2
  adb exec-out screencap -p >"$OUT/${name}.png"
  echo "${name}.png ($(wc -c <"$OUT/${name}.png") bytes)"
}

dismiss_fullscreen_tip() {
  local dump=/tmp/rs-ui.xml
  adb shell uiautomator dump /sdcard/window_dump.xml >/dev/null 2>&1 || return 0
  adb pull /sdcard/window_dump.xml "$dump" >/dev/null 2>&1 || return 0
  local xy
  xy="$(python3 - <<'PY'
import re
xml = open("/tmp/rs-ui.xml", encoding="utf-8", errors="ignore").read()
m = re.search(r'text="Got it"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml) or \
    re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*text="Got it"', xml)
if m:
    x1, y1, x2, y2 = map(int, m.groups())
    print((x1 + x2) // 2, (y1 + y2) // 2)
PY
)"
  if [[ -n "$xy" ]]; then
    # shellcheck disable=SC2086
    adb shell input tap $xy
    sleep 1
  fi
}

# Bottom-nav taps for Pixel Tablet skin 2560x1600 (y≈1500).
tap_nav() {
  local page="$1"
  local x
  case "$page" in
    home) x=160 ;;
    living) x=437 ;;
    rooms) x=774 ;;
    extras) x=1111 ;;
    compose) x=1448 ;;
    weather) x=1785 ;;
    thermostat) x=2122 ;;
    settings) x=2400 ;;
    *) echo "unknown nav page: $page" >&2; return 1 ;;
  esac
  adb shell input tap "$x" 1500
  sleep 2
}

echo "Using device $SERIAL → ${PLAYER_HOST}:${PLAYER_PORT} (ingress $INGRESS)"

adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
adb shell am force-stop "$PACKAGE" || true
sleep 1
adb shell am start -n "${PACKAGE}/.MainActivity" --es server "${PLAYER_HOST}:${PLAYER_PORT}"
sleep 5
dismiss_fullscreen_tip
shot player-pairing

code=""
for _ in $(seq 1 40); do
  players="$(curl -fsS "$INGRESS/api/players")"
  code="$(python3 -c "
import json,sys
players=json.loads(sys.argv[1])
for p in players:
    if p.get('online') and not p.get('screenId') and (p.get('pairingCode') or p.get('code')):
        print(p.get('pairingCode') or p.get('code')); break
" "$players")"
  if [[ -n "$code" ]]; then break; fi
  sleep 1
done
if [[ -z "$code" ]]; then
  echo "No unpaired player with a code. players=$(curl -fsS "$INGRESS/api/players")" >&2
  exit 1
fi

curl -fsS -X POST "$INGRESS/api/pair" \
  -H 'Content-Type: application/json' \
  -d "{\"code\":\"$code\",\"screenId\":\"$SCREEN_ID\",\"name\":\"Docs tablet\"}"
sleep 4
dismiss_fullscreen_tip
shot player-home

tap_nav extras; shot player-extras
tap_nav compose; shot player-compose
tap_nav thermostat; shot player-thermostat
tap_nav living; shot player-living
adb shell input tap 700 700
sleep 2
shot player-card
adb shell input keyevent KEYCODE_BACK
sleep 1
tap_nav home; shot player-home

echo "Done. Screenshots in $OUT"
