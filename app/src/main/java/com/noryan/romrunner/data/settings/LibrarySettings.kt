package com.noryan.romrunner.data.settings

import android.content.Context

/** The one setting that matters for v1: which folder the whole library gets scanned from. */
class LibrarySettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var rootFolderUri: String?
        get() = prefs.getString(KEY_ROOT_FOLDER_URI, null)
        set(value) = prefs.edit().putString(KEY_ROOT_FOLDER_URI, value).apply()

    /** The one folder holding every BIOS/keys/firmware file — see [com.noryan.romrunner.data.launch.BiosKeysImporter]. */
    var biosKeysFolderUri: String?
        get() = prefs.getString(KEY_BIOS_KEYS_FOLDER_URI, null)
        set(value) = prefs.edit().putString(KEY_BIOS_KEYS_FOLDER_URI, value).apply()

    /** Whether the user tapped "Skip for now" on the first-run BIOS/Keys folder prompt — keeps it
     *  from nagging on every launch for someone with no Switch/PS2 games. */
    var biosKeysPromptDismissed: Boolean
        get() = prefs.getBoolean(KEY_BIOS_KEYS_PROMPT_DISMISSED, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOS_KEYS_PROMPT_DISMISSED, value).apply()

    /** Whether to ask Android to kill other apps' background processes right before launching a
     *  game, freeing memory for it. Default on — see [com.noryan.romrunner.data.launch.BackgroundAppCleaner]. */
    var killBackgroundAppsOnLaunch: Boolean
        get() = prefs.getBoolean(KEY_KILL_BACKGROUND_APPS_ON_LAUNCH, true)
        set(value) = prefs.edit().putBoolean(KEY_KILL_BACKGROUND_APPS_ON_LAUNCH, value).apply()

    companion object {
        private const val PREFS_NAME = "romrunner_settings"
        private const val KEY_ROOT_FOLDER_URI = "root_folder_uri"
        private const val KEY_BIOS_KEYS_FOLDER_URI = "bios_keys_folder_uri"
        private const val KEY_BIOS_KEYS_PROMPT_DISMISSED = "bios_keys_prompt_dismissed"
        private const val KEY_KILL_BACKGROUND_APPS_ON_LAUNCH = "kill_background_apps_on_launch"
    }
}
