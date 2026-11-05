# Server UI previews in Android Studio

Build `:preview:assembleDebug`, then open a file under
[src/main/kotlin/tech/shaffersoftworks/remotescreens/preview](src/main/kotlin/tech/shaffersoftworks/remotescreens/preview)
and switch to **Split** or **Design**. No server, Home Assistant, emulator, or network connection is needed
after dependencies are downloaded.

```sh
./gradlew :preview:assembleDebug
```

After changing server rendering code or fixtures, run that task again and refresh the preview.
A normal preview refresh only recompiles the Android preview file; it does not regenerate JVM documents.

## Where to look

| File | Content |
| --- | --- |
| [ServerPreviews.kt](src/main/kotlin/tech/shaffersoftworks/remotescreens/preview/ServerPreviews.kt) | Demo screens and an AndroidX tooling comparison |
| [NavigationPreviews.kt](src/main/kotlin/tech/shaffersoftworks/remotescreens/preview/NavigationPreviews.kt) | Menu drawer key on each side and without the top or bottom bar |
| [TilesPreviews.kt](src/main/kotlin/tech/shaffersoftworks/remotescreens/preview/TilesPreviews.kt) | Every catalogue tile in every advertised size |
| [CardsPreviews.kt](src/main/kotlin/tech/shaffersoftworks/remotescreens/preview/CardsPreviews.kt) | Every entity card, plus history and energy |
| [PanelsPreviews.kt](src/main/kotlin/tech/shaffersoftworks/remotescreens/preview/PanelsPreviews.kt) | Full-screen panels, selected camera, music modes, home with a person photo |
| [PeoplePreviews.kt](src/main/kotlin/tech/shaffersoftworks/remotescreens/preview/PeoplePreviews.kt) | Photo and initials avatars at compact and tall tile sizes |
| [ComponentsPreviews.kt](src/main/kotlin/tech/shaffersoftworks/remotescreens/preview/ComponentsPreviews.kt) | Every free-form container and leaf |
| [WidgetsPreviews.kt](src/main/kotlin/tech/shaffersoftworks/remotescreens/preview/WidgetsPreviews.kt) | Shared controls, icons, spinner and typography |
| [DialogsPreviews.kt](src/main/kotlin/tech/shaffersoftworks/remotescreens/preview/DialogsPreviews.kt) | Light control modes, effects, alarm notice |
| [AlertsPreviews.kt](src/main/kotlin/tech/shaffersoftworks/remotescreens/preview/AlertsPreviews.kt) | All severities, with and without sample camera imagery |
| [StatesPreviews.kt](src/main/kotlin/tech/shaffersoftworks/remotescreens/preview/StatesPreviews.kt) | Entity on-states, off/unknown/unavailable, long labels, empty panels, calendar loading/errors |
| [CardStatesPreviews.kt](src/main/kotlin/tech/shaffersoftworks/remotescreens/preview/CardStatesPreviews.kt) | Card variants for entity on-states and off/unknown/unavailable |
| [SizePreviews.kt](src/main/kotlin/tech/shaffersoftworks/remotescreens/preview/SizePreviews.kt) | Portrait and larger tablet canvases |

Every scenario has dark and light variants. Names in the parameter previews identify the type, size and state.
The catalogue currently produces 1,330 documents; the count grows as scenarios are added. Open one category file at a time. To focus the gallery,
build with a case-insensitive substring matching its group or label:

```sh
./gradlew :preview:assembleDebug -PpreviewFilter=climate
./gradlew :preview:assembleDebug -PpreviewFilter=people
./gradlew :preview:assembleDebug -PpreviewFilter="Light ·"
```

Build again without `previewFilter` to restore the complete gallery. Rendering and coverage validation still run
for the full catalogue when filtering; only the Android preview index is filtered.

## Person photos and initials

Open [PeoplePreviews.kt](src/main/kotlin/tech/shaffersoftworks/remotescreens/preview/PeoplePreviews.kt).
It provides **Photo** and **Initials** cases at 1×1, 2×1 and 2×2 tile sizes, in both themes.
Both surfaces have a one-physical-pixel black ring. The normal demo person has no photo, so its standard
tile uses initials. To inspect the photo in a panel, select **Home · person photo** in `PanelsPreviews.kt`.

A missing ring after a server-side edit may indicate stale generated documents. Rebuild
`:preview:assembleDebug`, then refresh the preview. The photo fixtures use synthetic local artwork.

## How it works

The server's separate `src/preview` JVM source set uses the production renderers and `DemoHome` data.
`:server:generatePreviews` writes documents, a Kotlin preview index, and `coverage.txt` under
`preview/build/generated/serverPreviews`. These build outputs are not committed.
The preview module reads the generated assets. Its build runs generation automatically.

The snapshot adapter bakes named text, integer, float and color values into initial document constants,
including sample clock and battery values normally supplied by the player. Pictures use visibly synthetic,
local artwork. The document generator parses every result back through Remote Compose to catch malformed output.
Coverage checks require a fixture for every `NodeTypes.ALL` member and cover every catalogue tile size and card.
The snapshot adapter also has unit tests under `server/src/test`.

The main previews use the same native `RemoteComposePlayer` as the Android player app.
The official `remote-tooling-preview` library is included, and `ToolingLibraryPreview` uses its
`RemoteDocumentPreview` API. In alpha22 that renderer omitted canvas icons during verification, so it is
kept as a comparison rather than used for the main gallery. The native view must receive its document in
`AndroidView.factory` for canvas paths to initialize reliably in Layoutlib.

## Coverage limits

Every catalogue tile and advertised size, entity card type, and free-form component type has a scenario.
The gallery also includes major panels, selected dialogs, shared widgets, alert severities, entity state
variants, empty data and long labels. Most scenarios use an 800×480 mdpi canvas, with separate portrait and
large-tablet examples.

The registry checks do not prove every rendering branch is exercised. Additional component property
combinations, transient dialogs, custom themes, densities, languages and live overlays need their own cases.
Generated documents are parsed for validity, and representative previews are checked visually; the gallery
does not automatically compare screenshots for every scenario.

## Extending coverage

- New catalogue types and advertised sizes automatically join the tile gallery. Give them realistic demo entities
  and options in `CataloguePreviews.kt`; unknown domains otherwise receive a synthetic entity.
- Add an explicit binding/fixture in `PreviewFixtures.kt` for each new free-form node type.
  Generation fails when that registry and `NodeTypes.ALL` differ.
- Add transient panel states and dialogs in `PanelPreviews.kt`, and screen configurations in `ScreenPreviews.kt`.
- Shared widget examples live in `WidgetPreviews.kt`.

A state layout paints its first state in Layoutlib's single frame whatever its starting value, then switches
on the next. So **Screens** cases for pages other than the first show the first page's picture (their layout
tree is right). The modal drawer is native Material 3 UI in the Android player, so its open-state preview is
`PlayerNavigationDrawerPreview` in `player/.../ui/screens/player/PlayerNavigationDrawer.kt`.
The server gallery previews the drawer’s menu key.

Previews are snapshots. They do not execute Home Assistant actions, simulate navigation, play video/audio,
or mount Android map/search overlays. Camera, map and artwork areas show sample bitmaps.
Some time-dependent server fixtures use the generation date; regenerate to update those.
The development host requires API 29 because of the tooling library; the production player's minimum is unchanged.

## Verification

```sh
./gradlew :server:detekt :server:test :shared:test :player:testDebugUnitTest
./gradlew :player:assembleDebug :preview:assembleDebug :player:lintDebug :preview:lintDebug
```

`:server:detekt` also runs `:server:detektPreview`, covering the custom JVM preview source set.
