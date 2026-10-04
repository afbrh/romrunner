package com.noryan.romrunner.ui.platforms

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.noryan.romrunner.data.launch.EmulatorDownloader
import com.noryan.romrunner.data.launch.EmulatorLauncher
import com.noryan.romrunner.data.launch.RecommendedEmulator

/**
 * Tracks which emulators are currently being downloaded/installed and what stage each is at, so the
 * Settings rows can show live status no matter whether the install was started by tapping a single
 * row, the "Install All" button, or the first-run prompt on the Games tab (hence one instance shared
 * between LibraryScreen and PlatformsContent).
 */
class EmulatorInstallState {

    /** Package name -> status label ("Queued…", "Downloading…", …) for every emulator in flight. */
    val statuses = mutableStateMapOf<String, String>()

    var isInstallingAll by mutableStateOf(false)
        private set

    /** Bumped whenever an install attempt finishes, so rows re-check what's actually installed. */
    var refreshTick by mutableIntStateOf(0)
        private set

    /** Lets other flows (e.g. PrimeHack's folder setup) make the Settings rows re-read their state. */
    fun bumpRefresh() {
        refreshTick++
    }

    /** Downloads and installs a single emulator (the per-row tap). */
    suspend fun installOne(context: Context, emulator: RecommendedEmulator) {
        val pkg = emulator.packageName
        if (isInstallingAll || statuses.containsKey(pkg)) return
        statuses[pkg] = "Finding latest…"
        try {
            val local = EmulatorDownloader.findLocalApk(context, emulator)
            if (local != null) {
                // Already downloaded earlier (or by hand): install that copy instead of fetching another.
                statuses[pkg] = "Installing…"
                EmulatorDownloader.installApk(context, local)
                return
            }
            val downloadId = EmulatorDownloader.enqueue(context, emulator)
            if (downloadId == null) {
                openReleasesPage(context, emulator)
                return
            }
            statuses[pkg] = "Downloading…"
            if (!EmulatorDownloader.awaitDownload(context, downloadId)) {
                Toast.makeText(context, "Download failed for ${emulator.appLabel}.", Toast.LENGTH_LONG).show()
                return
            }
            statuses[pkg] = "Installing…"
            // Waits for the user to finish with the system's confirmation (see installApk), so the row
            // flips to "Installed" as soon as it actually is.
            EmulatorDownloader.runInstallerAndWait(context, downloadId, pkg)
        } finally {
            statuses.remove(pkg)
            refreshTick++
        }
    }

    /**
     * Installs every not-yet-installed emulator in [emulators], using an APK already in Downloads where
     * there is one. Other downloads are queued up front so they run in parallel while the user steps
     * through the system installer one app at a time.
     * Apps with no automatable download are skipped during the batch and their download
     * pages opened at the end instead, so a browser doesn't interrupt the installer screens.
     */
    suspend fun installAll(context: Context, emulators: List<RecommendedEmulator>) {
        if (isInstallingAll || statuses.isNotEmpty()) return
        val missing = emulators.filter { !EmulatorLauncher.isPackageInstalled(context, it.packageName) }
        if (missing.isEmpty()) return

        isInstallingAll = true
        missing.forEach { statuses[it.packageName] = "Queued…" }
        try {
            val queued = mutableListOf<Pair<RecommendedEmulator, Long>>()
            val local = mutableListOf<Pair<RecommendedEmulator, Uri>>()
            val manual = mutableListOf<RecommendedEmulator>()
            for (emulator in missing) {
                statuses[emulator.packageName] = "Finding latest…"
                // A copy that's already on the device is installed as-is, with no download.
                val existing = EmulatorDownloader.findLocalApk(context, emulator)
                if (existing != null) {
                    statuses[emulator.packageName] = "Queued…"
                    local += emulator to existing
                    continue
                }
                val downloadId = EmulatorDownloader.enqueue(context, emulator)
                if (downloadId == null) {
                    statuses.remove(emulator.packageName)
                    manual += emulator
                } else {
                    statuses[emulator.packageName] = "Downloading…"
                    queued += emulator to downloadId
                }
            }

            // Local copies first: they're ready now, while the downloads above are still running.
            for ((emulator, apkUri) in local) {
                statuses[emulator.packageName] = "Installing…"
                EmulatorDownloader.runInstallerAndWait(context, apkUri, emulator.packageName)
                statuses.remove(emulator.packageName)
                refreshTick++
            }

            for ((emulator, downloadId) in queued) {
                val pkg = emulator.packageName
                if (!EmulatorDownloader.awaitDownload(context, downloadId)) {
                    Toast.makeText(context, "Download failed for ${emulator.appLabel}.", Toast.LENGTH_LONG).show()
                    statuses.remove(pkg)
                    continue
                }
                statuses[pkg] = "Installing…"
                EmulatorDownloader.runInstallerAndWait(context, downloadId, pkg)
                statuses.remove(pkg)
                refreshTick++
            }

            manual.forEach { openReleasesPage(context, it) }
        } finally {
            statuses.clear()
            isInstallingAll = false
            refreshTick++
        }
    }

    private fun openReleasesPage(context: Context, emulator: RecommendedEmulator) {
        Toast.makeText(
            context,
            "${emulator.appLabel} has to be downloaded manually — opening its download page.",
            Toast.LENGTH_LONG
        ).show()
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(emulator.releasesPageUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
