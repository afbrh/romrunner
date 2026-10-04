package com.noryan.romrunner.data.launch

import android.content.Context
import android.content.pm.PackageManager
import org.json.JSONObject
import java.io.File

/**
 * Presets Dusklight (the native Twilight Princess port) so its first launch goes straight into the game.
 *
 * Dusklight keeps its settings in `config.json` inside its data folder, which by default is in the app's private
 * storage, out of RomRunner's reach. It does have a `--user-dir` option, so RomRunner points it at a folder under
 * Dusklight's own `Android/data` folder, which RomRunner can write (see [EmulatorFolders]), and puts a starting
 * `config.json` there: the "Dusklight" preset from its welcome screen, already chosen, with touch controls off.
 *
 * Moving the data folder moves where saves and settings live, so this only starts on a fresh setup: it is skipped
 * when Dusklight was installed before RomRunner (it may already hold progress in its default folder), and once
 * started the same folder is passed on every later launch, so saves never move again. The user can still change
 * any setting from Dusklight's own menu; an existing `config.json` is never overwritten.
 */
object DusklightSetup {

    const val PACKAGE = "dev.twilitrealm.dusk"

    private const val PREFS = "dusklight_setup"
    private const val KEY_USER_DIR_IN_USE = "user_dir_in_use"
    private const val DATA_FOLDER = "data"

    /** Extra command-line arguments for launching Dusklight: `--user-dir <folder>` once this setup is in use, else none. */
    fun extraArgs(context: Context): List<String> {
        val dir = File(EmulatorFolders.directDir(PACKAGE), DATA_FOLDER)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_USER_DIR_IN_USE, false)) return listOf("--user-dir", dir.path)

        if (!EmulatorFolders.canUseDirectly(PACKAGE)) return emptyList()
        if (installedBeforeRomRunner(context)) return emptyList()
        return try {
            dir.mkdirs()
            val config = File(dir, "config.json")
            if (!config.exists()) config.writeText(startingConfig().toString(2))
            prefs.edit().putBoolean(KEY_USER_DIR_IN_USE, true).apply()
            listOf("--user-dir", dir.path)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun installedBeforeRomRunner(context: Context): Boolean = try {
        val pm = context.packageManager
        pm.getPackageInfo(PACKAGE, 0).firstInstallTime < pm.getPackageInfo(context.packageName, 0).firstInstallTime
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** What Dusklight's welcome screen writes when "Dusklight" is picked (its `applyPresetDusk`), plus touch controls off. */
    private fun startingConfig(): JSONObject = JSONObject().apply {
        put("backend.wasPresetChosen", true)
        put("game.enableTouchControls", false)

        listOf(
            "hideTvSettingsScreen", "noReturnRupees", "disableRupeeCutscenes", "noSwordRecoil", "fastClimbing",
            "noMissClimbing", "fastTears", "biggerWallets", "invertCameraXAxis", "invertFirstPersonYAxis",
            "no2ndFishForCat", "buttonFishing", "enableAchievementToasts", "enableControllerToasts",
            "enableQuickTransform", "instantSaves", "midnasLamentNonStop", "sunsSong", "enableGyroAim", "autoSave",
            "enhancedMapMenus", "enableMenuPointer"
        ).forEach { put("game.$it", true) }

        put("game.bloomMode", 2) // BloomMode::Dusk
        put("game.depthOfFieldMode", 2) // DepthOfFieldMode::Dusk
        put("game.enableFrameInterpolation", 2) // FrameInterpMode::Unlimited
        put("game.internalResolutionScale", 0)
        put("game.shadowResolutionMultiplier", 4)
        put("game.menuScalingMode", 2) // MenuScaling::Dusklight
    }
}
