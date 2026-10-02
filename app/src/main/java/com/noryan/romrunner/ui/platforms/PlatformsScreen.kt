package com.noryan.romrunner.ui.platforms

import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.noryan.romrunner.data.launch.EmulatorLauncher
import com.noryan.romrunner.data.launch.LatestReleaseFinder
import com.noryan.romrunner.data.model.Game
import com.noryan.romrunner.data.model.Platform
import com.noryan.romrunner.data.repository.LibraryRepository
import com.noryan.romrunner.ui.components.RetroToggle
import com.noryan.romrunner.ui.components.glowColor
import com.noryan.romrunner.ui.components.glowShadow
import com.noryan.romrunner.ui.components.rememberFocusInteractionSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val InstalledGreen = Color(0xFF6BCB77)
private val NotInstalledRed = Color(0xFFFF6B6B)

private data class RecommendedEmulator(
    /** Shown in the row's left column — usually a system name ("3DS"), but a specific game title
     *  for a per-title override ("Metroid Prime Trilogy") where the system's usual app won't do. */
    val rowLabel: String,
    val appLabel: String,
    val packageName: String,
    /** Fallback the user lands on if [resolveApkUrl] can't find a matching asset (or there's no
     *  download API available for this app at all — it just always returns null in that case). */
    val releasesPageUrl: String,
    /** Resolves this release's current APK download URL, or null if none could be found/this app
     *  has no automatable download source at all. Blocking — always called on Dispatchers.IO. */
    val resolveApkUrl: () -> String?,
    /** Whether this row should show at all, given the platforms/games actually in the library. */
    val isNeeded: (platforms: List<Platform>, games: List<Game>) -> Boolean
)

// ARMSX2 ships one .apk per Android-version tier (sdk30/33/35) rather than one universal .apk —
// pick the highest tier this device's own SDK_INT actually qualifies for.
private val armsx2AssetMatcher: (String) -> Boolean = { name ->
    val tier = when {
        Build.VERSION.SDK_INT >= 35 -> "sdk35"
        Build.VERSION.SDK_INT >= 33 -> "sdk33"
        else -> "sdk30"
    }
    name.endsWith(".apk") && tier in name
}

private fun hasGameOn(platformName: String): (List<Platform>, List<Game>) -> Boolean = { platforms, games ->
    val platformId = platforms.find { it.name == platformName }?.id
    platformId != null && games.any { it.platformId == platformId }
}

