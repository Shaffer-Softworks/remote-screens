# Contributing

## Prerequisites

- JDK **21**
- Android SDK (player app)
- Node **24** (editor)
- Python **3.13** (integration tests, catalogue tools)

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

GitHub Actions workflow [`.github/workflows/addon.yml`](.github/workflows/addon.yml): **Actions → Release → Run
workflow** on `main` with the new version. Player signing secrets are listed in the README.

## Docs

- User guide in-repo: [`addon/DOCS.md`](addon/DOCS.md) (also shown in the Supervisor add-on panel)
- Deep guide: [GitHub Wiki](https://github.com/Shaffer-Softworks/remote-screens/wiki)
- Remote Compose notes: [`docs/REMOTE_COMPOSE.md`](docs/REMOTE_COMPOSE.md)
- Notifications: [`docs/NOTIFICATIONS.md`](docs/NOTIFICATIONS.md)
- Intercom: [`docs/INTERCOM.md`](docs/INTERCOM.md)
- ADB tasks and editor tablet management: [`docs/ADB.md`](docs/ADB.md)
- Verification: [`docs/TESTING.md`](docs/TESTING.md)
- Server UI previews: [`preview/README.md`](preview/README.md)

The wiki is a separate Git repository (`https://github.com/Shaffer-Softworks/remote-screens.wiki.git`).
When changing user-facing behavior, update the relevant repository guide and wiki page together, including
Home and sidebar links for new pages. Keep notification and intercom wiki guides aligned with their `docs/`
counterparts. Validate relative links in the repository and page links in the wiki before publishing.
