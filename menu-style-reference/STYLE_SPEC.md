# RomRunner in-game menu style spec

The canonical look every embedded core's in-game pause/quick menu should match, and the mechanism
for keeping it that way.

## The look

- Panel: fixed **300dp wide**, full height, **black** background (`#FF000000`), anchored to the
  left edge of the screen, scrollable if content overflows.
- Rows: **left-justified, top-justified** — the first row sits at the top of the panel, each
  following row directly below it. Never vertically centered or spread out.
- Panel padding: **20dp horizontal, 28dp vertical** (space between the panel's edge and its first/
  last row and the sides of the text).
- Row padding: **12dp horizontal, 16dp vertical** (so consecutive rows sit 32dp apart, center to
  center, with no separate spacer between them).
- Font: Silkscreen (`silkscreen_regular.ttf`), **15sp**.
- Text color: white (`#FFFFFFFF`) normal, `#FFFF6B6B` for destructive rows (e.g. "Exit"/"Close
  Game"). A toggle row's value ("ON"/"OFF") is white when on, 50%-alpha white when off.
- No title/header row, no rounded corners.

## Source of truth

For the three Compose-based cores (ARMSX2, Cemu, WatermelonDS), this spec is **real, shared
code** — not just documentation. `GameShelf/menu-style-reference/MenuStyle.kt.template` and
`MenuComponents.kt.template` are the canonical files; `GameShelf/menu-style-reference/sync.sh`
copies them byte-for-byte (substituting only the package declaration) into:

- `armsx2-src/platforms/android/app/src/main/java/com/armsx2/ui/emulation/`
- `cemu-src/src/android/app/src/main/java/info/cemu/cemu/emulation/`
- `watermelonds-src/app/src/main/java/me/magnum/melonds/ui/emulator/ui/`

**To change the style:** edit the `.template` files, run `sync.sh`, rebuild RomRunner. Never edit
a synced copy directly — it'll just get overwritten next sync, and the 3 cores will drift apart.

The three View-based cores (PrimeHack, Azahar, Eden) render their menus via native Android Views
(`NavigationView`/`LinearLayout`), which can't consume Kotlin/Compose code — for those, this
document itself is the source of truth. Their current `styles.xml`/layout values are close to
this spec already (same font, same 15sp, same black background, same 300dp width) but not
pixel-identical (e.g. PrimeHack currently uses fixed 48dp-tall rows with 32dp horizontal padding
rather than this spec's auto-height/12h-16v rows) — deliberately left as-is for now rather than
risk changing 3 stable, working menus with no reported problem. If/when someone wants them
pixel-exact too, match the row/panel padding numbers above in each core's own
`res/values/styles.xml` / `res/values/dimens.xml`.

## Why not a real Gradle dependency

A genuinely shared Gradle library module (all 3 Compose cores depending on one real binary
artifact) was considered and rejected: RomRunner embeds each core via `includeBuild` composite
builds, and Gradle's `dependencySubstitution` is non-transitive per build — each core would need
to `includeBuild` the shared module itself, but Gradle composite builds flatten, and the same
included build reachable via three different sibling parents in one overall graph is a
confirmed-broken topology (real Gradle forum reports, plus open Gradle issues #2535/#11301 in this
exact area). The alternative fix (a local Maven-publish pipeline) trades that risk for a different
set of footguns — a manual re-publish step before a style change takes effect, plus version/cache
staleness — not proportionate for a handful of shared constants and three small composables. The
copy-via-script approach here has no such failure mode: what you see in each core's `MenuStyle.kt`
is exactly what's compiled, no separate publish/resolve step to forget.
