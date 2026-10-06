package com.noryan.romrunner.data.launch

/**
 * The order systems read in on the Systems tab: GameBoy, NES, SNES, Genesis / Master System / Game Gear, 32X, N64,
 * PlayStation, Dreamcast, PSP, DS, 3DS, GameCube / Wii, PS2, Wii U, Switch, then Twilight Princess (its own
 * line, played in Dusklight), then every other system in its existing order.
 */
object SystemOrder {
    private val PLATFORMS = listOf(
        "GameBoy (Color + Advance)",
        "NES",
        "SNES",
        "Sega Genesis",
        "Sega Master System",
        "Sega Game Gear",
        "Sega 32X",
        "Nintendo 64",
        "PlayStation",
        "Dreamcast",
        "PSP",
        "Nintendo DS",
        "Nintendo 3DS",
        "GameCube / Wii",
        "PlayStation 2",
        "Wii U",
        "Nintendo Switch"
    )

    /** Where a per-title override line (Twilight Princess) goes: right after the listed systems. */
    val OVERRIDE_RANK = PLATFORMS.size
    private val OTHERS_RANK = PLATFORMS.size + 1

    fun rank(platformName: String): Int = PLATFORMS.indexOf(platformName).takeIf { it >= 0 } ?: OTHERS_RANK
}
