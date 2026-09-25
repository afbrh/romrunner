package com.noryan.romrunner.data.scanner

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.noryan.romrunner.data.model.Game
import com.noryan.romrunner.data.model.Platform

/** Recursively walks the user's single ROMs root folder, matching files against every configured platform's extensions. */
object RomScanner {

    suspend fun scanRoot(
        context: Context,
        rootUri: Uri,
        platforms: List<Platform>,
        existingUris: Set<String>
    ): List<Game> {
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return emptyList()

        val extensionToPlatformId = LinkedHashMap<String, Long>()
        for (platform in platforms) {
            for (ext in platform.extensions) {
                extensionToPlatformId.putIfAbsent(ext, platform.id)
            }
        }
        if (extensionToPlatformId.isEmpty()) return emptyList()

        val found = mutableListOf<Game>()
        walk(root, extensionToPlatformId, existingUris, found)
        return found
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