private val RECOMMENDED_EMULATORS = listOf(
    RecommendedEmulator(
        rowLabel = "3DS",
        appLabel = "Azahar",
        packageName = "org.azahar_emu.azahar",
        releasesPageUrl = "https://github.com/azahar-emu/azahar/releases",
        // "vanilla" (not "googleplay") is the sideload-capable build — same flavor pick this
        // project already made for the formerly-embedded Azahar core.
        resolveApkUrl = {
            LatestReleaseFinder.findStableAssetUrl("https://api.github.com/repos/azahar-emu/azahar/releases/latest") { name ->
                name.endsWith(".apk") && "vanilla" in name
            }
        },
        isNeeded = hasGameOn("Nintendo 3DS")
    ),
    RecommendedEmulator(
        rowLabel = "PS2",
        appLabel = "ARMSX2",
        packageName = "com.armsx2",
        releasesPageUrl = "https://github.com/ARMSX2/ARMSX2/releases",
        resolveApkUrl = {
            LatestReleaseFinder.findStableAssetUrl("https://api.github.com/repos/ARMSX2/ARMSX2/releases/latest", armsx2AssetMatcher)
        },
        isNeeded = hasGameOn("PlayStation 2")
    ),
    RecommendedEmulator(
        rowLabel = "Switch",
        appLabel = "Eden",
        packageName = "dev.eden.eden_emulator",
        releasesPageUrl = "https://git.eden-emu.dev/eden-emu/eden/releases",
        // Eden's maintainers moved off GitHub entirely to self-hosted infrastructure, which
        // exposes the same GitHub-compatible Releases API shape.
        // "standard" is Eden's generic build; "optimized" needs newer-CPU-specific instructions
        // not guaranteed on every device, and "chromeos"/"legacy" aren't the right pick either.
        resolveApkUrl = {
            LatestReleaseFinder.findStableAssetUrl("https://git.eden-emu.dev/api/v1/repos/eden-emu/eden/releases/latest") { name ->
                name.endsWith(".apk") && "standard" in name
            }
        },
        isNeeded = hasGameOn("Nintendo Switch")
    ),
    RecommendedEmulator(
        rowLabel = "Nintendo DS",
        appLabel = "MelonDS",
        // The app published under this package is WatermelonDS, a melonDS-android fork.
        packageName = "me.magnum.melondualds",
        releasesPageUrl = "https://github.com/SapphireRhodonite/WatermelonDS/releases",
        resolveApkUrl = {
            LatestReleaseFinder.findStableAssetUrl("https://api.github.com/repos/SapphireRhodonite/WatermelonDS/releases/latest") { name ->
                name.endsWith(".apk")
            }
        },
        isNeeded = hasGameOn("Nintendo DS")
    ),
    RecommendedEmulator(
        rowLabel = "GameCube / Wii",
        appLabel = "Dolphin",
        packageName = "org.dolphinemu.dolphinemu",
        // Dolphin isn't distributed via GitHub Releases at all (confirmed: the repo has none) —
        // it's built on its own buildbot with no stable, parseable "latest Android APK" URL, so
        // this always falls back to the download page rather than guessing one.
        releasesPageUrl = "https://dolphin-emu.org/download/",
        resolveApkUrl = { null },
        // Needed only for a GameCube/Wii game that ISN'T Metroid Prime Trilogy — that one routes
        // to PrimeHack instead (see the entry below and GameLaunchOverrides.kt).
        isNeeded = { platforms, games ->
            val platformId = platforms.find { it.name == "GameCube / Wii" }?.id
            platformId != null && games.any {
                it.platformId == platformId && !it.title.contains("metroid prime trilogy", ignoreCase = true)
            }
        }
    ),
    RecommendedEmulator(
        rowLabel = "Metroid Prime Trilogy",
        appLabel = "PrimeHack",
        packageName = "org.dolphinemu.primehack",
        releasesPageUrl = "https://github.com/Starlightbotanist/PrimeHack-Android/releases",
        resolveApkUrl = {
            LatestReleaseFinder.findStableAssetUrl("https://api.github.com/repos/Starlightbotanist/PrimeHack-Android/releases/latest") { name ->
                name.endsWith(".apk")
            }
        },
        isNeeded = { _, games -> games.any { it.title.contains("metroid prime trilogy", ignoreCase = true) } }
    ),
    RecommendedEmulator(
        rowLabel = "Wii U",
        appLabel = "Cemu",
        packageName = "info.cemu.cemu",
        releasesPageUrl = "https://github.com/SapphireRhodonite/Cemu/releases",
        resolveApkUrl = {
            LatestReleaseFinder.findStableAssetUrl("https://api.github.com/repos/SapphireRhodonite/Cemu/releases/latest") { name ->
                name.endsWith(".apk")
            }
        },
        isNeeded = hasGameOn("Wii U")
    ),
    RecommendedEmulator(
        rowLabel = "GameBoy",
        appLabel = "RetroArch",
        packageName = "com.retroarch",
        // Explicitly the web build per request, not the Play Store listing — RetroArch's GitHub
        // releases carry no Android APK either (source tarball only), so this resolves against
        // its own buildbot instead (see LatestReleaseFinder.findRetroArchStableApkUrl).
        releasesPageUrl = "https://www.retroarch.com/?page=platforms",
        resolveApkUrl = { LatestReleaseFinder.findRetroArchStableApkUrl() },
        isNeeded = hasGameOn("GameBoy (Color + Advance)")
        // NES/SNES/Nintendo 64/PlayStation also play through RetroArch but have no games in the
        // library yet — add their own hasGameOn(...) entries (same appLabel/packageName/resolver)
        // if/when they do, rather than show a RetroArch row with nothing in it to justify it.
    )
)

/**
 * Looks up [emulator]'s latest stable release, downloads its APK, and hands it straight to the
 * system installer once the download finishes — or falls back to just opening the releases page
 * if no matching asset could be found. [onStatusChange] drives the row's "Finding latest…" /
 * "Downloading…" / "Installing…" label while this runs; [onInstalled] fires the moment the
 * package actually shows up as installed, so the row can flip to "Installed" right away instead
 * of waiting for the user to back out to RomRunner and re-open Settings.
 */
