package com.noryan.romrunner.data.launch

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Environment
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * ARMSX2 (PlayStation 2) keeps its live settings in private storage, so most of it can't be configured from outside,
 * and its first-run wizard (import a BIOS, add a ROM folder) has to be completed inside the app. But it does read a few
 * files from its data folder (`Android/data/com.armsx2/files`) that RomRunner can write ahead of its very first start:
 *
 *  - `armsx2-settings.json`: restored as ARMSX2's global settings when the wizard finishes on a fresh install (never
 *    on one that has already been set up). We set 2x upscaling and the BIOS file name.
 *  - `bios/`: the BIOS file copied in from the user's Roms/BIOS folder (the wizard still asks for a BIOS once).
 *  - `inputprofiles` (a `.pad.json` file): a controller profile with this handheld's face buttons. ARMSX2 lists it in the Pad tab
 *    but only applies a profile when it's tapped there.
 *  - `inputprofiles/Default.touch.json`: an on-screen-controls layout with every widget switched off.
 *
 * Not possible from outside, because they live in private preferences: inverting the right stick, and the
 * on-screen controls' "never show" setting itself.
 */
object Armsx2Setup {

    const val PACKAGE = "com.armsx2"

    /** The restorable settings backup RomRunner writes for the user to pick in ARMSX2 (see [writeRestoreBackup]). */
    const val BACKUP_NAME = "ARMSX2-RomRunner-settings.zip"

    /** PS2 BIOS dumps are usually named like "SCPH-70012.bin" (or "ps2-0230e-20080220.bin"). */
    private val BIOS_NAME = Regex("""(?i)^(scph|ps2).*\.bin$""")

    /** A short line for a toast shown over the wizard, naming the BIOS file found in [romsFolderUri] and the folder to add. */
    suspend fun wizardHint(context: Context, romsFolderUri: Uri?): String = withContext(Dispatchers.IO) {
        val root = romsFolderUri?.let { DocumentFile.fromTreeUri(context, it) }
        val bios = root?.let { RomsFolderFiles.find(it) { name -> BIOS_NAME.matches(name) }.firstOrNull()?.name }
        val folder = romsFolderUri?.lastPathSegment?.substringAfterLast(':')?.takeIf { it.isNotBlank() }
        "ARMSX2: import BIOS ${bios ?: "(your PS2 BIOS)"}, then add ROM folder ${folder ?: "(your Roms folder)"}. " +
            "Then Settings > App > Restore backup: $BACKUP_NAME in Downloads/RomRunner."
    }

    sealed interface PreloadResult {
        data class Applied(val summary: List<String>) : PreloadResult
        /** ARMSX2 has already been set up (its settings file exists), and it ignores a pre-seeded one from then on. */
        data object AlreadyOpened : PreloadResult
        data class Failed(val message: String) : PreloadResult
    }

    /**
     * Writes everything listed on [Armsx2Setup] into ARMSX2's data folder. Needs RomRunner's All files access and
     * only does anything before ARMSX2's first set-up. Blocking I/O, run on IO.
     */
    suspend fun preload(context: Context, romsFolderUri: Uri?): PreloadResult = withContext(Dispatchers.IO) {
        try {
            if (!EmulatorFolders.canUseDirectly(PACKAGE)) return@withContext PreloadResult.Failed("Turn on All files access for RomRunner first.")
            val dir = EmulatorFolders.directDir(PACKAGE)
            val settingsFile = File(dir, "armsx2-settings.json")
            // ARMSX2 rewrites this file with its full settings once it has adopted it, so a file holding only our keys is
            // ours from an earlier run (e.g. one that was interrupted before it was recorded) and is safe to write again.
            if (settingsFile.exists() && !isOurOwnFile(settingsFile)) return@withContext PreloadResult.AlreadyOpened

            val summary = mutableListOf<String>()
            val biosName = runCatching { copyBios(context, dir, romsFolderUri) }.getOrNull()
            if (biosName != null) summary += "BIOS $biosName"

            val global = JSONObject().put("upscaleFloat", 2.0)
            if (biosName != null) global.put("biosFilename", biosName)
            settingsFile.writeText(JSONObject().put("global", global).toString())
            summary += "2x upscaling"

            val profiles = File(dir, "inputprofiles").also { it.mkdirs() }
            runCatching {
                File(profiles, "RomRunner Thor.pad.json").writeText(padProfile())
                summary += "controller profile (pick it in ARMSX2's Pad tab)"
            }
            runCatching {
                File(profiles, "Default.touch.json").writeText(hiddenTouchLayout())
                summary += "on-screen controls hidden"
            }
            runCatching {
                writeRestoreBackup(context, romsFolderUri, biosName, global)
                summary += "settings backup to restore in ARMSX2 ($BACKUP_NAME)"
            }
            PreloadResult.Applied(summary)
        } catch (e: Exception) {
            PreloadResult.Failed("Couldn't preload ARMSX2: ${e.message ?: "unknown error"}")
        }
    }

    /** True while ARMSX2 hasn't finished its first-run wizard yet: its settings file is still just the one RomRunner wrote. */
    fun wizardPending(): Boolean {
        val file = File(EmulatorFolders.directDir(PACKAGE), "armsx2-settings.json")
        return file.exists() && isOurOwnFile(file)
    }

