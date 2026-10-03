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
        Platform(name = "Nintendo DS", extensionsCsv = "nds", launchPackage = "me.magnum.melondualds"),
        Platform(name = "Nintendo 3DS", extensionsCsv = "3ds,cia,cci", launchPackage = "org.azahar_emu.azahar"),
        Platform(name = "Nintendo Switch", extensionsCsv = "xci", launchPackage = "dev.eden.eden_emulator"),
        Platform(name = "Wii U", extensionsCsv = "wua", launchPackage = "info.cemu.cemu"),
        // PrimeHack (a Dolphin fork) plays every GameCube/Wii title, so one app covers the whole
        // platform; it takes the game through the intent handoff in EmulatorLauncher. .wad is a Wii
        // channel / Virtual Console title (e.g. "Super Mario 64" for Wii).
        Platform(name = "GameCube / Wii", extensionsCsv = "rvz,ciso,wad", launchPackage = "org.dolphinemu.primehack"),
        // mimeType = "" (not the Platform default "application/octet-stream"): ARMSX2's own VIEW
        // intent-filters match by content/file Uri scheme only, with no <data mimeType> at all —
        // forcing an explicit type breaks Android's intent resolution against it entirely (see
        // EmulatorLauncher.buildIntentForPackage). launchActivity is also required, not just the
        // package: ARMSX2 declares three activities (BootSplashActivity, Main, and the
        // MainActivity alias) with the exact same VIEW intent-filter, and Android can't pick one
        // when startActivity's Intent is restricted to this package via setPackage() alone with no
        // chooser allowed ("unable to resolve Intent", confirmed directly against the real app) —
        // pointing at MainActivity explicitly resolves it unambiguously.
        Platform(
            name = "PlayStation 2",
            extensionsCsv = "iso",
            launchPackage = "com.armsx2",
            launchActivity = "com.armsx2.MainActivity",
            mimeType = ""
        ),
        // ".cue" chosen as the least-ambiguous common PS1 dump extension in this starter set —
        // same reasoning as ".iso" above for PS2. A library using .chd/.pbp/bare .bin can add
        // those extensions to this platform from Settings.
        Platform(name = "PlayStation", extensionsCsv = "cue", launchPackage = "com.retroarch"),
        // PSP: ".iso" is claimed by PS2 above, so PSP is matched by the extensions only it uses
        // (.cso compressed ISOs, .pbp EBOOT/PSN packages). A PSP library of plain .iso files has
        // to be moved to its own platform by hand. launchActivity is set explicitly so PPSSPP's
        // path-pattern VIEW filter (which matches by file name, awkward against SAF content
        // Uris) never decides whether the launch resolves.
        Platform(
            name = "PSP",
            extensionsCsv = "cso,pbp",
            launchPackage = "org.ppsspp.ppsspp",
            launchActivity = "org.ppsspp.ppsspp.PpssppActivity"
        ),
        // Flycast's VIEW intent-filters only declare the file:// scheme, but RomRunner hands over
        // content:// Uris — an explicit activity bypasses the filter, and Flycast's own code just
        // forwards the Uri string to its native layer, which opens content Uris itself. .chd/.bin/
        // .cue are shared with other disc systems and left out, same as for PS1.
        Platform(
            name = "Dreamcast",
            extensionsCsv = "cdi,gdi",
            launchPackage = "com.flycast.emulator",
            launchActivity = "com.flycast.emulator.NativeGLActivity"
        ),
        // Everything below is PS1/N64 era or earlier and plays through RetroArch.
        Platform(name = "Virtual Boy", extensionsCsv = "vb", launchPackage = "com.retroarch"),
        Platform(name = "Sega Genesis", extensionsCsv = "md,gen,smd", launchPackage = "com.retroarch"),
        Platform(name = "Sega Master System", extensionsCsv = "sms", launchPackage = "com.retroarch"),
        Platform(name = "Sega Game Gear", extensionsCsv = "gg", launchPackage = "com.retroarch"),
        Platform(name = "Sega 32X", extensionsCsv = "32x", launchPackage = "com.retroarch"),
        Platform(name = "PC Engine / TurboGrafx-16", extensionsCsv = "pce", launchPackage = "com.retroarch"),
        Platform(name = "Atari 2600", extensionsCsv = "a26", launchPackage = "com.retroarch"),
        Platform(name = "Atari 7800", extensionsCsv = "a78", launchPackage = "com.retroarch"),
        Platform(name = "Atari Lynx", extensionsCsv = "lnx", launchPackage = "com.retroarch"),
        Platform(name = "Neo Geo Pocket", extensionsCsv = "ngp,ngc", launchPackage = "com.retroarch"),
        Platform(name = "WonderSwan", extensionsCsv = "ws,wsc", launchPackage = "com.retroarch")
    )
}
