# RomRunner

A native Android (Kotlin + Jetpack Compose) ROM-folder game launcher, similar in spirit to Daijishō
or Beacon: you point it at the single folder that holds your games, it recursively finds everything
it recognizes and lists it as a dense, text-based list, and tapping a game hands the file off to
whichever emulator app is configured for that platform.

RomRunner does not include, download, or scrape any ROMs or emulators. It only launches apps you've
already installed, using files you've already put on your device.

## Requirements to build

- Android Studio (Koala/2024.1 or newer recommended), which bundles a compatible JDK, **or**
- A standalone JDK 17+ and the Android SDK, with Gradle available via the included wrapper
  (`./gradlew`).

Open this folder as a project in Android Studio and let it sync.

## Project layout

```
app/src/main/java/com/noryan/romrunner/
  data/model/       Room entities: Platform (a file-type/emulator mapping) and Game (a scanned ROM)
  data/dao/         Room DAOs
  data/db/          Room database
  data/repository/  Thin repository wrapping the DAOs + settings
  data/settings/    SharedPreferences-backed settings (ROMs folder, background-app cleanup, etc.)
  data/scanner/     Storage Access Framework folder walking + real-path recovery
  data/launch/      Builds the Intent that launches a ROM in an emulator, default platform seeds,
                    per-title launch overrides, and background-app cleanup before launching
  ui/               Compose screens: Library (the shelf), Platforms (Settings), Apps (app drawer)
```

## How it works

1. On first launch, RomRunner asks for **one** root folder — the top-level folder that holds your
   ROMs/games. Pick it once via the system folder picker; there's no per-platform folder setup.
2. RomRunner recursively walks that whole folder tree looking for files matching any configured
   platform's extensions, and lists everything it finds as one flat list. A starter set of common
   systems is pre-configured, each with a well-known external emulator app as its default — see
   [DefaultPlatforms.kt](app/src/main/java/com/noryan/romrunner/data/launch/DefaultPlatforms.kt)
   for exactly what's included, which app each one launches by default, and why some common
   extensions (`.bin`/`.cue`, `.chd`, `.nsp`, ...) are deliberately left out of the starter set.
3. Tap a game to launch it in its platform's configured emulator app. If that app isn't installed,
   RomRunner offers to open its Play Store page (or a web search) or lets you pick a different
   installed app instead — that choice is remembered for the platform going forward.
4. Long-press a game for play/favorite/rename/remove.
5. Pull to refresh any time to rescan the root folder for newly added ROMs.

In **Settings**, you can change the ROMs folder, toggle closing background apps before launching
(frees memory on constrained devices), and toggle filling a connected second display (e.g. a
dual-screen handheld) with RomRunner's own logo.

## Known limitations / next steps

- **No in-app platform editor.** Which file extensions map to which platform, and which app plays
  each one, come entirely from the seeded defaults (`DefaultPlatforms.kt`) plus whatever you pick
  via the "install this app?" / "choose a different app" flow when a default app isn't installed —
  there's currently no UI to add a new platform or hand-edit an existing one's extensions.
- **Extension ambiguity.** Several common optical-disc formats (`.bin`/`.cue`, `.chd`, `.wbfs`,
  `.nsp`, ...) are shared across multiple systems, so they're intentionally left out of the default
  seed to avoid mis-categorizing files.
- **Real file paths** are only recoverable for folders on the primary shared storage volume (or a
  best-effort guess for SD cards) — some emulators work much more reliably with real paths than
  with `content://` URIs.
- The UI is intentionally text-first (no cover art/box-art grid) — dense list rows rather than
  image tiles, matching the target UI direction.

## Other builds

A separate, full-featured build with 8 game-console emulator cores running embedded in-process
(no separate app installs needed) lives at `../romrunner-builtinemulators` — a frozen snapshot
taken before this build was stripped down to external-apps-only.
