package com.noryan.romrunner.data.launch

import android.os.Build
import com.noryan.romrunner.data.model.Game
import com.noryan.romrunner.data.model.Platform

data class RecommendedEmulator(
    /** Shown in the row's left column — usually a fixed system name ("3DS"), but a specific game
     *  title for a per-title override ("Metroid Prime Trilogy"), or computed from which of several
     *  systems actually have games for a multi-system app like RetroArch ("GameBoy / NES"). */
    val rowLabel: (platforms: List<Platform>, games: List<Game>) -> String,
    val appLabel: String,
    val packageName: String,
    /** Fallback the user lands on if [resolveApkUrl] can't find a matching asset (or there's no
     *  download API available for this app at all — it just always returns null in that case). */
    val releasesPageUrl: String,
    /** Resolves this release's current APK download URL, or null if none could be found/this app
     *  has no automatable download source at all. Blocking — always called on Dispatchers.IO. */
    val resolveApkUrl: () -> String?,
    /** Whether this row should show at all, given the platforms/games actually in the library. */
    val isNeeded: (platforms: List<Platform>, games: List<Game>) -> Boolean
)

// ARMSX2 ships one .apk per Android-version tier (sdk30/33/35) rather than one universal .apk —
// pick the highest tier this device's own SDK_INT actually qualifies for.
private val armsx2AssetMatcher: (String) -> Boolean = { name ->
    val tier = when {
        Build.VERSION.SDK_INT >= 35 -> "sdk35"
        Build.VERSION.SDK_INT >= 33 -> "sdk33"
        else -> "sdk30"
    }
    name.endsWith(".apk") && tier in name
}

private fun hasGameOn(platformName: String): (List<Platform>, List<Game>) -> Boolean = { platforms, games ->
    val platformId = platforms.find { it.name == platformName }?.id
    platformId != null && games.any { it.platformId == platformId }
}

/** Fixed, platform-independent row label — the common case for every entry except RetroArch's. */
private fun fixedLabel(label: String): (List<Platform>, List<Game>) -> String = { _, _ -> label }

// Every system that plays through the same RetroArch app (the PS1/N64 era and earlier, plus other
// retro handhelds), and the short name each shows as in the row label when it's one of the
// systems actually present in the library.
private val RETROARCH_SYSTEM_LABELS = linkedMapOf(
    "GameBoy (Color + Advance)" to "GameBoy",
    "NES" to "NES",
    "SNES" to "SNES",
    "Nintendo 64" to "N64",
    "Virtual Boy" to "Virtual Boy",
    "PlayStation" to "PS1",
    "Sega Genesis" to "Genesis",
    "Sega Master System" to "Master System",
    "Sega Game Gear" to "Game Gear",
    "Sega 32X" to "32X",
    "PC Engine / TurboGrafx-16" to "PC Engine",
    "Atari 2600" to "Atari 2600",
    "Atari 7800" to "Atari 7800",
    "Atari Lynx" to "Lynx",
    "Neo Geo Pocket" to "Neo Geo Pocket",
    "WonderSwan" to "WonderSwan"
)

