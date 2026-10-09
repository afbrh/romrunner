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
import java.io.File
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

    /** The mGBA core's library name, which RetroArch uses as the folder name for per-core presets. */
    private const val MGBA_PRESET_DIR = "mGBA"
    private const val MGBA_SHADER = "shaders/shaders_glsl/handheld/lcd-grid-v2-gba-color.glslp"

    /**
     * The CRT shader for the console (not handheld) systems: the fast Lottes CRT preset, with its screen curvature,
     * "Trinitron" curve and rounded corners switched off so the picture keeps its rectangle (the parameters are
     * overridden on top of the shipped preset, which is referenced rather than copied).
     */
    private const val CRT_SHADER = "shaders/shaders_glsl/crt/crt-lottes-fast.glslp"

    /**
     * The per-core folder names RetroArch looks for a core's auto-loaded preset in (each core's own library name) for the
     * consoles that get the CRT shader. Game Gear shares Genesis Plus GX with Genesis and Master System, so it gets it too.
     */
    private val CRT_PRESET_FOLDERS = listOf(
        "Nestopia", "Snes9x", "Mupen64Plus-Next", "SwanStation", "Genesis Plus GX",
        "PicoDrive", "Beetle PCE Fast", "Stella", "ProSystem"
    )

    private fun crtPreset(dataDir: String): String =
        "#reference \"$dataDir/$CRT_SHADER\"\nCURVATURE = \"0.000000\"\nTRINITRON_CURVE = \"0.000000\"\nCORNER = \"0.000000\"\n"

    /**
     * RomRunner's own RetroArch config, handed to RetroArch as the CONFIGFILE extra on every launch (it's the
     * main config for that run). Written once at setup and then left alone, because RetroArch saves any change
     * the user makes in its menus back into this file.
     *
     *  - Menu: RGUI, Gray Dark theme (35), no border filler, menu aspect ratio matched to this screen, and
     *    OK/Cancel not swapped.
     *  - Quit: quit_on_close_content is a number, not a boolean (0 = off, 1 = quit RetroArch whenever content
     *    closes). Hotkeys: combo 4 (Start+Select) opens the menu, combo 3 (L1+R1+Start+Select) quits.
     *  - Video: the GL driver (GLSL shader presets don't work on Vulkan) with shaders on. They only do anything
     *    where a preset is auto-loaded, which RomRunner sets up for the mGBA core (see [writeShaderPreset]).
     *  - Directories are pinned to RetroArch's own folders so nothing depends on how it guesses them.
     *  - Controls: see [ensureControllerBinds]; RetroArch's own autoconfig profile is the backstop.
     */
    private fun configText(context: Context, dataDir: String): String = """
        menu_driver = "rgui"
        rgui_menu_color_theme = "35"
        rgui_border_filler_enable = "false"
        rgui_aspect_ratio = "${menuAspectRatio(context)}"
        menu_swap_ok_cancel_buttons = "false"
        input_overlay_enable = "false"
        quit_on_close_content = "1"
        menu_show_quit_retroarch = "true"
        confirm_quit = "false"
        input_menu_toggle_gamepad_combo = "4"
        input_quit_gamepad_combo = "3"
        input_autodetect_enable = "true"
        video_driver = "gl"
        video_shader_enable = "true"
        auto_shaders_enable = "true"
        auto_overrides_enable = "true"
        video_shader_dir = "$dataDir/shaders"
        joypad_autoconfig_dir = "$dataDir/autoconfig"
        savefile_directory = "/storage/emulated/0/RetroArch/saves"
        savestate_directory = "/storage/emulated/0/RetroArch/states"
        system_directory = "/storage/emulated/0/RetroArch/system"
    """.trimIndent() + "\n"

    /**
     * RGUI's "Aspect Ratio" value for this device's screen: the nearest of the ratios RGUI offers.
     * 0 = 4:3, 1 = 16:9, 3 = 16:10, 5 = 21:9, 7 = 3:2, 9 = 5:3 (the "centred" variants sit between them).
     */
    private fun menuAspectRatio(context: Context): Int {
        val metrics = context.resources.displayMetrics
        val ratio = maxOf(metrics.widthPixels, metrics.heightPixels).toFloat() / minOf(metrics.widthPixels, metrics.heightPixels)
        val options = mapOf(0 to 4f / 3, 1 to 16f / 9, 3 to 16f / 10, 5 to 21f / 9, 7 to 3f / 2, 9 to 5f / 3)
        return options.minByOrNull { kotlin.math.abs(it.value - ratio) }!!.key
    }

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

    /** Whether RetroArch has been given storage access yet (it asks the first time it's opened); until it has, it can't read a ROM or a core. */
    fun hasStorageAccess(context: Context): Boolean {
        val pkg = EmulatorLauncher.installedPackageFor(context, RETROARCH_PACKAGE) ?: return false
        return context.packageManager.checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE, pkg) == PackageManager.PERMISSION_GRANTED
    }

    /** Stops RetroArch's process (it must be out of the foreground, which Android requires of an app that's asked to be killed). */
    fun quit(context: Context) {
        val pkg = EmulatorLauncher.installedPackageFor(context, RETROARCH_PACKAGE) ?: return
        (context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).killBackgroundProcesses(pkg)
    }

    /** The core RomRunner uses for [platformName] unless the user picked another one. */
    fun defaultCore(platformName: String): String? = CORE_BY_PLATFORM[platformName]

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
            val pkg = EmulatorLauncher.installedPackageFor(context, RETROARCH_PACKAGE) ?: return@withContext SetupResult.Failed("RetroArch isn't installed.")
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext SetupResult.Failed("Couldn't open RetroArch's folder.")
            writeConfig(context, root, dataDir(context, pkg), overwrite = true)
            writeShaderPreset(context, root, dataDir(context, pkg))
            ensureControllerBinds(context, root, pkg)
            val installed = platformNames.mapNotNull { CORE_BY_PLATFORM[it] }.distinct().onEach { installCore(context, root, it) }
            SetupResult.Done(installed)
        } catch (e: Exception) {
            SetupResult.Failed("Couldn't set up RetroArch: ${e.message ?: "unknown error"}")
        }
    }

    private fun ensureDir(parent: DocumentFile, name: String): DocumentFile =
        parent.findFile(name)?.takeIf { it.isDirectory } ?: parent.createDirectory(name) ?: error("couldn't create $name")

    private fun writeConfig(context: Context, root: DocumentFile, dataDir: String, overwrite: Boolean) {
        val files = ensureDir(root, "files")
        val existing = files.findFile(CONFIG_NAME)
        if (existing != null && !overwrite) return
        existing?.delete()
        val file = EmulatorFolders.createNamedFile(files, CONFIG_NAME) ?: error("couldn't create $CONFIG_NAME")
        context.contentResolver.openOutputStream(file.uri, "wt")?.use { it.write(configText(context, dataDir).toByteArray()) } ?: error("couldn't write $CONFIG_NAME")
    }

    /**
     * Makes every game played with the mGBA core use the "LCD Grid v2 (GBA colour)" shader from RetroArch's
     * handheld shaders. RetroArch auto-loads a preset named `<core>/<core>.glslp` from the folder holding the
     * config file; this one just points at the shipped preset, so it also follows the shader files if RetroArch updates them.
     */
    private fun writeShaderPreset(context: Context, root: DocumentFile, dataDir: String) {
        val dir = ensureDir(ensureDir(root, "files"), MGBA_PRESET_DIR)
        if (dir.findFile("$MGBA_PRESET_DIR.glslp") != null) return
        val file = EmulatorFolders.createNamedFile(dir, "$MGBA_PRESET_DIR.glslp") ?: error("couldn't create the mGBA shader preset")
        context.contentResolver.openOutputStream(file.uri, "wt")?.use {
            it.write("#reference \"$dataDir/$MGBA_SHADER\"\n".toByteArray())
        } ?: error("couldn't write the mGBA shader preset")

        for (folder in CRT_PRESET_FOLDERS) {
            val coreDir = ensureDir(ensureDir(root, "files"), folder)
            if (coreDir.findFile("$folder.glslp") != null) continue
            val crt = EmulatorFolders.createNamedFile(coreDir, "$folder.glslp") ?: error("couldn't create the $folder shader preset")
            context.contentResolver.openOutputStream(crt.uri, "wt")?.use { it.write(crtPreset(dataDir).toByteArray()) }
                ?: error("couldn't write the $folder shader preset")
        }
    }

    private val PLAYER_BIND = Regex("""^input_((up|down|left|right|a|b|x|y|start|select|l|r|l2|r2|l3|r3|[lr]_[xy]_(plus|minus))_(btn|axis))$""")
    private val HOTKEY_BIND = Regex("""^input_[a-z0-9_]+_(btn|axis)$""")

    /**
     * The player-1 bindings for this device's own gamepad, copied from the matching profile that ships inside the
     * RetroArch app ("Odin Controller" on the Thor, Retroid's on a Retroid). Read straight from RetroArch's APK, which
     * any app can read, so it needs no folder access and doesn't wait for RetroArch to have unpacked its own copy.
     * Putting them in the config means the mapping doesn't depend on RetroArch finding and matching the profile
     * itself. Empty if no gamepad is connected or no profile matches (RetroArch's own matching still applies then).
     */
    private fun controllerBinds(context: Context, packageName: String): List<Pair<String, String>> {
        val pad = Gamepad.primary() ?: return emptyList()
        val apk = try { context.packageManager.getApplicationInfo(packageName, 0).sourceDir } catch (e: Exception) { return emptyList() }
        val line = Regex("""^\s*([a-z0-9_]+)\s*=\s*"(.*)"\s*$""")
        val candidates = try {
            java.util.zip.ZipFile(apk).use { zip ->
                zip.entries().asSequence()
                    .filter { it.name.startsWith("assets/autoconfig/android/") && it.name.endsWith(".cfg") }
                    .mapNotNull { entry ->
                        val values = zip.getInputStream(entry).bufferedReader().readLines()
                            .mapNotNull { line.find(it)?.destructured }.associate { (k, v) -> k to v }
                        if (values["input_device"].equals(pad.name, ignoreCase = true)) values else null
                    }
                    .toList()
            }
        } catch (e: Exception) { return emptyList() }
        // Two profiles can share a device name (e.g. the Odin's normal and Xbox modes): the product id tells them apart.
        val profile = candidates.firstOrNull { it["input_product_id"] == pad.productId.toString() } ?: candidates.firstOrNull() ?: return emptyList()

        return profile.mapNotNull { (key, value) ->
            when {
                key.endsWith("_label") -> null
                PLAYER_BIND.matches(key) -> "input_player1_${key.removePrefix("input_")}" to value
                HOTKEY_BIND.matches(key) && key != "input_menu_toggle_btn" -> key to value
                else -> null
            }
        }
    }

    /** Grant route: adds the controller binds to RomRunner's own config once, leaving anything already in it alone. */
    private fun ensureControllerBinds(context: Context, root: DocumentFile, packageName: String) {
        val configFile = root.findFile("files")?.findFile(CONFIG_NAME) ?: return
        val config = context.contentResolver.openInputStream(configFile.uri)?.bufferedReader()?.use { it.readText() } ?: return
        if ("input_player1_b_btn" in config) return
        val binds = controllerBinds(context, packageName)
        if (binds.isEmpty()) return
        val text = config.trimEnd() + "\n" + binds.joinToString("\n") { (k, v) -> "$k = \"$v\"" } + "\n"
        context.contentResolver.openOutputStream(configFile.uri, "wt")?.use { it.write(text.toByteArray()) }
    }

    // ---- Direct route: RomRunner has All files access and writes RetroArch's own settings file itself ----

    /** True when RomRunner can write RetroArch's external files folder straight through the file system (see [EmulatorFolders]). */
    fun canSetUpDirectly(context: Context): Boolean {
        val pkg = EmulatorLauncher.installedPackageFor(context, RETROARCH_PACKAGE) ?: return false
        return EmulatorFolders.canUseDirectly(pkg)
    }

    /** Sets [config]'s `key = "value"` lines, replacing a key that's already there and adding the rest, and leaves every other line alone. */
    private fun mergeConfig(config: String, entries: List<Pair<String, String>>): String {
        var text = config
        for ((key, value) in entries) {
            val line = Regex("(?m)^\\s*${Regex.escape(key)}\\s*=.*$")
            val replacement = "$key = \"$value\""
            text = if (line.containsMatchIn(text)) text.replace(line, Regex.escapeReplacement(replacement)) else text.trimEnd() + "\n" + replacement + "\n"
        }
        return text
    }

    /**
     * Writes RomRunner's settings into RetroArch's own `retroarch.cfg` (in its external files folder, which RetroArch
     * reads first), merged into whatever is already there, plus the mGBA shader preset beside it. With this in place the
     * ordinary sideload launch picks it all up, so no folder grant is needed.
     */
    private fun applyDirectConfig(context: Context, packageName: String) {
        val dir = EmulatorFolders.directDir(packageName)
        if (!dir.isDirectory) dir.mkdirs()
        val base = dataDir(context, packageName)
        val entries = configText(context, base).lineSequence()
            .mapNotNull { Regex("""^\s*([a-z0-9_]+)\s*=\s*"(.*)"\s*$""").find(it)?.destructured }
            .map { (k, v) -> k to v }.toList() + controllerBinds(context, packageName)
        val config = File(dir, "retroarch.cfg")
        config.writeText(mergeConfig(if (config.exists()) config.readText() else "", entries))

        val presetDir = File(dir, MGBA_PRESET_DIR).apply { mkdirs() }
        val preset = File(presetDir, "$MGBA_PRESET_DIR.glslp")
        if (!preset.exists()) preset.writeText("#reference \"$base/$MGBA_SHADER\"\n")

        // Left alone once it exists, so a preset the user saved from RetroArch's own shader menu isn't replaced.
        for (folder in CRT_PRESET_FOLDERS) {
            val crt = File(File(dir, folder).apply { mkdirs() }, "$folder.glslp")
            if (!crt.exists()) crt.writeText(crtPreset(base))
        }
    }

    /** Re-applies the direct config if RetroArch has replaced it with one that lacks RomRunner's settings (e.g. it wrote its own default first). */
    private fun ensureDirectConfig(context: Context, packageName: String) {
        val config = File(EmulatorFolders.directDir(packageName), "retroarch.cfg")
        if (config.exists() && Regex("""(?m)^menu_driver\s*=\s*"rgui"""").containsMatchIn(config.readText())) return
        applyDirectConfig(context, packageName)
    }

    /** Direct setup: the config and preset above, plus the libretro cores for [platformNames] downloaded ahead of time. Blocking I/O. */
    suspend fun setUpDirect(context: Context, platformNames: Collection<String>): SetupResult = withContext(Dispatchers.IO) {
        try {
            val pkg = EmulatorLauncher.installedPackageFor(context, RETROARCH_PACKAGE) ?: return@withContext SetupResult.Failed("RetroArch isn't installed.")
            applyDirectConfig(context, pkg)
            val cores = platformNames.mapNotNull { CORE_BY_PLATFORM[it] }.distinct().onEach { ensureCore(context, "${it}_libretro_android.so") }
            SetupResult.Done(cores)
        } catch (e: Exception) {
            SetupResult.Failed("Couldn't set up RetroArch: ${e.message ?: "unknown error"}")
        }
    }

    /** Puts the libretro core [coreName] in RetroArch's own cores folder (a core can only be loaded from there, not from shared storage). */
    private fun installCore(context: Context, root: DocumentFile, coreName: String) {
        val coresDir = ensureDir(root, "cores")
        val fileName = "${coreName}_libretro_android.so"
        if ((coresDir.findFile(fileName)?.length() ?: 0L) > 0L) return
        ensureCore(context, fileName)
        val source = coreUri(context, fileName) ?: error("downloaded core vanished")
        coresDir.findFile(fileName)?.delete()
        val target = EmulatorFolders.createNamedFile(coresDir, fileName) ?: error("couldn't create $fileName")
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
    suspend fun prepare(context: Context, platform: Platform, game: Game, folderUri: Uri?, coreOverride: String? = null): Prepared = withContext(Dispatchers.IO) {
        val retroArchPackage = EmulatorLauncher.installedPackageFor(context, RETROARCH_PACKAGE)
            ?: return@withContext Prepared.Failed("RetroArch isn't installed.")
        val coreName = coreOverride ?: CORE_BY_PLATFORM[platform.name]
            ?: return@withContext Prepared.Failed("No RetroArch core is set for ${platform.name}.")

        if (!hasStorageAccess(context)) {
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
                val base = dataDir(context, retroArchPackage)
                writeConfig(context, root, base, overwrite = false)
                writeShaderPreset(context, root, base)
                ensureControllerBinds(context, root, retroArchPackage)
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

        // No folder grant: if RomRunner can write RetroArch's settings directly, make sure they're in place; the
        // sideload launch below then starts the game with them.
        if (folderUri == null && EmulatorFolders.canUseDirectly(retroArchPackage)) {
            runCatching { ensureDirectConfig(context, retroArchPackage) }
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
