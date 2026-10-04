package com.noryan.romrunner.data.launch

import android.Manifest
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.content.ContentUris
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import android.widget.Toast
import com.noryan.romrunner.data.model.Game
import com.noryan.romrunner.data.model.Platform
import com.noryan.romrunner.data.scanner.SafPathUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Launches a game straight into RetroArch with the right libretro core, instead of the generic
 * "VIEW this file" handoff every other external emulator gets — RetroArch declares no file-VIEW
 * intent filter at all, and doesn't ship any cores in its APK.
 *
 * How it works (all confirmed on a real device against RetroArch 1.22.2):
 *  - RetroArch's exported CoreSideloadActivity takes a LIBRETRO extra (a core .so path it can read),
 *    copies that core into its own private cores directory, then starts the game named by the ROM
 *    extra with it. That one call both "installs" the core and launches the game.
 *  - RomRunner has no storage permission, so it can't hand RetroArch a core from its own private
 *    files. Instead it downloads the core from libretro's buildbot (the same place RetroArch's own
 *    Online Updater uses) into the shared Downloads collection via MediaStore — which needs no
 *    permission — and RetroArch, which targets an old SDK and so keeps legacy storage access, reads
 *    it from there by path.
 *  - A never-opened RetroArch has neither the storage permission nor a cores directory (the
 *    sideload fails with "Destination directory doesn't exist"). Both are created by its normal
 *    first-run prompt, so that case just opens RetroArch once and asks the user to try again.
 */
object RetroArchLauncher {

    private const val RETROARCH_PACKAGE = "com.retroarch"
    private const val SIDELOAD_ACTIVITY = "com.retroarch.browser.debug.CoreSideloadActivity"
    private const val CORE_BASE_URL = "https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/"
    private const val CORE_RELATIVE_PATH = "Download/RomRunner/cores/"
    private const val RETRO_ACTIVITY = "com.retroarch.browser.retroactivity.RetroActivityFuture"
    private const val CONFIG_NAME = "romrunner.cfg"

    /**
     * RomRunner's own RetroArch config, handed to RetroArch as the CONFIGFILE extra on every launch (it's the
     * main config for that run). Written once at setup and then left alone, because RetroArch saves any change
     * the user makes in its menus back into this file. Controls need nothing here: RetroArch ships an
     * "Odin Controller" profile (and Retroid ones) that it applies by itself; only the hotkeys are set.
     *
     * quit_on_close_content is a number, not a boolean: 0 = off, 1 = quit RetroArch whenever content closes.
     * The menu-toggle combo 4 is Start+Select; the quit combo 3 is L1+R1+Start+Select.
     */
    private val CONFIG_TEXT = """
        menu_driver = "rgui"
        input_overlay_enable = "false"
        quit_on_close_content = "1"
        menu_show_quit_retroarch = "true"
        confirm_quit = "false"
        input_menu_toggle_gamepad_combo = "4"
        input_quit_gamepad_combo = "3"
        input_autodetect_enable = "true"
        savefile_directory = "/storage/emulated/0/RetroArch/saves"
        savestate_directory = "/storage/emulated/0/RetroArch/states"
        system_directory = "/storage/emulated/0/RetroArch/system"
    """.trimIndent() + "\n"

    /** The best libretro core for each RetroArch-played platform (by RomRunner platform name), without the "_libretro_android.so" suffix. */
    private val CORE_BY_PLATFORM = mapOf(
        "GameBoy (Color + Advance)" to "mgba",
        "NES" to "nestopia",
        "SNES" to "snes9x",
        "Nintendo 64" to "mupen64plus_next_gles3",
        "Virtual Boy" to "mednafen_vb",
        "PlayStation" to "swanstation",
        "Sega Genesis" to "genesis_plus_gx",
        "Sega Master System" to "genesis_plus_gx",
        "Sega Game Gear" to "genesis_plus_gx",
        "Sega 32X" to "picodrive",
        "PC Engine / TurboGrafx-16" to "mednafen_pce_fast",
        "Atari 2600" to "stella",
        "Atari 7800" to "prosystem",
        "Atari Lynx" to "handy",
        "Neo Geo Pocket" to "mednafen_ngp",
        "WonderSwan" to "mednafen_wswan"
    )

    /** True when this platform is set to RetroArch and RomRunner knows which core to run it with. */
    fun handles(platform: Platform): Boolean =
        platform.launchPackage == RETROARCH_PACKAGE && platform.name in CORE_BY_PLATFORM