    /**
     * ARMSX2 keeps its controller, on-screen control and second-screen settings in private preferences that nothing outside it
     * can write, but its own Settings > App > Restore backup replaces those preferences with the ones in a backup zip. So this
     * writes a zip holding exactly the preferences RomRunner wants (and what ARMSX2 needs to count its setup as done), into
     * Downloads/RomRunner, for the user to pick there once the wizard is finished. The same format ARMSX2's own Backup writes:
     * `armsx2-backup.json` and `prefs/ARMSX2.xml`.
     *
     * Keeps the wizard's results (done flag, the Roms folder, the BIOS), then sets: 2x upscaling and the BIOS, the Thor's face
     * buttons, the right stick's X axis inverted, the on-screen controls never shown with the pause button tap-to-reveal, and the
     * second-screen panel on when this device has a second display.
     */
    private fun writeRestoreBackup(context: Context, romsFolderUri: Uri?, biosName: String?, globalSettings: JSONObject) {
        val ints = linkedMapOf(
            "pad.map.cross" to 97, "pad.map.circle" to 96, "pad.map.triangle" to 99, "pad.map.square" to 100, // BUTTON_B, A, X, Y
            "touch.visibilityMode" to 0 // never show the on-screen controls
        )
        val booleans = linkedMapOf(
            "setupComplete" to true,
            "pad.rstick.invertX" to true,
            "touch.pauseTapToReveal" to true
        )
        if (hasSecondDisplay(context)) booleans["secondScreen.enabled"] = true
        val strings = linkedMapOf("config.global" to globalSettings.toString())
        romsFolderUri?.let { strings["romsDirs"] = JSONArray().put(it.toString()).toString() }
        biosName?.let {
            strings["bios"] = File(File(EmulatorFolders.directDir(PACKAGE), "bios"), it).absolutePath
        }

        val xml = buildString {
            append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n")
            booleans.forEach { (k, v) -> append("    <boolean name=\"$k\" value=\"$v\" />\n") }
            ints.forEach { (k, v) -> append("    <int name=\"$k\" value=\"$v\" />\n") }
            strings.forEach { (k, v) -> append("    <string name=\"$k\">${xmlEscape(v)}</string>\n") }
            append("</map>\n")
        }
        val out = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "RomRunner").also { it.mkdirs() }
        ZipOutputStream(File(out, BACKUP_NAME).outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("armsx2-backup.json"))
            zip.write("""{"schemaVersion":1,"package":"$PACKAGE","versionName":"RomRunner"}""".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("prefs/ARMSX2.xml"))
            zip.write(xml.toByteArray())
            zip.closeEntry()
        }
    }

    private fun xmlEscape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** Whether this device has a second screen for apps to use (the Thor and other two-screen handhelds). */
    private fun hasSecondDisplay(context: Context): Boolean {
        val displays = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return displays.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).any { it.name !in setOf("HiddenDisplay", "WebRTC_ScreenCapture") }
    }

    private fun isOurOwnFile(file: File): Boolean = runCatching {
        val keys = JSONObject(file.readText()).optJSONObject("global")?.keys()?.asSequence()?.toSet().orEmpty()
        keys.isNotEmpty() && keys.all { it == "upscaleFloat" || it == "biosFilename" }
    }.getOrDefault(false)

    /** Copies the PS2 BIOS found in the user's Roms/BIOS folder into ARMSX2's `bios` folder; its file name, or null if there's none. */
    private fun copyBios(context: Context, dir: File, romsFolderUri: Uri?): String? {
        val root = romsFolderUri?.let { DocumentFile.fromTreeUri(context, it) } ?: return null
        // A real BIOS dump is a few MB; this skips unrelated files with similar names.
        val source = RomsFolderFiles.find(root) { name -> BIOS_NAME.matches(name) }
            .firstOrNull { it.length() in 1_000_000L..16_000_000L } ?: return null
        val name = source.name ?: return null
        val target = File(File(dir, "bios").also { it.mkdirs() }, name)
        if (!target.exists() || target.length() != source.length()) {
            context.contentResolver.openInputStream(source.uri)?.use { input -> target.outputStream().use { input.copyTo(it) } } ?: return null
        }
        return name
    }

    /** This handheld's face buttons: the bottom button reports BUTTON_B and the right one BUTTON_A, so cross is B, circle A, triangle X, square Y. */
    private fun padProfile(): String = JSONObject()
        .put("name", "RomRunner Thor")
        .put(
            "values",
            JSONObject()
                .put("pad.map.cross", 97) // KEYCODE_BUTTON_B
                .put("pad.map.circle", 96) // KEYCODE_BUTTON_A
                .put("pad.map.triangle", 99) // KEYCODE_BUTTON_X
                .put("pad.map.square", 100) // KEYCODE_BUTTON_Y
        )
        .toString()

    private val TOUCH_WIDGETS = listOf(
        "CROSS", "CIRCLE", "SQUARE", "TRIANGLE", "L1", "R1", "L2", "R2", "START", "SELECT", "L3", "R3", "DPAD",
        "L_STICK", "R_STICK", "PAUSE", "FAST_FORWARD", "SAVE_STATE", "LOAD_STATE", "SCREENSHOT",
        "MACRO1", "MACRO2", "MACRO3", "MACRO4", "PRESSURE", "ANALOG_EXTRA"
    )

    /** A touch layout with every widget switched off (ARMSX2 fills any id left out with its default, so all are listed). */
    private fun hiddenTouchLayout(): String {
        val buttons = JSONArray()
        for (id in TOUCH_WIDGETS) {
            buttons.put(JSONObject().put("id", id).put("x", 0.5).put("y", 0.5).put("size", 58).put("on", false).put("hold", false).put("turbo", 0))
        }
        return JSONObject().put("name", "Default").put("layout", JSONObject().put("buttons", buttons)).toString()
    }
}
