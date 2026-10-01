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

    companion object {
        private const val PREFS_NAME = "romrunner_settings"
        private const val KEY_ROOT_FOLDER_URI = "root_folder_uri"
        private const val KEY_KILL_BACKGROUND_APPS_ON_LAUNCH = "kill_background_apps_on_launch"
        private const val KEY_DUAL_SCREEN_SUPPORT_ENABLED = "dual_screen_support_enabled"
    }
}