    /** The installed RetroArch variant's folder-provider authority (e.g. "com.retroarch.aarch64.documents"), or null if none is installed. */
    private fun authority(context: Context): String? =
        EmulatorLauncher.installedPackageFor(context, RETROARCH_PACKAGE)?.let { "$it.documents" }

    private fun dataDir(context: Context, packageName: String): String =
        context.packageManager.getApplicationInfo(packageName, 0).dataDir

    /**
     * Where the system folder picker should open: RetroArch's own data folder (the only root its provider
     * exposes — the picker opens on a *root* address, see PrimeHackControls.pickerInitialUri). Null if RetroArch isn't installed.
     */
    fun pickerInitialUri(context: Context): Uri? {
        val pkg = EmulatorLauncher.installedPackageFor(context, RETROARCH_PACKAGE) ?: return null
        return DocumentsContract.buildRootUri("$pkg.documents", dataDir(context, pkg))
    }

    fun isRetroArchTree(context: Context, treeUri: Uri): Boolean = treeUri.authority == authority(context)

    /** The RomRunner platforms (from [platformNames]) that play through RetroArch. */
    fun retroArchPlatforms(platformNames: Collection<String>): List<String> = platformNames.filter { it in CORE_BY_PLATFORM }

    sealed interface SetupResult {
        data class Done(val coresInstalled: List<String>) : SetupResult
        data class Failed(val message: String) : SetupResult
    }

    /**
     * Writes RomRunner's config into RetroArch (through the folder grant [treeUri]) and installs the libretro cores
     * for [platformNames] ahead of time, so the first game of each system doesn't wait on a download. Blocking I/O.
     */
    suspend fun setUp(context: Context, treeUri: Uri, platformNames: Collection<String>): SetupResult = withContext(Dispatchers.IO) {
        try {
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext SetupResult.Failed("Couldn't open RetroArch's folder.")
            writeConfig(context, root, overwrite = true)
            val installed = platformNames.mapNotNull { CORE_BY_PLATFORM[it] }.distinct().onEach { installCore(context, root, it) }
            SetupResult.Done(installed)
        } catch (e: Exception) {
            SetupResult.Failed("Couldn't set up RetroArch: ${e.message ?: "unknown error"}")
        }
    }

    private fun ensureDir(parent: DocumentFile, name: String): DocumentFile =
        parent.findFile(name)?.takeIf { it.isDirectory } ?: parent.createDirectory(name) ?: error("couldn't create $name")

    private fun writeConfig(context: Context, root: DocumentFile, overwrite: Boolean) {
        val files = ensureDir(root, "files")
        val existing = files.findFile(CONFIG_NAME)
        if (existing != null && !overwrite) return
        existing?.delete()
        val file = files.createFile("application/octet-stream", CONFIG_NAME) ?: error("couldn't create $CONFIG_NAME")
        context.contentResolver.openOutputStream(file.uri, "wt")?.use { it.write(CONFIG_TEXT.toByteArray()) } ?: error("couldn't write $CONFIG_NAME")
    }

    /** Puts the libretro core [coreName] in RetroArch's own cores folder (a core can only be loaded from there, not from shared storage). */
    private fun installCore(context: Context, root: DocumentFile, coreName: String) {
        val coresDir = ensureDir(root, "cores")
        val fileName = "${coreName}_libretro_android.so"
        if ((coresDir.findFile(fileName)?.length() ?: 0L) > 0L) return
        ensureCore(context, fileName)
        val source = coreUri(context, fileName) ?: error("downloaded core vanished")
        coresDir.findFile(fileName)?.delete()
        val target = coresDir.createFile("application/octet-stream", fileName) ?: error("couldn't create $fileName")
        context.contentResolver.openInputStream(source)?.use { input ->
            context.contentResolver.openOutputStream(target.uri, "wt")?.use { input.copyTo(it) } ?: error("couldn't write $fileName")
        } ?: error("couldn't read the downloaded core")
    }

    private fun coreUri(context: Context, fileName: String): Uri? {
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        return context.contentResolver.query(
            collection, arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
            arrayOf(fileName, CORE_RELATIVE_PATH), null
        )?.use { if (it.moveToFirst()) ContentUris.withAppendedId(collection, it.getLong(0)) else null }
    }

