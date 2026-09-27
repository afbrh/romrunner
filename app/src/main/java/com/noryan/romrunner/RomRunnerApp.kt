package com.noryan.romrunner

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.noryan.romrunner.data.db.AppDatabase
import com.noryan.romrunner.data.embedded.EdenIntegration
import com.noryan.romrunner.data.embedded.WatermelonDSApplication
import com.noryan.romrunner.data.repository.LibraryRepository
import com.noryan.romrunner.data.settings.LibrarySettings
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import info.cemu.cemu.CemuApplication
import org.citra.citra_emu.CitraApplication
import org.dolphinemu.dolphinemu.DolphinApplication

/**
 * A plain Application (not one of the embedded cores' own Application subclasses) so it can be
 * @HiltAndroidApp — required by the embedded WatermelonDS core, whose Activities/ViewModels are
 * Hilt @AndroidEntryPoints. Hilt's Gradle-plugin bytecode transform requires the annotated class
 * to extend android.app.Application directly, and only one @HiltAndroidApp root is allowed per
 * compiled app, so none of the embedded cores' own Application classes (Azahar's CitraApplication,
 * PrimeHack's DolphinApplication, Cemu's CemuApplication) can be the base here anymore — each now
 * runs its startup logic via its own static initializeForEmbedding(this) call below instead of
 * real inheritance. RomRunner's manifest still wins the single registered-Application slot (see
 * AndroidManifest.xml's tools:replace="android:name") — Hilt's transform only swaps this class's
 * superclass, it doesn't rename it, so that mechanism is unaffected.
 */
@HiltAndroidApp
class RomRunnerApp : Application(), Configuration.Provider {
    @Inject lateinit var hiltWorkerFactory: HiltWorkerFactory

    lateinit var repository: LibraryRepository
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        disablePausableCompositionInPrefetch()
        super.onCreate()
        // RomRunner integration: Azahar (embedded 3DS core) — used to be this class's own base
        // class (CitraApplication); now just one more initializeForEmbedding(this) call, same
        // shape as the other three. See CitraApplication.kt for the full story.
        CitraApplication.initializeForEmbedding(this)
        // PrimeHack (embedded GameCube/Wii core) — its own DolphinApplication.onCreate(), which
        // loads its native lib, sets up its directory structure, and registers its activity
        // tracker, would otherwise never run at all. initializeForEmbedding runs that same logic
        // explicitly instead; see DolphinApplication.kt for the full story.
        DolphinApplication.initializeForEmbedding(this)
        // Same story again for Cemu (embedded Wii U core); see CemuApplication.kt.
        CemuApplication.initializeForEmbedding(this)
        // Eden (embedded Nintendo Switch core, "full" flavor only) is a no-op here — its own
        // directory/native init happens lazily on first touch instead; see EdenIntegration.kt.
        EdenIntegration.initialize(this)
        // WatermelonDS (embedded Nintendo DS/DSi core). loadNativeLib() was originally called from
        // attachBaseContext (matching where standalone MelonDSApplication loads it) — moved here
        // instead, in the same relative position as every other core's own native-lib load, after
        // a crash loop (SIGABRT immediately after PrimeHack's DirectoryInitialization log lines)
        // that only appeared once WatermelonDS's native lib was added to the startup sequence: with
        // 8 cores' native libraries now sharing one process, loading one uniquely early — before
        // any other core's own native init — is the most likely trigger for a symbol/ODR
        // collision. initializeForEmbedding() itself still needs to run after super.onCreate()
        // (the live Hilt component), unlike the three cores above.
        WatermelonDSApplication.loadNativeLib()
        WatermelonDSApplication.initializeForEmbedding(this)
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
            // Same story for Nintendo DS — routed to the embedded WatermelonDS core (see
            // WatermelonDSEmbeddedLauncher) instead of an external me.magnum.melondualds install.
            repository.clearLaunchPackage("Nintendo DS")
            // Same story for Nintendo Switch ("full" flavor only) — routed to the embedded Eden
            // core instead of an external dev.eden.eden_emulator install. No-op on "lite".
            EdenIntegration.applyLaunchRoutingDefaults(repository)
        }
    }

    // RomRunner integration: WatermelonDS's own manifest disables WorkManager's default
    // auto-init (since MelonDSApplication normally supplies its own HiltWorkerFactory via this
    // same Configuration.Provider mechanism) — once merged, that removal applies app-wide. Without
    // this override, Azahar's own CIA-install background worker (MainActivity.kt's
    // WorkManager.getInstance(applicationContext) call, which relied until now on WorkManager's
    // default auto-init since RomRunner never disabled it before) would crash with "WorkManager is
    // not initialized properly." Supplying a HiltWorkerFactory-backed Configuration here restores
    // on-demand init for every caller — HiltWorkerFactory falls back to reflection-based
    // instantiation for workers that aren't Hilt-aware, so Azahar's plain (non-@HiltWorker) worker
    // needs no changes of its own.
    // Explicit function override (not `override val ... : Configuration`) — Configuration.Provider
    // is a plain Java interface (`getWorkManagerConfiguration(): Configuration`), and Kotlin's
    // JavaBean-property-override inference didn't recognize the property form here, failing with
    // "overrides nothing." Implementing the method directly by name sidesteps that.
    override fun getWorkManagerConfiguration(): Configuration =
        Configuration.Builder().setWorkerFactory(hiltWorkerFactory).build()

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
