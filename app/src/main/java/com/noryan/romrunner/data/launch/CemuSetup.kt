package com.noryan.romrunner.data.launch

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.view.InputDevice
import android.view.KeyEvent
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sets Cemu (Wii U) up for this handheld: a Wii U Pro Controller profile that matches the built-in
 * gamepad, plus the Wii U keys and online files from the user's Roms/BIOS folder if they have any.
 *
 * Cemu doesn't map a gamepad by itself (the user has to do it by hand in its settings), and it keeps
 * its files in Android/data/info.cemu.cemu/files, which other apps can only reach through Cemu's
 * DocumentsProvider after a one-time folder permission — the same arrangement as PrimeHack and Eden.
 *
 * Cemu reads controllerProfiles/ once, when its process starts, and RomRunner's folder access is
 * itself what starts that process, so the profile is always written too late for the running copy.
 * The caller has to ask the user to Force stop Cemu once afterwards (see ForceStopDialog).
 */
object CemuSetup {

    const val PACKAGE = "info.cemu.cemu"
    private const val AUTHORITY = "$PACKAGE.provider"

    /** Files Cemu looks for in its own folder that the user may keep in their Roms/BIOS folder. */
    private val SUPPORT_FILES = listOf("keys.txt", "otp.bin", "seeprom.bin")

    sealed interface Result {
        /** [controller] is the name of the gamepad the profile was written for, or null if none was connected. */
        data class Applied(val controller: String?, val copiedFiles: List<String>) : Result
        data class Failed(val message: String) : Result
    }

    fun pickerInitialUri(): Uri = DocumentsContract.buildDocumentUri(AUTHORITY, "root")

    fun isCemuTree(treeUri: Uri): Boolean = treeUri.authority == AUTHORITY

    /** The handheld's own gamepad if it can be recognised, else any connected physical gamepad. */
    private fun findController(): InputDevice? {
        val pads = InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }.filter {
            !it.isVirtual && (it.sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
        }
        return pads.firstOrNull { it.name.equals("Odin Controller", ignoreCase = true) || it.name.contains("Retroid", ignoreCase = true) }
            ?: pads.firstOrNull()
    }

    /** Blocking I/O, run on IO. [romsFolderUri] is the user's Roms/BIOS folder, searched for the key files. */
    suspend fun apply(context: Context, treeUri: Uri, romsFolderUri: Uri?): Result = withContext(Dispatchers.IO) {
        try {
            val root = DocumentFile.fromTreeUri(context, treeUri)
                ?: return@withContext Result.Failed("Couldn't open Cemu's folder.")

            val controller = findController()
            if (controller != null) {
                val profiles = root.findFile("controllerProfiles")?.takeIf { it.isDirectory }
                    ?: root.createDirectory("controllerProfiles") ?: error("couldn't create controllerProfiles")
                writeFile(context, profiles, "controller0.xml", proControllerProfile(controller).toByteArray())
            }

            val copied = mutableListOf<String>()
            val source = romsFolderUri?.let { DocumentFile.fromTreeUri(context, it) }
            if (source != null) {
                val available = RomsFolderFiles.find(source) { found -> SUPPORT_FILES.any { it.equals(found, ignoreCase = true) } }
                for (name in SUPPORT_FILES) {
                    val file = available.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: continue
                    val bytes = context.contentResolver.openInputStream(file.uri)?.use { it.readBytes() } ?: continue
                    writeFile(context, root, name, bytes)
                    copied += name
                }
            }
            Result.Applied(controller?.name, copied)
        } catch (e: Exception) {
            Result.Failed("Couldn't write to Cemu's folder: ${e.message ?: "unknown error"}")
        }
    }

    /**
     * Replaces [name] under [dir]. An existing file is deleted first: Cemu creates `keys.txt` itself
     * on first use, and creating a document over an existing name would make "keys (1).txt" instead.
     */
    private fun writeFile(context: Context, dir: DocumentFile, name: String, bytes: ByteArray) {
        dir.findFile(name)?.delete()
        val file = dir.createFile("application/octet-stream", name) ?: error("couldn't create $name")
        context.contentResolver.openOutputStream(file.uri, "wt")?.use { it.write(bytes) } ?: error("couldn't write $name")
    }

    // Wii U Pro Controller input ids (Cemu's NativeInput.ProButton) paired with the Android key each
    // is bound to. The Wii U face buttons are placed by position — A right, B bottom, X top, Y left —
    // and this handheld reports its right button as BUTTON_A, bottom BUTTON_B, top BUTTON_X and left
    // BUTTON_Y, so they line up one to one.
    private val BUTTONS = listOf(
        1 to KeyEvent.KEYCODE_BUTTON_A,
        2 to KeyEvent.KEYCODE_BUTTON_B,
        3 to KeyEvent.KEYCODE_BUTTON_X,
        4 to KeyEvent.KEYCODE_BUTTON_Y,
        5 to KeyEvent.KEYCODE_BUTTON_L1,
        6 to KeyEvent.KEYCODE_BUTTON_R1,
        7 to KeyEvent.KEYCODE_BUTTON_L2,
        8 to KeyEvent.KEYCODE_BUTTON_R2,
        9 to KeyEvent.KEYCODE_BUTTON_START,
        10 to KeyEvent.KEYCODE_BUTTON_SELECT,
        12 to KeyEvent.KEYCODE_DPAD_UP,
        13 to KeyEvent.KEYCODE_DPAD_DOWN,
        14 to KeyEvent.KEYCODE_DPAD_LEFT,
        15 to KeyEvent.KEYCODE_DPAD_RIGHT,
        16 to KeyEvent.KEYCODE_BUTTON_THUMBL,
        17 to KeyEvent.KEYCODE_BUTTON_THUMBR
    )

    // Sticks use Cemu's own axis-direction constants (NativeInput.Axis): the left stick is Android
    // AXIS_X/Y and the right stick AXIS_Z/RZ, which Cemu calls the rotation axes.
    private val STICKS = listOf(
        18 to 45, 19 to 39, 20 to 44, 21 to 38, // left: up (Y-), down (Y+), left (X-), right (X+)
        22 to 47, 23 to 41, 24 to 46, 25 to 40  // right: up (rotation Y-), down (+), left (rotation X-), right (+)
    )

    private fun proControllerProfile(device: InputDevice): String {
        @Suppress("DEPRECATION")
        val uuid = device.descriptor
        val entries = (BUTTONS + STICKS).joinToString("") { (id, code) ->
            "<entry><mapping>$id</mapping><button>$code</button></entry>"
        }
        return """<?xml version="1.0" encoding="UTF-8"?>
<emulated_controller>
<type>Wii U Pro Controller</type>
<controller>
<api>Android</api>
<uuid>${xml(uuid)}</uuid>
<display_name>${xml(device.name)}</display_name>
<mappings>$entries</mappings>
</controller>
</emulated_controller>
"""
    }

    private fun xml(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
