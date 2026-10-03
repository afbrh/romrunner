package com.noryan.romrunner.data.launch

/**
 * Hard-coded per-title exceptions: specific games that should launch in a different app than
 * their platform's usual default. Matched by whole words, ignoring case and punctuation, against the
 * game's (cleaned) title, so slight filename variations (dashes, region tags, etc.) still match.
 */
data class GameOverride(
    val titleKeyword: String,
    val appLabel: String,
    /** Null when we don't have a confirmed package name for this app yet (not installed anywhere we could check). */
    val packageName: String?,
    /**
     * A deep link that launches straight into this exact title instead of the generic
     * file-handoff Intent, for apps that support one. Null falls back to the generic path.
     */
    val launchUri: String? = null,
    /**
     * True for an app with no way to be handed a game at all: it's just opened plainly and the
     * user picks the game from within its own UI. (Nothing needs this right now — PrimeHack, which
     * used to, turned out to accept a game through its launcher activity; see
     * EmulatorLauncher.buildPrimeHackIntent.)
     */
    val openAppOnly: Boolean = false,
    /** When set, the override only applies to games on this platform (by name), e.g. so "Twilight Princess HD" on Wii U isn't caught by a GameCube-only override. */
    val platformName: String? = null,
    /** For apps that take the game on their command line: launches the app straight into the game. */
    val argvLaunch: ArgvLaunch? = null
)

/**
 * Launches an app's activity with the game's real file path appended to a fixed argument list, passed
 * as a string-array Intent extra. Confirmed for Dusklight: DuskActivity reads the `borealis_argv`
 * extra as its command line, and `--dvd <path>` opens that disc directly (tested on-device with a
 * .ciso image — it skips the app's own disc picker).
 */
data class ArgvLaunch(
    val activity: String,
    val extraKey: String,
    val argsBeforeRomPath: List<String>
)

object GameLaunchOverrides {
    val ALL: List<GameOverride> = listOf(
        // PrimeHack is now the GameCube/Wii platform's default app (Metroid Prime Trilogy
        // included), so the only remaining exception is Twilight Princess.
        // Dusklight is a native port built specifically for Twilight Princess (GameCube; it also
        // reads the Wii disc), so it replaces PrimeHack for that one title. Restricted to the
        // GameCube/Wii platform: "Twilight Princess HD" is a different game on Wii U.
        GameOverride(
            titleKeyword = "legend of zelda twilight princess",
            appLabel = "Dusklight",
            packageName = "dev.twilitrealm.dusk",
            platformName = "GameCube / Wii",
            argvLaunch = ArgvLaunch(
                activity = "dev.twilitrealm.dusk.DuskActivity",
                extraKey = "borealis_argv",
                argsBeforeRomPath = listOf("--dvd")
            )
        )
    )

    fun find(gameTitle: String, platformName: String): GameOverride? = ALL.firstOrNull {
        (it.platformName == null || it.platformName == platformName) && titleMatches(gameTitle, it.titleKeyword)
    }

    /**
     * Whether [title] contains [keyword] as a run of whole words, ignoring case, punctuation and
     * spacing — so "Metroid - Prime Trilogy", "Metroid: Prime Trilogy" and "metroid_prime_trilogy" all
     * match "metroid prime trilogy". Plain substring matching missed the dashed filename style that
     * ROM sets commonly use, silently sending the game to the wrong emulator.
     */
    fun titleMatches(title: String, keyword: String): Boolean =
        " ${normalizeTitle(title)} ".contains(" ${normalizeTitle(keyword)} ")

    private fun normalizeTitle(text: String): String =
        text.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
}
