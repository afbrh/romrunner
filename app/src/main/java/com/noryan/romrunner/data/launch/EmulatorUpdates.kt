package com.noryan.romrunner.data.launch

import android.app.DownloadManager
import android.content.Context
import com.noryan.romrunner.data.repository.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps the emulators RomRunner uses up to date: on start-up it asks each needed, installed emulator's release channel (the same
 * one the installer uses) for its latest stable APK, and downloads and installs it if what's installed is older.
 *
 * How "older" is decided, as no emulator offers a version number to compare: each release is identified by its download address
 * (GitHub and Gitea put the release tag in it) plus, for fixed addresses such as RetroArch's and PPSSPP's, what the server says
 * about the file (ETag, last-modified, size). RomRunner remembers the one it last installed. With nothing remembered, an app
 * counts as current when its version name matches the release tag, and as out of date otherwise, which brings an app
 * installed from an old APK up to date once.
 *
 * Android makes the user confirm installs. An update to an app RomRunner installed can skip that on Android 12 and later
 * (see [EmulatorDownloader.installApk]); where it can't, the usual confirmation shows.
 */
object EmulatorUpdates {

    private val ranThisLaunch = AtomicBoolean(false)

    /** True once per run of the app, so the check happens at start-up and not every time the screen is redrawn. */
    fun claimThisLaunch(): Boolean = ranThisLaunch.compareAndSet(false, true)

    data class Release(val fingerprint: String, val tag: String?)

    data class Report(
        val current: List<String>,
        val updated: List<String>,
        val failed: List<String>,
        val notInstalled: List<String>,
        /** Emulators whose release channel couldn't be reached. */
        val unchecked: List<String>
    )

    /**
     * Checks [emulators] and updates the out-of-date ones. [announce] is told "Updating X, Y…" before the first download;
     * [setStatus] puts "Updating…" on an emulator's Systems line while it's being updated, and clears it (null) afterwards.
     */
    suspend fun run(
        context: Context,
        repository: LibraryRepository,
        emulators: List<RecommendedEmulator>,
        announce: suspend (String) -> Unit,
        setStatus: (packageName: String, text: String?) -> Unit
    ): Report {
        val current = mutableListOf<String>()
        val updated = mutableListOf<String>()
        val failed = mutableListOf<String>()
        val notInstalled = mutableListOf<String>()
        val unchecked = mutableListOf<String>()
        val outdated = mutableListOf<Pair<RecommendedEmulator, Release>>()

        for (emulator in emulators) {
            if (!EmulatorLauncher.isPackageInstalled(context, emulator.packageName)) {
                notInstalled += emulator.appLabel
                continue
            }
            val release = withContext(Dispatchers.IO) { latestRelease(emulator) }
            if (release == null) {
                unchecked += emulator.appLabel
                continue
            }
            val remembered = repository.getReleaseFingerprint(emulator.packageName)
            val isCurrent = when {
                remembered == release.fingerprint -> true
                remembered == null && release.tag != null && versionMatches(installedVersion(context, emulator.packageName), release.tag) -> {
                    repository.setReleaseFingerprint(emulator.packageName, release.fingerprint)
                    true
                }
                else -> false
            }
            if (isCurrent) current += emulator.appLabel else outdated += emulator to release
        }

        if (outdated.isNotEmpty()) {
            announce("Updating ${outdated.joinToString(", ") { it.first.appLabel }}…")
            for ((emulator, release) in outdated) {
                setStatus(emulator.packageName, "Updating…")
                val ok = try {
                    update(context, emulator)
                } finally {
                    setStatus(emulator.packageName, null)
                }
                if (ok) {
                    repository.setReleaseFingerprint(emulator.packageName, release.fingerprint)
                    updated += emulator.appLabel
                } else {
                    failed += emulator.appLabel
                }
            }
        }
        return Report(current, updated, failed, notInstalled, unchecked)
    }

    /** Downloads the latest release afresh (never an older copy left in Downloads) and installs it over the current one. */
    private suspend fun update(context: Context, emulator: RecommendedEmulator): Boolean {
        val downloadId = EmulatorDownloader.enqueue(context, emulator) ?: return false
        if (!EmulatorDownloader.awaitDownload(context, downloadId)) return false
        val downloads = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = downloads.getUriForDownloadedFile(downloadId) ?: return false
        return EmulatorDownloader.installApk(context, uri, unattended = true)
    }

    private fun latestRelease(emulator: RecommendedEmulator): Release? = runCatching {
        val url = emulator.resolveApkUrl() ?: return null
        val tag = Regex("/download/([^/]+)/").find(url)?.groupValues?.get(1)
        // A release address with the tag in it changes with every release; a fixed address needs the server's word on the file.
        Release(if (tag != null) url else "$url|${headFingerprint(url)}", tag)
    }.getOrNull()

    private fun headFingerprint(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            listOf(connection.getHeaderField("ETag"), connection.getHeaderField("Last-Modified"), connection.getHeaderField("Content-Length"))
                .joinToString("|") { it.orEmpty() }
        } finally {
            connection.disconnect()
        }
    }

    private fun installedVersion(context: Context, packageName: String): String? = runCatching {
        val installed = EmulatorLauncher.installedPackageFor(context, packageName) ?: packageName
        context.packageManager.getPackageInfo(installed, 0).versionName
    }.getOrNull()

    private fun versionMatches(installed: String?, tag: String): Boolean {
        fun norm(text: String) = text.lowercase().trim().removePrefix("v")
        val a = norm(installed ?: return false)
        val b = norm(tag)
        return a.length >= 3 && b.length >= 3 && (a.contains(b) || b.contains(a))
    }
}
