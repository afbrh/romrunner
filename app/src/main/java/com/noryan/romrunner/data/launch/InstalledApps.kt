package com.noryan.romrunner.data.launch

import android.content.Context
import android.content.Intent

data class InstalledApp(val label: String, val packageName: String)

object InstalledApps {
    /** Every app with a launcher icon, for the "which app should play this?" fallback picker. */
    fun listLaunchable(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { InstalledApp(label = it.loadLabel(pm).toString(), packageName = it.activityInfo.packageName) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
