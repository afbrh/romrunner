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
        // No overrides currently needed — every platform routes through its own default
        // launchPackage (see DefaultPlatforms.kt). Add an entry here for a specific title that
        // needs a different app (or a direct deep link) than its platform's usual default.
    )

    fun find(gameTitle: String): GameOverride? {
        val lower = gameTitle.lowercase()
        return ALL.firstOrNull { lower.contains(it.titleKeyword) }
    }
}
