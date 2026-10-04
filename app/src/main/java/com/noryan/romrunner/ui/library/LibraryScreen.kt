package com.noryan.romrunner.ui.library

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.noryan.romrunner.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noryan.romrunner.data.launch.BackgroundAppCleaner
import com.noryan.romrunner.data.launch.EmulatorLauncher
import com.noryan.romrunner.data.launch.EdenGpuDriver
import com.noryan.romrunner.data.launch.EdenSetup
import com.noryan.romrunner.data.launch.InstalledApp
import com.noryan.romrunner.data.launch.Armsx2Setup
import com.noryan.romrunner.data.launch.CemuSetup
import com.noryan.romrunner.data.launch.PrimeHackControls
import com.noryan.romrunner.data.launch.RECOMMENDED_EMULATORS
import com.noryan.romrunner.data.launch.RetroArchLauncher
import com.noryan.romrunner.data.model.Game
import com.noryan.romrunner.data.model.Platform
import com.noryan.romrunner.data.repository.LibraryRepository
import com.noryan.romrunner.ui.components.AppPickerDialog
import com.noryan.romrunner.ui.components.GameActionSheet
import com.noryan.romrunner.ui.components.GameRow
import com.noryan.romrunner.ui.components.EmulatorSetupDialog
import com.noryan.romrunner.ui.components.ForceStopDialog
import com.noryan.romrunner.ui.components.MissingAppDialog
import com.noryan.romrunner.ui.platforms.EmulatorInstallState
import com.noryan.romrunner.ui.components.RenameDialog
import com.noryan.romrunner.ui.components.glowColor
import com.noryan.romrunner.ui.components.glowShadow
import com.noryan.romrunner.ui.components.HomeStatusInfo
import com.noryan.romrunner.ui.components.rememberFocusInteractionSource
import com.noryan.romrunner.ui.apps.AppsContent
import com.noryan.romrunner.ui.platforms.PlatformsContent
import com.noryan.romrunner.ui.platforms.SystemsContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class MissingAppRequest(val platform: Platform, val game: Game, val target: EmulatorLauncher.Target)

private enum class HomeTab { GAMES, SYSTEMS, SETTINGS, APPS }

