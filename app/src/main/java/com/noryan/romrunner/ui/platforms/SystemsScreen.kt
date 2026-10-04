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
import androidx.compose.foundation.layout.Arrangement
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
import com.noryan.romrunner.data.launch.CemuSetup
import com.noryan.romrunner.data.launch.EdenGpuDriver
import com.noryan.romrunner.data.launch.PrimeHackControls
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
 * The Systems tab: everything that's specific to a game system's emulator — a row per system in the
 * library, plus the one-off setup rows for PrimeHack's
 * controller profile and Eden's graphics driver. General app settings live in [PlatformsContent].
 */
@Composable
fun SystemsContent(
    repository: LibraryRepository,
    installState: EmulatorInstallState,
    onSetUpPrimeHack: () -> Unit,
    onSetUpEdenDriver: () -> Unit,
    onSetUpCemu: () -> Unit,
    onSetUpRetroArch: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Safety net alongside downloadLatestRelease's own post-install poll: re-checks every
    // emulator row whenever RomRunner comes back to the foreground (e.g. returning
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

    // Only show an emulator row for a platform (or specific title, for a per-title
    // override) the library actually has a matching game for — e.g. no point recommending Eden if
    // there isn't a single Switch game scanned in yet.
    val games by repository.observeGames().collectAsStateWithLifecycle(initialValue = emptyList())
    val platforms by repository.observePlatforms().collectAsStateWithLifecycle(initialValue = emptyList())
    val visibleRecommendedEmulators = remember(games, platforms) {
        RECOMMENDED_EMULATORS.filter { it.isNeeded(platforms, games) }
    }
    val primeHackInstalled = remember(installCheckTick, installState.refreshTick) {
        EmulatorLauncher.isPackageInstalled(context, PrimeHackControls.PACKAGE)
    }
    val primeHackLinked = remember(installState.refreshTick) { repository.getPrimeHackFolderUri() != null }
    val cemuInstalled = remember(installCheckTick, installState.refreshTick) {
        EmulatorLauncher.isPackageInstalled(context, CemuSetup.PACKAGE)
    }
    val cemuLinked = remember(installState.refreshTick) { repository.getCemuFolderUri() != null }
    val retroArchInstalled = remember(installCheckTick, installState.refreshTick) {
        EmulatorLauncher.isPackageInstalled(context, "com.retroarch")
    }
    val retroArchLinked = remember(installState.refreshTick) { repository.getRetroArchFolderUri() != null }
    val edenInstalled = remember(installCheckTick, installState.refreshTick) {
        EmulatorLauncher.isPackageInstalled(context, EdenGpuDriver.PACKAGE)
    }
    val edenDriverApplied = remember(installState.refreshTick) { repository.isEdenDriverApplied() }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (primeHackInstalled) {
            item {
                val primeHackInteractionSource = rememberFocusInteractionSource()
                val primeHackGlow = primeHackInteractionSource.glowShadow()
                val primeHackColor = primeHackInteractionSource.glowColor(MaterialTheme.colorScheme.onSurface)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = primeHackInteractionSource,
                            indication = null,
                            onClick = onSetUpPrimeHack
                        )
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "PrimeHack setup",
                        style = LocalTextStyle.current.copy(shadow = primeHackGlow),
                        color = primeHackColor,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (primeHackLinked) "Loaded" else "Set up",
                        style = LocalTextStyle.current.copy(shadow = primeHackGlow),
                        color = primeHackColor,
                        textAlign = TextAlign.End
                    )
                }
            }
        }

        if (retroArchInstalled) {
            item {
                val raInteractionSource = rememberFocusInteractionSource()
                val raGlow = raInteractionSource.glowShadow()
                val raColor = raInteractionSource.glowColor(MaterialTheme.colorScheme.onSurface)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = raInteractionSource,
                            indication = null,
                            onClick = onSetUpRetroArch
                        )
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "RetroArch setup",
                        style = LocalTextStyle.current.copy(shadow = raGlow),
                        color = raColor,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (retroArchLinked) "Loaded" else "Set up",
                        style = LocalTextStyle.current.copy(shadow = raGlow),
                        color = raColor,
                        textAlign = TextAlign.End
                    )
                }
            }
        }

        if (cemuInstalled) {
            item {
                val cemuInteractionSource = rememberFocusInteractionSource()
                val cemuGlow = cemuInteractionSource.glowShadow()
                val cemuColor = cemuInteractionSource.glowColor(MaterialTheme.colorScheme.onSurface)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = cemuInteractionSource,
                            indication = null,
                            onClick = onSetUpCemu
                        )
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Cemu setup",
                        style = LocalTextStyle.current.copy(shadow = cemuGlow),
                        color = cemuColor,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (cemuLinked) "Loaded" else "Set up",
                        style = LocalTextStyle.current.copy(shadow = cemuGlow),
                        color = cemuColor,
                        textAlign = TextAlign.End
                    )
                }
            }
        }

        if (edenInstalled) {
            item {
                val edenInteractionSource = rememberFocusInteractionSource()
                val edenGlow = edenInteractionSource.glowShadow()
                val edenColor = edenInteractionSource.glowColor(MaterialTheme.colorScheme.onSurface)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = edenInteractionSource,
                            indication = null,
                            onClick = onSetUpEdenDriver
                        )
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Eden setup",
                        style = LocalTextStyle.current.copy(shadow = edenGlow),
                        color = edenColor,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (edenDriverApplied) "Loaded" else "Set up",
                        style = LocalTextStyle.current.copy(shadow = edenGlow),
                        color = edenColor,
                        textAlign = TextAlign.End
                    )
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
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
