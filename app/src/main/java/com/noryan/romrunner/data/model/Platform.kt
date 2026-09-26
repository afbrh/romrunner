package com.noryan.romrunner.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A configured game system, e.g. "Game Boy Advance": which file extensions belong to it, and how
 * to hand a matched ROM off to an emulator app. Not tied to any particular folder — there's a
 * single ROMs root folder for the whole library (see [com.noryan.romrunner.data.settings.LibrarySettings]),
 * and every platform's extensions are matched against it.
 */
@Entity(tableName = "platforms")
data class Platform(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val extensionsCsv: String = "",
    val launchPackage: String = "",
    val launchActivity: String = "",
    val launchAction: String = "android.intent.action.VIEW",
    val mimeType: String = "application/octet-stream",
    val passAsIntentData: Boolean = true,
    val extrasRaw: String = "",
    val sortOrder: Int = 0,
    val useBuiltIn: Boolean = true,
    /** Whether this platform's embedded emulator uses RomRunner's global Controller Mapping
     *  (see [com.noryan.romrunner.data.input.ControllerMapping]) or its own [controllerMappingJson]
     *  override. Only meaningful when [useBuiltIn] is true. */
    val useGlobalControllerMapping: Boolean = true,
    /** This platform's own Controller Mapping override, JSON-encoded — see
     *  [com.noryan.romrunner.data.input.ControllerMappingSerializer]. Only read when
     *  [useGlobalControllerMapping] is false; `null` means "not customized yet," which falls back
     *  to the current global mapping as a starting point. */
    val controllerMappingJson: String? = null
) {
    val extensions: List<String>
        get() = extensionsCsv.split(",")
            .map { it.trim().trimStart('.').lowercase() }
            .filter { it.isNotEmpty() }

    val extras: Map<String, String>
        get() = extrasRaw.split(";")
            .mapNotNull { entry ->
                val idx = entry.indexOf('=')
                if (idx <= 0) null else entry.substring(0, idx).trim() to entry.substring(idx + 1).trim()
            }
            .toMap()

    companion object {
        fun extensionsToCsv(list: List<String>): String =
            list.map { it.trim().trimStart('.').lowercase() }
                .filter { it.isNotEmpty() }
                .joinToString(",")

        fun extrasToRaw(map: Map<String, String>): String =
            map.entries.joinToString(";") { "${it.key}=${it.value}" }
    }
}