private fun cycleTab(current: HomeTab, delta: Int): HomeTab {
    val tabs = HomeTab.entries
    val nextIndex = (tabs.indexOf(current) + delta + tabs.size) % tabs.size
    return tabs[nextIndex]
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    repository: LibraryRepository,
    onDualScreenSupportChanged: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(HomeTab.GAMES) }
    // Focus starts on the GAMES heading itself, not the screen-spanning Scaffold — a focus rect
    // as big as the whole screen has no sensible "next focusable node below it" for D-pad/joystick
    // spatial search to find, which silently breaks all downward navigation into the list.
    val gamesTabFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { gamesTabFocusRequester.requestFocus() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory(repository))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var actionGame by remember { mutableStateOf<Game?>(null) }
    var renameGame by remember { mutableStateOf<Game?>(null) }
    var installPrompt by remember { mutableStateOf<MissingAppRequest?>(null) }
    var appPickerFor by remember { mutableStateOf<MissingAppRequest?>(null) }

    // Shared with the Settings tab so its rows show live progress for installs started from here.
    val emulatorInstallState = remember { EmulatorInstallState() }
    var showEmulatorSetupPrompt by remember { mutableStateOf(false) }
    // Set while the user is in Android's "all files access" settings, so the install-all they asked for
    // continues once they return to RomRunner.
    var installAllAfterStorageAccess by remember { mutableStateOf(false) }
    // Same idea for the "install unknown apps" screen that follows it.
    var waitingForInstallPermission by remember { mutableStateOf(false) }
    val neededEmulators = remember(state.games, state.platforms) {
        RECOMMENDED_EMULATORS.filter { it.isNeeded(state.platforms, state.games) }
    }
    fun startInstallAll(askForInstallPermission: Boolean = true) {
        if (askForInstallPermission && !context.packageManager.canRequestPackageInstalls()) {
            // "Install unknown apps" has to be on for RomRunner, or each install first shows a "you're not allowed" box
            // that sends the user to Settings. Ask for it up front instead: RomRunner returns by itself once it's on.
            waitingForInstallPermission = true
            context.startActivity(
                Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            )
            return
        }
        // Jump to Systems so the per-emulator progress is visible while it works.
        selectedTab = HomeTab.SYSTEMS
        scope.launch { emulatorInstallState.installAll(context, neededEmulators) }
    }
    // First-run offer: right after the first ROMs folder is chosen and scanned, ask once whether to
    // download the emulators those games need. Waits for the scan to finish and for at least one
    // game to turn up; if everything needed is already installed there's nothing to ask about.
    LaunchedEffect(state.games, state.platforms, state.isScanning) {
        if (!repository.isEmulatorSetupPending() || state.isScanning || state.games.isEmpty()) return@LaunchedEffect
        val missing = neededEmulators.filter { !EmulatorLauncher.isPackageInstalled(context, it.packageName) }
        if (missing.isEmpty()) {
            repository.clearEmulatorSetupPending()
        } else {
            repository.markEmulatorSetupPromptShown()
            showEmulatorSetupPrompt = true
        }
    }

    // True while Eden's folder picker is up, so no other setup prompt opens over it.
    var edenPickerOpen by remember { mutableStateOf(false) }
    var showPrimeHackPrompt by remember { mutableStateOf(false) }
    // The app (label, package) the user must Force stop once so it re-reads the settings just written.
    var forceStopApp by remember { mutableStateOf<Pair<String, String>?>(null) }
    // Each setup opens its folder picker by itself at most once per app run, and stops for good once
    // the folder is granted or the user backs out of the picker (the Systems tab can still run it).
    var primeHackOfferedThisRun by remember { mutableStateOf(false) }
    var edenOfferedThisRun by remember { mutableStateOf(false) }

    // PrimeHack setup (controller profiles + graphics defaults): the folder grant comes from the
    // system picker, opened right on PrimeHack's own folder (see PrimeHackControls).
    fun applyPrimeHackProfile(treeUri: Uri) {
        scope.launch {
            val message = when (val result = PrimeHackControls.apply(context, treeUri)) {
                is PrimeHackControls.Result.Applied -> result.profile
                    ?.let { "Set up PrimeHack: loaded the ${it.label} controller profile and graphics settings." }
                    ?: "Set up PrimeHack's graphics settings and added the controller profiles."
                is PrimeHackControls.Result.Failed -> result.message
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            emulatorInstallState.bumpRefresh()
        }
    }
    lateinit var launchPrimeHackPicker: () -> Unit
    val primeHackPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        showPrimeHackPrompt = false
        if (uri == null) {
            // Backed out: take that as "no", and don't offer it again by itself (the Systems tab still can).
            repository.markPrimeHackSetupPrompted()
            return@rememberLauncherForActivityResult
        }
        if (!PrimeHackControls.isPrimeHackTree(uri)) {
            Toast.makeText(context, "That wasn't PrimeHack's folder — choose \"Prime Hack\" in the menu (top left).", Toast.LENGTH_LONG).show()
            launchPrimeHackPicker()
            return@rememberLauncherForActivityResult
        }
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        repository.setPrimeHackFolderUri(uri.toString())
        applyPrimeHackProfile(uri)
    }
    // Opens straight onto PrimeHack's own folder, so the user only taps "Use this folder" and "Allow".
    launchPrimeHackPicker = {
        showPrimeHackPrompt = true
        primeHackPicker.launch(PrimeHackControls.pickerInitialUri())
    }
    fun setUpPrimeHack() {
        val granted = repository.getPrimeHackFolderUri()?.let { Uri.parse(it) }
        if (granted != null) applyPrimeHackProfile(granted) else launchPrimeHackPicker()
    }
    LaunchedEffect(emulatorInstallState.refreshTick, emulatorInstallState.isInstallingAll, state.games, edenPickerOpen) {
        if (emulatorInstallState.isInstallingAll || edenPickerOpen) return@LaunchedEffect
        if (primeHackOfferedThisRun || repository.isPrimeHackSetupPrompted() || repository.getPrimeHackFolderUri() != null) return@LaunchedEffect
        if (neededEmulators.none { it.packageName == PrimeHackControls.PACKAGE }) return@LaunchedEffect
        if (!EmulatorLauncher.isPackageInstalled(context, PrimeHackControls.PACKAGE)) return@LaunchedEffect
        primeHackOfferedThisRun = true
        launchPrimeHackPicker()
    }

    // Cemu controller profile and Wii U key files (see CemuSetup): same one-time folder grant.
    var showCemuPrompt by remember { mutableStateOf(false) }
    var cemuOfferedThisRun by remember { mutableStateOf(false) }
    fun applyCemuSetup(treeUri: Uri) {
        scope.launch {
            val message = when (val result = CemuSetup.apply(context, treeUri, repository.getRootFolderUri()?.let { Uri.parse(it) })) {
                is CemuSetup.Result.Applied -> {
                    forceStopApp = "Cemu" to CemuSetup.PACKAGE
                    buildString {
                        append(if (result.controller != null) "Set up Cemu's controls for the ${result.controller}." else "No controller was connected, so Cemu's controls weren't set up.")
                        if (result.copiedFiles.isNotEmpty()) append(" Copied ${result.copiedFiles.joinToString(", ")}.")
                    }
                }
                is CemuSetup.Result.Failed -> result.message
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            emulatorInstallState.bumpRefresh()
        }
    }
    lateinit var launchCemuPicker: () -> Unit
    val cemuPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        showCemuPrompt = false
        if (uri == null) {
            repository.markCemuSetupPrompted()
            return@rememberLauncherForActivityResult
        }
        if (!CemuSetup.isCemuTree(uri)) {
            Toast.makeText(context, "That wasn't Cemu's folder — choose \"Cemu\" in the menu (top left).", Toast.LENGTH_LONG).show()
            launchCemuPicker()
            return@rememberLauncherForActivityResult
        }
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        repository.setCemuFolderUri(uri.toString())
        applyCemuSetup(uri)
    }
    launchCemuPicker = {
        showCemuPrompt = true
        cemuPicker.launch(CemuSetup.pickerInitialUri())
    }
    fun setUpCemu() {
        val granted = repository.getCemuFolderUri()?.let { Uri.parse(it) }
        if (granted != null) applyCemuSetup(granted) else launchCemuPicker()
    }
    LaunchedEffect(emulatorInstallState.refreshTick, emulatorInstallState.isInstallingAll, state.games, showPrimeHackPrompt, edenPickerOpen) {
        if (emulatorInstallState.isInstallingAll || showPrimeHackPrompt || edenPickerOpen) return@LaunchedEffect
        if (cemuOfferedThisRun || repository.isCemuSetupPrompted() || repository.getCemuFolderUri() != null) return@LaunchedEffect
        if (neededEmulators.none { it.packageName == CemuSetup.PACKAGE }) return@LaunchedEffect
        if (!EmulatorLauncher.isPackageInstalled(context, CemuSetup.PACKAGE)) return@LaunchedEffect
        cemuOfferedThisRun = true
        launchCemuPicker()
    }

    // RetroArch (see RetroArchLauncher): one folder grant on its data folder lets RomRunner install the cores
    // and its own config (RGUI menu, hotkeys, quit on close). Same hands-off picker flow as the others.
    var retroArchPickerOpen by remember { mutableStateOf(false) }
    var retroArchOfferedThisRun by remember { mutableStateOf(false) }
    fun applyRetroArchSetup(treeUri: Uri) {
        scope.launch {
            val systems = state.games.mapNotNull { g -> state.platforms.find { it.id == g.platformId }?.name }.distinct()
            val cores = RetroArchLauncher.retroArchPlatforms(systems)
            Toast.makeText(context, "Setting up RetroArch…", Toast.LENGTH_SHORT).show()
            val message = when (val result = RetroArchLauncher.setUp(context, treeUri, cores)) {
                is RetroArchLauncher.SetupResult.Done -> "RetroArch is set up: RGUI menu, hotkeys, quit on close" +
                    if (result.coresInstalled.isNotEmpty()) ", and cores installed (${result.coresInstalled.joinToString(", ")})." else "."
                is RetroArchLauncher.SetupResult.Failed -> result.message
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            emulatorInstallState.bumpRefresh()
        }
    }
    lateinit var launchRetroArchPicker: () -> Unit
    val retroArchPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        retroArchPickerOpen = false
        if (uri == null) {
            repository.markRetroArchSetupPrompted()
            return@rememberLauncherForActivityResult
        }
        if (!RetroArchLauncher.isRetroArchTree(context, uri)) {
            Toast.makeText(context, "That wasn't RetroArch's folder — choose \"RetroArch\" in the menu (top left).", Toast.LENGTH_LONG).show()
            launchRetroArchPicker()
            return@rememberLauncherForActivityResult
        }
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        repository.setRetroArchFolderUri(uri.toString())
        applyRetroArchSetup(uri)
    }
    launchRetroArchPicker = {
        val start = RetroArchLauncher.pickerInitialUri(context)
        if (start != null) {
            retroArchPickerOpen = true
            retroArchPicker.launch(start)
        }
    }
    fun setUpRetroArch() {
        val granted = repository.getRetroArchFolderUri()?.let { Uri.parse(it) }
        if (granted != null) applyRetroArchSetup(granted) else launchRetroArchPicker()
    }
    LaunchedEffect(emulatorInstallState.refreshTick, emulatorInstallState.isInstallingAll, state.games, showPrimeHackPrompt, showCemuPrompt, edenPickerOpen) {
        if (emulatorInstallState.isInstallingAll || showPrimeHackPrompt || showCemuPrompt || edenPickerOpen) return@LaunchedEffect
        if (retroArchOfferedThisRun || repository.isRetroArchSetupPrompted() || repository.getRetroArchFolderUri() != null) return@LaunchedEffect
        if (neededEmulators.none { it.packageName == "com.retroarch" }) return@LaunchedEffect
        if (!EmulatorLauncher.isPackageInstalled(context, "com.retroarch")) return@LaunchedEffect
        retroArchOfferedThisRun = true
        launchRetroArchPicker()
    }

    // ARMSX2 can't be set up from outside (see Armsx2Setup), so once it's installed just open it on its
    // first-run wizard with a hint of what to pick. Offered once, after the other setups are out of the way.
    var armsx2OfferedThisRun by remember { mutableStateOf(false) }
    LaunchedEffect(emulatorInstallState.refreshTick, emulatorInstallState.isInstallingAll, state.games, showPrimeHackPrompt, showCemuPrompt, edenPickerOpen, retroArchPickerOpen) {
        if (emulatorInstallState.isInstallingAll || showPrimeHackPrompt || showCemuPrompt || edenPickerOpen || retroArchPickerOpen) return@LaunchedEffect
        if (armsx2OfferedThisRun || repository.isArmsx2WizardShown()) return@LaunchedEffect
        if (neededEmulators.none { it.packageName == Armsx2Setup.PACKAGE }) return@LaunchedEffect
        if (!EmulatorLauncher.isPackageInstalled(context, Armsx2Setup.PACKAGE)) return@LaunchedEffect
        val launch = context.packageManager.getLaunchIntentForPackage(Armsx2Setup.PACKAGE) ?: return@LaunchedEffect
        armsx2OfferedThisRun = true
        repository.markArmsx2WizardShown()
        val hint = Armsx2Setup.wizardHint(context, repository.getRootFolderUri()?.let { Uri.parse(it) })
        Toast.makeText(context, hint, Toast.LENGTH_LONG).show()
        context.startActivity(launch)
    }

    // Eden setup (keys, firmware, on-screen controls, graphics driver; see EdenSetup). Runs by itself with
    // no confirmation dialogs: the only thing Android makes the user do is the one-time folder pick.
    var edenSetupRunning by remember { mutableStateOf(false) }
    fun openAppInfo(packageName: String) {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
    fun applyEdenSetup(treeUri: Uri, quiet: Boolean) {
        // The picker result and the "back in the foreground" retry below can fire together, and two
        // runs would both try to unpack the firmware.
        if (edenSetupRunning) return
        edenSetupRunning = true
        scope.launch {
            try {
                val romsFolder = repository.getRootFolderUri()?.let { Uri.parse(it) }
                if (!quiet && romsFolder != null) {
                    Toast.makeText(context, "Setting up Eden — installing the firmware can take a minute.", Toast.LENGTH_LONG).show()
                }
                when (val result = EdenSetup.apply(context, treeUri, romsFolder)) {
                    is EdenSetup.Result.Applied -> {
                        repository.setEdenDriverApplied(true)
                        Toast.makeText(
                            context,
                            result.summary.joinToString(" ") + " Last step: tap Force stop, then OK, so Eden starts using it.",
                            Toast.LENGTH_LONG
                        ).show()
                        // Eden only reads some of this when its process starts, and Android won't let
                        // RomRunner stop it, so take the user straight to the button that does.
                        openAppInfo(EdenGpuDriver.PACKAGE)
                    }
                    is EdenSetup.Result.NotOpenedYet -> if (!quiet) {
                        // Eden writes its settings file the first time it runs; open it once. The setup
                        // finishes by itself when the user comes back to RomRunner.
                        Toast.makeText(
                            context,
                            "Opening Eden once so it can create its settings — come back to RomRunner afterwards.",
                            Toast.LENGTH_LONG
                        ).show()
                        context.packageManager.getLaunchIntentForPackage(EdenGpuDriver.PACKAGE)?.let { context.startActivity(it) }
                    }
                    is EdenSetup.Result.Failed ->
                        if (!quiet) Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                }
            } finally {
                edenSetupRunning = false
            }
            emulatorInstallState.bumpRefresh()
        }
    }
    lateinit var launchEdenPicker: () -> Unit
    val edenPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        edenPickerOpen = false
        if (uri == null) {
            repository.markEdenSetupPrompted()
            return@rememberLauncherForActivityResult
        }
        if (!EdenGpuDriver.isEdenTree(uri)) {
            Toast.makeText(context, "That wasn't Eden's folder — choose \"Eden\" in the menu (top left).", Toast.LENGTH_LONG).show()
            launchEdenPicker()
            return@rememberLauncherForActivityResult
        }
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        repository.setEdenFolderUri(uri.toString())
        applyEdenSetup(uri, quiet = false)
    }
    launchEdenPicker = {
        edenPickerOpen = true
        edenPicker.launch(EdenGpuDriver.pickerInitialUri())
    }
    fun setUpEdenDriver() {
        val granted = repository.getEdenFolderUri()?.let { Uri.parse(it) }
        if (granted != null) applyEdenSetup(granted, quiet = false) else launchEdenPicker()
    }
    LaunchedEffect(emulatorInstallState.refreshTick, emulatorInstallState.isInstallingAll, state.games, showPrimeHackPrompt) {
        if (emulatorInstallState.isInstallingAll || showPrimeHackPrompt) return@LaunchedEffect
        if (edenOfferedThisRun || repository.isEdenSetupPrompted() || repository.getEdenFolderUri() != null) return@LaunchedEffect
        if (neededEmulators.none { it.packageName == EdenGpuDriver.PACKAGE }) return@LaunchedEffect
        if (!EmulatorLauncher.isPackageInstalled(context, EdenGpuDriver.PACKAGE)) return@LaunchedEffect
        edenOfferedThisRun = true
        launchEdenPicker()
    }
    // If the grant was given before Eden had ever been opened (so it had no settings file yet), finish
    // the job quietly the next time RomRunner comes back to the foreground.
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeTick by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumeTick++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resumeTick) {
        val granted = repository.getEdenFolderUri()?.let { Uri.parse(it) } ?: return@LaunchedEffect
        if (repository.isEdenDriverApplied()) return@LaunchedEffect
        if (!EmulatorLauncher.isPackageInstalled(context, EdenGpuDriver.PACKAGE)) return@LaunchedEffect
        applyEdenSetup(granted, quiet = true)
    }
    // While the user is on Android's "all files access" screen, watch for the switch going on and bring RomRunner
    // back to the front by itself, so the install prompts that follow open over RomRunner and not over Settings.
    // Settings is started inside RomRunner's own task (no NEW_TASK, see the launch below), which is what lets an
    // app that is in the background pull itself forward; CLEAR_TOP also closes the Settings screen on the way.
    LaunchedEffect(installAllAfterStorageAccess) {
        if (!installAllAfterStorageAccess) return@LaunchedEffect
        while (!android.os.Environment.isExternalStorageManager()) delay(300)
        context.startActivity(
            Intent(context, com.noryan.romrunner.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }
    LaunchedEffect(waitingForInstallPermission) {
        if (!waitingForInstallPermission) return@LaunchedEffect
        while (!context.packageManager.canRequestPackageInstalls()) delay(300)
        context.startActivity(
            Intent(context, com.noryan.romrunner.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }
    // Back from "install unknown apps" (switched on or not): go ahead and install. If the user backed out
    // without allowing it, the system's own prompt still handles it, so don't ask a second time.
    LaunchedEffect(resumeTick) {
        if (!waitingForInstallPermission) return@LaunchedEffect
        waitingForInstallPermission = false
        startInstallAll(askForInstallPermission = false)
    }
    // Back from Android's "all files access" settings (granted or not): carry on with the install-all.
    LaunchedEffect(resumeTick) {
        if (!installAllAfterStorageAccess) return@LaunchedEffect
        installAllAfterStorageAccess = false
        startInstallAll()
    }

    val snackbarHostState = remember { SnackbarHostState() }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) viewModel.setRootFolder(context, uri)
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val platformsById = remember(state.platforms) { state.platforms.associateBy { it.id } }

    fun attemptLaunch(platform: Platform, game: Game) {
        // Best-effort memory reclaim before any launch path below — see BackgroundAppCleaner's
        // own doc comment for exactly what this can and can't do.
        if (repository.getKillBackgroundAppsOnLaunch()) {
            BackgroundAppCleaner.killNonEssentialApps(context)
        }
        val target = EmulatorLauncher.resolveTarget(platform, game)
        if (target != null && !EmulatorLauncher.isPackageInstalled(context, target.packageName)) {
            installPrompt = MissingAppRequest(platform, game, target)
            return
        }
        if (RetroArchLauncher.handles(platform)) {
            scope.launch {
                when (val prepared = RetroArchLauncher.prepare(context, platform, game, repository.getRetroArchFolderUri()?.let { Uri.parse(it) })) {
                    is RetroArchLauncher.Prepared.Ready -> {
                        context.startActivity(prepared.intent)
                        viewModel.markPlayed(game)
                    }
                    is RetroArchLauncher.Prepared.NeedsFirstRun -> {
                        Toast.makeText(
                            context,
                            "RetroArch needs one-time setup: allow storage access in it, then launch ${game.title} again.",
                            Toast.LENGTH_LONG
                        ).show()
                        prepared.intent?.let { context.startActivity(it) }
                    }
                    is RetroArchLauncher.Prepared.Failed ->
                        Toast.makeText(context, prepared.message, Toast.LENGTH_LONG).show()
                }
            }
            return
        }
        val intent = EmulatorLauncher.buildIntent(context, platform, game)
        try {
            context.startActivity(intent)
            viewModel.markPlayed(game)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                context,
                "No app could open this file. Check the emulator for ${platform.name} on the Systems tab.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun launchWithChosenApp(platform: Platform, game: Game, packageName: String) {
        val intent = EmulatorLauncher.buildIntentForPackage(context, platform, game, packageName)
        try {
            context.startActivity(intent)
            viewModel.markPlayed(game)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Couldn't open ${game.title} with that app.", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        modifier = Modifier
            .onKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                // With several tabs (was two, one fixed jump per shoulder button), L1/R1 cycle
                // through them in order instead of each jumping to one fixed tab.
                when (keyEvent.nativeKeyEvent.keyCode) {
                    android.view.KeyEvent.KEYCODE_BUTTON_L1 -> {
                        selectedTab = cycleTab(selectedTab, -1)
                        true
                    }
                    android.view.KeyEvent.KEYCODE_BUTTON_R1 -> {
                        selectedTab = cycleTab(selectedTab, 1)
                        true
                    }
                    else -> false
                }
            },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    HomeTabHeading(
                        text = "GAMES",
                        selected = selectedTab == HomeTab.GAMES,
                        onClick = { selectedTab = HomeTab.GAMES },
                        modifier = Modifier.focusRequester(gamesTabFocusRequester)
                    )
                    HomeTabHeading(
                        text = "SYSTEMS",
                        selected = selectedTab == HomeTab.SYSTEMS,
                        onClick = { selectedTab = HomeTab.SYSTEMS }
                    )
                    HomeTabHeading(
                        text = "SETTINGS",
                        selected = selectedTab == HomeTab.SETTINGS,
                        onClick = { selectedTab = HomeTab.SETTINGS }
                    )
                    HomeTabHeading(
                        text = "APPS",
                        selected = selectedTab == HomeTab.APPS,
                        onClick = { selectedTab = HomeTab.APPS }
                    )
                }
                HomeStatusInfo()
            }
            HorizontalDivider()

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (selectedTab) {
                    HomeTab.GAMES -> PullToRefreshBox(
                        isRefreshing = state.isScanning,
                        onRefresh = { viewModel.rescanAll(context) },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        when {
                            state.romsRootUri == null -> ChooseFolderPrompt(onChooseFolder = { folderPicker.launch(null) })
                            state.games.isEmpty() -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("No games found yet. Pull down to refresh, or check Settings for supported file types.")
                                }
                            }
                            else -> {
                                // GameRow.onClick/onLongClick are hoisted as stable (Game) -> Unit
                                // lambdas, reused across every row, so Compose's skip-recomposition
                                // check for a row isn't defeated by a fresh closure every time this
                                // LazyColumn recomposes for an unrelated reason.
                                val onGameClick = remember(platformsById) {
                                    { game: Game ->
                                        val platform = platformsById[game.platformId]
                                        if (platform != null) attemptLaunch(platform, game)
                                    }
                                }
                                val onGameLongClick = remember { { game: Game -> actionGame = game } }
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(state.games, key = { it.id }) { game ->
                                        val platform = platformsById[game.platformId]
                                        GameRow(
                                            game = game,
                                            platformName = displayPlatformName(platform, game),
                                            onClick = onGameClick,
                                            onLongClick = onGameLongClick
                                        )
                                    }
                                }
                            }
                        }
                    }
                    HomeTab.SYSTEMS -> SystemsContent(
                        repository = repository,
                        installState = emulatorInstallState,
                        onSetUpPrimeHack = { setUpPrimeHack() },
                        onSetUpEdenDriver = { setUpEdenDriver() },
                        onSetUpCemu = { setUpCemu() },
                        onSetUpRetroArch = { setUpRetroArch() }
                    )
                    HomeTab.SETTINGS -> PlatformsContent(
                        repository = repository,
                        onDualScreenSupportChanged = onDualScreenSupportChanged,
                        onRomsFolderChanged = { viewModel.rescanAll(context) }
                    )
                    HomeTab.APPS -> AppsContent()
                }
            }
        }
    }

    actionGame?.let { game ->
        val platform = platformsById[game.platformId]
        GameActionSheet(
            game = game,
            onDismiss = { actionGame = null },
            onPlay = {
                actionGame = null
                platform?.let { attemptLaunch(it, game) }
            },
            onToggleFavorite = { viewModel.toggleFavorite(game); actionGame = null },
            onRename = { renameGame = game; actionGame = null },
            onRemove = { viewModel.removeGame(game); actionGame = null }
        )
    }

    renameGame?.let { game ->
        RenameDialog(
            initial = game.title,
            onDismiss = { renameGame = null },
            onConfirm = { newTitle ->
                viewModel.renameGame(game, newTitle)
                renameGame = null
            }
        )
    }

    forceStopApp?.let { (label, packageName) ->
        ForceStopDialog(
            appName = label,
            onOpenAppInfo = {
                forceStopApp = null
                openAppInfo(packageName)
            },
            onDone = { forceStopApp = null }
        )
    }

    if (showEmulatorSetupPrompt) {
        EmulatorSetupDialog(
            emulatorLabels = neededEmulators
                .filter { !EmulatorLauncher.isPackageInstalled(context, it.packageName) }
                .map { it.appLabel },
            onYes = {
                showEmulatorSetupPrompt = false
                if (android.os.Environment.isExternalStorageManager() || repository.isStorageAccessAsked()) {
                    startInstallAll()
                } else {
                    // Straight to Android's "all files access" screen (lets RomRunner reuse emulator APKs
                    // already in Downloads); the install-all carries on when the user comes back.
                    repository.markStorageAccessAsked()
                    installAllAfterStorageAccess = true
                    context.startActivity(
                        Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
                    )
                }
            },
            onNotNow = { showEmulatorSetupPrompt = false }
        )
    }

    installPrompt?.let { req ->
        MissingAppDialog(
            appLabel = req.target.label,
            gameTitle = req.game.title,
            onDismiss = { installPrompt = null },
            onInstall = {
                installPrompt = null
                if (req.target.packageName in EmulatorLauncher.KNOWN_PLAY_STORE_PACKAGES) {
                    openPlayStore(context, req.target.packageName)
                } else {
                    openWebSearch(context, "${req.target.label} android emulator")
                }
            },
            onNotNow = {
                installPrompt = null
                appPickerFor = req
            }
        )
    }

    appPickerFor?.let { req ->
        AppPickerDialog(
            title = "Which app should play ${req.platform.name} games?",
            onDismiss = { appPickerFor = null },
            onPick = { app: InstalledApp ->
                appPickerFor = null
                scope.launch { repository.savePlatform(req.platform.copy(launchPackage = app.packageName)) }
                launchWithChosenApp(req.platform, req.game, app.packageName)
            }
        )
    }

}

