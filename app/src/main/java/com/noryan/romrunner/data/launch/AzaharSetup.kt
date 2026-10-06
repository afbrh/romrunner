package com.noryan.romrunner.data.launch

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Azahar (Nintendo 3DS) keeps three things RomRunner would like to preset in its own private app storage:
 * which folder holds its data, its controller bindings, and whether the on-screen controls show. None of
 * those can be written from outside, so the first run is guided instead (see [wizardHint]).
 *
 * What *is* a plain file is `config/config.ini` inside the data folder, so when the user points Azahar at
 * their Roms folder (as the hint says), RomRunner can set a few renderer defaults there (see [applyDefaults]).
 */
object AzaharSetup {

    const val PACKAGE = "org.azahar_emu.azahar"

    /** A short line for a toast shown as Azahar opens, naming the folder to pick and the two settings to change. */
    fun wizardHint(romsFolderUri: Uri?): String {
        val folder = romsFolderUri?.lastPathSegment?.substringAfterLast(':')?.takeIf { it.isNotBlank() }
        return "Azahar: pick ${folder ?: "your Roms folder"} as the User Folder. Then Settings > Gamepad > Auto-Map Controller, " +
            "and in a game: Overlay Options > turn off Show Controller Overlay."
    }

    /**
     * Sets the defaults RomRunner prefers in Azahar's config.ini inside [romsFolderUri] (OpenGL, 4x
     * resolution, the frame limiter on at 100%, which Azahar's first-run file leaves off so games run as fast
     * as the hardware allows, and the performance overlay off). Returns true once they are written, false if Azahar hasn't created its config yet
     * (it hasn't been set up, or it was pointed at a different folder), so the caller can try again later.
     */
    suspend fun applyDefaults(context: Context, romsFolderUri: Uri?): Boolean = withContext(Dispatchers.IO) {
        val root = romsFolderUri?.let { DocumentFile.fromTreeUri(context, it) } ?: return@withContext false
        val file = root.findFile("config")?.takeIf { it.isDirectory }?.findFile("config.ini") ?: return@withContext false
        try {
            val existing = context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val updated = listOf(
                Triple("Renderer", "graphics_api", "1"),
                Triple("Renderer", "resolution_factor", "4"),
                Triple("Renderer", "use_frame_limit", "true"),
                Triple("Renderer", "frame_limit", "100"),
                // Azahar's on-screen performance stats (FPS, RAM, ...), off.
                Triple("Layout", "performance_overlay_enable", "false")
            ).fold(existing) { ini, (section, key, value) -> withSetting(ini, section, key, value) }
            if (updated != existing) {
                context.contentResolver.openOutputStream(file.uri, "wt")?.bufferedWriter()?.use { it.write(updated) } ?: return@withContext false
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** [ini] with `key = value` set inside its `[section]` (added if the key or the section is missing). */
    internal fun withSetting(ini: String, section: String, key: String, value: String): String {
        val lines = ini.lines().toMutableList()
        val start = lines.indexOfFirst { it.trim() == "[$section]" }
        if (start == -1) return ini.trimEnd() + "\n\n[$section]\n$key = $value\n"
        var end = lines.size
        for (i in start + 1 until lines.size) {
            if (lines[i].trim().startsWith("[")) {
                end = i
                break
            }
        }
        val existing = (start + 1 until end).firstOrNull { Regex("""^\s*${Regex.escape(key)}\s*=""").containsMatchIn(lines[it]) }
        if (existing != null) {
            lines[existing] = "$key = $value"
        } else {
            // Insert after the section's last non-blank line so a trailing blank line before the next section stays put.
            var insertAt = end
            while (insertAt > start + 1 && lines[insertAt - 1].isBlank()) insertAt--
            lines.add(insertAt, "$key = $value")
        }
        return lines.joinToString("\n")
    }
}
