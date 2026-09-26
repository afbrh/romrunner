package com.noryan.romrunner.data.embedded

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.noryan.romrunner.data.model.Game
import java.io.File
import java.io.FilenameFilter
import org.yuzu.yuzu_emu.YuzuApplication
import org.yuzu.yuzu_emu.activities.EmulationActivity
import org.yuzu.yuzu_emu.features.settings.model.BooleanSetting
import org.yuzu.yuzu_emu.model.Game as EdenGame
import org.yuzu.yuzu_emu.utils.DirectoryInitialization
import org.yuzu.yuzu_emu.utils.FileUtil
import org.yuzu.yuzu_emu.utils.NativeConfig
import org.yuzu.yuzu_emu.NativeLibrary as EdenNativeLibrary

/**
 * Starts the embedded Eden core's own EmulationActivity in-process for a Switch ROM, instead of
 * handing off to a separate installed app. Ported from a prior session's integration snapshot
 * (eden-src/gameshelf-integration-snapshot/), which reached and verified the prod.keys
 * auto-import path live on the AYN Thor before being reverted; picking back up from there.
 *
 * Unlike Azahar, no real-path resolution is needed here: Eden's own EmulationActivity accepts a
 * plain content:// Uri as a game's `path` directly (its manifest's own ACTION_VIEW/content
 * intent-filter is built to accept launches from external frontends the same way), matching
 * ARMSX2 rather than Azahar.
 *
 * Eden's own directory setup needs no user-visible step at all: DirectoryInitialization derives
 * everything from this app's own private external-files directory, with no separate "choose a
 * data folder" the way Azahar's onboarding wizard asks for one. The keys/firmware steps Eden's own
 * wizard would otherwise show are replaced by [importKeys]/[importFirmware] — one-time
 * RomRunner-native file picks (triggered from EdenIntegration.rememberState the first time a
 * Switch game is launched), since real prod.keys and firmware dumped from the user's own console
 * are unavoidably required (there is no HLE substitute, the same situation as ARMSX2's BIOS).
 * Both are found automatically by [com.noryan.romrunner.data.launch.BiosKeysImporter] scanning
 * the user's own configured BIOS/Keys folder (Settings), rather than a picker shown per-launch.
 */
object EdenEmbeddedLauncher {

    const val PLATFORM_NAME = "Nintendo Switch"
    private const val LAUNCHER_PREFS_NAME = "eden_embedded_launcher"
    private const val KEY_OVERLAY_HIDDEN = "overlay_hidden"
    private const val KEY_BUNDLED_MODS_INSTALLED = "bundled_mods_installed"
    private const val BUNDLED_MODS_ASSET_DIR = "eden_mods"

    /**
     * Eden's own Application class (YuzuApplication) never gets its onCreate() called — RomRunner's
     * actual registered Application extends Azahar's CitraApplication instead (Kotlin single
     * inheritance allows only one), so YuzuApplication.appContext and everything downstream of it
     * (DirectoryInitialization, GPU driver setup, controller nav hook, ...) would otherwise throw
     * UninitializedPropertyAccessException the moment anything here touches Eden's native code.
     * YuzuApplication.initializeForEmbedding() is eden-src's own GameShelf-integration commit,
     * already present in this checkout — performs that same essential startup directly, given any
     * host Application. Called lazily here (on first touch) rather than eagerly from
     * RomRunnerApp.onCreate(), matching the already-verified-working pattern from the prior
     * session rather than introducing an untested ordering change.
     */
    private fun ensureDirectoryReady(context: Context) {
        if (DirectoryInitialization.areDirectoriesReady) return
        YuzuApplication.initializeForEmbedding(context.applicationContext as Application)
        ensureBundledModsInstalled(context)
    }

