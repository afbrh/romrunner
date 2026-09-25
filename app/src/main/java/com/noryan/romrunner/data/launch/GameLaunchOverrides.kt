package com.noryan.romrunner.data.launch

/**
 * Hard-coded per-title exceptions: specific games that should launch in a different app than
 * their platform's usual default. Matched by a case-insensitive substring against the game's
 * (cleaned) title, so slight filename variations (region tags, etc.) still match.
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
    val launchUri: String? = null
)

object GameLaunchOverrides {
    val ALL: List<GameOverride> = listOf(
        // Metroid Prime Trilogy no longer needs an override at all: GameCube/Wii is now fully
        // embedded (see PrimeHackEmbeddedLauncher), so it — like every other GameCube/Wii title —
        // routes there automatically. This used to deep-link out to a separately-installed
        // PrimeHack app (dolphinemu://app/play/<channelId>/<gameId>), which depended on that app
        // staying installed with its own game-folder scan intact; the embedded core doesn't have
        // that fragility.
        //
        // Twilight Princess no longer needs an override either, for the same reason: it now has
        // its own embedded DuskLight core (see DuskLightEmbeddedLauncher), checked directly in
        // LibraryScreen.attemptLaunch() before the generic GameCube/Wii branch. This used to
        // deep-link out to a separately-installed DuskLight app (dev.twilitrealm.dusk/.DuskActivity,
        // via the dusk_args/dusk_argv Intent extras), which depended on that app staying
        // installed; the embedded core doesn't have that fragility either.
    )

    fun find(gameTitle: String): GameOverride? {
        val lower = gameTitle.lowercase()
        return ALL.firstOrNull { lower.contains(it.titleKeyword) }
    }
}
