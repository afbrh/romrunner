package com.noryan.romrunner.data.scanner

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.noryan.romrunner.data.model.Game
import com.noryan.romrunner.data.model.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What a scan found: the games not already in the library, every matching file that exists now ([presentUris], so games
 * whose file is gone can be removed), and whether the scan can be trusted to say a missing file is really gone.
 */
data class ScanResult(val newGames: List<Game>, val presentUris: Set<String>, val trustworthy: Boolean)

/** Recursively walks the user's single ROMs root folder, matching files against every configured platform's extensions. */
object RomScanner {

    // RomRunner integration: this was already `suspend`, but never actually switched dispatcher —
    // its call site (LibraryViewModel.rescanAll's viewModelScope) defaults to
    // Dispatchers.Main.immediate, so the whole recursive SAF walk below ran on the main thread.
    // DocumentFile.listFiles() is a synchronous Binder IPC per directory (plus a query() per
    // child), so a real ROM collection could stall the UI for a visible stretch on first folder
    // pick and every pull-to-refresh. withContext(Dispatchers.IO) here fixes that.
    suspend fun scanRoot(
        context: Context,
        rootUri: Uri,
        platforms: List<Platform>,
        existingUris: Set<String>
    ): ScanResult = withContext(Dispatchers.IO) {
        val untrusted = ScanResult(emptyList(), emptySet(), trustworthy = false)
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext untrusted

        val extensionToPlatformId = LinkedHashMap<String, Long>()
        for (platform in platforms) {
            for (ext in platform.extensions) {
                extensionToPlatformId.putIfAbsent(ext, platform.id)
            }
        }
        if (extensionToPlatformId.isEmpty()) return@withContext untrusted
        // A folder that can't be read or lists nothing at all (an SD card that isn't mounted, a revoked grant) says nothing
        // about which games are gone, so it must never be taken as "everything was deleted".
        if (!root.exists() || !root.canRead() || root.listFiles().isEmpty()) return@withContext untrusted

        val found = mutableListOf<Game>()
        val present = HashSet<String>()
        walk(root, extensionToPlatformId, existingUris, found, present)
        ScanResult(found, present, trustworthy = true)
    }

    private fun walk(
        dir: DocumentFile,
        extensionToPlatformId: Map<String, Long>,
        existingUris: Set<String>,
        acc: MutableList<Game>,
        present: MutableSet<String>
    ) {
        for (child in dir.listFiles()) {
            if (child.isDirectory) {
                walk(child, extensionToPlatformId, existingUris, acc, present)
                continue
            }
            val name = child.name ?: continue
            val ext = name.substringAfterLast('.', "").lowercase()
            val platformId = extensionToPlatformId[ext] ?: continue
            val uriString = child.uri.toString()
            present += uriString
            if (uriString in existingUris) continue
            acc += Game(
                platformId = platformId,
                title = cleanRomTitle(name),
                fileName = name,
                fileUri = uriString
            )
        }
    }

    /** Strips common ROM-set tags like "(USA)", "(Rev 1)", "[!]" to make a cleaner display title. */
    fun cleanRomTitle(fileName: String): String {
        val withoutExt = fileName.substringBeforeLast('.')
        val withoutTags = withoutExt
            .replace(Regex("""\([^)]*\)"""), "")
            .replace(Regex("""\[[^]]*]"""), "")
        return withoutTags.replace(Regex("""\s+"""), " ").trim().ifEmpty { withoutExt }
    }
}
