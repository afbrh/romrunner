package com.noryan.romrunner.data.scanner

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.noryan.romrunner.data.model.Game
import com.noryan.romrunner.data.model.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    ): List<Game> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext emptyList()

        val extensionToPlatformId = LinkedHashMap<String, Long>()
        for (platform in platforms) {
            for (ext in platform.extensions) {
                extensionToPlatformId.putIfAbsent(ext, platform.id)
            }
        }
        if (extensionToPlatformId.isEmpty()) return@withContext emptyList()

        val found = mutableListOf<Game>()
        walk(root, extensionToPlatformId, existingUris, found)
        found
    }

    private fun walk(
        dir: DocumentFile,
        extensionToPlatformId: Map<String, Long>,
        existingUris: Set<String>,
        acc: MutableList<Game>
    ) {
        for (child in dir.listFiles()) {
            if (child.isDirectory) {
                walk(child, extensionToPlatformId, existingUris, acc)
                continue
            }
            val name = child.name ?: continue
            val ext = name.substringAfterLast('.', "").lowercase()
            val platformId = extensionToPlatformId[ext] ?: continue
            val uriString = child.uri.toString()
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
