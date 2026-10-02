package com.noryan.romrunner.data.launch

import android.app.Activity
import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** The download-and-install steps for a [RecommendedEmulator], shared by the one-row tap and "Install All". */
object EmulatorDownloader {

    /**
     * Looks up [emulator]'s latest stable APK and queues it with DownloadManager. Returns the
     * download id, or null if no matching asset could be found (or this app has no automatable
     * download source at all) — the caller decides whether to fall back to the releases page.
     */
    suspend fun enqueue(context: Context, emulator: RecommendedEmulator): Long? {
        val apkUrl = withContext(Dispatchers.IO) { emulator.resolveApkUrl() } ?: return null
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle(emulator.appLabel)
            .setDescription("Downloading latest ${emulator.appLabel} release")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "${emulator.appLabel}.apk")
            .setMimeType("application/vnd.android.package-archive")
        return downloadManager.enqueue(request)
    }

    /**
     * Polls [downloadId]'s status until DownloadManager reports it finished (successfully or not),
     * since DownloadManager has no suspend-friendly completion API of its own. Gives up after 5
     * minutes so a stalled/paused download (e.g. lost network mid-transfer) can't hang this forever.
     */
    suspend fun awaitDownload(context: Context, downloadId: Long): Boolean = withContext(Dispatchers.IO) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        repeat(600) {
            downloadManager.query(query).use { cursor ->
                if (cursor.moveToFirst()) {
                    when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                        DownloadManager.STATUS_SUCCESSFUL -> return@withContext true
                        DownloadManager.STATUS_FAILED -> return@withContext false
                    }
                }
            }
            Thread.sleep(500)
        }
        false
    }

    /** Hands the finished download to the system installer UI. */
    fun launchInstaller(context: Context, downloadId: Long) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val apkUri = downloadManager.getUriForDownloadedFile(downloadId)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    /** Polls PackageManager until [packageName] shows up as installed, or [seconds] pass. */
    suspend fun awaitInstalled(context: Context, packageName: String, seconds: Int): Boolean =
        withContext(Dispatchers.IO) {
            repeat(seconds) {
                if (EmulatorLauncher.isPackageInstalled(context, packageName)) return@withContext true
                Thread.sleep(1_000)
            }
            EmulatorLauncher.isPackageInstalled(context, packageName)
        }

    /**
     * Opens the system installer for [downloadId] and waits until the user is done with it, so a
     * batch install can move on to the next app without stacking installer screens on top of each
     * other. "Done" means RomRunner goes to the background (the installer took over) and then comes
     * back; the install itself is then confirmed with a short PackageManager poll. If the installer
     * never appears to take over, falls back to polling for up to a minute.
     */
    suspend fun runInstallerAndWait(context: Context, downloadId: Long, packageName: String): Boolean {
        val application = context.applicationContext as Application
        val wentToBackground = CompletableDeferred<Unit>()
        val cameBack = CompletableDeferred<Unit>()
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStopped(activity: Activity) {
                wentToBackground.complete(Unit)
            }

            override fun onActivityStarted(activity: Activity) {
                if (wentToBackground.isCompleted) cameBack.complete(Unit)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        }
        application.registerActivityLifecycleCallbacks(callbacks)
        try {
            launchInstaller(context, downloadId)
            if (withTimeoutOrNull(15_000) { wentToBackground.await() } == null) {
                return awaitInstalled(context, packageName, seconds = 60)
            }
            withTimeoutOrNull(10 * 60_000L) { cameBack.await() }
            return awaitInstalled(context, packageName, seconds = 5)
        } finally {
            application.unregisterActivityLifecycleCallbacks(callbacks)
        }
    }
}
