#!/bin/sh
# Puts what the add-on image needs in addon/dist/: the server jar, the built editor and the player app.
#   tools/package_addon.sh [path/to/player.apk]
# Without an APK the image ships without one, and tablets are not offered updates.
set -eu
root=$(cd "$(dirname "$0")/.." && pwd)
dist="$root/addon/dist"
rm -rf "$dist"
mkdir -p "$dist/static" "$dist/apk"

(cd "$root" && ./gradlew :server:fatJar -q)
cp "$root/server/build/libs/remote-screens-server.jar" "$dist/"

(cd "$root/editor" && npm ci --silent && npm run build --silent)
cp -R "$root/editor/dist/." "$dist/static/"

apk="${1:-}"
if [ -n "$apk" ]; then
  cp "$apk" "$dist/apk/remote-screens.apk"
  # Only bundle mappings from the exact APK built in this checkout. Never attach a fresh
  # mapping to an unrelated/rebuilt APK, even when its version name is the same.
  built_apk="$root/player/build/outputs/apk/release/player-release.apk"
  mapping="$root/player/build/outputs/mapping/release/mapping.txt"
  if [ -f "$built_apk" ] && [ -f "$mapping" ] && cmp -s "$apk" "$built_apk"; then
    cp "$mapping" "$dist/apk/mapping.txt"
    unzip -p "$apk" assets/sentry-debug-meta.properties > "$dist/apk/sentry-debug-meta.properties"
    test -s "$dist/apk/sentry-debug-meta.properties"
    shasum -a 256 "$apk" | cut -d ' ' -f1 > "$dist/apk/mapping-apk.sha256"
  else
    echo "No matching release mapping; Sentry deobfuscation will be unavailable for this APK" >&2
  fi
  version_name=$(cat "$root/addon/VERSION")
  # The same sum as versionCode in player/build.gradle.kts.
  version_code=$(echo "$version_name" | awk -F'[.-]' '{ print $1 * 10000 + $2 * 100 + $3 }')
  # QR enrollment checks the signing certificate: its SHA-256, URL-safe Base64 without padding. Without apksigner
  # the add-on falls back to the APK's own checksum, which works until the next build.
  checksum=""
  apksigner=$(command -v apksigner || ls "${ANDROID_HOME:-$HOME/Library/Android/sdk}"/build-tools/*/apksigner 2>/dev/null | tail -1 || true)
  if [ -n "$apksigner" ]; then
    digest=$("$apksigner" verify --print-certs "$apk" 2>/dev/null | awk '/certificate SHA-256 digest/ { print $NF; exit }')
    if [ -n "$digest" ]; then
      checksum=$(echo "$digest" | xxd -r -p | base64 | tr '+/' '-_' | tr -d '=\n')
    fi
  else
    echo "apksigner not found: QR enrollment will check the APK itself" >&2
  fi
  printf '{"versionCode": %s, "versionName": "%s", "signatureChecksum": "%s"}\n' \
    "$version_code" "$version_name" "$checksum" > "$dist/apk/version.json"
fi
echo "addon/dist ready"
