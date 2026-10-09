package com.noryan.romrunner.data.launch

import android.app.DownloadManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import android.provider.MediaStore
import android.content.ContentUris
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

    /**
     * An APK for [emulator] that's already in the Downloads folder, so it needn't be fetched again, or
     * null. Matches the names DownloadManager gives RomRunner's own downloads ("PrimeHack.apk",
     * "PrimeHack-3.apk", …) and takes the newest. Without "All files access" Android only shows RomRunner
     * the files its current install downloaded; once a fresh install has that permission it also sees
     * the ones left behind by earlier installs, which Android no longer attributes to anyone.
     */
    suspend fun findLocalApk(context: Context, emulator: RecommendedEmulator): Uri? = withContext(Dispatchers.IO) {
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val name = Regex("^${Regex.escape(emulator.appLabel)}(-\\d+)?\\.apk$", RegexOption.IGNORE_CASE)
        try {
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads.SIZE),
                "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?",
                arrayOf("${emulator.appLabel}%.apk"),
                "${MediaStore.Downloads.DATE_ADDED} DESC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val displayName = cursor.getString(1) ?: continue
                    // Skip anything too small to be a real APK (e.g. a failed or empty download).
                    if (name.matches(displayName) && cursor.getLong(2) > 1_000_000) {
                        return@withContext ContentUris.withAppendedId(collection, cursor.getLong(0))
                    }
                }
            }
        } catch (e: Exception) {
            // fall through: no local copy, so the caller downloads one
        }
        null
    }

    /**
     * Installs the finished download and waits until the user is done with the system's confirmation. See
     * [installApk]; returns whether the app was installed.
     */
    suspend fun runInstallerAndWait(context: Context, downloadId: Long, packageName: String): Boolean {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return installApk(context, downloadManager.getUriForDownloadedFile(downloadId))
    }

    suspend fun runInstallerAndWait(context: Context, apkUri: Uri, packageName: String): Boolean = installApk(context, apkUri)

    /**
     * Installs the APK at [apkUri] through a PackageInstaller session instead of handing the file to the system
     * installer as a "view this file" intent. The user still gets Android's "Do you want to install this app?"
     * confirmation (that can't be skipped), but afterwards the system doesn't show its "App installed — Done / Open"
     * screen, which a view-intent install always does; the result comes back to RomRunner as a broadcast instead,
     * which is also how it knows the user has finished, so installs in a batch don't stack up.
     * Returns true once the app is installed, false if the user cancelled or it failed.
     */
    suspend fun installApk(context: Context, apkUri: Uri, unattended: Boolean = false): Boolean {
        val app = context.applicationContext
        val installer = app.packageManager.packageInstaller
        val sessionId = withContext(Dispatchers.IO) {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            // An update to an app this app installed can go through without a confirmation on Android 12+; elsewhere the usual one shows.
            if (unattended && android.os.Build.VERSION.SDK_INT >= 31) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            installer.createSession(params)
        }
        val action = "com.noryan.romrunner.INSTALL_RESULT.$sessionId"
        val result = CompletableDeferred<Int>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                    // The system hands back its confirmation screen to show; RomRunner is in front, so it opens over it.
                    @Suppress("DEPRECATION")
                    val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { c.startActivity(it) }
                } else {
                    result.complete(status)
                }
            }
        }
        ContextCompat.registerReceiver(app, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            withContext(Dispatchers.IO) {
                installer.openSession(sessionId).use { session ->
                    val input = app.contentResolver.openInputStream(apkUri) ?: error("couldn't read the APK")
                    input.use { source ->
                        session.openWrite("base.apk", 0, -1).use { out ->
                            source.copyTo(out)
                            session.fsync(out)
                        }
                    }
                    val resultIntent = PendingIntent.getBroadcast(
                        app, sessionId, Intent(action).setPackage(app.packageName),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    )
                    session.commit(resultIntent.intentSender)
                }
            }
            val status = withTimeoutOrNull(10 * 60_000L) { result.await() }
            if (status == null) runCatching { installer.abandonSession(sessionId) }
            return status == PackageInstaller.STATUS_SUCCESS
        } catch (e: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            return false
        } finally {
            app.unregisterReceiver(receiver)
        }
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
}
