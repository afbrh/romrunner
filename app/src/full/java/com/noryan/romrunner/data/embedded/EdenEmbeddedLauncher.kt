package com.noryan.romrunner.data.embedded

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
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
 * Before asking for prod.keys specifically, [findKeysCandidates] is tried first — a device scan
 * for a file already named prod.keys, since that's a fixed, predictable filename (unlike
 * firmware's arbitrarily-named zip) worth looking for automatically before bothering the user.
 */
object EdenEmbeddedLauncher {

    const val PLATFORM_NAME = "Nintendo Switch"
    private const val LAUNCHER_PREFS_NAME = "eden_embedded_launcher"
    private const val KEY_OVERLAY_HIDDEN = "overlay_hidden"

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

    /** Same as [importKeys], for a plain absolute filesystem path rather than a picked SAF Uri —
     *  used for a file [findKeysCandidates] already located on disk. No persistable-permission
     *  grant needed here: that's a SAF/content:// concept, and a raw path found via
     *  MANAGE_EXTERNAL_STORAGE or an already-granted SAF tree needs no extra grant to read again. */
    private fun importKeysFromPath(context: Context, absolutePath: String): Boolean {
        ensureDirectoryReady(context)
        return EdenNativeLibrary.installKeys(absolutePath, "keys") == 0
    }

    /** A located prod.keys file, from either search scope in [findKeysCandidates] — either one
     *  can be handed straight to [import] without the caller needing to know which. */
    sealed class KeysCandidate {
        abstract fun import(context: Context): Boolean

        class FromPath(val path: String) : KeysCandidate() {
            override fun import(context: Context) = importKeysFromPath(context, path)
        }

        class FromUri(val uri: Uri) : KeysCandidate() {
            override fun import(context: Context) = importKeys(context, uri)
        }
    }

    /**
     * Looks for a file literally named "prod.keys" already sitting on the device, so the user
     * doesn't have to hunt for and manually pick it via the file browser if it's a leftover from a
     * previous Switch-emulation setup (a common case — keys are dumped once from a real console
     * and often just left wherever they were first extracted to). Blocking/IO-bound: call this off
     * the main thread. Two search scopes, tried in order, stopping at the first with any matches:
     *
     * 1. The device's own shared storage, every mounted volume — only actually searched if
     *    RomRunner holds MANAGE_EXTERNAL_STORAGE ("All files access"). Declaring that permission in
     *    the manifest (inherited from ARMSX2's sideload-capable flavor) doesn't mean the user has
     *    granted it; this never prompts for it, it just quietly widens the search when it's already
     *    there.
     * 2. The user's chosen ROMs folder — the same SAF tree RomScanner already walks, which
     *    RomRunner always has access to regardless of that special permission. Falling back to
     *    this (rather than also merging it into scope 1) avoids double-counting the same physical
     *    file found two different ways as "more than one match".
     *
     * The caller auto-imports on exactly one match and falls back to asking the user for the file
     * otherwise (none found, or genuinely more than one — e.g. an old backup copy lying around).
     */
    fun findKeysCandidates(context: Context, romsRootUri: String?): List<KeysCandidate> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            val matches = storageRoots(context).flatMap { findFilesNamed(it, "prod.keys") }
            if (matches.isNotEmpty()) return matches.map { KeysCandidate.FromPath(it.absolutePath) }
        }
        val root = romsRootUri?.let { DocumentFile.fromTreeUri(context, Uri.parse(it)) } ?: return emptyList()
        val matches = mutableListOf<DocumentFile>()
        findInSafTree(root, "prod.keys", matches)
        return matches.map { KeysCandidate.FromUri(it.uri) }
    }

    /** Every mounted shared-storage volume's root — primary storage plus any SD card, derived from
     *  the per-app external files dirs Android always exposes one of per volume (there's no direct
     *  public API for "list storage volume roots" below API 30's less broadly available
     *  StorageManager APIs, so this is the same climb-up-from-a-known-child trick many Android
     *  file-manager apps use: <volume>/Android/data/<pkg>/files -> <volume>). */
    private fun storageRoots(context: Context): List<File> {
        val roots = linkedSetOf(Environment.getExternalStorageDirectory())
        for (dir in context.getExternalFilesDirs(null)) {
            val volumeRoot = dir?.parentFile?.parentFile?.parentFile?.parentFile ?: continue
            roots += volumeRoot
        }
        return roots.toList()
    }

    /** Recursively finds files named [targetName] (case-insensitive) under [dir], skipping hidden
     *  directories (caches/thumbnails/etc. — never a sensible place for a keys file, and often
     *  large) and capping depth as a guard against a pathological symlink loop or similarly deep
     *  tree rather than actually expecting one. */
    private fun findFilesNamed(dir: File, targetName: String, depth: Int = 0): List<File> {
        if (depth > 20) return emptyList()
        val children = dir.listFiles() ?: return emptyList()
        val found = mutableListOf<File>()
        for (child in children) {
            if (child.isDirectory) {
                if (!child.isHidden) found += findFilesNamed(child, targetName, depth + 1)
            } else if (child.name.equals(targetName, ignoreCase = true)) {
                found += child
            }
        }
        return found
    }

    /** SAF equivalent of [findFilesNamed], for the ROMs-folder fallback scope. */
    private fun findInSafTree(dir: DocumentFile, targetName: String, acc: MutableList<DocumentFile>) {
        for (child in dir.listFiles()) {
            if (child.isDirectory) {
                findInSafTree(child, targetName, acc)
            } else if (child.name?.equals(targetName, ignoreCase = true) == true) {
                acc += child
            }
        }
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
