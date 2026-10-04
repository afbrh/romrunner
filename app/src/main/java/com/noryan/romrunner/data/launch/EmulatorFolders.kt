package com.noryan.romrunner.data.launch

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * Where RomRunner writes an emulator's settings, keys and profiles: that app's own `files` folder under
 * Android/data. There are two ways in, and the setups don't care which one they got:
 *
 *  - **Direct**, through the file system. With "All files access" on, this Thor's Android gives RomRunner
 *    read/write access to other apps' Android/data folders (tested from the running app: it can write there, and
 *    the emulators can then read and overwrite what it wrote). No picker, no extra prompt, and the emulator's
 *    process isn't started by RomRunner's access, so it reads the new settings when it next starts.
 *  - **Through a folder grant**, the one-time system picker, which is the fallback for a device or Android version
 *    where direct access isn't allowed (or when the user didn't turn on All files access).
 */
object EmulatorFolders {

    fun directDir(packageName: String): File =
        File(Environment.getExternalStorageDirectory(), "Android/data/$packageName/files")

    /** True when RomRunner can reach [packageName]'s files folder straight through the file system. */
    fun canUseDirectly(packageName: String): Boolean {
        if (!Environment.isExternalStorageManager()) return false
        val dir = directDir(packageName)
        return (dir.isDirectory || dir.mkdirs()) && dir.canWrite()
    }

    /**
     * The emulator's files folder as a [DocumentFile]: direct if possible, else through [treeUri] (the saved folder
     * grant), else null. A direct one has a `file:` Uri, see [isDirect].
     */
    fun open(context: Context, packageName: String, treeUri: Uri?): DocumentFile? {
        if (canUseDirectly(packageName)) return DocumentFile.fromFile(directDir(packageName))
        return treeUri?.let { DocumentFile.fromTreeUri(context, it) }
    }

    fun isDirect(folder: DocumentFile): Boolean = folder.uri.scheme == "file"

    /**
     * Creates a file called exactly [name] in this folder, or null. Use this instead of `createFile`: for a plain-file
     * (direct) folder, `createFile` appends the extension of the mime type it's given, so a generic type turns
     * "prod.keys" into "prod.keys.bin". The folder-grant route has no such habit, and takes a generic type.
     */
    fun createNamedFile(folder: DocumentFile, name: String): DocumentFile? {
        if (!isDirect(folder)) return folder.createFile("application/octet-stream", name)
        val file = File(File(folder.uri.path ?: return null), name)
        return if (file.exists() || file.createNewFile()) DocumentFile.fromFile(file) else null
    }
}
