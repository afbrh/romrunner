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

    private const val PREFS_NAME = "watermelonds_embedded_launcher"
    // Bumped from v1: v1 only set video_internal_resolution, but WatermelonDS's own
    // RendererConfiguration.resolutionScaling hardcodes 1x whenever the renderer is SOFTWARE
    // (its own default) — the resolution value is silently ignored unless a hardware renderer is
    // also selected. Installs that already ran under v1 need to reapply once under v2.
    private const val KEY_DEFAULTS_APPLIED = "defaults_applied_v2"

    fun launch(context: Context, game: Game, controllerMapping: ControllerMapping) {
        applyDefaultsIfNeeded(context)
        ControllerMappingApplier.applyToWatermelonDS(context, controllerMapping)

        val intent = Intent(context, EmulatorActivity::class.java).apply {
            data = Uri.parse(game.fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    /**
     * One-time defaults for two of WatermelonDS's own settings, backed by its default
     * SharedPreferences (see SharedPreferencesSettingsRepository's "soft_input_behaviour"/
     * "video_internal_resolution" keys) — same file-naming trick as
     * ControllerMappingApplier.applyToAzahar, since androidx.preference isn't on this module's own
     * classpath: hides the on-screen touch overlay entirely (a physical controller is expected,
     * same rationale as PrimeHackEmbeddedLauncher's ShowInputOverlay=False) and bumps the internal
     * render resolution to 5x. The resolution value alone does nothing under WatermelonDS's default
     * "software" renderer (RendererConfiguration.resolutionScaling hardcodes 1x for
     * VideoRenderer.SOFTWARE, ignoring the configured value entirely), so video_renderer is switched
     * to "vulkan" too — the Thor's Adreno 740 has solid native Vulkan support, and this fork's own
     * driver-management code (getVulkanDriverConfiguration et al.) treats Vulkan as its primary
     * hardware-accelerated path. Applied once, not on every launch — like PrimeHack's overlay/shader
     * defaults, not the remappable ControllerMapping slots — so a later change from WatermelonDS's
     * own Settings screen sticks.
     */
    private fun applyDefaultsIfNeeded(context: Context) {
        val launcherPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (launcherPrefs.getBoolean(KEY_DEFAULTS_APPLIED, false)) return

        val prefs = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("soft_input_behaviour", "always_invisible")
            .putString("video_renderer", "vulkan")
            .putString("video_internal_resolution", "5")
            .apply()

        launcherPrefs.edit().putBoolean(KEY_DEFAULTS_APPLIED, true).apply()
    }
}
