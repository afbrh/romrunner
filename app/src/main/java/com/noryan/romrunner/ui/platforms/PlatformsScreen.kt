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
import com.noryan.romrunner.data.launch.RECOMMENDED_EMULATORS
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

/**
 * The Settings tab's content on RomRunner's home screen (see LibraryScreen). Deliberately has no
 * Scaffold/TopAppBar of its own — it's embedded directly under the GAMES/SETTINGS tab heading row
 * rather than being a separate navigation destination.
 */
@Composable
fun PlatformsContent(
    repository: LibraryRepository,
    installState: EmulatorInstallState,
    onDualScreenSupportChanged: () -> Unit,
    onRomsFolderChanged: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
    val anyMissing = remember(visibleRecommendedEmulators, installCheckTick, installState.refreshTick) {
        visibleRecommendedEmulators.any { !EmulatorLauncher.isPackageInstalled(context, it.packageName) }
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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Recommended Emulators",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (installState.isInstallingAll) {
                        Text("Installing…", style = MaterialTheme.typography.bodyLarge)
                    } else if (anyMissing) {
                        val installAllInteractionSource = rememberFocusInteractionSource()
                        val installAllGlow = installAllInteractionSource.glowShadow()
                        Text(
                            "Install All",
                            style = MaterialTheme.typography.bodyLarge.copy(shadow = installAllGlow),
                            color = installAllInteractionSource.glowColor(MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.clickable(
                                interactionSource = installAllInteractionSource,
                                indication = null
                            ) {
                                scope.launch { installState.installAll(context, visibleRecommendedEmulators) }
                            }
                        )
                    }
                }
            }
        }

        visibleRecommendedEmulators.forEach { emulator ->
            item(key = emulator.packageName) {
                val isInstalled = remember(emulator.packageName, installCheckTick, installState.refreshTick) {
                    EmulatorLauncher.isPackageInstalled(context, emulator.packageName)
                }
                val status = installState.statuses[emulator.packageName]
                Row(
                    // Extra start padding beyond the header's 20.dp — visually nests each row "one
                    // tab over" under the "Recommended Emulators" heading.
                    modifier = Modifier.fillMaxWidth().padding(start = 36.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        emulator.rowLabel(platforms, games),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    val isResolving = status != null
                    Text(
                        text = buildAnnotatedString {
                            append("${emulator.appLabel} — ")
                            when {
                                isInstalled -> withStyle(SpanStyle(color = InstalledGreen)) { append("Installed") }
                                isResolving -> append(status.orEmpty())
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
                            scope.launch { installState.installOne(context, emulator) }
                        }
                    )
                }
            }
        }
    }
}