private suspend fun downloadLatestRelease(
    context: android.content.Context,
    emulator: RecommendedEmulator,
    onStatusChange: (String) -> Unit,
    onInstalled: () -> Unit
) {
    onStatusChange("Finding latest…")
    val apkUrl = withContext(Dispatchers.IO) { emulator.resolveApkUrl() }
    if (apkUrl == null) {
        Toast.makeText(context, "Couldn't find a download for ${emulator.appLabel} — opening its releases page.", Toast.LENGTH_LONG).show()
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(emulator.releasesPageUrl)))
        return
    }

    val downloadManager = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as DownloadManager
    val request = DownloadManager.Request(Uri.parse(apkUrl))
        .setTitle(emulator.appLabel)
        .setDescription("Downloading latest ${emulator.appLabel} release")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "${emulator.appLabel}.apk")
        .setMimeType("application/vnd.android.package-archive")
    val downloadId = downloadManager.enqueue(request)
    onStatusChange("Downloading…")

    val succeeded = withContext(Dispatchers.IO) { awaitDownload(downloadManager, downloadId) }
    if (!succeeded) {
        Toast.makeText(context, "Download failed for ${emulator.appLabel}.", Toast.LENGTH_LONG).show()
        return
    }

    onStatusChange("Installing…")
    val apkUri = downloadManager.getUriForDownloadedFile(downloadId)
    context.startActivity(
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )

    // The install Intent above just hands off to the system installer UI — it doesn't tell us
    // when (or whether) the user actually finishes it. Poll PackageManager in the background
    // (this coroutine keeps running while the installer is in front) so the row can flip to
    // "Installed" the moment it actually becomes installed, without needing the user to back out
    // to RomRunner first. Gives up after a minute if the user never completes/cancels the prompt;
    // PlatformsContent's own resume-triggered recheck still catches it later if they finish it
    // after that.
    val installed = withContext(Dispatchers.IO) { awaitInstall(context, emulator.packageName) }
    if (installed) onInstalled()
}

private fun awaitInstall(context: android.content.Context, packageName: String): Boolean {
    repeat(60) {
        if (EmulatorLauncher.isPackageInstalled(context, packageName)) return true
        Thread.sleep(1_000)
    }
    return false
}

/**
 * Polls [downloadId]'s status until DownloadManager reports it finished (successfully or not),
 * since DownloadManager has no suspend-friendly completion API of its own. Gives up after 5
 * minutes so a stalled/paused download (e.g. lost network mid-transfer) can't hang this forever.
 */
private fun awaitDownload(downloadManager: DownloadManager, downloadId: Long): Boolean {
    val query = DownloadManager.Query().setFilterById(downloadId)
    repeat(600) {
        downloadManager.query(query).use { cursor ->
            if (cursor.moveToFirst()) {
                when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                    DownloadManager.STATUS_SUCCESSFUL -> return true
                    DownloadManager.STATUS_FAILED -> return false
                }
            }
        }
        Thread.sleep(500)
    }
    return false
}

/**
 * The Settings tab's content on RomRunner's home screen (see LibraryScreen). Deliberately has no
 * Scaffold/TopAppBar of its own — it's embedded directly under the GAMES/SETTINGS tab heading row
 * rather than being a separate navigation destination.
 */
