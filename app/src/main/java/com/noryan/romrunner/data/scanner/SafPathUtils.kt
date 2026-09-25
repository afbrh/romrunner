package com.noryan.romrunner.data.scanner

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract

/**
 * Best-effort recovery of a real filesystem path from a SAF document Uri.
 *
 * Scoped storage normally only gives us a content:// Uri, but some emulators (e.g. RetroArch)
 * need an actual file path. This works for the common case of folders on the primary shared
 * storage volume, and makes a reasonable guess for secondary volumes (SD cards). It returns
 * null when it can't be confident, and callers should fall back to the content:// Uri instead.
 */
object SafPathUtils {

    /** Works for a document Uri (a file) or a bare tree Uri (a picked folder's root, no child document segment). */
    fun realPathFromDocumentUri(uri: Uri): String? {
        return try {
            val docId = try {
                DocumentsContract.getDocumentId(uri)
            } catch (e: IllegalArgumentException) {
                DocumentsContract.getTreeDocumentId(uri)
            }
            val parts = docId.split(":", limit = 2)
            if (parts.size < 2) return null
            val volume = parts[0]
            val relativePath = parts[1]
            when (volume) {
                "primary" -> "${Environment.getExternalStorageDirectory().path}/$relativePath"
                else -> "/storage/$volume/$relativePath"
            }
        } catch (e: Exception) {
            null
        }
    }
}
