package com.noryan.romrunner.data.launch

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.noryan.romrunner.data.embedded.EdenIntegration
import com.noryan.romrunner.data.embedded.PS2EmbeddedLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What [BiosKeysImporter] found and successfully imported from a scanned folder. */
data class BiosKeysImportResult(
    val ps2BiosImported: Boolean,
    val switchKeysImported: Boolean,
    val switchFirmwareImported: Boolean
) {
    val importedAnything: Boolean get() = ps2BiosImported || switchKeysImported || switchFirmwareImported
}

/**
 * Scans one user-chosen folder (and every subfolder) for the BIOS/keys/firmware files RomRunner's
 * embedded cores need, recognizing each by filename convention instead of asking the user to hunt
 * down and pick them one at a time:
 *  - Nintendo Switch keys: a file literally named "prod.keys"
 *  - Nintendo Switch firmware: a .zip file with "firmware" somewhere in its name
 *  - PS2 BIOS: a .bin file with "SCPH" somewhere in its name (e.g. SCPH-70012.bin)
 * Each match is copied into its own core's private storage exactly the way the old one-at-a-time
 * pickers did (PS2EmbeddedLauncher.importBios / EdenIntegration's key+firmware importers) — this
 * only changes how the file gets found, not how it's used afterward. Stops at the first match for
 * each of the three categories; a folder with more than one candidate for the same category keeps
 * whichever is found first during the walk.
 */
object BiosKeysImporter {
    suspend fun scanAndImport(context: Context, folderUri: Uri): BiosKeysImportResult =
        withContext(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(context, folderUri)
                ?: return@withContext BiosKeysImportResult(false, false, false)

            var ps2Bios = false
            var switchKeys = false
            var switchFirmware = false

            walk(root) { file ->
                val name = file.name ?: return@walk
                val lower = name.lowercase()
                when {
                    lower == "prod.keys" && !switchKeys ->
                        switchKeys = EdenIntegration.importKeysFromUri(context, file.uri)
                    lower.endsWith(".zip") && "firmware" in lower && !switchFirmware ->
                        switchFirmware = EdenIntegration.importFirmwareFromUri(context, file.uri)
                    lower.endsWith(".bin") && "scph" in lower && !ps2Bios ->
                        ps2Bios = PS2EmbeddedLauncher.importBios(context, file.uri)
                }
            }

            BiosKeysImportResult(ps2Bios, switchKeys, switchFirmware)
        }

    private fun walk(dir: DocumentFile, onFile: (DocumentFile) -> Unit) {
        for (child in dir.listFiles()) {
            if (child.isDirectory) {
                walk(child, onFile)
            } else {
                onFile(child)
            }
        }
    }
}
