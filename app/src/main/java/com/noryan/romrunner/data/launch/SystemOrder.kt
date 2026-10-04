package com.noryan.romrunner.data.launch

import com.noryan.romrunner.data.model.Game
import com.noryan.romrunner.data.model.Platform

/**
 * The order the library lists games in: grouped by system in the sequence below (the order
 * the user wants their systems to read in), with every other system after them. Games within a
 * system stay in title order (the sort is stable and the games arrive title-sorted).
 */
object SystemOrder {
    /** Platform names, in listing order. Twilight Princess is its own entry: it plays in Dusklight rather than PrimeHack. */
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
    private val TWILIGHT_PRINCESS_RANK = PLATFORMS.size
    private val OTHERS_RANK = PLATFORMS.size + 1

    fun rank(platform: Platform?, game: Game): Int {
        if (platform == null) return OTHERS_RANK
        if (GameLaunchOverrides.find(game.title, platform.name) != null) return TWILIGHT_PRINCESS_RANK
        return PLATFORMS.indexOf(platform.name).takeIf { it >= 0 } ?: OTHERS_RANK
    }

    fun sort(games: List<Game>, platforms: List<Platform>): List<Game> {
        val byId = platforms.associateBy { it.id }
        return games.sortedBy { rank(byId[it.platformId], it) }
    }
}