/**
 * Shortened labels for the library list. "GameCube / Wii" is one platform (shared extensions,
 * one emulator default) but shown split by the game's actual file extension, since ciso/rvz are
 * unambiguous per-file even though the platform itself covers both systems.
 */
private fun displayPlatformName(platform: Platform?, game: Game): String {
    if (platform == null) return "Unknown"
    if (platform.name == "GameCube / Wii") {
        return when (game.fileName.substringAfterLast('.', "").lowercase()) {
            "ciso" -> "GameCube"
            "rvz", "wad" -> "Wii"
            else -> platform.name
        }
    }
    return when (platform.name) {
        "PlayStation 2" -> "PS2"
        "PlayStation" -> "PS1"
        "Nintendo 3DS" -> "3DS"
        "Nintendo Switch" -> "Switch"
        "Nintendo DS" -> "DS"
        "GameBoy (Color + Advance)" -> "GameBoy"
        else -> platform.name
    }
}

private fun openPlayStore(context: Context, packageName: String) {
    val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
    try {
        context.startActivity(marketIntent)
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
    }
}

private fun openWebSearch(context: Context, query: String) {
    val url = "https://www.google.com/search?q=" + Uri.encode(query)
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No browser available to search for it.", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun HomeTabHeading(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val baseColor = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
    val interactionSource = rememberFocusInteractionSource()
    val color = interactionSource.glowColor(baseColor)
    // width(IntrinsicSize.Min) makes this Column size itself to the Text's natural width instead
    // of the Row's whole remaining width — without it, the underline Box's fillMaxWidth() below
    // bubbles up and makes BOTH tab headings fight over the entire row. focusRequester (passed in
    // via [modifier], only on the GAMES heading) must stay outside/before width+clickable in the
    // chain — it has to wrap the actual focus target, not the other way around.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(IntrinsicSize.Min)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        Text(
            text = text,
            // headlineSmall rather than titleMedium (what the list rows below use) — one notch
            // larger to read as the section heading it is, without jumping all the way to
            // titleLarge, which is reserved for the (now-removed) app-title-sized text.
            style = MaterialTheme.typography.headlineSmall.copy(shadow = interactionSource.glowShadow()),
            color = color
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) color else Color.Transparent)
        )
    }
}

@Composable
private fun ChooseFolderPrompt(onChooseFolder: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // The RomRunner mark itself (see branding/mark.svg) — tint = Unspecified keeps its own
        // brand orange rather than being recolored to the app's own amber/indigo UI palette.
        Icon(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Color.Unspecified
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Point RomRunner at your Roms/BIOS folder", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Choose the one folder that holds BOTH your ROMs and your BIOS, keys and firmware files. " +
                "RomRunner searches it and every subfolder: it lists the games it recognizes, and sets up " +
                "the emulators with the BIOS, keys and firmware it finds.",
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onChooseFolder) { Text("Choose folder") }
    }
}
