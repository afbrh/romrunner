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
    val launchUri: String? = null,
    /**
     * True for an app with no generic "hand it a file" Intent filter at all (confirmed for
     * PrimeHack-Android: its only VIEW filter uses a custom `dolphinemu://` scheme that needs a
     * channel/game id from that app's own library scan, which RomRunner has no way to know ahead
     * of time). The app is just opened plainly instead — the user picks the game from within its
     * own UI, the same as it'd need its own one-time ROM-folder setup regardless.
     */
    val openAppOnly: Boolean = false
)

object GameLaunchOverrides {
    val ALL: List<GameOverride> = listOf(
        // Metroid Prime Trilogy plays best with PrimeHack's mouselook controls rather than the
        // library's plain Dolphin default for every other GameCube/Wii title.
        GameOverride(
            titleKeyword = "metroid prime trilogy",
            appLabel = "PrimeHack",
            packageName = "org.dolphinemu.primehack",
            openAppOnly = true
        )
    )

    fun find(gameTitle: String): GameOverride? {
        val lower = gameTitle.lowercase()
        return ALL.firstOrNull { lower.contains(it.titleKeyword) }
    }
}
