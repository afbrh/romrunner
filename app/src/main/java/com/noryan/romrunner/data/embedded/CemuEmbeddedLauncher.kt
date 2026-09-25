@file:OptIn(ExperimentalPathApi::class)

package com.noryan.romrunner.data.embedded

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import com.noryan.romrunner.data.model.Game
import info.cemu.cemu.common.android.inputdevice.listGameControllers
import info.cemu.cemu.common.android.inputdevice.toControllerInfo
import info.cemu.cemu.common.customdrivers.getCustomDriversDir
import info.cemu.cemu.common.io.unzip
import info.cemu.cemu.common.settings.AppSettingsStore
import info.cemu.cemu.common.settings.HotkeyAction
import info.cemu.cemu.common.settings.HotkeyCombo
import info.cemu.cemu.emulation.EmulationActivity
import info.cemu.cemu.nativeinterface.NativeInput
import info.cemu.cemu.nativeinterface.NativeSettings
import info.cemu.cemu.settings.input.controller.InputMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

/**
 * Starts the embedded Cemu core's own EmulationActivity in-process for a Wii U ROM, instead of
 * handing off to a separate installed app.
 *
 * Like PS2/PrimeHack, no real-path resolution is needed: Cemu's own file layer accepts a raw
 * content:// Uri string directly (see EmulationActivity.getGamePath(), and its manifest's own
 * ACTION_VIEW intent-filter which already declares scheme="content"), so [game]'s raw fileUri is
 * passed straight through via the EXTRA_LAUNCH_PATH extra.
 *
 * Cemu's own onboarding (game-folder scan, controller setup wizard) is never shown: native
 * directory setup happens via CemuApplication.initializeForEmbedding (called once from
 * RomRunnerApp, since RomRunnerApp extends a DIFFERENT embedded core's Application class and
 * Cemu's own onCreate() never runs — see that file). Unlike Dolphin, Cemu's controller config
 * isn't a text file we can pre-seed: every button is DISABLED and unmapped by default until
 * explicitly configured through NativeInput's JNI calls (see [applyDefaultsIfNeeded]), which is
 * exactly what Cemu's own Settings > Input screen does under the hood (ControllersViewModel).
 * Controller 0 is set up as a Wii U Pro Controller — a plain gamepad with no GamePad-specific
 * touchscreen/motion features, matching how the AYN Thor's own physical controller is used.
 */
object CemuEmbeddedLauncher {
    const val PLATFORM_NAME = "Wii U"

    private const val PREFS_NAME = "cemu_embedded_launcher"
    // Bumped again: now also turns the performance overlay back OFF (it was turned on for
    // development, per request to remove every emulator's stats overlay) — installs that already
    // ran under an older key need to reapply once under this one.
    private const val KEY_DEFAULTS_APPLIED = "defaults_applied_v7"

