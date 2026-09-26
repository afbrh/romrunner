package com.noryan.romrunner.data.launch

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo

/**
 * Frees memory before launching a game by asking Android to kill other apps' cached/background
 * processes — the same ActivityManager.killBackgroundProcesses() building block every Play
 * Store "task killer" app is built on, since a regular app has no broader authority than that.
 * Android reserves the unconditional force-stop the AYN Thor's own launcher uses (confirmed via
 * logcat: forceStopPackage(), a system/signature-level API) for privileged callers only — a
 * normal app calling it gets a SecurityException. killBackgroundProcesses() also can't touch a
 * foreground app, one with an active foreground service (e.g. actively playing music), or
 * anything else Android considers "perceptible" — that's a deliberate OS restriction, not a gap
 * in this code, so this is a best-effort memory reclaim, not a guaranteed "kill everything else."
 *
 * Motivated by an Eden (embedded Switch core) session dying with no Java exception, no native
 * crash signal, and no ActivityManager-initiated kill — the signature of external memory-pressure
 * reclaim rather than an app bug (see romrunner-ayn-thor-device-quirks memory for the diagnosis).
 */
object BackgroundAppCleaner {
    private val EXCLUDED_PACKAGES = setOf("com.noryan.romrunner")

    /** Best-effort; every package is handled independently so one failure can't skip the rest. */
    fun killNonEssentialApps(context: Context) {
        val packageManager = context.packageManager
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launchableApps = runCatching { packageManager.queryIntentActivities(launcherIntent, 0) }
            .getOrDefault(emptyList())

        val targetPackages = launchableApps
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it !in EXCLUDED_PACKAGES }
            .filter { packageName ->
                // Skip system apps (device launcher, settings, dialer, ...) — killing these has
                // no real memory-pressure upside and risks destabilizing the device.
                val info = runCatching { packageManager.getApplicationInfo(packageName, 0) }.getOrNull()
                info == null || (info.flags and ApplicationInfo.FLAG_SYSTEM) == 0
            }

        for (packageName in targetPackages) {
            runCatching { activityManager.killBackgroundProcesses(packageName) }
        }
    }
}
