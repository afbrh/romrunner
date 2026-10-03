package com.noryan.romrunner.data.launch

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.view.InputDevice
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Loads the community PrimeHack controller profile that matches this handheld (AYN Odin/Thor or
 * Retroid Pocket) into PrimeHack. The profiles are published by PrimeHack-Android itself, as assets
 * of its "Controller_config_files" release.
 *
 * PrimeHack keeps its config in Android/data/org.dolphinemu.primehack/files, which other apps can't
 * touch directly on Android 13. Dolphin-based apps do expose that folder through Android's file
 * picker, though (PrimeHack's DocumentProvider, enabled on API 24+), so the user picks PrimeHack's
 * folder once, RomRunner keeps the grant, and the profile is written through it.
 */
object PrimeHackControls {

    const val PACKAGE = "org.dolphinemu.primehack"
    private const val PROVIDER_AUTHORITY = "$PACKAGE.user"
    private const val RELEASES_URL = "https://api.github.com/repos/Starlightbotanist/PrimeHack-Android/releases"

    enum class DeviceProfile(val assetName: String, val label: String) {
        ODIN("PrimeHack.Odin.ini", "AYN Odin"),
        RETROID("PrimeHack.Retroid.ini", "Retroid Pocket")
    }

    sealed interface Result {
        data class Applied(val profile: DeviceProfile) : Result
        data class Failed(val message: String) : Result
    }

    /**
     * Which profile fits this device. The profiles bind to a controller by name ("Odin Controller",
     * "Retroid Pocket Controller"), so a connected controller with that name is the strongest signal;
     * the device manufacturer is the fallback.
     */
    fun detectProfile(): DeviceProfile? {
        val controllerNames = InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it)?.name }
        return when {
            controllerNames.any { it.equals("Odin Controller", ignoreCase = true) } -> DeviceProfile.ODIN
            controllerNames.any { it.contains("Retroid", ignoreCase = true) } -> DeviceProfile.RETROID
            Build.MANUFACTURER.equals("AYN", ignoreCase = true) -> DeviceProfile.ODIN
            Build.MANUFACTURER.contains("Retroid", ignoreCase = true) -> DeviceProfile.RETROID
            else -> null
        }
    }

    /** Where the system folder picker should open: PrimeHack's own root, so the user just confirms it. */
    fun pickerInitialUri(): Uri = DocumentsContract.buildDocumentUri(PROVIDER_AUTHORITY, "root")

    /** True if [treeUri] really is PrimeHack's folder, not some other folder the user picked by mistake. */
    fun isPrimeHackTree(treeUri: Uri): Boolean = treeUri.authority == PROVIDER_AUTHORITY

    /**
     * Downloads (or, offline, reuses the last downloaded copy of) the profile for this device and
     * writes it into PrimeHack through [treeUri]: as a loadable profile under Config/Profiles/Wiimote,
     * and as the active Wii Remote 1 mapping in Config/WiimoteNew.ini. Blocking I/O, run on IO.
     */
    suspend fun apply(context: Context, treeUri: Uri): Result = withContext(Dispatchers.IO) {
        val profile = detectProfile()
            ?: return@withContext Result.Failed("No PrimeHack controller profile for this device.")
        val profileText = fetchProfile(context, profile)
            ?: return@withContext Result.Failed("Couldn't download the ${profile.label} controller profile.")
        try {
            val root = DocumentFile.fromTreeUri(context, treeUri)
                ?: return@withContext Result.Failed("Couldn't open PrimeHack's folder.")
            val config = root.ensureDir("Config")
            val profilesDir = config.ensureDir("Profiles").ensureDir("Wiimote")
            writeText(context, profilesDir, profile.assetName, profileText)

            val body = profileText.lines().filterNot { it.trim() == "[Profile]" }.joinToString("\n").trim()
            val wiimoteIni = config.findFile("WiimoteNew.ini")
            val existing = wiimoteIni?.let { readText(context, it) }.orEmpty()
            writeText(context, config, "WiimoteNew.ini", replaceSection(existing, "Wiimote1", body))
            Result.Applied(profile)
        } catch (e: Exception) {
            Result.Failed("Couldn't write to PrimeHack's folder: ${e.message ?: "unknown error"}")
        }
    }

    private fun fetchProfile(context: Context, profile: DeviceProfile): String? {
        val cache = File(context.filesDir, "primehack/${profile.assetName}")
        val url = LatestReleaseFinder.findStableAssetUrl(RELEASES_URL) { it == profile.assetName }
        if (url != null) {
            try {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 15_000
                }
                try {
                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        val text = connection.inputStream.bufferedReader().use { it.readText() }
                        cache.parentFile?.mkdirs()
                        cache.writeText(text)
                        return text
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                // fall through to the cached copy
            }
        }
        return if (cache.exists()) cache.readText() else null
    }

    private fun DocumentFile.ensureDir(name: String): DocumentFile =
        findFile(name)?.takeIf { it.isDirectory } ?: createDirectory(name) ?: error("couldn't create $name")

    private fun readText(context: Context, file: DocumentFile): String =
        context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }.orEmpty()

    /** Replaces the file named [name] under [dir] (creating it if needed) with [text]. */
    private fun writeText(context: Context, dir: DocumentFile, name: String, text: String) {
        val file = dir.findFile(name) ?: dir.createFile("application/octet-stream", name) ?: error("couldn't create $name")
        // "wt" truncates; plain "w" can leave stale bytes after a shorter rewrite.
        context.contentResolver.openOutputStream(file.uri, "wt")?.bufferedWriter()?.use { it.write(text) }
            ?: error("couldn't write $name")
    }

    /** Replaces (or adds) a whole `[sectionName]` section in [existing] with [body], leaving every other section alone. */
    private fun replaceSection(existing: String, sectionName: String, body: String): String {
        val withoutSection = existing.replace(Regex("""\[$sectionName\][^\[]*"""), "")
        return withoutSection.trimEnd() + "\n\n[$sectionName]\n$body\n"
    }
}
