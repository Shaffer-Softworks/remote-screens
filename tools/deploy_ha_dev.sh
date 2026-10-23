#!/usr/bin/env bash
# Package Remote Screens and upload it to the local HA add-on "Remote Screens dev".
#
# Usage:
#   export HA_SAMBA_PASSWORD='…'          # required (from core_samba options; never commit)
#   # optional: HA_SAMBA_HOST HA_SAMBA_USER HA_SAMBA_WORKGROUP
#   # optional: HA_URL + HA_TOKEN to rebuild via Supervisor API after upload
#   tools/deploy_ha_dev.sh [options] [path/to/player.apk]
#
# Options:
#   --upload-only     Skip packaging; upload existing addon/dist
#   --no-rebuild      Never call Supervisor rebuild
#   --rebuild         Always rebuild (requires HA_TOKEN)
#   --env FILE        Source env file first (default: /tmp/ha-samba.env if present)
#   -h, --help        Show this help
#
# Rebuild is automatic when HA_TOKEN is set (unless --no-rebuild).
#
# Env file example (/tmp/ha-samba.env, chmod 600):
#   export HA_SAMBA_HOST=10.11.0.17
#   export HA_SAMBA_USER=ha
#   export HA_SAMBA_WORKGROUP=SHAFFERCO
#   export HA_SAMBA_PASSWORD='…'
#   export HA_URL=http://10.11.0.17:8123
#   export HA_TOKEN='…'   # long-lived access token for rebuild
#
# Notes:
#   - Uploads to Samba share local_apps/remote_screens (HA OS “Local apps”).
#   - Strips image: from config.yaml and sets name/panel_title to "Remote Screens dev".
#   - Same version → rebuild; bump addon/VERSION (+ config.yaml) for a store-style update.
#   - Keep the published store add-on (032995b0_remote_screens) stopped while testing —
#     both bind host port 8100.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
REMOTE="local_apps/remote_screens"
ADDON_SLUG="local_remote_screens"
DEV_NAME="Remote Screens dev"

UPLOAD_ONLY=0
# auto = rebuild when HA_TOKEN is set; on/off from flags
REBUILD_MODE=auto
ENV_FILE=""
APK=""

usage() {
  sed -n '2,28p' "$0" | sed 's/^# \?//'
}

while [ $# -gt 0 ]; do
  case "$1" in
    --upload-only) UPLOAD_ONLY=1 ;;
    --no-rebuild) REBUILD_MODE=off ;;
    --rebuild) REBUILD_MODE=on ;;
    --env)
      ENV_FILE="${2:?}"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    -*)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
    *)
      APK=$1
      ;;
  esac
  shift
done

# Prefer Framework Python (smbprotocol/cryptography); fall back to python3.
resolve_python() {
  local candidates=(
    /Library/Frameworks/Python.framework/Versions/3.14/bin/python3
    /Library/Frameworks/Python.framework/Versions/3.13/bin/python3
    /Library/Frameworks/Python.framework/Versions/3.12/bin/python3
    python3
  )
  local candidate
  for candidate in "${candidates[@]}"; do
    if command -v "$candidate" >/dev/null 2>&1 || [ -x "$candidate" ]; then
      echo "$candidate"
      return 0
    fi
  done
  echo "python3 not found" >&2
  exit 1
}

resolve_samba() {
  local candidates=(
    "${HA_SAMBA_CLI:-}"
    "$HOME/.cursor/skills/ha-samba-files/scripts/ha_samba.py"
  )
  local candidate
  for candidate in "${candidates[@]}"; do
    [ -n "$candidate" ] || continue
    if [ -f "$candidate" ]; then
      echo "$candidate"
      return 0
    fi
  done
  echo "ha_samba.py not found. Set HA_SAMBA_CLI or install ~/.cursor/skills/ha-samba-files." >&2
  exit 1
}

load_env() {
  local file=${ENV_FILE:-}
  if [ -z "$file" ] && [ -f /tmp/ha-samba.env ]; then
    file=/tmp/ha-samba.env
  fi
  if [ -n "$file" ]; then
    if [ ! -f "$file" ]; then
      echo "Env file not found: $file" >&2
      exit 1
    fi
    # shellcheck disable=SC1090
    set -a
    # shellcheck disable=SC1090
    source "$file"
    set +a
    echo "Loaded env from $file"
  fi
  : "${HA_SAMBA_HOST:=10.11.0.17}"
  : "${HA_SAMBA_USER:=ha}"
  : "${HA_SAMBA_WORKGROUP:=SHAFFERCO}"
  export HA_SAMBA_HOST HA_SAMBA_USER HA_SAMBA_WORKGROUP
  if [ -z "${HA_SAMBA_PASSWORD:-}" ]; then
    echo "HA_SAMBA_PASSWORD is not set (export it or use --env /tmp/ha-samba.env)." >&2
    exit 1
  fi
  export HA_SAMBA_PASSWORD
}

ensure_java() {
  if command -v java >/dev/null 2>&1; then
    return 0
  fi
  local studio_jbr="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  if [ -d "$studio_jbr" ]; then
    export JAVA_HOME=$studio_jbr
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
}

