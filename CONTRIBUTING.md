# Contributing

## Prerequisites

- JDK **21**
- Android SDK (player app)
- Android CLI (`android`), dev channel. `./scripts/install-android-cli.sh` installs it to `~/.local/bin`.
  Agents in this repo follow `.cursor/skills/android-cli` (same text as `.agents/skills/android-cli`).
- Node **24** (editor)
- Python **3.13** (integration tests, catalogue tools)

Player work can use the Android skills in `.cursor/skills/` (the same copies are in `.agents/skills/`): `android-permissions-security`, `android-intent-security`, `anr-audit-and-fix`, `jetpack-compose-performance`, and `android-profiler`.

## Day-to-day

```sh
./gradlew :shared:test :server:test
./gradlew :server:installDist
./gradlew :player:assembleDebug
cd editor && npm ci && npm test && npm run build
python -m pip install -r requirements_test.txt && python -m pytest
```

Run without Home Assistant using the [demo home](docs/DEMO.md). Retake docs images with
[docs/SCREENSHOTS.md](docs/SCREENSHOTS.md).

## Server UI previews

Build `:preview:assembleDebug`, then open a preview file under `preview/src/main/kotlin` in Android Studio
and select **Split** or **Design**. The previews use server-generated documents and local demo data;
no running server or Home Assistant connection is needed.

```sh
./gradlew :preview:assembleDebug
./gradlew :preview:assembleDebug -PpreviewFilter=climate
```

Rebuild after editing server renderers or fixtures, then refresh the preview. Build without `previewFilter`
to restore the full gallery. See [the preview guide](preview/README.md) for gallery files, fixture locations,
coverage checks and limitations.

Before submitting rendering changes, run the checks in [Testing](docs/TESTING.md), inspect relevant light/dark
previews, and verify interactive behavior on a device. The server's standard Detekt task includes the
`src/preview` source set.

## Catalogue

Domain tiles and cards are YAML under `catalogue/`. Regenerate Kotlin/TS with
`tools/generate_catalogue.py` after edits.

## Releases

1. Run [the release workflow](.github/workflows/addon.yml) from **Actions → Release → Run workflow** on
   `main` and enter the new version (e.g. `0.1.14`). You do not need to bump version files first.
2. The workflow tags `v<version>` on the current `main` tip, builds the signed player app (stamping that
   version into the build), packages the add-on, pushes
   `ghcr.io/<owner>/remote-screens-{amd64,aarch64}`, and attaches the APK to the GitHub release.
3. When `main` still has an older `addon/VERSION` / `config.yaml`, it opens a sync PR so those files (and a
   changelog stub, if missing) match the tag. Merge that PR so the repo stays aligned with the published
   add-on. Optionally edit the changelog in that PR for curated notes next time.

You can still push tag `v<version>` yourself; the same build runs, and a sync PR is opened if needed.

For curated GitHub release notes, add a `## <version>` section to `addon/CHANGELOG.md` on `main` before
step 1; otherwise the release uses GitHub’s generated notes.

Tablets only accept an update signed with the key the app was first installed with, so every release must use
the same key. The workflow reads it from these repository secrets:

| Secret | What |
| ------ | ---- |
| `PLAYER_KEYSTORE_BASE64` | The keystore, base64 encoded |
| `PLAYER_KEYSTORE_PASSWORD` | Its password |
| `PLAYER_KEY_ALIAS` | The key's alias |
| `PLAYER_KEY_PASSWORD` | The key's password |

## Docs

- User guide in-repo: [`addon/DOCS.md`](addon/DOCS.md) (also shown in the Supervisor add-on panel)
- Deep guide: [GitHub Wiki](https://github.com/Shaffer-Softworks/remote-screens/wiki)
- Remote Compose notes: [`docs/REMOTE_COMPOSE.md`](docs/REMOTE_COMPOSE.md)
- Notifications: [`docs/NOTIFICATIONS.md`](docs/NOTIFICATIONS.md)
- Intercom: [`docs/INTERCOM.md`](docs/INTERCOM.md)
- ADB tasks and editor tablet management: [`docs/ADB.md`](docs/ADB.md)
- Screen orientation and large-tablet wake: [`docs/ORIENTATION.md`](docs/ORIENTATION.md)
- Verification: [`docs/TESTING.md`](docs/TESTING.md)
- Server UI previews: [`preview/README.md`](preview/README.md)

The wiki is a separate Git repository (`https://github.com/Shaffer-Softworks/remote-screens.wiki.git`).
When changing user-facing behavior, update the relevant repository guide and wiki page together, including
Home and sidebar links for new pages. Keep notification and intercom wiki guides aligned with their `docs/`
counterparts. Validate relative links in the repository and page links in the wiki before publishing.
