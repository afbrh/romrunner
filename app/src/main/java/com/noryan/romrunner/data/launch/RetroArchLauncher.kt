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
import android.provider.MediaStore
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

    sealed interface Prepared {
        data class Ready(val intent: Intent) : Prepared

        /** RetroArch has never been set up (no storage permission yet); [intent] opens it so its first-run prompt appears. */
        data class NeedsFirstRun(val intent: Intent?) : Prepared

        data class Failed(val message: String) : Prepared
    }

    /** Blocking network/disk work happens on Dispatchers.IO; the returned Intent is started by the caller. */
    suspend fun prepare(context: Context, platform: Platform, game: Game): Prepared = withContext(Dispatchers.IO) {
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
