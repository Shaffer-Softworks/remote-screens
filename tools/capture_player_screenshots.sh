#!/usr/bin/env bash
# Capture player-app screenshots against a demo server (see docs/SCREENSHOTS.md).
#
# Prerequisites:
#   - Demo server on PLAYER_PORT (default 18100) with REMOTE_SCREENS_DEMO=1
#   - Debug APK installed on SERIAL (Pixel_Tablet recommended)
#   - If the emulator has no route to 10.0.2.2 (fresh wipe), use:
#       adb reverse tcp:18100 tcp:18100
#       PLAYER_HOST=127.0.0.1
#
# Navigation uses the modal drawer (page titles are native UI with accessibility text).
# Falls back to evenly spaced bottom-nav taps when the drawer cannot be opened.
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
UI_DUMP=/tmp/rs-ui.xml

# Visible demo pages (More is hidden), then Settings — matches DemoLayout.screen().
NAV_TITLES=(
  Home
  "Living room"
  Rooms
  Extras
  Compose
  Weather
  Thermostat
  Music
  Lights
  Cameras
  Blinds
  Security
  Vacuum
  Energy
  Settings
)

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

dump_ui() {
  adb shell uiautomator dump /sdcard/window_dump.xml >/dev/null 2>&1 || return 1
  adb pull /sdcard/window_dump.xml "$UI_DUMP" >/dev/null 2>&1 || return 1
}

tap_bounds_center() {
  local x1="$1" y1="$2" x2="$3" y2="$4"
  adb shell input tap $(((x1 + x2) / 2)) $(((y1 + y2) / 2))
}

# Tap the first node whose text or content-desc equals $1 (exact).
tap_text() {
  local want="$1"
  dump_ui || return 1
  local xy
  xy="$(WANT="$want" python3 - <<'PY'
import os, re
want = os.environ["WANT"]
xml = open("/tmp/rs-ui.xml", encoding="utf-8", errors="ignore").read()
# Prefer exact text= or content-desc=, then take bounds on the same node.
pat = re.compile(
    r'(?:text|content-desc)="' + re.escape(want) + r'"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
    r'|bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*(?:text|content-desc)="' + re.escape(want) + r'"',
    re.I,
)
m = pat.search(xml)
if not m:
    raise SystemExit(1)
groups = [g for g in m.groups() if g is not None]
x1, y1, x2, y2 = map(int, groups)
print((x1 + x2) // 2, (y1 + y2) // 2)
PY
)" || return 1
  # shellcheck disable=SC2086
  adb shell input tap $xy
  sleep 1.5
}

dismiss_fullscreen_tip() {
  dump_ui || return 0
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
  if [[ -n "${xy:-}" ]]; then
    # shellcheck disable=SC2086
    adb shell input tap $xy
    sleep 1
  fi
}

screen_size() {
  adb shell wm size | awk -F': ' '/Physical size/{print $2; exit}'
}

# Evenly spaced bottom-nav fallback (fill layout). Index matches NAV_TITLES.
tap_nav_bar_index() {
  local index="$1"
  local size width height count button_w x y
  size="$(screen_size)"
  width="${size%x*}"
  height="${size#*x}"
  count="${#NAV_TITLES[@]}"
  button_w=$((width / count))
  x=$((button_w * index + button_w / 2))
  y=$((height * 94 / 100))
  adb shell input tap "$x" "$y"
  sleep 2
}

open_drawer() {
  local size height
  size="$(screen_size)"
  height="${size#*x}"
  # Edge swipe opens the modal drawer when the demo screen enables it; otherwise no-op.
  adb shell input swipe 5 $((height / 2)) 700 $((height / 2)) 250
  sleep 1.2
  if dump_ui && grep -q 'text="Living room tablet"' "$UI_DUMP" 2>/dev/null; then
    return 0
  fi
  if tap_text "Open navigation drawer" 2>/dev/null; then
    return 0
  fi
  return 1
}

tap_nav() {
  local title="$1"
  local i
  if open_drawer && tap_text "$title" 2>/dev/null; then
    sleep 1.5
    return 0
  fi
  echo "drawer tap missed for '$title'; using bottom nav" >&2
  for i in "${!NAV_TITLES[@]}"; do
    if [[ "${NAV_TITLES[$i]}" == "$title" ]]; then
      tap_nav_bar_index "$i"
      return 0
    fi
  done
  echo "unknown nav page: $title" >&2
  return 1
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

tap_nav Extras; shot player-extras
tap_nav Compose; shot player-compose
tap_nav Thermostat; shot player-thermostat
tap_nav Lights; shot player-lights
tap_nav Cameras; shot player-cameras
tap_nav Blinds; shot player-blinds
tap_nav Security; shot player-security
tap_nav Vacuum; shot player-vacuum
tap_nav Energy; shot player-energy-dashboard
tap_nav "Living room"; shot player-living
# Open a tile card on Living room (center-ish of the grid).
size="$(screen_size)"
width="${size%x*}"
height="${size#*x}"
adb shell input tap $((width * 28 / 100)) $((height * 45 / 100))
sleep 2
shot player-card
adb shell input keyevent KEYCODE_BACK
sleep 1
tap_nav Home; shot player-home

echo "Done. Screenshots in $OUT"
