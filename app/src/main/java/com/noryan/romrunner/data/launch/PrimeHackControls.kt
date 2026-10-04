package com.noryan.romrunner.data.launch

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.view.InputDevice
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sets PrimeHack up for this handheld: installs the community controller profiles for AYN Odin/Thor
 * and Retroid Pocket (so both show up in PrimeHack's profile list, with the one that matches this
 * device made active, plus the matching GameCube pad mapping), and writes the graphics defaults we settled on while getting PrimeHack running
 * well on this hardware. The two profiles are the ones PrimeHack-Android publishes as assets of its
 * "Controller_config_files" release; RomRunner ships its own copies (assets/primehack/), so this works
 * offline and doesn't depend on that release staying up.
 *
 * PrimeHack keeps its config in Android/data/org.dolphinemu.primehack/files, which other apps can't
 * touch directly on Android 13. Dolphin-based apps do expose that folder through Android's file
 * picker, though (PrimeHack's DocumentProvider, enabled on API 24+), so the user picks PrimeHack's
 * folder once, RomRunner keeps the grant, and everything is written through it.
 */
object PrimeHackControls {

    const val PACKAGE = "org.dolphinemu.primehack"
    private const val PROVIDER_AUTHORITY = "$PACKAGE.user"

    enum class DeviceProfile(val assetName: String, val label: String) {
        ODIN("PrimeHack.Odin.ini", "AYN Odin"),
        RETROID("PrimeHack.Retroid.ini", "Retroid Pocket")
    }

    sealed interface Result {
        /** [profile] is null when this device isn't a known handheld: graphics defaults and both profiles are still installed, but none is made active. */
        data class Applied(val profile: DeviceProfile?) : Result
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

    /**
     * Where the system folder picker should open: PrimeHack's own root, so the user just confirms it.
     * Has to be a *root* address: the picker ignores a document or tree address for providers like this
     * one (tested on-device), which don't implement findDocumentPath, but does open on a root.
     */
    fun pickerInitialUri(): Uri = DocumentsContract.buildRootUri(PROVIDER_AUTHORITY, "root")

    /** True if [treeUri] really is PrimeHack's folder, not some other folder the user picked by mistake. */
    fun isPrimeHackTree(treeUri: Uri): Boolean = treeUri.authority == PROVIDER_AUTHORITY

    /**
     * Writes everything through [treeUri]: both controller profiles under Config/Profiles/Wiimote
     * (where PrimeHack's profile list reads them), this device's profile as the active Wii Remote 1
     * mapping in Config/WiimoteNew.ini, and the graphics defaults. Blocking I/O, run on IO.
     */
    suspend fun apply(context: Context, treeUri: Uri?): Result = withContext(Dispatchers.IO) {
        val profile = detectProfile()
        try {
            val root = EmulatorFolders.open(context, PACKAGE, treeUri)
                ?: return@withContext Result.Failed("Couldn't open PrimeHack's folder.")
            val config = root.ensureDir("Config")

            val profilesDir = config.ensureDir("Profiles").ensureDir("Wiimote")
            for (candidate in DeviceProfile.entries) {
                writeText(context, profilesDir, candidate.assetName, loadProfile(context, candidate))
            }
            if (profile != null) {
                val body = loadProfile(context, profile).lines().filterNot { it.trim() == "[Profile]" }.joinToString("\n").trim()
                val wiimoteIni = config.findFile("WiimoteNew.ini")
                val existing = wiimoteIni?.let { readText(context, it) }.orEmpty()
                writeText(context, config, "WiimoteNew.ini", replaceSection(existing, "Wiimote1", body))

                // The profiles above only cover the Wii Remote, and PrimeHack has no default button
                // bindings at all for GameCube pad 1, so GameCube discs would ignore the controller.
                val device = Regex("""(?m)^Device\s*=\s*(.+)$""").find(loadProfile(context, profile))?.groupValues?.get(1)?.trim()
                if (device != null) editIni(context, config, "GCPadNew.ini") { ini -> withGameCubePad(ini, device) }
            }

            editIni(context, config, "Dolphin.ini") { ini ->
                // Hide the on-screen touch overlay: a physical controller is expected. Cheats and save states are
                // both off in Dolphin by default; they're switched on so Gecko codes run and the quick menu's
                // save/load state works.
                listOf("EnableCheats", "EnableSaveStates")
                    .fold(setIniValue(ini, "Android", "ShowInputOverlay", "False")) { acc, key -> setIniValue(acc, "Core", key, "True") }
            }
            editIni(context, config, "GFX.ini") { ini ->
                // Compile shaders before a game starts, and use asynchronous ubershaders for the ones
                // that still turn up mid-game. The second is the one that matters: a cold-compile burst
                // used to spike memory/CPU enough to get PrimeHack OOM-killed.
                setIniValue(setIniValue(ini, "Settings", "WaitForShadersBeforeStarting", "True"), "Settings", "ShaderCompilationMode", "2")
            }
            applySuperMarioSunshineDefaults(context, root, config)
            Result.Applied(profile)
        } catch (e: Exception) {
            Result.Failed("Couldn't write to PrimeHack's folder: ${e.message ?: "unknown error"}")
        }
    }

    /**
     * Super Mario Sunshine: turns on its bundled Widescreen and 60FPS Gecko codes and forces a real
     * 16:9 render. The codes alone leave a letterboxed 4:3 picture, and forcing the aspect without the
     * widescreen hack stretches it, so all three are set (confirmed on-device). Per-game settings live
     * in `GameSettings/<gameId>.ini`, a sibling of `Config`, and Gecko codes only run when the global
     * `EnableCheats` switch is on.
     */
    private fun applySuperMarioSunshineDefaults(context: Context, root: DocumentFile, config: DocumentFile) {
        editIni(context, config, "Dolphin.ini") { setIniValue(it, "Core", "EnableCheats", "True") }
        editIni(context, root.ensureDir("GameSettings"), "GMSE01.ini") { ini ->
            var out = ini
            val existing = ini.lines().map { it.trim() }
            val codes = listOf("\$Widescreen", "\$60FPS").filter { it !in existing }
            if (codes.isNotEmpty()) {
                out = if ("[Gecko_Enabled]" in existing) {
                    out.lines().flatMap { if (it.trim() == "[Gecko_Enabled]") listOf(it) + codes else listOf(it) }.joinToString("\n")
                } else {
                    out.trimEnd() + "\n\n[Gecko_Enabled]\n" + codes.joinToString("\n") + "\n"
                }
            }
            out = setIniValue(out, "Video_Settings", "AspectRatio", "1")
            setIniValue(out, "Video_Settings", "wideScreenHack", "True")
        }
    }

    /**
     * Merges a GameCube pad mapping for this handheld into `[GCPad1]` of [ini], leaving any other keys
     * (PrimeHack's own `PrimeHack/Mode`, other pads) alone. Settled on-device with the real controller:
     * the face buttons are mapped by physical position (the handheld's bottom button reports
     * BUTTON_B and its right one BUTTON_A), L2/R2 need both the click and the analog axis (22/23 on
     * this hardware) or the triggers never reach full power, and Z has no natural equivalent so it
     * goes on either bumper.
     */
    private fun withGameCubePad(ini: String, device: String): String {
        val mapping = listOf(
            "Device" to device,
            "Buttons/A" to "`Button A`",
            "Buttons/B" to "`Button B`",
            "Buttons/X" to "`Button Y`",
            "Buttons/Y" to "`Button X`",
            "Buttons/Start" to "`Start`",
            "Buttons/Z" to "`Button L1` | `Button R1`",
            "Triggers/L" to "`Button L2`",
            "Triggers/R" to "`Button R2`",
            "Triggers/L-Analog" to "`Axis 23+`",
            "Triggers/R-Analog" to "`Axis 22+`",
            "D-Pad/Up" to "`Up`",
            "D-Pad/Down" to "`Down`",
            "D-Pad/Left" to "`Left`",
            "D-Pad/Right" to "`Right`",
            "Main Stick/Up" to "`Axis 1-`",
            "Main Stick/Down" to "`Axis 1+`",
            "Main Stick/Left" to "`Axis 0-`",
            "Main Stick/Right" to "`Axis 0+`",
            "C-Stick/Up" to "`Axis 14-`",
            "C-Stick/Down" to "`Axis 14+`",
            "C-Stick/Left" to "`Axis 11-`",
            "C-Stick/Right" to "`Axis 11+`"
        )
        return mapping.fold(ini) { acc, (key, value) -> setIniValue(acc, "GCPad1", key, value) }
    }

    private fun loadProfile(context: Context, profile: DeviceProfile): String =
        context.assets.open("primehack/${profile.assetName}").bufferedReader().use { it.readText() }

    /** Reads [name] under [dir] (empty if it doesn't exist yet), runs [edit] on it, and writes the result back. */
    private fun editIni(context: Context, dir: DocumentFile, name: String, edit: (String) -> String) {
        val existing = dir.findFile(name)?.let { readText(context, it) }.orEmpty()
        writeText(context, dir, name, edit(existing))
    }

    /**
     * Sets `key = value` inside `[section]` of [ini], adding the section or key if missing and leaving
     * every other line alone — these files already hold settings PrimeHack wrote itself.
     */
    private fun setIniValue(ini: String, section: String, key: String, value: String): String {
        val lines = ini.lines().toMutableList()
        val header = "[$section]"
        val start = lines.indexOfFirst { it.trim() == header }
        if (start == -1) return ini.trimEnd() + (if (ini.isBlank()) "" else "\n\n") + "$header\n$key = $value\n"
        var end = lines.size
        for (i in start + 1 until lines.size) {
            if (lines[i].trim().startsWith("[")) {
                end = i
                break
            }
        }
        val keyIndex = (start + 1 until end).firstOrNull { lines[it].trim().startsWith("$key ") || lines[it].trim().startsWith("$key=") }
        if (keyIndex != null) lines[keyIndex] = "$key = $value" else lines.add(end, "$key = $value")
        return lines.joinToString("\n")
    }

    private fun DocumentFile.ensureDir(name: String): DocumentFile =
        findFile(name)?.takeIf { it.isDirectory } ?: createDirectory(name) ?: error("couldn't create $name")

    private fun readText(context: Context, file: DocumentFile): String =
        context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }.orEmpty()

    /** Replaces the file named [name] under [dir] (creating it if needed) with [text]. */
    private fun writeText(context: Context, dir: DocumentFile, name: String, text: String) {
        val file = dir.findFile(name) ?: EmulatorFolders.createNamedFile(dir, name) ?: error("couldn't create $name")
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
