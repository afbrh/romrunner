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
 * it's mapped to a single platform here.
 *
 * Every platform below has a well-known external emulator app filled in as its default
 * `launchPackage` — tapping a game launches straight into that app if it's installed, or shows a
 * one-time "install it?" prompt if not. A platform with no obvious single default (none currently)
 * would be left blank on purpose: tapping a game would just fall back to Android's normal "open
 * with" chooser instead.
 */
object DefaultPlatforms {
    val ALL: List<Platform> = listOf(
        // GameBoy, GameBoy Color, and GameBoy Advance are one combined platform since all three
        // play through the same multi-system emulator — no reason to make the user manage three
        // separate app choices for one app.
        Platform(name = "GameBoy (Color + Advance)", extensionsCsv = "gba,gb,gbc", launchPackage = "com.retroarch"),
        Platform(name = "NES", extensionsCsv = "nes", launchPackage = "com.retroarch"),
        Platform(name = "SNES", extensionsCsv = "sfc,smc", launchPackage = "com.retroarch"),
        Platform(name = "Nintendo 64", extensionsCsv = "n64,z64", launchPackage = "com.retroarch"),
        Platform(name = "Nintendo DS", extensionsCsv = "nds", launchPackage = "com.dsemu.drastic"),
        Platform(name = "Nintendo 3DS", extensionsCsv = "3ds,cia,cci", launchPackage = "org.azahar_emu.azahar"),
        Platform(name = "Nintendo Switch", extensionsCsv = "xci", launchPackage = "dev.eden.eden_emulator"),
        Platform(name = "Wii U", extensionsCsv = "wua", launchPackage = "info.cemu.cemu"),
        Platform(name = "GameCube / Wii", extensionsCsv = "rvz,ciso", launchPackage = "org.dolphinemu.dolphinemu"),
        Platform(name = "PlayStation 2", extensionsCsv = "iso", launchPackage = "com.armsx2"),
        // ".cue" chosen as the least-ambiguous common PS1 dump extension in this starter set —
        // same reasoning as ".iso" above for PS2. A library using .chd/.pbp/bare .bin can add
        // those extensions to this platform from Settings.
        Platform(name = "PlayStation", extensionsCsv = "cue", launchPackage = "com.retroarch")
        // Removed for now (2026-09-24), per request: Sega Genesis/Mega Drive, Sega Game Gear,
        // PSP, Atari Lynx, Neo Geo Pocket, WonderSwan, PC Engine/TurboGrafx-16. No default-launch-
        // package support was ever wired up for any of these, so removing them here only affects
        // the starter seed a fresh ROMs-folder setup gets — add them back the same way (a
        // Platform(...) entry) if support for them is wanted again later.
        // Sega Master System removed the same way (2026-09-25), per request.
    )
}
