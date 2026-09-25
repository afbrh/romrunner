# GameShelf

A native Android (Kotlin + Jetpack Compose) ROM-folder game launcher, similar in spirit to Daijishō
or Beacon: you point it at the single folder that holds your games, it recursively finds everything
it recognizes and lists it as a dense, text-based list, and tapping a game hands the file off to
whatever emulator app you've configured for that file type.

GameShelf does not include, download, or scrape any ROMs or emulators. It only launches apps you've
already installed, using files you've already put on your device.

## Requirements to build

This project was scaffolded on a machine with no JDK or Android SDK installed, so it has **not**
been compiled yet. To build and run it you'll need:

- Android Studio (Koala/2024.1 or newer recommended), which bundles a compatible JDK, **or**
- A standalone JDK 17+ and the Android SDK (compileSdk 34) with Gradle available via the included
  wrapper (`./gradlew`).

Open the `GameShelf/` folder as a project in Android Studio and let it sync — that will surface any
dependency-version mismatches (Kotlin/AGP/Compose versions drift occasionally; Studio's "Upgrade
Assistant" can bump them if a sync error suggests it).

## Project layout

```
app/src/main/java/com/noryan/gameshelf/
  data/model/       Room entities: Platform (a file-type/emulator mapping) and Game (a scanned ROM)
  data/dao/         Room DAOs
  data/db/          Room database
  data/repository/  Thin repository wrapping the DAOs + the ROMs-folder setting
  data/settings/    Single-value setting: the ROMs root folder Uri
  data/scanner/     Storage Access Framework folder walking + real-path recovery
  data/launch/      Builds the Intent that launches a ROM in an emulator, default platform seeds,
                    and emulator presets
  ui/               Compose screens: Library (the shelf), Platforms (Settings: folder + emulators)
```

## How it works

1. On first launch, GameShelf asks for **one** root folder — the top-level folder that holds your
   ROMs/games. Pick it once via the system folder picker; there's no per-platform folder setup.
2. GameShelf recursively walks that whole folder tree looking for files matching any configured
   platform's extensions, and lists everything it finds as one flat list (no platform tabs/grouping).
   A starter set of common systems (GBA, GB/GBC, NES, SNES, N64, NDS, 3DS, Genesis, PSP, etc.) is
   pre-configured so this works immediately — see [DefaultPlatforms.kt](app/src/main/java/com/noryan/gameshelf/data/launch/DefaultPlatforms.kt)
   for exactly what's included and why some common extensions (`.iso`, `.bin`/`.cue`, `.chd`, ...)
   are deliberately left out.
3. In **Settings** (gear icon), you can change the ROMs folder, and add/edit/remove platforms —
   each platform is just "these extensions" + "how to launch them": pick a preset (PPSSPP, Dolphin,
   DraStic, or a generic "let Android choose" option) or fill in a package name / activity / extra
   intent params yourself. Advanced options support custom intent extras with `{FILE_PATH}` /
   `{FILE_URI}` placeholders, for emulators (like RetroArch) that need more than a plain "open this
   file" intent. Saving a platform immediately re-scans the root folder, so newly-recognized
   extensions show up right away.
4. Back on the shelf, tap a game to launch it. Tap the star to favorite it inline, or use the
   overflow (⋮) menu — or a long-press — for play/favorite/rename/remove.
5. Hit refresh any time to rescan the root folder for newly added ROMs.

## Known limitations / next steps

- **Extension ambiguity.** Several common optical-disc formats (`.iso`, `.bin`/`.cue`, `.chd`,
  `.wbfs`, `.nsp`, ...) are shared across multiple systems (PS1/PS2/PSP/Wii/GameCube/Switch/etc.),
  so they're intentionally left out of the default seed to avoid mis-categorizing files. Add them
  yourself as a platform in Settings if your library uses them.
- **Emulator intents vary a lot.** The presets are a best-effort starting point; exact package names
  and required extras differ across emulator versions and forks. If a preset doesn't launch, use
  "Show advanced options" to inspect/adjust the package, action, MIME type, and extras.
- **Real file paths** are only recoverable for folders on the primary shared storage volume (or a
  best-effort guess for SD cards) — some emulators (RetroArch in particular) work much more
  reliably with real paths than with `content://` URIs.
- The UI is intentionally text-first (no cover art/box-art grid) — dense list rows rather than
  image tiles, matching the target UI direction.
- No installed-app scanning or manual web/cloud-gaming shortcuts — this build is scoped to
  ROM-folder scanning only, per the current requirements.
