package com.noryan.romrunner

import com.noryan.romrunner.data.db.AppDatabase
import com.noryan.romrunner.data.embedded.EdenIntegration
import com.noryan.romrunner.data.repository.LibraryRepository
import com.noryan.romrunner.data.settings.LibrarySettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import info.cemu.cemu.CemuApplication
import org.citra.citra_emu.CitraApplication
import org.dolphinemu.dolphinemu.DolphinApplication

/**
 * Extends the embedded Azahar library's own Application class (rather than plain
 * android.app.Application) so its required startup init (native lib logging, directory setup,
 * notification channels, play-time tracking) still runs via super.onCreate() — only one
 * Application class can be registered per process, and RomRunner's manifest wins that slot
 * (see AndroidManifest.xml's tools:replace="android:name").
 */
class RomRunnerApp : CitraApplication() {
    lateinit var repository: LibraryRepository
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        disablePausableCompositionInPrefetch()
        super.onCreate()
        // RomRunner integration: PrimeHack (embedded GameCube/Wii core) is a THIRD embedded
        // Application-subclassing library, but Kotlin single inheritance means RomRunnerApp can
        // only ever extend one of them (Azahar's, above). PrimeHack's own DolphinApplication.
        // onCreate() — which loads its native lib, sets up its directory structure, and
        // registers its activity tracker — would otherwise never run at all. initializeForEmbedding
        // runs that same logic explicitly instead; see DolphinApplication.kt for the full story.
        DolphinApplication.initializeForEmbedding(this)
        // Same story again for Cemu (embedded Wii U core) — a FOURTH embedded
        // Application-subclassing library; see CemuApplication.kt.
        CemuApplication.initializeForEmbedding(this)
        // Eden (embedded Nintendo Switch core, "full" flavor only) is a no-op here — its own
        // directory/native init happens lazily on first touch instead; see EdenIntegration.kt.
        EdenIntegration.initialize(this)
        val db = AppDatabase.getInstance(this)
        val settings = LibrarySettings(this)
        repository = LibraryRepository(db.platformDao(), db.gameDao(), settings)
        appScope.launch {
            repository.seedDefaultPlatformsIfEmpty()
            // Must run before seedMissingDefaultPlatforms() below — otherwise that call would see
            // "GameBoy (Color + Advance)" as a missing default and insert it as a brand-new
            // platform before this has a chance to merge the old GBA/GBC rows into it.
            repository.mergeGameBoyPlatforms()
            // Adds "PlayStation" (new, embedded via RetroArch's SwanStation core) to installs that
            // already seeded/scanned a library before this platform existed in DefaultPlatforms.
            repository.seedMissingDefaultPlatforms()
            repository.repairKnownMisclassifications()
            repository.applyDefaultLaunchPackagesIfMissing()
            // Nintendo 3DS no longer routes through an external app at all: LibraryScreen sends
            // it straight to the embedded Azahar core (see AzaharEmbeddedLauncher), so it doesn't
            // need a launchPackage the way other platforms do.
            repository.clearLaunchPackage("Nintendo 3DS")
            // Same story for PlayStation 2 — routed to the embedded ARMSX2 core (see
            // PS2EmbeddedLauncher) instead of an external com.armsx2 install.
            repository.clearLaunchPackage("PlayStation 2")
            // Same story for GameCube/Wii — routed to the embedded PrimeHack core (see
            // PrimeHackEmbeddedLauncher) instead of an external org.dolphinemu.dolphinemu
            // install. (Twilight Princess is still its own explicit override, unrelated to
            // this platform-level launchPackage — see GameLaunchOverrides.)
            repository.clearLaunchPackage("GameCube / Wii")
            // Same story for Wii U — routed to the embedded Cemu core (see CemuEmbeddedLauncher)
            // instead of an external info.cemu.cemu install.
            repository.clearLaunchPackage("Wii U")
            // Same story for Nintendo Switch ("full" flavor only) — routed to the embedded Eden
            // core instead of an external dev.eden.eden_emulator install. No-op on "lite".
            EdenIntegration.applyLaunchRoutingDefaults(repository)
        }
    }

    /**
     * Our own declared compose-bom (2024.09.02, Foundation 1.7.2) gets overridden project-wide by
     * a much newer one (2026.06.01, Foundation 1.11.4 — confirmed via
     * `./gradlew :app:dependencyInsight --dependency androidx.compose.foundation:foundation-android`)
     * pulled in transitively through one of the embedded emulator cores' own dependency graphs;
     * Gradle's default conflict resolution takes the highest version across the whole app. That
     * newer Foundation's "pausable composition in prefetch" optimization crashes
     * (IllegalArgumentException: "Cannot disable reuse from root if it was caused by other
     * groups", in GapComposer/PausedCompositionImpl) when LazyColumn prefetches an off-screen row
     * whose content shape changes conditionally, e.g. PlatformsScreen's per-platform
     * expand/collapse. Disabling this flag is the officially-provided escape hatch for exactly
     * this kind of regression — cheaper and lower-risk than pinning one Foundation version across
     * every embedded core's own independent build.
     *
     * Reflection (rather than a direct `ComposeFoundationFlags.isPausableCompositionInPrefetchEnabled
     * = false` call) because our own module's *compile* classpath still resolves the older 1.7.2,
     * which doesn't have this class at all — only the newer version actually merged into the
     * final app at runtime does.
     */
    private fun disablePausableCompositionInPrefetch() {
        try {
            val flags = Class.forName("androidx.compose.foundation.ComposeFoundationFlags")
            flags.getField("isPausableCompositionInPrefetchEnabled").setBoolean(null, false)
        } catch (e: ReflectiveOperationException) {
            // The resolved Foundation version no longer has this flag (renamed/removed) or never
            // had it (older than expected) — nothing to disable either way.
        }
    }
}