package() {
  ensure_java
  export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
  local apk=$APK
  if [ -z "$apk" ]; then
    if [ -f "$ROOT/player/build/outputs/apk/release/player-release.apk" ]; then
      apk=$ROOT/player/build/outputs/apk/release/player-release.apk
    elif [ -f "$ROOT/addon/dist/apk/remote-screens.apk" ]; then
      # package_addon.sh wipes dist; preserve first
      mkdir -p /tmp/rs-deploy
      cp -f "$ROOT/addon/dist/apk/remote-screens.apk" /tmp/rs-deploy/remote-screens.apk
      apk=/tmp/rs-deploy/remote-screens.apk
    fi
  fi
  if [ -z "$apk" ]; then
    echo "No player APK found; assembling release…"
    (cd "$ROOT" && ./gradlew :player:assembleRelease -q)
    apk=$ROOT/player/build/outputs/apk/release/player-release.apk
  fi
  echo "Packaging with $apk"
  "$ROOT/tools/package_addon.sh" "$apk"
}

samba_put() {
  local src=$1 dest=$2
  "$PY" "$SAMBA" put "$src" "$dest" >/dev/null
  echo "  put $dest"
}

samba_rm() {
  "$PY" "$SAMBA" rm "$1" >/dev/null 2>&1 || true
}

samba_ls_names() {
  "$PY" "$SAMBA" ls "$1" 2>/dev/null | awk '{print $NF}' || true
}

upload() {
  local dist=$ROOT/addon/dist
  if [ ! -f "$dist/remote-screens-server.jar" ]; then
    echo "Missing $dist/remote-screens-server.jar — run without --upload-only first." >&2
    exit 1
  fi

  echo "Uploading to $HA_SAMBA_HOST:$REMOTE …"

  # Drop stale hashed editor assets (names change each Vite build).
  local name
  for name in $(samba_ls_names "$REMOTE/dist/static/assets"); do
    case "$name" in
      .|..) ;;
      *) samba_rm "$REMOTE/dist/static/assets/$name" ;;
    esac
  done

  local tmp_cfg
  tmp_cfg=$(mktemp)
  # Local build: no GHCR image line; distinct name from the store add-on.
  grep -v '^image:' "$ROOT/addon/config.yaml" | \
    sed "s/^name: Remote Screens\$/name: ${DEV_NAME}/" | \
    sed "s/^panel_title: Remote Screens\$/panel_title: ${DEV_NAME}/" >"$tmp_cfg"
  samba_put "$tmp_cfg" "$REMOTE/config.yaml"
  rm -f "$tmp_cfg"

  samba_put "$ROOT/addon/Dockerfile" "$REMOTE/Dockerfile"
  samba_put "$ROOT/addon/CHANGELOG.md" "$REMOTE/CHANGELOG.md"
  samba_put "$ROOT/addon/DOCS.md" "$REMOTE/DOCS.md"
  samba_put "$ROOT/addon/VERSION" "$REMOTE/VERSION"
  samba_put "$ROOT/addon/icon.png" "$REMOTE/icon.png"
  samba_put "$ROOT/addon/logo.png" "$REMOTE/logo.png"
  samba_put "$ROOT/addon/translations/en.yaml" "$REMOTE/translations/en.yaml"

  samba_put "$dist/remote-screens-server.jar" "$REMOTE/dist/remote-screens-server.jar"
  if [ -f "$dist/apk/remote-screens.apk" ]; then
    samba_put "$dist/apk/remote-screens.apk" "$REMOTE/dist/apk/remote-screens.apk"
    samba_put "$dist/apk/version.json" "$REMOTE/dist/apk/version.json"
  fi
  samba_put "$dist/static/index.html" "$REMOTE/dist/static/index.html"
  local asset
  for asset in "$dist/static/assets"/*; do
    [ -f "$asset" ] || continue
    samba_put "$asset" "$REMOTE/dist/static/assets/$(basename "$asset")"
  done
  echo "Upload complete."
}

rebuild() {
  local want=0
  case "$REBUILD_MODE" in
    on) want=1 ;;
    off) want=0 ;;
    auto)
      if [ -n "${HA_TOKEN:-}" ]; then
        want=1
      fi
      ;;
  esac
  if [ "$want" -ne 1 ]; then
    echo "Skipping rebuild (set HA_TOKEN for auto-rebuild, or pass --rebuild)."
    return 0
  fi
  if [ -z "${HA_TOKEN:-}" ]; then
    echo "HA_TOKEN is not set; cannot rebuild. Rebuild ${ADDON_SLUG} in Settings → Add-ons." >&2
    exit 1
  fi
  local base=${HA_URL:-http://${HA_SAMBA_HOST}:8123}
  base=${base%/}
  echo "Rebuilding ${ADDON_SLUG} via ${base} …"
  local code
  code=$(curl -sS -o /tmp/rs-ha-rebuild.json -w '%{http_code}' \
    -X POST \
    -H "Authorization: Bearer ${HA_TOKEN}" \
    -H "Content-Type: application/json" \
    "${base}/api/hassio/addons/${ADDON_SLUG}/rebuild")
  if [ "$code" != "200" ] && [ "$code" != "201" ] && [ "$code" != "202" ]; then
    echo "Rebuild failed (HTTP ${code}):" >&2
    cat /tmp/rs-ha-rebuild.json >&2 || true
    exit 1
  fi
  echo "Rebuild requested (HTTP ${code}). Check Supervisor logs for ${ADDON_SLUG}."
}

load_env
PY=$(resolve_python)
SAMBA=$(resolve_samba)

if [ "$UPLOAD_ONLY" -eq 0 ]; then
  package
else
  echo "Skipping package (--upload-only)."
fi

upload
rebuild
echo "Done. Open Settings → Add-ons → ${DEV_NAME} (slug ${ADDON_SLUG})."
