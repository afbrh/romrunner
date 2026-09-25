package com.noryan.romrunner.data.launch

/** A starting-point configuration for a well-known emulator, editable afterwards. */
data class EmulatorPreset(
    val label: String,
    val packageName: String,
    val activity: String = "",
    val action: String = "android.intent.action.VIEW",
    val mimeType: String = "application/octet-stream",
    val passAsIntentData: Boolean = true,
    val extras: Map<String, String> = emptyMap(),
    val note: String = ""
)

object EmulatorPresets {
    val GENERIC = EmulatorPreset(
        label = "Generic (let Android choose the app)",
        packageName = "",
        note = "Leave the package blank to show a chooser, or fill in a specific app's package name below."
    )

    val PPSSPP = EmulatorPreset(
        label = "PPSSPP",
        packageName = "org.ppsspp.ppsspp",
        note = "Verify this package name matches your installed build (regular vs. Gold)."
    )

    val DOLPHIN = EmulatorPreset(
        label = "Dolphin Emulator",
        packageName = "org.dolphinemu.dolphinemu",
        note = "Matches the official Dolphin app from the Play Store."
    )

    val DRASTIC = EmulatorPreset(
        label = "DraStic (DS)",
        packageName = "com.dsemu.drastic",
        note = "DraStic is a paid app; the package name may differ if you're on a trial build."
    )

    val RETROARCH_ADVANCED = EmulatorPreset(
        label = "RetroArch (advanced)",
        packageName = "com.retroarch",
        passAsIntentData = false,
        extras = mapOf(
            "ROM" to "{FILE_PATH}",
            "LIBRETRO" to "/data/data/com.retroarch/cores/REPLACE_WITH_CORE.so"
        ),
        note = "RetroArch needs an explicit libretro core path, and reliably reads real file paths " +
            "rather than content Uris — pick a folder on internal shared storage, not an SD card. " +
            "Edit the LIBRETRO extra to point at the installed core for this platform."
    )

    val ALL = listOf(GENERIC, PPSSPP, DOLPHIN, DRASTIC, RETROARCH_ADVANCED)
}
