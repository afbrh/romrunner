package com.noryan.romrunner.data.launch

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ARMSX2 (PlayStation 2) can't be configured from outside: it has no DocumentsProvider, its settings are
 * private to the app and rewritten at launch, and its first-run wizard (import a BIOS, add a ROM folder)
 * has to be completed inside the app itself. All RomRunner can do is open it and say what to pick.
 */
object Armsx2Setup {

    const val PACKAGE = "com.armsx2"

    /** PS2 BIOS dumps are usually named like "SCPH-70012.bin" (or "ps2-0230e-20080220.bin"). */
    private val BIOS_NAME = Regex("""(?i)^(scph|ps2).*\.bin$""")

    /** A short line for a toast shown over the wizard, naming the BIOS file found in [romsFolderUri] and the folder to add. */
    suspend fun wizardHint(context: Context, romsFolderUri: Uri?): String = withContext(Dispatchers.IO) {
        val root = romsFolderUri?.let { DocumentFile.fromTreeUri(context, it) }
        val bios = root?.let { RomsFolderFiles.find(it) { name -> BIOS_NAME.matches(name) }.firstOrNull()?.name }
        val folder = romsFolderUri?.lastPathSegment?.substringAfterLast(':')?.takeIf { it.isNotBlank() }
        "ARMSX2: import BIOS ${bios ?: "(your PS2 BIOS)"}, then add ROM folder ${folder ?: "(your Roms folder)"}"
    }
}
