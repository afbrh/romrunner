package com.noryan.romrunner.data.settings

import android.content.Context

/** The one setting that matters for v1: which folder the whole library gets scanned from. */
class LibrarySettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var rootFolderUri: String?
        get() = prefs.getString(KEY_ROOT_FOLDER_URI, null)
        set(value) = prefs.edit().putString(KEY_ROOT_FOLDER_URI, value).apply()

    /** Whether to ask Android to kill other apps' background processes right before launching a
     *  game, freeing memory for it. Default on — see [com.noryan.romrunner.data.launch.BackgroundAppCleaner]. */
    var killBackgroundAppsOnLaunch: Boolean
        get() = prefs.getBoolean(KEY_KILL_BACKGROUND_APPS_ON_LAUNCH, true)
        set(value) = prefs.edit().putBoolean(KEY_KILL_BACKGROUND_APPS_ON_LAUNCH, value).apply()

    /** Whether to claim a connected second display (e.g. the AYN Thor's own second screen) and
     *  fill it with RomRunner's own logo — see [com.noryan.romrunner.ui.secondscreen.DualScreenController].
     *  Default on. */
    var dualScreenSupportEnabled: Boolean
        get() = prefs.getBoolean(KEY_DUAL_SCREEN_SUPPORT_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_DUAL_SCREEN_SUPPORT_ENABLED, value).apply()

    /** True once the first-run "download the emulators you need?" prompt has been shown (or waved
     *  through) — it's only ever offered once per install. */
    var emulatorSetupPrompted: Boolean
        get() = prefs.getBoolean(KEY_EMULATOR_SETUP_PROMPTED, false)
        set(value) = prefs.edit().putBoolean(KEY_EMULATOR_SETUP_PROMPTED, value).apply()

    /** Set when a ROMs folder is chosen before the prompt above has been shown; the Games tab shows
     *  the prompt as soon as that folder's scan turns up games that need a missing emulator. */
    var emulatorSetupPending: Boolean
        get() = prefs.getBoolean(KEY_EMULATOR_SETUP_PENDING, false)
        set(value) = prefs.edit().putBoolean(KEY_EMULATOR_SETUP_PENDING, value).apply()

    /** True once the user has been offered "All files access" (for reusing downloaded emulator APKs) — asked once. */
    var storageAccessAsked: Boolean
        get() = prefs.getBoolean(KEY_STORAGE_ACCESS_ASKED, false)
        set(value) = prefs.edit().putBoolean(KEY_STORAGE_ACCESS_ASKED, value).apply()

    /** Whether [key]'s emulator ("primehack", "cemu", "eden", "retroarch") has been set up by writing straight into its folder, with no folder grant. */
    /** The libretro core the user picked for a system RetroArch plays (by platform name), or null for RomRunner's default. */
    fun retroArchCore(platformName: String): String? = prefs.getString("retroarch_core_$platformName", null)
    fun setRetroArchCore(platformName: String, coreId: String?) =
        prefs.edit().apply { if (coreId == null) remove("retroarch_core_$platformName") else putString("retroarch_core_$platformName", coreId) }.apply()

    /** The user's own menus (see [com.noryan.romrunner.data.model.CustomTab]) as a JSON array; null when there are none. */
    var customTabsJson: String?
        get() = prefs.getString("custom_tabs", null)
        set(value) = prefs.edit().putString("custom_tabs", value).apply()

    fun isSetUp(key: String): Boolean = prefs.getBoolean("setup_done_$key", false)
    fun markSetUp(key: String) = prefs.edit().putBoolean("setup_done_$key", true).apply()

    /** The folder grant for PrimeHack's own user directory (see PrimeHackControls), if the user gave one. */
    var primeHackFolderUri: String?
        get() = prefs.getString(KEY_PRIMEHACK_FOLDER_URI, null)
        set(value) = prefs.edit().putString(KEY_PRIMEHACK_FOLDER_URI, value).apply()

    /** True once the "load PrimeHack's controller profile?" prompt has been shown — offered once. */
    var primeHackSetupPrompted: Boolean
        get() = prefs.getBoolean(KEY_PRIMEHACK_SETUP_PROMPTED, false)
        set(value) = prefs.edit().putBoolean(KEY_PRIMEHACK_SETUP_PROMPTED, value).apply()

    /** The folder grant for Eden's own user directory (see EdenGpuDriver), if the user gave one. */
    var edenFolderUri: String?
        get() = prefs.getString(KEY_EDEN_FOLDER_URI, null)
        set(value) = prefs.edit().putString(KEY_EDEN_FOLDER_URI, value).apply()

    /** True once the user has backed out of Eden's folder picker, so it isn't opened again by itself. */
    var edenSetupPrompted: Boolean
        get() = prefs.getBoolean(KEY_EDEN_SETUP_PROMPTED, false)
        set(value) = prefs.edit().putBoolean(KEY_EDEN_SETUP_PROMPTED, value).apply()

    /** True once ARMSX2 has been opened on its first-run wizard by RomRunner — there is no way to tell when the wizard is finished, so it's offered once. */
    var armsx2WizardShown: Boolean
        get() = prefs.getBoolean(KEY_ARMSX2_WIZARD_SHOWN, false)
        set(value) = prefs.edit().putBoolean(KEY_ARMSX2_WIZARD_SHOWN, value).apply()

    /** True once Azahar has been opened on its first-run setup by RomRunner (it can't tell when that's finished, so it's offered once). */
    var azaharWizardShown: Boolean
        get() = prefs.getBoolean(KEY_AZAHAR_WIZARD_SHOWN, false)
        set(value) = prefs.edit().putBoolean(KEY_AZAHAR_WIZARD_SHOWN, value).apply()

    /** True once RomRunner has written its renderer defaults into Azahar's config.ini (see AzaharSetup.applyDefaults). */
    var azaharDefaultsApplied: Boolean
        get() = prefs.getBoolean(KEY_AZAHAR_DEFAULTS_APPLIED, false)
        set(value) = prefs.edit().putBoolean(KEY_AZAHAR_DEFAULTS_APPLIED, value).apply()

    /** The folder grant for RetroArch's own data folder (see RetroArchLauncher), if the user gave one. */
    var retroArchFolderUri: String?
        get() = prefs.getString(KEY_RETROARCH_FOLDER_URI, null)
        set(value) = prefs.edit().putString(KEY_RETROARCH_FOLDER_URI, value).apply()

    /** True once the user has backed out of RetroArch's folder picker, so it isn't opened again by itself. */
    var retroArchSetupPrompted: Boolean
        get() = prefs.getBoolean(KEY_RETROARCH_SETUP_PROMPTED, false)
        set(value) = prefs.edit().putBoolean(KEY_RETROARCH_SETUP_PROMPTED, value).apply()

    /** The folder grant for Cemu's own user directory (see CemuSetup), if the user gave one. */
    var cemuFolderUri: String?
        get() = prefs.getString(KEY_CEMU_FOLDER_URI, null)
        set(value) = prefs.edit().putString(KEY_CEMU_FOLDER_URI, value).apply()

    /** True once the user has declined the "set up Cemu?" prompt, so it isn't offered again by itself. */
    var cemuSetupPrompted: Boolean
        get() = prefs.getBoolean(KEY_CEMU_SETUP_PROMPTED, false)
        set(value) = prefs.edit().putBoolean(KEY_CEMU_SETUP_PROMPTED, value).apply()

    /** True once the Turnip driver has actually been written into Eden (needs Eden opened once first). */
    var edenDriverApplied: Boolean
        get() = prefs.getBoolean(KEY_EDEN_DRIVER_APPLIED, false)
        set(value) = prefs.edit().putBoolean(KEY_EDEN_DRIVER_APPLIED, value).apply()

    companion object {
        private const val PREFS_NAME = "romrunner_settings"
        private const val KEY_ROOT_FOLDER_URI = "root_folder_uri"
        private const val KEY_KILL_BACKGROUND_APPS_ON_LAUNCH = "kill_background_apps_on_launch"
        private const val KEY_DUAL_SCREEN_SUPPORT_ENABLED = "dual_screen_support_enabled"
        private const val KEY_EMULATOR_SETUP_PROMPTED = "emulator_setup_prompted"
        private const val KEY_EMULATOR_SETUP_PENDING = "emulator_setup_pending"
        private const val KEY_STORAGE_ACCESS_ASKED = "storage_access_asked"
        private const val KEY_EDEN_SETUP_PROMPTED = "eden_setup_prompted"
        private const val KEY_ARMSX2_WIZARD_SHOWN = "armsx2_wizard_shown"
        private const val KEY_AZAHAR_WIZARD_SHOWN = "azahar_wizard_shown"
        private const val KEY_AZAHAR_DEFAULTS_APPLIED = "azahar_defaults_applied"
        private const val KEY_RETROARCH_FOLDER_URI = "retroarch_folder_uri"
        private const val KEY_RETROARCH_SETUP_PROMPTED = "retroarch_setup_prompted"
        private const val KEY_CEMU_FOLDER_URI = "cemu_folder_uri"
        private const val KEY_CEMU_SETUP_PROMPTED = "cemu_setup_prompted"
        private const val KEY_EDEN_FOLDER_URI = "eden_folder_uri"
        private const val KEY_EDEN_DRIVER_APPLIED = "eden_driver_applied"
        private const val KEY_PRIMEHACK_FOLDER_URI = "primehack_folder_uri"
        private const val KEY_PRIMEHACK_SETUP_PROMPTED = "primehack_setup_prompted"
    }
}