    sealed interface Prepared {
        data class Ready(val intent: Intent) : Prepared

        /** RetroArch has never been set up (no storage permission yet); [intent] opens it so its first-run prompt appears. */
        data class NeedsFirstRun(val intent: Intent?) : Prepared

        data class Failed(val message: String) : Prepared
    }

    /** Blocking network/disk work happens on Dispatchers.IO; the returned Intent is started by the caller. */
    suspend fun prepare(context: Context, platform: Platform, game: Game, folderUri: Uri?): Prepared = withContext(Dispatchers.IO) {
        val retroArchPackage = EmulatorLauncher.installedPackageFor(context, RETROARCH_PACKAGE)
            ?: return@withContext Prepared.Failed("RetroArch isn't installed.")
        val coreName = CORE_BY_PLATFORM[platform.name]
            ?: return@withContext Prepared.Failed("No RetroArch core is set for ${platform.name}.")

        if (context.packageManager.checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE, retroArchPackage) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext Prepared.NeedsFirstRun(context.packageManager.getLaunchIntentForPackage(retroArchPackage))
        }

        val romPath = SafPathUtils.realPathFromDocumentUri(Uri.parse(game.fileUri))
            ?: return@withContext Prepared.Failed(
                "Can't locate ${game.title} on disk (only works for folders on internal storage, not SD cards)."
            )

        if (folderUri != null) {
            // The folder grant lets RomRunner put the core and its own config inside RetroArch, so launch
            // RetroArch directly with that config (menu, hotkeys, quit behaviour) instead of the sideload
            // screen, which starts the game with RetroArch's default config.
            try {
                val root = DocumentFile.fromTreeUri(context, folderUri) ?: error("couldn't open RetroArch's folder")
                installCore(context, root, coreName)
                writeConfig(context, root, overwrite = false)
                val base = dataDir(context, retroArchPackage)
                return@withContext Prepared.Ready(Intent().apply {
                    component = ComponentName(retroArchPackage, RETRO_ACTIVITY)
                    putExtra("LIBRETRO", "$base/cores/${coreName}_libretro_android.so")
                    putExtra("ROM", romPath)
                    putExtra("CONFIGFILE", "$base/files/$CONFIG_NAME")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                })
            } catch (e: Exception) {
                // Fall through to the plain sideload launch below: the game still plays, just with RetroArch's own settings.
            }
        }

        val corePath = try {
            ensureCore(context, "${coreName}_libretro_android.so")
        } catch (e: Exception) {
            return@withContext Prepared.Failed("Couldn't download the ${coreName} core: ${e.message ?: "network error"}")
        }

        val intent = Intent().apply {
            component = ComponentName(retroArchPackage, SIDELOAD_ACTIVITY)
            putExtra("LIBRETRO", corePath)
            putExtra("ROM", romPath)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        Prepared.Ready(intent)
    }

    /**
     * Returns the absolute path of [fileName] in the shared Downloads collection, downloading and
     * unzipping it from libretro's buildbot first if RomRunner hasn't already. Existence is checked
     * through MediaStore (which reliably lists the files this app created) rather than by file path,
     * since without storage permission a path check can't be trusted and a wrong "missing" would
     * insert a renamed duplicate on every launch.
     */
    private fun ensureCore(context: Context, fileName: String): String {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI

        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.SIZE),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
            arrayOf(fileName, CORE_RELATIVE_PATH),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst() && cursor.getLong(1) > 0) return cursor.getString(0)
        }

        showToast(context, "Downloading ${fileName.removeSuffix("_libretro_android.so")} core…")

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, CORE_RELATIVE_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val itemUri = resolver.insert(collection, values) ?: error("couldn't create a file in Downloads")
        try {
            val connection = (URL("$CORE_BASE_URL$fileName.zip").openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) error("server returned ${connection.responseCode}")
                ZipInputStream(connection.inputStream).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null && entry.name != fileName) entry = zip.nextEntry
                    if (entry == null) error("core missing from the downloaded zip")
                    val out = resolver.openOutputStream(itemUri) ?: error("couldn't write to Downloads")
                    out.use { zip.copyTo(it) }
                }
            } finally {
                connection.disconnect()
            }
            resolver.update(itemUri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(itemUri, null, null)
            throw e
        }

        resolver.query(itemUri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        error("downloaded core vanished")
    }

    private fun showToast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }
}
