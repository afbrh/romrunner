package com.noryan.romrunner.data.launch

import androidx.documentfile.provider.DocumentFile

/**
 * Finds support files (keys, firmware, BIOS) inside the user's Roms/BIOS folder, which holds both their
 * games and those files, usually in a subfolder such as `_BIOS`. Searches the folder and the folders
 * under it, a few levels deep, and returns every file [matches] accepts (by file name).
 */
object RomsFolderFiles {

    private const val MAX_DEPTH = 3

    fun find(root: DocumentFile, matches: (name: String) -> Boolean): List<DocumentFile> {
        val found = mutableListOf<DocumentFile>()
        var level = listOf(root)
        repeat(MAX_DEPTH + 1) {
            val next = mutableListOf<DocumentFile>()
            for (dir in level) {
                for (child in dir.listFiles()) {
                    val name = child.name ?: continue
                    when {
                        child.isDirectory -> next += child
                        child.isFile && matches(name) -> found += child
                    }
                }
            }
            level = next
        }
        return found
    }
}
