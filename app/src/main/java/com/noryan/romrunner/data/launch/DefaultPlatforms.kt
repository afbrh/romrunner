package com.noryan.romrunner.data.launch

import com.noryan.romrunner.data.model.Platform

/**
 * A starter set of common systems, seeded once so a freshly chosen ROMs folder shows results
 * right away without the user having to configure extensions by hand first.
 *
 * Deliberately sticks to file extensions that aren't shared across multiple common systems.
 * Still-ambiguous ones (`.bin`/`.cue`, `.chd`, `.nsp`, etc.) are left out; the user can add those as
 * their own platform in Settings, since extension-only auto-detection can't disambiguate them
 * reliably. `.iso` is included below despite being used by several optical-disc systems (PS2, Wii,
 * GameCube, ...) because it's common enough that leaving it out entirely does more harm than good;
 * it's mapped to a single platform here and can be renamed/reassigned in Settings if it's wrong for
 * a given library.
 *
 * A handful of platforms below have a default `launchPackage` filled in — well-known emulators
 * that most people setting up a system like this will already have (or can easily get). Everything
 * else is left blank on purpose: if a platform has no default app, tapping a game just falls back to
 * Android's normal "open with" chooser rather than triggering the missing-app prompt.
 */
object DefaultPlatforms {
    val ALL: List<Platform> = listOf(
        // No launchPackage: Game Boy, Game Boy Color, and Game Boy Advance are one combined
        // platform since all three are embedded via the same RetroArch mGBA core (see
        // data/embedded/RetroArchEmbeddedLauncher.kt) — no reason to make the user manage two
        // separate toggles for one emulator.
        Platform(name = "GameBoy (Color + Advance)", extensionsCsv = "gba,gb,gbc"),
        // No launchPackage: embedded via RetroArch's Nestopia core.
        Platform(name = "NES", extensionsCsv = "nes"),
        // No launchPackage: embedded via RetroArch's Snes9x core.
        Platform(name = "SNES", extensionsCsv = "sfc,smc"),
        // No launchPackage: embedded via RetroArch's Mupen64Plus-Next core.
        Platform(name = "Nintendo 64", extensionsCsv = "n64,z64"),
        Platform(name = "Nintendo DS", extensionsCsv = "nds"),
        // No launchPackage: 3DS emulation is embedded directly in RomRunner (see
        // data/embedded/AzaharEmbeddedLauncher.kt) rather than launched as an external app.
        Platform(name = "Nintendo 3DS", extensionsCsv = "3ds,cia,cci"),
        Platform(name = "Nintendo Switch", extensionsCsv = "xci", launchPackage = "dev.eden.eden_emulator"),
        // No launchPackage: Wii U emulation is embedded directly in RomRunner (see
        // data/embedded/CemuEmbeddedLauncher.kt) rather than launched as an external app.
        Platform(name = "Wii U", extensionsCsv = "wua"),
        // No launchPackage: GameCube/Wii emulation is embedded directly in RomRunner (see
        // data/embedded/PrimeHackEmbeddedLauncher.kt) rather than launched as an external app.
        Platform(name = "GameCube / Wii", extensionsCsv = "rvz,ciso"),
        Platform(name = "PlayStation 2", extensionsCsv = "iso", launchPackage = "com.armsx2"),
        // No launchPackage: embedded via RetroArch's SwanStation core. ".cue" chosen as the
        // least-ambiguous common PS1 dump extension in this starter set — same reasoning as
        // ".iso" above for PS2. A library using .chd/.pbp/bare .bin can add those extensions to
        // this platform from Settings.
        Platform(name = "PlayStation", extensionsCsv = "cue")
        // Removed for now (2026-09-24), per request: Sega Genesis/Mega Drive, Sega Game Gear,
        // PSP, Atari Lynx, Neo Geo Pocket, WonderSwan, PC Engine/TurboGrafx-16. No embedded or
        // default-launch-package support was ever wired up for any of these, so removing them
        // here only affects the starter seed a fresh ROMs-folder setup gets — add them back the
        // same way (a Platform(...) entry) if support for them is wanted again later.
        // Sega Master System removed the same way (2026-09-25), per request.
    )
}
