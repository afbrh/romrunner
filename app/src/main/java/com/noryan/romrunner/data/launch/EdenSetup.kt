package com.noryan.romrunner.data.launch

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.zip.ZipInputStream

/**
 * Sets Eden (Nintendo Switch) up for this handheld, through the same one-time folder permission
 * [EdenGpuDriver] already uses:
 *  - installs the user's Switch keys (`prod.keys`, `title.keys`) and firmware from their Roms/BIOS folder;
 *  - hides Eden's on-screen touch controls (Eden also auto-hides them while a gamepad is connected) and
 *    its performance-stats and device-info overlays, all of which Eden shows by default;
 *  - installs the Turnip graphics driver on handhelds that need it (see [EdenGpuDriver]).
 *
 * Controls are deliberately left to Eden: on first launch it maps the connected gamepad by itself, and
 * the Odin's A/B/X/Y keycodes already land on the matching Switch buttons by position. Writing bindings
 * ourselves would switch that auto-map off and depend on a port number and device id we'd have to guess.
 *
 * Keys and firmware go in as plain files, which Eden finds on its next start. The config edits need
 * Eden's settings file, which Eden only creates the first time it runs (see [Result.NotOpenedYet]).
 */
object EdenSetup {

    sealed interface Result {
        /** One human-readable line per thing that was done (or couldn't be). */
        data class Applied(val summary: List<String>) : Result

        /** Keys/firmware were handled, but Eden hasn't been opened yet, so it has no settings file to edit. */
        data object NotOpenedYet : Result

        data class Failed(val message: String) : Result
    }

    /** Blocking I/O, run on IO. Safe to repeat: keys are overwritten and firmware is skipped once installed. */
    suspend fun apply(context: Context, treeUri: Uri, romsFolderUri: Uri?): Result = withContext(Dispatchers.IO) {
        try {
            val root = DocumentFile.fromTreeUri(context, treeUri)
                ?: return@withContext Result.Failed("Couldn't open Eden's folder.")
            val summary = mutableListOf<String>()

            val source = romsFolderUri?.let { DocumentFile.fromTreeUri(context, it) }
            if (source == null) {
                summary += "No Roms/BIOS folder is set, so Switch keys and firmware weren't installed."
            } else {
                val files = RomsFolderFiles.find(source) { name ->
                    name.equals("prod.keys", ignoreCase = true) || name.equals("title.keys", ignoreCase = true) ||
                        (name.endsWith(".zip", ignoreCase = true) && name.contains("firmware", ignoreCase = true))
                }
                summary += installKeys(context, root, files)
                summary += installFirmware(context, root, files)
            }

            val configDir = root.findFile("config")
            val configFile = configDir?.findFile("config.ini")
            if (configDir == null || configFile == null) return@withContext Result.NotOpenedYet

            val existing = context.contentResolver.openInputStream(configFile.uri)?.bufferedReader()?.use { it.readText() }
                ?: error("couldn't read Eden's settings")
            // All three default to on. show_performance_overlay is the master switch for the FPS / frame
            // time / build and driver info lines, and show_soc_overlay is Eden's "Device Info Overlay".
            val overlaysOff = listOf("show_input_overlay", "show_performance_overlay", "show_soc_overlay")
                .fold(existing) { ini, key -> EdenGpuDriver.withSetting(ini, "Overlay", key, "false") }
            EdenGpuDriver.writeConfig(context, configDir, configFile, overlaysOff)
            summary += "Hid Eden's on-screen controls and stats overlays."

            if (EdenGpuDriver.isEligible()) {
                when (val driver = EdenGpuDriver.apply(context, treeUri)) {
                    is EdenGpuDriver.Result.Applied -> summary += "Installed the graphics driver."
                    is EdenGpuDriver.Result.NotOpenedYet -> return@withContext Result.NotOpenedYet
                    is EdenGpuDriver.Result.Failed -> summary += driver.message
                }
            }
            Result.Applied(summary)
        } catch (e: Exception) {
            Result.Failed("Couldn't set up Eden: ${e.message ?: "unknown error"}")
        }
    }

    /** Copies `prod.keys` / `title.keys` into Eden's keys folder (created if Eden hasn't made it yet). */
    private fun installKeys(context: Context, root: DocumentFile, files: List<DocumentFile>): List<String> {
        val found = listOf("prod.keys", "title.keys").mapNotNull { name ->
            files.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { name to it }
        }
        if (found.none { it.first == "prod.keys" }) return listOf("No prod.keys in your Roms/BIOS folder, so Switch keys weren't installed.")
        val keysDir = ensureDir(root, "keys")
        for ((name, file) in found) {
            // Delete first: creating a document over an existing name would make "prod (1).keys".
            keysDir.findFile(name)?.delete()
            val target = keysDir.createFile("application/octet-stream", name) ?: error("couldn't create $name")
            context.contentResolver.openInputStream(file.uri)?.use { input ->
                context.contentResolver.openOutputStream(target.uri, "wt")?.use { input.copyTo(it) } ?: error("couldn't write $name")
            } ?: error("couldn't read $name")
        }
        return listOf("Installed ${found.joinToString(" and ") { it.first }}.")
    }

    /**
     * Unpacks the firmware ZIP (a file with "firmware" in its name, holding the .nca files flat) into
     * `nand/system/Contents/registered`. Skipped if that folder already has firmware in it.
     */
    private fun installFirmware(context: Context, root: DocumentFile, files: List<DocumentFile>): List<String> {
        val registered = listOf("nand", "system", "Contents", "registered").fold(root) { dir, name -> ensureDir(dir, name) }
        if (registered.listFiles().any { it.name?.endsWith(".nca", ignoreCase = true) == true }) return emptyList()

        val zip = files
            .filter { it.name?.endsWith(".zip", ignoreCase = true) == true }
            .maxByOrNull { it.name.orEmpty() }
            ?: return listOf("No firmware ZIP in your Roms/BIOS folder, so Switch firmware wasn't installed.")

        var count = 0
        ZipInputStream(context.contentResolver.openInputStream(zip.uri) ?: error("couldn't read ${zip.name}")).use { zipStream ->
            while (true) {
                val entry = zipStream.nextEntry ?: break
                val name = entry.name.substringAfterLast('/')
                if (entry.isDirectory || !name.endsWith(".nca", ignoreCase = true)) continue
                val target = registered.createFile("application/octet-stream", name) ?: error("couldn't create $name")
                context.contentResolver.openOutputStream(target.uri, "wt")?.use { zipStream.copyTo(it) } ?: error("couldn't write $name")
                count++
            }
        }
        return listOf(if (count > 0) "Installed Switch firmware ($count files)." else "The firmware ZIP held no .nca files.")
    }

    private fun ensureDir(parent: DocumentFile, name: String): DocumentFile =
        parent.findFile(name)?.takeIf { it.isDirectory } ?: parent.createDirectory(name) ?: error("couldn't create $name")
}