@Composable
fun PlatformsContent(
    repository: LibraryRepository,
    onDualScreenSupportChanged: () -> Unit,
    onRomsFolderChanged: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var resolvingPackage by remember { mutableStateOf<String?>(null) }
    var resolvingLabel by remember { mutableStateOf("") }

    // Safety net alongside downloadLatestRelease's own post-install poll: re-checks every
    // Recommended Emulators row whenever RomRunner comes back to the foreground (e.g. returning
    // from the system installer), so a row still catches up to "Installed" even if the user takes
    // longer than that poll's own timeout to finish installing.
    val lifecycleOwner = LocalLifecycleOwner.current
    var installCheckTick by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) installCheckTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var rootFolderUri by remember { mutableStateOf(repository.getRootFolderUri()) }
    LaunchedEffect(Unit) { rootFolderUri = repository.getRootFolderUri() }

    var biosKeysFolderUri by remember { mutableStateOf(repository.getBiosKeysFolderUri()) }
    LaunchedEffect(Unit) { biosKeysFolderUri = repository.getBiosKeysFolderUri() }

    // Only show a Recommended Emulators row for a platform (or specific title, for a per-title
    // override) the library actually has a matching game for — e.g. no point recommending Eden if
    // there isn't a single Switch game scanned in yet.
    val games by repository.observeGames().collectAsStateWithLifecycle(initialValue = emptyList())
    val platforms by repository.observePlatforms().collectAsStateWithLifecycle(initialValue = emptyList())
    val visibleRecommendedEmulators = remember(games, platforms) {
        RECOMMENDED_EMULATORS.filter { it.isNeeded(platforms, games) }
    }

    var killBackgroundAppsOnLaunch by remember { mutableStateOf(repository.getKillBackgroundAppsOnLaunch()) }
    var dualScreenSupportEnabled by remember { mutableStateOf(repository.getDualScreenSupportEnabled()) }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            repository.setRootFolderUri(uri.toString())
            rootFolderUri = uri.toString()
            // Unlike the first-run "Choose folder" prompt on the Games tab (which goes through
            // LibraryViewModel.setRootFolder and rescans immediately), changing the folder from
            // here used to just update the stored Uri with no rescan at all — the Games list
            // wouldn't reflect the new folder until the user separately pulled to refresh.
            onRomsFolderChanged()
        }
    }

    val biosKeysFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            repository.setBiosKeysFolderUri(uri.toString())
            biosKeysFolderUri = uri.toString()
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            val romsFolderInteractionSource = rememberFocusInteractionSource()
            val romsFolderGlow = romsFolderInteractionSource.glowShadow()
            val romsFolderColor = romsFolderInteractionSource.glowColor(MaterialTheme.colorScheme.onSurface)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = romsFolderInteractionSource,
                        indication = null
                    ) {
                        folderPicker.launch(rootFolderUri?.let { Uri.parse(it) })
                    }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "ROMs folder",
                    style = LocalTextStyle.current.copy(shadow = romsFolderGlow),
                    color = romsFolderColor,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = rootFolderUri?.let { Uri.parse(it).lastPathSegment ?: it } ?: "Not set",
                    style = LocalTextStyle.current.copy(shadow = romsFolderGlow),
                    color = romsFolderColor,
                    textAlign = TextAlign.End
                )
            }
        }

        item {
            val biosFolderInteractionSource = rememberFocusInteractionSource()
            val biosFolderGlow = biosFolderInteractionSource.glowShadow()
            val biosFolderColor = biosFolderInteractionSource.glowColor(MaterialTheme.colorScheme.onSurface)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = biosFolderInteractionSource,
                        indication = null
                    ) {
                        biosKeysFolderPicker.launch(biosKeysFolderUri?.let { Uri.parse(it) })
                    }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "BIOS/Keys/Firmware folder",
                    style = LocalTextStyle.current.copy(shadow = biosFolderGlow),
                    color = biosFolderColor,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = biosKeysFolderUri?.let { Uri.parse(it).lastPathSegment ?: it } ?: "Not set",
                    style = LocalTextStyle.current.copy(shadow = biosFolderGlow),
                    color = biosFolderColor,
                    textAlign = TextAlign.End
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Close background apps before launching",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                RetroToggle(
                    checked = killBackgroundAppsOnLaunch,
                    onCheckedChange = {
                        killBackgroundAppsOnLaunch = it
                        repository.setKillBackgroundAppsOnLaunch(it)
                    }
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Dual-Screen Support",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                RetroToggle(
                    checked = dualScreenSupportEnabled,
                    onCheckedChange = {
                        dualScreenSupportEnabled = it
                        repository.setDualScreenSupportEnabled(it)
                        onDualScreenSupportChanged()
                    }
                )
            }
        }

        if (visibleRecommendedEmulators.isNotEmpty()) {
            item {
                Text(
                    "Recommended Emulators",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)
                )
            }
        }

        visibleRecommendedEmulators.forEach { emulator ->
            item(key = emulator.packageName) {
                var isInstalled by remember(emulator.packageName, installCheckTick) {
                    mutableStateOf(EmulatorLauncher.isPackageInstalled(context, emulator.packageName))
                }
                Row(
                    // Extra start padding beyond the header's 20.dp — visually nests each row "one
                    // tab over" under the "Recommended Emulators" heading.
                    modifier = Modifier.fillMaxWidth().padding(start = 36.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        emulator.rowLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    val isResolving = resolvingPackage == emulator.packageName
                    Text(
                        text = buildAnnotatedString {
                            append("${emulator.appLabel} — ")
                            when {
                                isInstalled -> withStyle(SpanStyle(color = InstalledGreen)) { append("Installed") }
                                isResolving -> append(resolvingLabel)
                                else -> withStyle(SpanStyle(color = NotInstalledRed)) { append("Not Installed") }
                            }
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.End,
                        // Tapping "Installed" opens the app directly — RomRunner can't configure
                        // another app's own settings (sandboxed private storage, no public API for
                        // it; confirmed against ARMSX2's real source), so this is a one-tap
                        // shortcut into its own setup/settings rather than real automation.
                        // Tapping "Not Installed" looks up the latest stable release, downloads
                        // it, and hands it to the system installer. No action while this row's
                        // download/install is already in flight.
                        modifier = Modifier.clickable(enabled = !isResolving) {
                            if (isInstalled) {
                                context.packageManager.getLaunchIntentForPackage(emulator.packageName)
                                    ?.let { context.startActivity(it) }
                                return@clickable
                            }
                            resolvingPackage = emulator.packageName
                            scope.launch {
                                downloadLatestRelease(
                                    context = context,
                                    emulator = emulator,
                                    onStatusChange = { status -> resolvingLabel = status },
                                    onInstalled = { isInstalled = true }
                                )
                                resolvingPackage = null
                            }
                        }
                    )
                }
            }
        }
    }
}
