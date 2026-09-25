package com.noryan.romrunner.data.settings

import android.content.Context

/** The one setting that matters for v1: which folder the whole library gets scanned from. */
class LibrarySettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var rootFolderUri: String?
        get() = prefs.getString(KEY_ROOT_FOLDER_URI, null)
        set(value) = prefs.edit().putString(KEY_ROOT_FOLDER_URI, value).apply()

    companion object {
        private const val PREFS_NAME = "romrunner_settings"
        private const val KEY_ROOT_FOLDER_URI = "root_folder_uri"
    }
}
