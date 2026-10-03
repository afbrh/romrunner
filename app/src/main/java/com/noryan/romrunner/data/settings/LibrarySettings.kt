package com.noryan.romrunner.data.settings

import android.content.Context

/** The one setting that matters for v1: which folder the whole library gets scanned from. */
class LibrarySettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var rootFolderUri: String?
        get() = prefs.getString(KEY_ROOT_FOLDER_URI, null)
        set(value) = prefs.edit().putString(KEY_ROOT_FOLDER_URI, value).apply()

    /** The folder holding BIOS/keys/firmware files for emulators that need them — see
     *  [com.noryan.romrunner.ui.platforms.PlatformsContent]'s "Recommended Emulators" auto-setup. */
    var biosKeysFolderUri: String?
        get() = prefs.getString(KEY_BIOS_KEYS_FOLDER_URI, null)
        set(value) = prefs.edit().putString(KEY_BIOS_KEYS_FOLDER_URI, value).apply()

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

    /** The folder grant for PrimeHack's own user directory (see PrimeHackControls), if the user gave one. */
    var primeHackFolderUri: String?
        get() = prefs.getString(KEY_PRIMEHACK_FOLDER_URI, null)
        set(value) = prefs.edit().putString(KEY_PRIMEHACK_FOLDER_URI, value).apply()

    /** True once the "load PrimeHack's controller profile?" prompt has been shown — offered once. */
    var primeHackSetupPrompted: Boolean
        get() = prefs.getBoolean(KEY_PRIMEHACK_SETUP_PROMPTED, false)
        set(value) = prefs.edit().putBoolean(KEY_PRIMEHACK_SETUP_PROMPTED, value).apply()

    companion object {
        private const val PREFS_NAME = "romrunner_settings"
        private const val KEY_ROOT_FOLDER_URI = "root_folder_uri"
        private const val KEY_BIOS_KEYS_FOLDER_URI = "bios_keys_folder_uri"
        private const val KEY_KILL_BACKGROUND_APPS_ON_LAUNCH = "kill_background_apps_on_launch"
        private const val KEY_DUAL_SCREEN_SUPPORT_ENABLED = "dual_screen_support_enabled"
        private const val KEY_EMULATOR_SETUP_PROMPTED = "emulator_setup_prompted"
        private const val KEY_EMULATOR_SETUP_PENDING = "emulator_setup_pending"
        private const val KEY_PRIMEHACK_FOLDER_URI = "primehack_folder_uri"
        private const val KEY_PRIMEHACK_SETUP_PROMPTED = "primehack_setup_prompted"
    }
}