val RECOMMENDED_EMULATORS = listOf(
    RecommendedEmulator(
        rowLabel = fixedLabel("3DS"),
        appLabel = "Azahar",
        packageName = "org.azahar_emu.azahar",
        releasesPageUrl = "https://github.com/azahar-emu/azahar/releases",
        // "vanilla" (not "googleplay") is the sideload-capable build — same flavor pick this
        // project already made for the formerly-embedded Azahar core.
        resolveApkUrl = {
            LatestReleaseFinder.findStableAssetUrl("https://api.github.com/repos/azahar-emu/azahar/releases") { name ->
                name.endsWith(".apk") && "vanilla" in name
            }
        },
        isNeeded = hasGameOn("Nintendo 3DS")
    ),
    RecommendedEmulator(
        rowLabel = fixedLabel("PS2"),
        appLabel = "ARMSX2",
        packageName = "com.armsx2",
        releasesPageUrl = "https://github.com/ARMSX2/ARMSX2/releases",
        resolveApkUrl = {
            LatestReleaseFinder.findStableAssetUrl("https://api.github.com/repos/ARMSX2/ARMSX2/releases", armsx2AssetMatcher)
        },
        isNeeded = hasGameOn("PlayStation 2")
    ),
    RecommendedEmulator(
        rowLabel = fixedLabel("Switch"),
        appLabel = "Eden",
        packageName = "dev.eden.eden_emulator",
        releasesPageUrl = "https://git.eden-emu.dev/eden-emu/eden/releases",
        // Eden's maintainers moved off GitHub entirely to self-hosted infrastructure, which
        // exposes the same GitHub-compatible Releases API shape.
        // "standard" is Eden's generic build; "optimized" needs newer-CPU-specific instructions
        // not guaranteed on every device, and "chromeos"/"legacy" aren't the right pick either.
        resolveApkUrl = {
            LatestReleaseFinder.findStableAssetUrl("https://git.eden-emu.dev/api/v1/repos/eden-emu/eden/releases") { name ->
                name.endsWith(".apk") && "standard" in name
            }
        },
        isNeeded = hasGameOn("Nintendo Switch")
    ),
    RecommendedEmulator(
        rowLabel = fixedLabel("Nintendo DS"),
        appLabel = "MelonDS",
        // The app published under this package is WatermelonDS, a melonDS-android fork.
        packageName = "me.magnum.melondualds",
        releasesPageUrl = "https://github.com/SapphireRhodonite/WatermelonDS/releases",
        resolveApkUrl = {
            LatestReleaseFinder.findStableAssetUrl("https://api.github.com/repos/SapphireRhodonite/WatermelonDS/releases") { name ->
                name.endsWith(".apk")
            }
        },
        isNeeded = hasGameOn("Nintendo DS")
    ),
    RecommendedEmulator(
        rowLabel = fixedLabel("GameCube / Wii"),
        appLabel = "Dolphin",
        packageName = "org.dolphinemu.dolphinemu",
        // Dolphin isn't distributed via GitHub Releases at all (confirmed: the repo has none) —
        // it's built on its own buildbot with no stable, parseable "latest Android APK" URL, so
        // this always falls back to the download page rather than guessing one.
        releasesPageUrl = "https://dolphin-emu.org/download/",
        resolveApkUrl = { null },
        // Needed only for a GameCube/Wii game that ISN'T Metroid Prime Trilogy — that one routes
        // to PrimeHack instead (see the entry below and GameLaunchOverrides.kt).
        isNeeded = { platforms, games ->
            val platformId = platforms.find { it.name == "GameCube / Wii" }?.id
            platformId != null && games.any {
                it.platformId == platformId && !it.title.contains("metroid prime trilogy", ignoreCase = true)
            }
        }
    ),
    RecommendedEmulator(
        rowLabel = fixedLabel("Metroid Prime Trilogy"),
        appLabel = "PrimeHack",
        packageName = "org.dolphinemu.primehack",
        releasesPageUrl = "https://github.com/Starlightbotanist/PrimeHack-Android/releases",
        resolveApkUrl = {
            LatestReleaseFinder.findStableAssetUrl("https://api.github.com/repos/Starlightbotanist/PrimeHack-Android/releases") { name ->
                name.endsWith(".apk")
            }
        },
        isNeeded = { _, games -> games.any { it.title.contains("metroid prime trilogy", ignoreCase = true) } }
    ),
    RecommendedEmulator(
        rowLabel = fixedLabel("Wii U"),
        appLabel = "Cemu",
        packageName = "info.cemu.cemu",
        releasesPageUrl = "https://github.com/SapphireRhodonite/Cemu/releases",
        resolveApkUrl = {
            LatestReleaseFinder.findStableAssetUrl("https://api.github.com/repos/SapphireRhodonite/Cemu/releases") { name ->
                name.endsWith(".apk")
            }
        },
        isNeeded = hasGameOn("Wii U")
    ),
    RecommendedEmulator(
        rowLabel = fixedLabel("PSP"),
        appLabel = "PPSSPP",
        packageName = "org.ppsspp.ppsspp",
        // PPSSPP's GitHub releases carry no Android APK (confirmed: source/desktop/iOS only); the
        // official site hosts the stable Android build as a static file instead.
        releasesPageUrl = "https://www.ppsspp.org/download",
        resolveApkUrl = { LatestReleaseFinder.findPpssppStableApkUrl() },
        isNeeded = hasGameOn("PSP")
    ),
    RecommendedEmulator(
        rowLabel = fixedLabel("Dreamcast"),
        appLabel = "Flycast",
        packageName = "com.flycast.emulator",
        releasesPageUrl = "https://github.com/flyinghead/flycast/releases",
        resolveApkUrl = { LatestReleaseFinder.findStableAssetUrl("https://api.github.com/repos/flyinghead/flycast/releases") { name -> name.endsWith(".apk") } },
        isNeeded = hasGameOn("Dreamcast")
    ),
    RecommendedEmulator(
        // Lists just the systems actually present, e.g. "GameBoy" alone, or "GameBoy / NES" once
        // both have games — not a fixed label, since this one app covers five systems at once.
        rowLabel = { platforms, games ->
            RETROARCH_SYSTEM_LABELS.entries
                .filter { (platformName, _) -> hasGameOn(platformName)(platforms, games) }
                .joinToString(" / ") { it.value }
        },
        appLabel = "RetroArch",
        packageName = "com.retroarch",
        // Explicitly the web build per request, not the Play Store listing — RetroArch's GitHub
        // releases carry no Android APK either (source tarball only), so this resolves against
        // its own buildbot instead (see LatestReleaseFinder.findRetroArchStableApkUrl).
        releasesPageUrl = "https://www.retroarch.com/?page=platforms",
        resolveApkUrl = { LatestReleaseFinder.findRetroArchStableApkUrl() },
        isNeeded = { platforms, games -> RETROARCH_SYSTEM_LABELS.keys.any { hasGameOn(it)(platforms, games) } }
    )
)