    fun launch(context: Context, game: Game, romsRootUri: String) {
        applyDefaultsIfNeeded(context)
        applyDefaultHotkeysIfNeeded(context)

        val intent = Intent(context, EmulationActivity::class.java)
        intent.putExtra(EmulationActivity.EXTRA_LAUNCH_PATH, game.fileUri)
        // RomRunner's own catalogued title (shown in the library list) rather than anything
        // read from Cemu's own game database — that's keyed by titleId and populated by scanning
        // the whole library (NativeGameTitles.reloadGameTitles), a much heavier lookup than
        // RomRunner already having this on hand from the launch it's already doing.
        intent.putExtra(EmulationActivity.EXTRA_GAME_TITLE, game.title)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun applyDefaultsIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DEFAULTS_APPLIED, false)) return

        // Wii U Pro Controller — no GamePad touchscreen/motion features to worry about, just a
        // plain gamepad, matching how the AYN Thor's own physical controller is actually used.
        NativeInput.setControllerType(0, NativeInput.EmulatedControllerType.PRO)

        val controllers = listGameControllers()
        if (controllers.isNotEmpty()) {
            NativeInput.setControllers(controllers.map { it.toControllerInfo() }.toTypedArray())
            // Auto-maps every VPAD button to this device's standard gamepad buttons/axes — the
            // same heuristic Cemu's own Settings > Input > "map all" control uses, keyed off
            // which KEYCODE_BUTTON_*/axes the device actually reports (see InputMapper.kt).
            InputMapper.mapAllInputs(controllers.first().id, 0)
        }

        NativeInput.saveInputs()

        // Performance diagnostics (FPS/CPU-usage/draw-calls) were on by default while Wii U
        // performance was being tuned — turned off now that tuning is done, per request to
        // remove every emulator's stats overlay. setOverlayPosition is the master switch behind
        // the individual toggles (a non-DISABLED position is required for any of them to render
        // at all — see Cemu's own Overlay settings screen); disabling it directly is enough on
        // its own, but the individual toggles are turned off too so they read correctly if a
        // user later re-enables the overlay from Cemu's own settings.
        NativeSettings.setOverlayPosition(NativeSettings.OverlayScreenPosition.DISABLED)
        NativeSettings.setOverlayFPSEnabled(false)
        NativeSettings.setOverlayCPUUsageEnabled(false)
        NativeSettings.setOverlayDrawCallsPerFrameEnabled(false)
        NativeSettings.saveSettings()

        installBundledTurnipDriver(context)

        // Trades a small chance of graphical flickering for performance — user-requested after
        // the driver swap alone only gave a modest gain.
        NativeSettings.setAccurateBarriers(false)
        NativeSettings.saveSettings()

        prefs.edit().putBoolean(KEY_DEFAULTS_APPLIED, true).apply()
    }

    private const val KEY_HOTKEYS_APPLIED = "hotkeys_applied_v1"

    /**
     * Binds the physical Back button to Cemu's TOGGLE_MENU hotkey, so it opens the in-game
     * pop-out menu (RomRunnerMenuFont-styled drawer in EmulationScreen.kt) the same way the
     * physical Back button already does on the other three embedded cores.
     *
     * Without this, Cemu's own EmulationActivity.dispatchKeyEvent() hands every key event from a
     * physical controller straight to InputHandler.onKeyEvent(), which unconditionally consumes
     * it (forwards to NativeInput.onControllerKey and returns true) before it can ever reach
     * super.dispatchKeyEvent() — the path Compose's BackHandler in EmulationScreen relies on. The
     * AYN Thor's built-in controls report as a physical controller, so its Back button was being
     * silently swallowed as (unmapped) game input instead of opening the menu.
     * HotkeyManager.onKeyEvent() runs BEFORE that swallow, though, and AppSettings.hotkeySettings
     * defaults to an empty map (no hotkeys bound at all) — so seeding a Back-only combo for
     * TOGGLE_MENU here fixes it without touching Cemu's own input-dispatch code.
     *
     * hotkeySettings lives in Cemu's own Kotlin-side DataStore (AppSettingsStore), not the native
     * NativeSettings/NativeInput config the rest of this file writes to, so this goes through the
     * real suspend API (dataStore.updateData) on a background coroutine rather than a raw file
     * edit — safe against the DataStore's own in-memory cache stomping a direct-to-disk write.
     * Gated on GameShelf's own prefs (not Cemu's) so a user who later rebinds or clears this
     * hotkey in Cemu's own Controller Hotkeys settings isn't overridden again on the next launch.
     */
    private fun applyDefaultHotkeysIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_HOTKEYS_APPLIED, false)) return
        prefs.edit().putBoolean(KEY_HOTKEYS_APPLIED, true).apply()

        CoroutineScope(Dispatchers.IO).launch {
            AppSettingsStore.dataStore.updateData { current ->
                if (current.hotkeySettings.containsKey(HotkeyAction.TOGGLE_MENU)) {
                    current
                } else {
                    current.copy(
                        hotkeySettings = current.hotkeySettings + (
                            HotkeyAction.TOGGLE_MENU to HotkeyCombo(setOf(KeyEvent.KEYCODE_BACK))
                        )
                    )
                }
            }
        }
    }

    /**
     * Installs the bundled Turnip Vulkan driver (assets/cemu_turnip_driver.zip — a
     * community-maintained Adreno driver build the user already vetted) and makes it the active
     * driver, following the exact same install shape as Cemu's own Settings > Graphics > Custom
     * drivers screen (CustomDriversViewModel.installDriver): unzip a meta.json + .so pair into
     * getCustomDriversDir(), then point NativeSettings at that folder. The stock Adreno driver
     * carries more per-draw-call overhead than Turnip, which matters a lot for Wii U titles —
     * Cemu's overlay showed 1000+ draw calls/frame on Wind Waker HD with CPU usage well under
     * 100%, indicating the GPU/driver submission path (not CPU-side PPC recompilation) was the
     * bottleneck.
     */
    private fun installBundledTurnipDriver(context: Context) {
        val driverDir = getCustomDriversDir().resolve("turnip_bundled")
        if (!driverDir.exists()) {
            driverDir.createDirectories()
            context.assets.open("cemu_turnip_driver.zip").use { unzip(it, driverDir) }
        }
        NativeSettings.setCustomDriverPath(driverDir.toString())
        NativeSettings.saveSettings()
    }
}
