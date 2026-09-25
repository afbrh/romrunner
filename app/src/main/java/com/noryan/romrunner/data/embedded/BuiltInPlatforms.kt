package com.noryan.romrunner.data.embedded

/**
 * The platform names RomRunner has an embedded emulator core for. Single source of truth so
 * "does this platform have a built-in option" doesn't drift between the launch-routing logic
 * (LibraryScreen.attemptLaunch) and the Settings UI (PlatformsScreen).
 */
object BuiltInPlatforms {
    val NAMES: Set<String> = setOf(
        AzaharEmbeddedLauncher.PLATFORM_NAME,
        PS2EmbeddedLauncher.PLATFORM_NAME,
        CemuEmbeddedLauncher.PLATFORM_NAME,
        PrimeHackEmbeddedLauncher.PLATFORM_NAME
    ) + RetroArchEmbeddedLauncher.PLATFORM_NAMES + listOfNotNull(EdenIntegration.platformName)

    /** Matches [name] against [NAMES] case-insensitively, returning the canonical-cased name. */
    fun canonicalNameOrNull(name: String): String? =
        NAMES.firstOrNull { it.equals(name.trim(), ignoreCase = true) }
}