    /**
     * One-time copy of RomRunner-bundled per-title mods/cheats (assets/eden_mods/<title_id>/...)
     * into Eden's own mod-load directory (<userDirectory>/load/<title_id>/...), matching the exact
     * "<mod dir>/<folder>/cheats/<build id>.txt" and "<mod dir>/<folder>/romfs/..." layouts Eden's
     * own PatchManager expects (core/file_sys/patch_manager.cpp) — same "any addon folder under
     * load/<title_id>/" mechanism Eden's own mod system already reads, just pre-populated by
     * RomRunner instead of requiring the user to install mods by hand. Currently bundles a
     * depth-of-field removal fix for two titles: a verified Atmosphere-format cheat code for
     * Echoes of Wisdom (source: community cheat database), and a shader replacement mod for
     * Link's Awakening (source: GameBanana's "No DOF Blur" mod, CC BY-NC-ND 4.0). Never overwrites
     * a file the user (or another mod) already placed there, and only ever runs once per install
     * (tracked via [KEY_BUNDLED_MODS_INSTALLED]) so a user who deliberately removes a bundled mod
     * afterward doesn't have it silently reappear on the next launch.
     */
    private fun ensureBundledModsInstalled(context: Context) {
        val prefs = context.getSharedPreferences(LAUNCHER_PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_BUNDLED_MODS_INSTALLED, false)) return
        val loadDir = File(DirectoryInitialization.userDirectory, "load")
        copyAssetTreeIfAbsent(context, BUNDLED_MODS_ASSET_DIR, loadDir)
        prefs.edit().putBoolean(KEY_BUNDLED_MODS_INSTALLED, true).apply()
    }

    /** Recursively copies [assetPath] (a directory in this module's assets) into [destination],
     *  skipping any file that already exists there. AssetManager has no native "copy directory"
     *  API and no way to ask "is this a file or a directory" directly — [android.content.res.AssetManager.list]
     *  returns a non-empty array for a directory and an empty array for a plain file, which is
     *  what distinguishes the two cases below. */
    private fun copyAssetTreeIfAbsent(context: Context, assetPath: String, destination: File) {
        val children = context.assets.list(assetPath) ?: emptyArray()
        if (children.isEmpty()) {
            if (destination.exists()) return
            destination.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
            return
        }
        for (child in children) {
            copyAssetTreeIfAbsent(context, "$assetPath/$child", File(destination, child))
        }
    }

    /** True once prod.keys has been imported and is still present on disk. */
    fun isKeysImported(context: Context): Boolean {
        ensureDirectoryReady(context)
        val keysFile = File(DirectoryInitialization.userDirectory + "/keys/prod.keys")
        return keysFile.isFile && keysFile.length() > 0L
    }

    /**
     * Imports the user-picked prod.keys file via Eden's own native installer (which reads
     * directly from the content:// Uri — no manual copy needed here, unlike ARMSX2's BIOS import).
     * Returns false if the native installer reports anything other than success.
     */
    fun importKeys(context: Context, sourceUri: Uri): Boolean {
        ensureDirectoryReady(context)
        context.contentResolver.takePersistableUriPermission(
            sourceUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        return EdenNativeLibrary.installKeys(sourceUri.toString(), "keys") == 0
    }

    /** True once firmware has been installed (its NAND "registered" content dir is non-empty). */
    fun isFirmwareImported(context: Context): Boolean {
        ensureDirectoryReady(context)
        val firmwareDir = File(NativeConfig.getNandDir() + "/system/Contents/registered/")
        return firmwareDir.isDirectory && !firmwareDir.list().isNullOrEmpty()
    }

    /**
     * Imports the user-picked firmware zip, mirroring InstallableActions.processFirmware's core
     * logic without its Fragment/ViewModel/progress-dialog dependencies (this runs from
     * RomRunner's own EdenIntegration, not one of Eden's own fragments). Extracts to a cache
     * directory first and only commits it over the real NAND content directory if every extracted
     * file is a genuine .nca (Eden's own sanity check for "this was actually a firmware zip").
     */
    fun importFirmware(context: Context, sourceUri: Uri): Boolean {
        ensureDirectoryReady(context)
        context.contentResolver.takePersistableUriPermission(
            sourceUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        val filterNca = FilenameFilter { _, name -> name.endsWith(".nca") }
        val firmwareDir = File(NativeConfig.getNandDir() + "/system/Contents/registered/")
        val cacheFirmwareDir = File("${context.cacheDir.path}/registered/")
        return try {
            FileUtil.unzipToInternalStorage(sourceUri.toString(), cacheFirmwareDir)
            val allFiles = cacheFirmwareDir.list()?.size ?: -1
            val ncaFiles = cacheFirmwareDir.list(filterNca)?.size ?: -2
            if (allFiles != ncaFiles || allFiles <= 0) {
                false
            } else {
                firmwareDir.deleteRecursively()
                cacheFirmwareDir.copyRecursively(firmwareDir, overwrite = true)
                EdenNativeLibrary.initializeSystem(true)
                true
            }
        } catch (e: Exception) {
            false
        } finally {
            cacheFirmwareDir.deleteRecursively()
        }
    }

    fun launch(context: Context, game: Game) {
        ensureDirectoryReady(context)
        hideTouchOverlayIfNeeded(context)

        // EmulationActivity.onCreate() passes intent.extras straight through as the nav graph's
        // start-destination arguments (see eden_emulation_navigation.xml's "game" argument) —
        // EmulationFragment reads it via the Safe-Args-generated `args.game` property, checked
        // BEFORE its intent.data/GameMetadata-backed fallback (which needs the file to already be
        // registered in Eden's own native metadata cache from its own folder scan — not the case
        // for a ROM RomRunner found on its own). So the key must be exactly "game", matching the
        // nav argument's android:name — NOT EmulationActivity.EXTRA_SELECTED_GAME ("SelectedGame"),
        // which is only consulted by that class's own launch() companion helper, not by the
        // fragment's actual argument lookup.
        val intent = Intent(context, EmulationActivity::class.java).apply {
            putExtra("game", EdenGame(title = game.title, path = game.fileUri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    /**
     * One-time default for the AYN Thor's physical controls, matching Azahar's own
     * applyDefaultSettingsIfNeeded (EmulationMenuSettings.showOverlay = false) and ARMSX2's
     * touch.visibilityMode default. Gated on RomRunner's own prefs so it only ever happens once:
     * the user is free to turn the overlay back on in Eden's own settings afterward without
     * RomRunner quietly reverting it on the next launch.
     */
    private fun hideTouchOverlayIfNeeded(context: Context) {
        val launcherPrefs = context.getSharedPreferences(LAUNCHER_PREFS_NAME, Context.MODE_PRIVATE)
        if (launcherPrefs.getBoolean(KEY_OVERLAY_HIDDEN, false)) return
        BooleanSetting.SHOW_INPUT_OVERLAY.setBoolean(false)
        NativeConfig.saveGlobalConfig()
        launcherPrefs.edit().putBoolean(KEY_OVERLAY_HIDDEN, true).apply()
    }
}
