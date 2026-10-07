# Remote Compose: what this project leans on

Remote Screens builds its screens with `androidx.compose.remote` **1.0.0-alpha20**. The library is alpha, so this
page records the parts of its API we depend on, what the phase 0 spike proved, and the workarounds. When the
library is upgraded, check each item here.

All creation calls go through `server/.../render/Rc.kt` and the tile renderers; all player calls go through
`player/.../RcHost.kt`.

## Creation on the JVM

- `remote-creation-jvm` brings `JvmRcPlatformServices`, so the add-on builds documents without Android.
- The profile is `PROFILE_ANDROIDX | PROFILE_EXPERIMENTAL` at `CoreDocument.DOCUMENT_API_LEVEL`, with a plain
  `RemoteComposeWriter` as the factory, and the same value in the `DOC_PROFILES` header. Long press
  (`MODIFIER_MULTI_CLICK`, operation 83) and `Flow` are experimental in alpha20; without the flag the writer throws
  "Operation 83 is not supported for this version".
- **Declarations inside a layout component are lost.** A `remoteNamedText` (or colour, path, text) declared inside
  a `Box`'s content is written, but `CoreDocument` drops it when it reads the document back. `DocContext.build`
  therefore builds every document twice: a dry run records what the content declares, and the real build declares
  all of it at the top before the layout. `NestingProbeTest` fails the day the library stops losing them.
- **Paths.** `JvmRcPlatformServices.pathToFloatArray` accepts only its own `RemotePath`, which is not the
  `RcPathArrayCreator` that `remotePathData` takes, so every path fails with "Invalid path data". `Rc` wraps the
  platform services to accept any `RcPathArrayCreator`. The library's SVG parsers know neither relative commands
  nor `V`/`T` nor numbers like `1-2.5.5`, so `tools/generate_icons.py` rewrites every icon to absolute `M L C Z`
  and `Rc.path` reads that into a core `RemotePathBase` itself.
- `CoreDocument.width` is the measured root layout's width, so it is 0 for a document read on the JVM.
- The Kotlin DSL (`createRcBuffer { Box { Text(...) } }`, `Modifier.size/padding/background/onClick`) is marked
  `@RestrictTo(LIBRARY_GROUP)`. That is a lint rule for Android; on the JVM it compiles, and we accept the risk.
- Documents are built in **pixels** for the device that shows them (the legacy density behaviour). The add-on
  knows each player's size and density from its hello, and converts dp with `Units` in the document builder.

## Live values: named variables

- A document declares `remoteNamedText/Integer/Color("USER:<name>", initial)`.
- The player overrides it with `setUserLocalString/Int/Color("<name>", value)`; the player adds the `USER:` prefix.
- An override is sticky until cleared, so the document never sets these itself: every change of a named
  variable comes from the add-on (a state change) or the player (a prediction, a page change).
- `remoteThemedColor(Int, Int)` in alpha20 passes the colours as ids. Don't use it; the add-on picks the colour of
  the screen's theme itself and sends it as a named colour.

## Taps: host actions with metadata

- `Modifier.onClick { hostAction(id, metadataText) }` reaches the player's `addIdActionListener { id, metadata -> }`.
- The metadata is a static text `tile|verb|arg` (`ActionRef` in `shared`). A slider is a row of steps, each its
  own action with its value, rather than a drag with a dynamic value.
- `addIdActionListener` can only be added after a document is set, so the player adds it on every new document.

## Pages

- One `StateLayout` driven by the named integer `USER:page` holds every page of a screen. The player changes the
  page with `setUserLocalInt("page", i)`, from a page dot (a local host action) or a swipe it detects itself.

## Pictures

- A named bitmap (`remoteNamedBitmap("USER:pic.<id>", placeholder)`) reserves a picture in the document; the
  player swaps it with `setUserLocalBitmap`. Camera snapshots, album art and map tiles travel as `bitmap` messages.

## What the spike proved, and how

- `RcSpikeTest` (JVM): a document with named strings, ints and colours, a `StateLayout` and a click with host
  action metadata is written, read back by `CoreDocument`, and lists its named variables.
- On a device: the player renders the demo home, a tap reaches the add-on and the change shows (see
  docs/TESTING.md, "On a device").
