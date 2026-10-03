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
 *  - installs the user's Switch keys (`prod.keys`, `title.keys`) and firmware from their BIOS/Keys folder;
 *  - hides Eden's on-screen touch controls (Eden also auto-hides them while a gamepad is connected);
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
    suspend fun apply(context: Context, treeUri: Uri, biosKeysFolderUri: Uri?): Result = withContext(Dispatchers.IO) {
        try {
            val root = DocumentFile.fromTreeUri(context, treeUri)
                ?: return@withContext Result.Failed("Couldn't open Eden's folder.")
            val summary = mutableListOf<String>()

            val source = biosKeysFolderUri?.let { DocumentFile.fromTreeUri(context, it) }
            if (source == null) {
                summary += "No BIOS/Keys folder is set in Settings, so Switch keys and firmware weren't installed."
            } else {
                summary += installKeys(context, root, source)
                summary += installFirmware(context, root, source)
            }

            val configDir = root.findFile("config")
            val configFile = configDir?.findFile("config.ini")
            if (configDir == null || configFile == null) return@withContext Result.NotOpenedYet

            val existing = context.contentResolver.openInputStream(configFile.uri)?.bufferedReader()?.use { it.readText() }
                ?: error("couldn't read Eden's settings")
            EdenGpuDriver.writeConfig(
                context, configDir, configFile,
                EdenGpuDriver.withSetting(existing, "Overlay", "show_input_overlay", "false")
            )
            summary += "Hid Eden's on-screen controls."

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
    private fun installKeys(context: Context, root: DocumentFile, source: DocumentFile): List<String> {
        val available = source.listFiles()
        val found = listOf("prod.keys", "title.keys").mapNotNull { name ->
            available.firstOrNull { it.isFile && it.name.equals(name, ignoreCase = true) }?.let { name to it }
        }
        if (found.none { it.first == "prod.keys" }) return listOf("No prod.keys in your BIOS/Keys folder, so Switch keys weren't installed.")
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
    private fun installFirmware(context: Context, root: DocumentFile, source: DocumentFile): List<String> {
        val registered = listOf("nand", "system", "Contents", "registered").fold(root) { dir, name -> ensureDir(dir, name) }
        if (registered.listFiles().any { it.name?.endsWith(".nca", ignoreCase = true) == true }) return emptyList()

        val zip = source.listFiles()
            .filter { it.isFile && it.name?.endsWith(".zip", ignoreCase = true) == true && it.name?.contains("firmware", ignoreCase = true) == true }
            .maxByOrNull { it.name.orEmpty() }
            ?: return listOf("No firmware ZIP in your BIOS/Keys folder, so Switch firmware wasn't installed.")

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
