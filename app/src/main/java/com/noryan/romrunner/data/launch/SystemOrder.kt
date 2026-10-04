package com.noryan.romrunner.data.launch

/**
 * The order systems read in on the Systems tab. Each row there is an emulator app, so rows are
 * ordered by the first of their systems; RetroArch's one row covers every system from GameBoy
 * through PlayStation (its own label lists them in this same order, see RETROARCH_SYSTEM_LABELS),
 * so it leads. Anything not listed here (a future app) goes after, in its existing order.
 *
 * GameBoy, NES, SNES, Genesis / Master System / Game Gear, 32X, N64, PlayStation (all RetroArch),
 * then Dreamcast, PSP, DS, 3DS, GameCube / Wii, PS2, Wii U, Switch, Twilight Princess.
 */
object SystemOrder {
    private val EMULATOR_PACKAGES = listOf(
        "com.retroarch",
        "com.flycast.emulator",
        "org.ppsspp.ppsspp",
        "me.magnum.melondualds",
        "org.azahar_emu.azahar",
        "org.dolphinemu.primehack",
        "com.armsx2",
        "info.cemu.cemu",
        "dev.eden.eden_emulator",
        "dev.twilitrealm.dusk"
    )

    fun sortEmulators(emulators: List<RecommendedEmulator>): List<RecommendedEmulator> =
        emulators.sortedBy { EMULATOR_PACKAGES.indexOf(it.packageName).takeIf { i -> i >= 0 } ?: EMULATOR_PACKAGES.size }
}
