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
