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
  version_code=$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' "$root/player/build.gradle.kts" | head -1)
  version_name=$(cat "$root/addon/VERSION")
  printf '{"versionCode": %s, "versionName": "%s"}\n' "$version_code" "$version_name" > "$dist/apk/version.json"
fi
echo "addon/dist ready"
