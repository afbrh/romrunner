package com.noryan.romrunner.data.embedded

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.noryan.romrunner.data.input.ControllerMapping
import com.noryan.romrunner.data.input.ControllerMappingApplier
import com.noryan.romrunner.data.model.Game
import me.magnum.melonds.ui.emulator.EmulatorActivity

/**
 * Starts the embedded WatermelonDS core's own EmulatorActivity in-process for a .nds ROM.
 * Intent.data (not the RomParcelable "rom" extra or the deprecated "PATH"/"uri" extras — confirmed
 * against LaunchArgs.fromIntent()) is what a third-party launch like this one should set — it does
 * NOT require the ROM to have been pre-scanned by WatermelonDS's own library
 * (FileSystemRomsRepository.getRomAtUri() builds a Rom on the fly for an unknown URI). DS defaults
 * to melonDS's built-in HLE BIOS (use_custom_bios=false) — no BIOS import needed to boot, unlike PS2.
 *
 * Save-file caveat (documented, not fixed in v1): since the ROM was never scanned by WatermelonDS's
 * own library, its save lands in this app's own Android/data/me.magnum.melondualds/files/saves/
 * rather than next to the ROM — deterministic per filename (repeat launches find the same save),
 * just not visible/manageable from WatermelonDS's own UI.
 */
object WatermelonDSEmbeddedLauncher {
    const val PLATFORM_NAME = "Nintendo DS"

    fun launch(context: Context, game: Game, controllerMapping: ControllerMapping) {
        ControllerMappingApplier.applyToWatermelonDS(context, controllerMapping)

        val intent = Intent(context, EmulatorActivity::class.java).apply {
            data = Uri.parse(game.fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
