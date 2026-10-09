package com.noryan.romrunner.data.launch

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File

/**
 * Looks through the device's storage for the folder that holds the user's ROMs, by name: one with "ROMS" in it first, then
 * one with "roms", then (since people write it "Roms" or "_ROMs") any capitalization. Needs All files access to see into storage.
 * Looks in the top level of each storage volume and a couple of folders down, nearest first, never inside Android/.
 */
object RomsFolderFinder {

    private const val MAX_DEPTH = 3

    /** The best match, or null if no folder has "roms" in its name (in any capitalization). */
    fun find(): File? {
        val candidates = mutableListOf<Pair<File, Int>>() // folder, how many levels down
        for (root in storageRoots()) {
            var level = listOf(root)
            for (depth in 1..MAX_DEPTH) {
                val next = mutableListOf<File>()
                for (dir in level) {
                    for (child in dir.listFiles().orEmpty()) {
                        if (!child.isDirectory || child.name.startsWith(".") || child.name == "Android") continue
                        candidates += child to depth
                        next += child
                    }
                }
                level = next
            }
        }
        val needles: List<(String) -> Boolean> = listOf(
            { name -> "ROMS" in name },
            { name -> "roms" in name },
            { name -> name.contains("roms", ignoreCase = true) }
        )
        for (matches in needles) {
            candidates.filter { (dir, _) -> matches(dir.name) }
                .minWithOrNull(compareBy<Pair<File, Int>>({ it.second }, { it.first.name.length }, { it.first.path }))
                ?.let { return it.first }
        }
        return null
    }

    /** The shared storage root plus any other mounted volume (SD card, USB drive). */
    private fun storageRoots(): List<File> {
        val primary = Environment.getExternalStorageDirectory()
        val others = File("/storage").listFiles().orEmpty()
            .filter { it.isDirectory && it.name != "emulated" && it.name != "self" && it.canRead() }
        return listOf(primary) + others
    }

    /**
     * The folder as the address the system folder picker understands, so the picker can open right on it (the
     * picker still has to be confirmed by the user; Android gives an app access to a folder no other way).
     */
    fun pickerUri(folder: File): Uri? {
        val path = folder.absolutePath
        val primary = Environment.getExternalStorageDirectory().absolutePath
        val docId = when {
            path.startsWith("$primary/") -> "primary:${path.removePrefix("$primary/")}"
            path.startsWith("/storage/") -> {
                val parts = path.removePrefix("/storage/").split('/', limit = 2)
                if (parts.size < 2) return null else "${parts[0]}:${parts[1]}"
            }
            else -> return null
        }
        return DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents", docId)
    }
}
