package com.noryan.romrunner.data.embedded

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.armsx2.CustomDriver
import com.armsx2.Main as Armsx2Main
import com.noryan.romrunner.data.input.ControllerMapping
import com.noryan.romrunner.data.input.ControllerMappingApplier
import com.noryan.romrunner.data.model.Game
import java.io.File
import java.util.zip.ZipInputStream
import org.json.JSONArray
import org.json.JSONObject

/**
 * Starts the embedded ARMSX2 core's own Main activity in-process for a PS2 ROM, instead of
 * handing off to a separate installed app. Since this now compiles as part of RomRunner itself
 * (see armsx2-src/GAMESHELF_INTEGRATION.md), there's no separate package to check for or launch.
 *
 * Unlike Azahar, no real-path resolution is needed here: ARMSX2's own intent handling
 * (MainActivityRuntime.extractLaunchUri/handleExternalLaunchIntent) already reads a plain
 * content:// Uri straight off the intent, since it's built to accept launches from external
 * frontends (Cocoon/Daijisho/ES-DE) the same way.
 *
 * ARMSX2's own onboarding wizard (data folder + BIOS + ROMs folder) is never shown: the data
 * folder step is skipped by simply leaving ARMSX2's "systemDir" pref unset, which makes it fall
 * back to app-private storage (assetCopyRoot's default) — the same place everything else RomRunner
 * owns already lives. The ROMs folder step is skipped by [seedArmsx2Prefs] reusing RomRunner's own
 * already-granted ROMs root permission. The BIOS step is replaced by [importBios] — a one-time
 * RomRunner-native file pick (triggered from LibraryScreen the first time a PS2 game is launched)
 * that copies the user's own BIOS dump into ARMSX2's internal BIOS folder, since a real BIOS is
 * unavoidably required (PS2 has no viable HLE BIOS replacement, unlike PS1).
 */
object PS2EmbeddedLauncher {

    const val PLATFORM_NAME = "PlayStation 2"
    private const val PREFS_NAME = "ARMSX2"
    private const val LAUNCHER_PREFS_NAME = "ps2_embedded_launcher"
    // Bumped from "face_buttons_mapped": now also inverts the right stick by default, so
    // installs that already ran under the old key get that new default applied once too.
    private const val KEY_CONTROL_DEFAULTS_APPLIED = "face_buttons_mapped_v2"

    /** True once a BIOS has been imported and is still present on disk. */
    fun isBiosImported(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val biosPath = prefs.getString("bios", null) ?: return false
        return File(biosPath).let { it.isFile && it.length() > 0L }
    }

