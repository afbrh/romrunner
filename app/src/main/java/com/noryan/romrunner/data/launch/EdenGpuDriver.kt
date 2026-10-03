package com.noryan.romrunner.data.launch

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Installs and selects a custom Mesa Turnip Vulkan driver in Eden on Snapdragon 8 Gen 1/2/3 handhelds.
 *
 * Why: on the AYN Thor (Adreno 740), Eden crashed every game within seconds with a null-pointer fault
 * inside Qualcomm's stock `vulkan.adreno.so`, on Eden's Vulkan worker thread. With Turnip selected the
 * same game ran at a steady 60 FPS (confirmed on-device).
 *
 * How: like PrimeHackControls, Eden's files live in Android/data (off limits to other apps on Android 13)
 * but Eden shares them through a DocumentProvider, so the user grants Eden's folder once in the system
 * picker. The driver zip goes into `gpu_drivers/` and Eden's `config/config.ini` gets `driver_path`
 * pointed at it — the same two things Eden's own "install driver" button does. This only edits an
 * existing config.ini (Eden writes one the first time it's opened); it never invents one.
 */
object EdenGpuDriver {

    const val PACKAGE = "dev.eden.eden_emulator"
    private const val AUTHORITY = "$PACKAGE.user"
    private const val RELEASES_URL = "https://api.github.com/repos/K11MCH1/AdrenoToolsDrivers/releases"
    private val DRIVER_ASSET = Regex("""^Turnip_v[0-9.]+_R[0-9]+\.zip$""")

    /** Snapdragon 8 Gen 1 / 8+ Gen 1 / 8 Gen 2 / 8 Gen 3 (Adreno 730–750), which the "ad07xx" Turnip build targets. */
    private val ELIGIBLE_SOC = Regex("""^(SM|QCS|QCM)(8450|8475|8550|8650)$""")

    sealed interface Result {
        data object Applied : Result

        /** Eden has never been opened, so it hasn't written its settings file yet; nothing is wrong, it just has to run once. */
        data object NotOpenedYet : Result

        data class Failed(val message: String) : Result
    }

    /** True on handhelds where a Turnip driver is the right fix; elsewhere Eden's own driver is left alone. */
    fun isEligible(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ELIGIBLE_SOC.matches(Build.SOC_MODEL.trim().uppercase())

    fun pickerInitialUri(): Uri = DocumentsContract.buildDocumentUri(AUTHORITY, "root")

    fun isEdenTree(treeUri: Uri): Boolean = treeUri.authority == AUTHORITY

    /** Blocking I/O, run on IO. Safe to call repeatedly: it re-selects the same driver each time. */
    suspend fun apply(context: Context, treeUri: Uri): Result = withContext(Dispatchers.IO) {
        if (!isEligible()) return@withContext Result.Failed("This device doesn't need a custom Eden graphics driver.")
        try {
            val root = DocumentFile.fromTreeUri(context, treeUri)
                ?: return@withContext Result.Failed("Couldn't open Eden's folder.")
            val configFile = root.findFile("config")?.findFile("config.ini")
                ?: return@withContext Result.NotOpenedYet

            val (driverName, driverFile) = fetchDriver(context)
                ?: return@withContext Result.Failed("Couldn't download the Turnip graphics driver.")

            val driversDir = root.findFile("gpu_drivers")?.takeIf { it.isDirectory }
                ?: root.createDirectory("gpu_drivers") ?: error("couldn't create gpu_drivers")
            val target = driversDir.findFile(driverName) ?: driversDir.createFile("application/zip", driverName)
                ?: error("couldn't create $driverName")
            context.contentResolver.openOutputStream(target.uri, "wt")?.use { out ->
                driverFile.inputStream().use { it.copyTo(out) }
            } ?: error("couldn't write $driverName")

            val driverPath = "${Environment.getExternalStorageDirectory().path}/Android/data/$PACKAGE/files/gpu_drivers/$driverName"
            val existing = context.contentResolver.openInputStream(configFile.uri)?.bufferedReader()?.use { it.readText() }
                ?: error("couldn't read Eden's settings")
            writeConfig(context, root.findFile("config")!!, configFile, withDriverPath(existing, driverPath))
            Result.Applied
        } catch (e: Exception) {
            Result.Failed("Couldn't set up Eden's graphics driver: ${e.message ?: "unknown error"}")
        }
    }

    /**
     * Overwrites config.ini in place; if that isn't permitted (a file left owned by another user, e.g.
     * after being copied in over adb), deletes it and recreates it through Eden's own provider, which
     * gives it back to Eden.
     */
    private fun writeConfig(context: Context, configDir: DocumentFile, configFile: DocumentFile, text: String) {
        try {
            context.contentResolver.openOutputStream(configFile.uri, "wt")?.bufferedWriter()?.use { it.write(text) }
                ?: error("couldn't write Eden's settings")
        } catch (e: Exception) {
            configFile.delete()
            val recreated = configDir.createFile("application/octet-stream", "config.ini") ?: error("couldn't recreate Eden's settings")
            context.contentResolver.openOutputStream(recreated.uri, "wt")?.bufferedWriter()?.use { it.write(text) }
                ?: error("couldn't write Eden's settings")
        }
    }

    /** The newest Turnip driver zip (cached after the first download so setup still works offline). */
    private fun fetchDriver(context: Context): Pair<String, File>? {
        val cacheDir = File(context.filesDir, "eden").apply { mkdirs() }
        val url = LatestReleaseFinder.findStableAssetUrl(RELEASES_URL) { DRIVER_ASSET.matches(it) }
        if (url != null) {
            val name = url.substringAfterLast('/')
            val file = File(cacheDir, name)
            if (file.exists() && file.length() > 0) return name to file
            try {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                }
                try {
                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        val partial = File(cacheDir, "$name.part")
                        connection.inputStream.use { input -> partial.outputStream().use { input.copyTo(it) } }
                        if (partial.renameTo(file)) return name to file
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                // fall through to any previously downloaded copy
            }
        }
        val cached = cacheDir.listFiles { f -> DRIVER_ASSET.matches(f.name) }?.maxByOrNull { it.lastModified() }
        return cached?.let { it.name to it }
    }

    /** Returns [ini] with `[GpuDriver] driver_path` set to [path], leaving every other line untouched. */
    private fun withDriverPath(ini: String, path: String): String {
        val lines = ini.lines().toMutableList()
        val start = lines.indexOfFirst { it.trim() == "[GpuDriver]" }
        val driverLines = listOf("driver_path\\default=false", "driver_path=$path")
        if (start == -1) return ini.trimEnd() + "\n\n[GpuDriver]\n" + driverLines.joinToString("\n") + "\n"
        var end = lines.size
        for (i in start + 1 until lines.size) {
            if (lines[i].trim().startsWith("[")) {
                end = i
                break
            }
        }
        val others = lines.subList(start + 1, end)
            .filterNot { it.startsWith("driver_path\\default=") || it.startsWith("driver_path=") }
        val rebuilt = lines.subList(0, start + 1) + driverLines + others + lines.subList(end, lines.size)
        return rebuilt.joinToString("\n")
    }
}