    /**
     * Copies the user-picked BIOS file into ARMSX2's internal BIOS folder (the same
     * app-private location its own wizard would have copied it to — see
     * MainActivityRuntime.internalBiosDir/kt's onCreate BIOS-migration block) and marks setup
     * complete. Returns false (and leaves nothing behind) if the copy fails or the source Uri
     * is unreadable.
     */
    fun importBios(context: Context, sourceUri: Uri): Boolean {
        val fileName = queryDisplayName(context, sourceUri)?.takeIf { it.isNotBlank() } ?: "bios.bin"
        val biosDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "bios").apply { mkdirs() }
        val target = File(biosDir, fileName)
        val copied = runCatching {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
                true
            } ?: false
        }.getOrDefault(false)
        if (!copied || target.length() == 0L) {
            target.delete()
            return false
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString("bios", target.absolutePath)
            .putBoolean("setupComplete", true)
            .apply()
        return true
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    fun launch(context: Context, game: Game, romsRootUri: String, controllerMapping: ControllerMapping) {
        seedArmsx2Prefs(context, romsRootUri)
        applyStandingStickPreferencesIfNeeded(context)
        ControllerMappingApplier.applyToArmsx2(context, controllerMapping)
        applyDefaultGraphicsSettings(context)
        applyJak3PerGameHacks(context)
        // Must run before startActivity: CustomDriver.applyToNative's doc comment notes the
        // native side only reads this on the next MTGS::Open, driven by
        // MainActivityRuntime.start()'s applyRendererPrefs, which happens inside the activity
        // we're about to launch.
        installBundledTurnipDriver(context)

        val intent = Intent(context, Armsx2Main::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(game.fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    /**
     * One-time right-stick Y/X invert (ControllerMappings' "pad.rstick.invertX"/"invertY", global
     * scope — see its KEY_RSTICK_INVX/INVY) — a standing camera-control preference, requested
     * after testing Jak 3, unrelated to (and deliberately left alone by) the Controller Mapping
     * feature — see ControllerMappingApplier.applyToArmsx2's own doc comment for why sticks are
     * out of scope there. The face-button/D-pad/shoulder/trigger/click remap this function used to
     * also apply here is now handled every launch by ControllerMappingApplier.applyToArmsx2
     * instead, driven by the user's stored (or default) Controller Mapping.
     *
     * Written directly to ARMSX2's "ARMSX2" SharedPreferences file under the same keys
     * ControllerMappings' own setters would use — not through those setters, since they touch
     * MainActivityRuntime.prefs, uninitialized before ARMSX2's own onCreate runs.
     *
     * Gated on RomRunner's own prefs (not ARMSX2's) so this only ever happens ONCE: after that,
     * the user is free to change it in ARMSX2's own Controls/Pad tab without RomRunner quietly
     * reverting it on the next launch.
     */
    private fun applyStandingStickPreferencesIfNeeded(context: Context) {
        val launcherPrefs = context.getSharedPreferences(LAUNCHER_PREFS_NAME, Context.MODE_PRIVATE)
        if (launcherPrefs.getBoolean(KEY_CONTROL_DEFAULTS_APPLIED, false)) return

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("pad.rstick.invertX", true)
            .putBoolean("pad.rstick.invertY", true)
            .apply()
        launcherPrefs.edit().putBoolean(KEY_CONTROL_DEFAULTS_APPLIED, true).apply()
    }

    /**
     * Forces the Vulkan backend and 2x internal resolution on every launch — a standing
     * preference, unlike the one-time face-button remap, so it's reasserted every time rather
     * than gated on a "did this once already" flag.
     *
     * Was OpenGL until profiling Jak 3 showed the GS (render) thread pegged at ~99% while the
     * actual host GPU sat at ~20% usage — a CPU-side draw-call/state-submission bottleneck, not
     * a real rendering-compute limit (1400+ draw calls/frame). Same profile Cemu hit on Wii U,
     * fixed there by moving to Vulkan + a custom driver — see [installBundledTurnipDriver].
     *
     * Also enables CoalesceRenderPasses: Jak 3 was still speed-capped after the Vulkan switch
     * (GS thread ~99.5% busy across multiple scenes, e.g. 21-23ms/frame vs. a 16.6ms budget),
     * with 22-64 render passes/frame — each one a full tile load/store on Adreno's tile-based
     * renderer. ARMSX2's own comment on this flag says it merges consecutive draws to the same
     * target into one pass with rendering unchanged, aimed squarely at tiling GPUs (i.e. every
     * Android GPU). Confirmed on-device: render-pass count dropped ~2-3x (45-64 -> 22) with it
     * on. Deliberately NOT also setting GSBackThreadMode (the GS front/back thread split) —
     * tried Pipelined (3) here first and it made the same heavy combat scene WORSE (speed
     * 68%->55%, both GS and the new GSB backend thread near-saturated), so it's left at
     * ARMSX2's own default (0/off) rather than guessed at project-wide.
     *
     * Settings (renderer, upscaleFloat, ...) live as one JSON blob under ARMSX2's own
     * "config.global" pref key (see ConfigStore.KEY_GLOBAL/loadGlobal), not as individual
     * top-level keys the way pad.map.* or touch.visibilityMode are — so this reads that blob (if
     * one exists yet), overwrites just the relevant fields, and writes it back. Settings.fromJson
     * is lenient about missing keys (falls back to each field's own default), so a first-ever
     * launch with no existing blob still works: the partial object written here resolves every
     * other field to its normal default when ARMSX2 reads it back.
     */
    private fun applyDefaultGraphicsSettings(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = runCatching {
            prefs.getString("config.global", null)?.let { JSONObject(it) } ?: JSONObject()
        }.getOrDefault(JSONObject())
        json.put("renderer", "vulkan")
        json.put("upscaleFloat", 2.0)
        json.put("coalesceRenderPasses", true)
        // Performance diagnostics (FPS/speed%/EE-CPU/GS-GPU load) were on by default while
        // bottlenecks were being chased during development — turned off now that tuning is done,
        // per request to remove every emulator's stats overlay.
        json.put("osdShowFps", false)
        json.put("osdShowSpeed", false)
        json.put("osdShowCpu", false)
        json.put("osdShowGpu", false)
        json.put("osdShowGsStats", false)
        prefs.edit().putString("config.global", json.toString()).apply()
    }

    // Jak 3's PS2 serial (from its SYSTEM.CNF, confirmed via the boot log's
    // "cdvdLoadElf" / "Serial:" lines) — the key ARMSX2's own per-game override tier
    // (ConfigStore's "config.game.<serial>") is keyed on.
    private const val JAK3_SERIAL = "SCUS-97330"

    /**
     * Per-game GS hacks for Jak 3 ONLY — not folded into [applyDefaultGraphicsSettings], since
     * these trade away some of the HW renderer's normal safety margins for less CPU-side work on
     * the GS thread, and haven't been verified safe across PS2 titles generally. Written into
     * ARMSX2's own per-game override tier (ConfigStore's "config.game.<serial>", a sparse JSON
     * blob distinct from the "config.global" one [applyDefaultGraphicsSettings] writes — see
     * ConfigStore.resolveForGame) rather than global.
     *
     * Written unconditionally on every PS2 launch rather than gated on the launched game's
     * title: ARMSX2 only ever reads this key when a disc whose OWN SYSTEM.CNF serial is
     * literally SCUS-97330 boots (see ConfigStore.resolveForGame), so pre-writing it ahead of
     * every PS2 launch is a no-op for every other title.
     *
     * disablePartialInvalidation (UserHacks_DisablePartialInvalidation) is a confirmed real speed
     * win for Jak 3 — GS thread dropped from ~99.5% busy to ~83-87% across several scenes,
     * holding a solid 100% speed where it previously sagged to 68-79% — at the cost of confirmed
     * texture corruption during real play (it skips per-draw texture-cache validity/rectangle
     * checks the HW renderer normally does). disableSafeFeatures (UserHacks_Disable_Safe_Features)
     * was tried alongside it first, then ruled out as NOT the source of the corruption (turning
     * it off alone didn't fix anything) and gave no extra speed either — left off.
     *
     * The user explicitly chose speed over accuracy for this title after seeing both trade-offs
     * play out, so this stays ON as Jak 3's setting; it is not a general recommendation for other
     * PS2 games, which is exactly why it's scoped to this one serial rather than folded into
     * [applyDefaultGraphicsSettings].
     *
     * Reasserted every launch like [applyDefaultGraphicsSettings].
     */
    private fun applyJak3PerGameHacks(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "config.game.$JAK3_SERIAL"
        val json = runCatching {
            prefs.getString(key, null)?.let { JSONObject(it) } ?: JSONObject()
        }.getOrDefault(JSONObject())
        json.put("disableSafeFeatures", false)
        json.put("disablePartialInvalidation", true)
        prefs.edit().putString(key, json.toString()).apply()
    }

    /**
     * Installs the bundled Turnip Vulkan driver (assets/cemu_turnip_driver.zip — the same
     * community-vetted Adreno driver build already used for Cemu) and makes it the active
     * driver for ARMSX2's GS Vulkan renderer, via ARMSX2's own adrenotools-based
     * CustomDriver.applyToNative. The zip's meta.json + .so schema is the same one CustomDriver
     * expects (K11MCH1/AdrenoToolsDrivers format), so it's extracted directly into
     * `<filesDir>/drivers/romrunner_turnip/` rather than going through the SAF-picker-oriented
     * installFromUri path. Idempotent and cheap — safe to call before every launch, same as
     * [applyDefaultGraphicsSettings].
     */
    private fun installBundledTurnipDriver(context: Context) {
        val driverDir = File(File(context.filesDir, "drivers"), "romrunner_turnip")
        if (!driverDir.exists()) {
            driverDir.mkdirs()
            context.assets.open("cemu_turnip_driver.zip").use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val outFile = File(driverDir, entry.name.substringAfterLast('/'))
                            outFile.outputStream().use { out -> zip.copyTo(out) }
                        }
                        entry = zip.nextEntry
                    }
                }
            }
            File(driverDir, "cache").mkdirs()
        }
        val installed = CustomDriver.listInstalled(context).firstOrNull { it.id == driverDir.name }
        CustomDriver.applyToNative(context, installed)
    }

    /**
     * Writes directly to ARMSX2's own "ARMSX2" SharedPreferences file — not through its Kotlin
     * API (MainActivityRuntime.setRomsDirs, TouchControls.setVisibilityMode), because those touch
     * MainActivityRuntime.prefs, which stays uninitialized until ARMSX2's own onCreate runs. This
     * has to land before startActivity, so it goes straight to the file both eventually read from
     * (same app/UID, so this is uncontested — nothing else reads or writes "ARMSX2" prefs before
     * ARMSX2's own onCreate does).
     *
     * Three fixes:
     *
     * 1. setupComplete: forced true unconditionally. importBios already sets this once, but
     *    reasserting it here too means a launch can never regress into showing ARMSX2's wizard
     *    for any other reason.
     *
     * 2. romsDirs: MainActivityRuntime.onCreate resets setupComplete back to false on every
     *    launch if none of its configured romsDirs are currently reachable (a guard against an
     *    Android Auto Backup restore whose SAF permissions didn't survive). Since RomRunner routes
     *    PS2 launches around ARMSX2's own ROMs-folder picker entirely, romsDirs is always empty
     *    from ARMSX2's own point of view — so that guard fired on every single launch, bouncing
     *    the user back to the full setup wizard even after completing it once. Seeding it with
     *    RomRunner's own already-granted ROMs root URI satisfies the check directly: it's the same
     *    app/UID, so the SAF read permission RomRunner holds on this URI is equally visible to
     *    ARMSX2's reachability check (context.contentResolver.persistedUriPermissions is
     *    process-wide, not per "module").
     *
     * 3. touch.visibilityMode: forced to 0 ("Never show" — ARMSX2's own preset for physical-
     *    controls devices, which also hides the settings cog). This is played with a physical
     *    controller, not the touchscreen.
     *
     * All three writes are cheap and idempotent, so it's safe to call before every launch.
     */
    private fun seedArmsx2Prefs(context: Context, romsRootUri: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentRomsDirs = runCatching {
            val json = prefs.getString("romsDirs", null) ?: return@runCatching emptyList<String>()
            val arr = JSONArray(json)
            List(arr.length()) { arr.getString(it) }
        }.getOrDefault(emptyList())
        prefs.edit().apply {
            putBoolean("setupComplete", true)
            if (romsRootUri !in currentRomsDirs) {
                val updated = JSONArray().apply {
                    put(romsRootUri)
                    currentRomsDirs.forEach { put(it) }
                }
                putString("romsDirs", updated.toString())
            }
            putInt("touch.visibilityMode", 0)
        }.apply()
    }
}
