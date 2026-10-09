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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import com.noryan.romrunner.data.model.CustomTab
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
import com.noryan.romrunner.data.launch.GameFocus
import com.noryan.romrunner.data.launch.EdenGpuDriver
import com.noryan.romrunner.data.launch.EdenSetup
import com.noryan.romrunner.data.launch.EmulatorFolders
import com.noryan.romrunner.data.launch.InstalledApp
import com.noryan.romrunner.data.launch.Armsx2Setup
import com.noryan.romrunner.data.launch.AzaharSetup
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
import com.noryan.romrunner.ui.components.RemoveMenuRow
import com.noryan.romrunner.ui.components.ConfirmRemoveDialog
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
import com.noryan.romrunner.ui.secondscreen.SecondScreenState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class MissingAppRequest(val platform: Platform, val game: Game, val target: EmulatorLauncher.Target)

private enum class HomeTab { GAMES, SETTINGS, APPS }

/** The shortest sort of list name that has to fit for a new list to be allowed at all. */
private const val MinMenuName = "MENU"

/** The next tab in the bar (L1/R1) out of [keys] (Games and Apps unless removed, then the user's own menus in order). Settings (and Systems inside it) is the icon, not a tab. */
private fun cycleTab(currentKey: String, keys: List<String>, delta: Int): String {
    if (keys.isEmpty()) return currentKey
    val nextIndex = (keys.indexOf(currentKey).coerceAtLeast(0) + delta + keys.size) % keys.size
    return keys[nextIndex]
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    repository: LibraryRepository,
    onDualScreenSupportChanged: () -> Unit
) {
    // The selected tab: a HomeTab's name, or "custom:<id>" for one of the user's own menus (the "+" in the tab bar).
    // Games and Apps are the two menus every install starts with; either can be removed with the "-" at the bottom of its list
    // (and brought back from Settings), and so can the user's own. With none left, the screen shows Settings.
    var hiddenDefaults by remember { mutableStateOf(repository.getHiddenDefaultTabs()) }
    var customTabs by remember { mutableStateOf(repository.getCustomTabs()) }
    var selectedKey by remember {
        mutableStateOf(
            listOf(HomeTab.GAMES.name, HomeTab.APPS.name).firstOrNull { it !in hiddenDefaults }
                ?: customTabs.firstOrNull()?.let { "custom:${it.id}" } ?: HomeTab.SETTINGS.name
        )
    }
    // Which built-in menu is being asked about removing ("GAMES" or "APPS"), if any.
    var removingDefault by remember { mutableStateOf<String?>(null) }
    val visibleTabKeys = listOf(HomeTab.GAMES.name, HomeTab.APPS.name).filter { it !in hiddenDefaults } + customTabs.map { "custom:${it.id}" }
    // Null = no editor open; otherwise the menu being edited, or a blank one when "+" was just pressed.
    var tabEditor by remember { mutableStateOf<CustomTab?>(null) }
    var creatingTab by remember { mutableStateOf(false) }
    // The tab bar never scrolls, so what fits in it limits how many menus there can be and how long their names are:
    // [tabBarFits] measures the headings (same style and spacing as drawn) against the bar's measured width.
    val textMeasurer = rememberTextMeasurer()
    val tabDensity = LocalDensity.current
    val tabHeadingStyle = MaterialTheme.typography.headlineSmall
    var tabBarWidthPx by remember { mutableStateOf(0) }
    fun tabBarFits(customNames: List<String>): Boolean {
        if (tabBarWidthPx == 0) return true // not measured yet
        val labels = listOf(HomeTab.GAMES.name, HomeTab.APPS.name).filter { it !in hiddenDefaults } + customNames.map { it.uppercase() } + "+"
        val spacing = with(tabDensity) { 24.dp.toPx() }
        val total = labels.sumOf { textMeasurer.measure(it, tabHeadingStyle).size.width } + spacing * (labels.size - 1)
        return total <= tabBarWidthPx
    }
    val selectedTab = HomeTab.entries.firstOrNull { it.name == selectedKey } ?: HomeTab.GAMES
    // Whether Settings has its Systems list tabbed down.
    var systemsOpen by remember { mutableStateOf(false) }
    var listsOpen by remember { mutableStateOf(false) }
    // A user-made list being asked about removing from Settings > Lists.
    var removingCustom by remember { mutableStateOf<CustomTab?>(null) }
    val selectedCustomTab = customTabs.firstOrNull { "custom:${it.id}" == selectedKey }
    // Focus starts on the GAMES heading itself, not the screen-spanning Scaffold — a focus rect
    // as big as the whole screen has no sensible "next focusable node below it" for D-pad/joystick
    // spatial search to find, which silently breaks all downward navigation into the list.
    val gamesTabFocusRequester = remember { FocusRequester() }
    // (Games can have been removed, in which case there is no heading to focus.)
    LaunchedEffect(Unit) { runCatching { gamesTabFocusRequester.requestFocus() } }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The bar doesn't scroll, so a new list is only allowed while one more still fits.
    fun tryCreateList() {
        if (tabBarFits(customTabs.map { it.name } + MinMenuName)) creatingTab = true
        else Toast.makeText(context, "The tab bar is full. Remove a list or shorten a list name to make room.", Toast.LENGTH_LONG).show()
    }
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
        // Jump to Settings > Systems so the per-emulator progress is visible while it works.
        selectedKey = HomeTab.SETTINGS.name
        systemsOpen = true
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

    // Each emulator setup below runs silently when RomRunner can write the emulator's folder directly (All files
    // access, see EmulatorFolders), and otherwise falls back to the one-time system folder picker.
    // PrimeHack setup (controller profiles + graphics defaults): [treeUri] is null for the direct route.
    fun applyPrimeHackProfile(treeUri: Uri?) {
        scope.launch {
            val message = when (val result = PrimeHackControls.apply(context, treeUri)) {
                is PrimeHackControls.Result.Applied -> {
                    repository.markSetUp("primehack")
                    result.profile
                        ?.let { "Set up PrimeHack: loaded the ${it.label} controller profile and graphics settings." }
                        ?: "Set up PrimeHack's graphics settings and added the controller profiles."
                }
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
        if (EmulatorFolders.canUseDirectly(PrimeHackControls.PACKAGE)) applyPrimeHackProfile(null)
        else if (granted != null) applyPrimeHackProfile(granted) else launchPrimeHackPicker()
    }
    LaunchedEffect(emulatorInstallState.refreshTick, emulatorInstallState.isInstallingAll, state.games, edenPickerOpen) {
        if (emulatorInstallState.isInstallingAll || edenPickerOpen) return@LaunchedEffect
        val direct = EmulatorFolders.canUseDirectly(PrimeHackControls.PACKAGE)
        if (primeHackOfferedThisRun || repository.isSetUp("primehack") || repository.getPrimeHackFolderUri() != null ||
            (!direct && repository.isPrimeHackSetupPrompted())) return@LaunchedEffect
        if (neededEmulators.none { it.packageName == PrimeHackControls.PACKAGE }) return@LaunchedEffect
        if (!EmulatorLauncher.isPackageInstalled(context, PrimeHackControls.PACKAGE)) return@LaunchedEffect
        primeHackOfferedThisRun = true
        if (direct) applyPrimeHackProfile(null) else launchPrimeHackPicker()
    }

    // Cemu controller profile and Wii U key files (see CemuSetup): same one-time folder grant.
    var showCemuPrompt by remember { mutableStateOf(false) }
    var cemuOfferedThisRun by remember { mutableStateOf(false) }
    fun applyCemuSetup(treeUri: Uri?) {
        scope.launch {
            val message = when (val result = CemuSetup.apply(context, treeUri, repository.getRootFolderUri()?.let { Uri.parse(it) })) {
                is CemuSetup.Result.Applied -> {
                    repository.markSetUp("cemu")
                    // Cemu reads its controller profile when its process starts. Writing straight into its folder
                    // doesn't start it, so nothing to restart; a folder grant does, hence the Force stop step.
                    if (!result.direct) forceStopApp = "Cemu" to CemuSetup.PACKAGE
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
        if (EmulatorFolders.canUseDirectly(CemuSetup.PACKAGE)) applyCemuSetup(null)
        else if (granted != null) applyCemuSetup(granted) else launchCemuPicker()
    }
    LaunchedEffect(emulatorInstallState.refreshTick, emulatorInstallState.isInstallingAll, state.games, showPrimeHackPrompt, edenPickerOpen) {
        if (emulatorInstallState.isInstallingAll || showPrimeHackPrompt || edenPickerOpen) return@LaunchedEffect
        val direct = EmulatorFolders.canUseDirectly(CemuSetup.PACKAGE)
        if (cemuOfferedThisRun || repository.isSetUp("cemu") || repository.getCemuFolderUri() != null ||
            (!direct && repository.isCemuSetupPrompted())) return@LaunchedEffect
        if (neededEmulators.none { it.packageName == CemuSetup.PACKAGE }) return@LaunchedEffect
        if (!EmulatorLauncher.isPackageInstalled(context, CemuSetup.PACKAGE)) return@LaunchedEffect
        cemuOfferedThisRun = true
        if (direct) applyCemuSetup(null) else launchCemuPicker()
    }

    // RetroArch (see RetroArchLauncher): one folder grant on its data folder lets RomRunner install the cores
    // and its own config (RGUI menu, hotkeys, quit on close). Same hands-off picker flow as the others.
    var retroArchPickerOpen by remember { mutableStateOf(false) }
    var retroArchOfferedThisRun by remember { mutableStateOf(false) }
    fun applyRetroArchSetup(treeUri: Uri?) {
        scope.launch {
            val systems = state.games.mapNotNull { g -> state.platforms.find { it.id == g.platformId }?.name }.distinct()
            val cores = RetroArchLauncher.retroArchPlatforms(systems)
            Toast.makeText(context, "Setting up RetroArch…", Toast.LENGTH_SHORT).show()
            val outcome = if (treeUri == null) RetroArchLauncher.setUpDirect(context, cores) else RetroArchLauncher.setUp(context, treeUri, cores)
            val message = when (val result = outcome) {
                is RetroArchLauncher.SetupResult.Done -> "RetroArch is set up: RGUI menu, hotkeys, quit on close" +
                    if (result.coresInstalled.isNotEmpty()) ", and cores ready (${result.coresInstalled.joinToString(", ")})." else "."
                is RetroArchLauncher.SetupResult.Failed -> result.message
            }
            if (outcome is RetroArchLauncher.SetupResult.Done) repository.markSetUp("retroarch")
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
        if (RetroArchLauncher.canSetUpDirectly(context)) applyRetroArchSetup(null)
        else if (granted != null) applyRetroArchSetup(granted) else launchRetroArchPicker()
    }
    LaunchedEffect(emulatorInstallState.refreshTick, emulatorInstallState.isInstallingAll, state.games, showPrimeHackPrompt, showCemuPrompt, edenPickerOpen) {
        if (emulatorInstallState.isInstallingAll || showPrimeHackPrompt || showCemuPrompt || edenPickerOpen) return@LaunchedEffect
        val direct = RetroArchLauncher.canSetUpDirectly(context)
        if (retroArchOfferedThisRun || repository.isSetUp("retroarch") || repository.getRetroArchFolderUri() != null ||
            (!direct && repository.isRetroArchSetupPrompted())) return@LaunchedEffect
        if (neededEmulators.none { it.packageName == "com.retroarch" }) return@LaunchedEffect
        if (!EmulatorLauncher.isPackageInstalled(context, "com.retroarch")) return@LaunchedEffect
        retroArchOfferedThisRun = true
        if (direct) applyRetroArchSetup(null) else launchRetroArchPicker()
    }

    // ARMSX2: as soon as it's installed, preload what can be preloaded (see Armsx2Setup). It isn't opened for the user: they
    // open it from the Systems tab when they're ready (that shows what to pick in its wizard). Done once, after the other setups.
    var armsx2OfferedThisRun by remember { mutableStateOf(false) }
    LaunchedEffect(emulatorInstallState.refreshTick, emulatorInstallState.isInstallingAll, state.games, showPrimeHackPrompt, showCemuPrompt, edenPickerOpen, retroArchPickerOpen) {
        if (emulatorInstallState.isInstallingAll || showPrimeHackPrompt || showCemuPrompt || edenPickerOpen || retroArchPickerOpen) return@LaunchedEffect
        if (armsx2OfferedThisRun || repository.isArmsx2WizardShown()) return@LaunchedEffect
        if (neededEmulators.none { it.packageName == Armsx2Setup.PACKAGE }) return@LaunchedEffect
        if (!EmulatorLauncher.isPackageInstalled(context, Armsx2Setup.PACKAGE)) return@LaunchedEffect
        armsx2OfferedThisRun = true
        repository.markArmsx2WizardShown()
        // Runs in the screen's own scope, not this effect's: the effect restarts whenever the list of games or an install
        // finishes, and a restart in the middle would cancel the work after the files were written but before it was recorded.
        scope.launch {
            // Written before its first start, the only time ARMSX2 reads them (see Armsx2Setup).
            if (Armsx2Setup.preload(context, repository.getRootFolderUri()?.let { Uri.parse(it) }) is Armsx2Setup.PreloadResult.Applied) {
                repository.markSetUp("armsx2")
                emulatorInstallState.bumpRefresh()
            }
        }
    }

    // The Setup cell on ARMSX2's line: preload its settings again by hand (only works before it has been set up).
    fun setUpArmsx2() {
        scope.launch {
            val message = when (val result = Armsx2Setup.preload(context, repository.getRootFolderUri()?.let { Uri.parse(it) })) {
                is Armsx2Setup.PreloadResult.Applied -> {
                    repository.markSetUp("armsx2")
                    "ARMSX2 preloaded: " + result.summary.joinToString(", ") + "."
                }
                Armsx2Setup.PreloadResult.AlreadyOpened ->
                    "ARMSX2 has already been set up, so its settings can't be preloaded now. Uninstall and reinstall it to start fresh."
                is Armsx2Setup.PreloadResult.Failed -> result.message
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            emulatorInstallState.bumpRefresh()
        }
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
    fun applyEdenSetup(treeUri: Uri?, quiet: Boolean) {
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
                        repository.markSetUp("eden")
                        if (result.direct) {
                            // Written straight into Eden's folder, which doesn't start Eden, so it reads the new
                            // settings the next time it launches: nothing more to do.
                            Toast.makeText(context, result.summary.joinToString(" "), Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(
                                context,
                                result.summary.joinToString(" ") + " Last step: tap Force stop, then OK, so Eden starts using it.",
                                Toast.LENGTH_LONG
                            ).show()
                            // Going through Eden's folder provider started Eden's process, which has already read its
                            // settings, and Android won't let RomRunner stop it: take the user to the button that does.
                            openAppInfo(EdenGpuDriver.PACKAGE)
                        }
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
        if (EmulatorFolders.canUseDirectly(EdenGpuDriver.PACKAGE)) applyEdenSetup(null, quiet = false)
        else if (granted != null) applyEdenSetup(granted, quiet = false) else launchEdenPicker()
    }
    LaunchedEffect(emulatorInstallState.refreshTick, emulatorInstallState.isInstallingAll, state.games, showPrimeHackPrompt) {
        if (emulatorInstallState.isInstallingAll || showPrimeHackPrompt) return@LaunchedEffect
        val direct = EmulatorFolders.canUseDirectly(EdenGpuDriver.PACKAGE)
        if (edenOfferedThisRun || repository.isSetUp("eden") || repository.getEdenFolderUri() != null ||
            (!direct && repository.isEdenSetupPrompted())) return@LaunchedEffect
        if (neededEmulators.none { it.packageName == EdenGpuDriver.PACKAGE }) return@LaunchedEffect
        if (!EmulatorLauncher.isPackageInstalled(context, EdenGpuDriver.PACKAGE)) return@LaunchedEffect
        edenOfferedThisRun = true
        if (direct) applyEdenSetup(null, quiet = false) else launchEdenPicker()
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
                when (val prepared = RetroArchLauncher.prepare(context, platform, game, repository.getRetroArchFolderUri()?.let { Uri.parse(it) }, repository.getRetroArchCore(platform.name))) {
                    is RetroArchLauncher.Prepared.Ready -> {
                        context.startActivity(prepared.intent)
                        GameFocus.reassertAfterLaunch(context, "com.retroarch")
                        SecondScreenState.gameStarted(game.title, "RetroArch")
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
        fun startBuilt() {
            val intent = EmulatorLauncher.buildIntent(context, platform, game)
            try {
                context.startActivity(intent)
                GameFocus.reassertAfterLaunch(context, target?.packageName)
                SecondScreenState.gameStarted(game.title, target?.let { EmulatorLauncher.labelFor(it.packageName) }.orEmpty())
                viewModel.markPlayed(game)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(
                    context,
                    "No app could open this file. Check the emulator for ${platform.name} in Settings > Systems.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        if (target?.packageName == AzaharSetup.PACKAGE && !repository.isAzaharDefaultsApplied()) {
            // Azahar's config.ini only exists once its first-run setup has been done, so this is retried on each launch until it takes.
            scope.launch {
                if (AzaharSetup.applyDefaults(context, repository.getRootFolderUri()?.let { Uri.parse(it) })) {
                    repository.markAzaharDefaultsApplied()
                }
                startBuilt()
            }
            return
        }
        startBuilt()
    }

    fun launchWithChosenApp(platform: Platform, game: Game, packageName: String) {
        val intent = EmulatorLauncher.buildIntentForPackage(context, platform, game, packageName)
        try {
            context.startActivity(intent)
            GameFocus.reassertAfterLaunch(context, packageName)
            SecondScreenState.gameStarted(game.title, EmulatorLauncher.labelFor(packageName))
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
                        selectedKey = cycleTab(selectedKey, visibleTabKeys, -1)
                        true
                    }
                    android.view.KeyEvent.KEYCODE_BUTTON_R1 -> {
                        selectedKey = cycleTab(selectedKey, visibleTabKeys, 1)
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.weight(1f).onSizeChanged { tabBarWidthPx = it.width }
                ) {
                    if (HomeTab.GAMES.name !in hiddenDefaults) {
                        HomeTabHeading(
                            text = "GAMES",
                            selected = selectedKey == HomeTab.GAMES.name,
                            onClick = { selectedKey = HomeTab.GAMES.name },
                            modifier = Modifier.focusRequester(gamesTabFocusRequester)
                        )
                    }
                    if (HomeTab.APPS.name !in hiddenDefaults) {
                        HomeTabHeading(
                            text = "APPS",
                            selected = selectedKey == HomeTab.APPS.name,
                            onClick = { selectedKey = HomeTab.APPS.name }
                        )
                    }
                    // The user's own menus, then the "+" that makes another.
                    customTabs.forEach { tab ->
                        HomeTabHeading(
                            text = tab.name.uppercase(),
                            selected = selectedKey == "custom:${tab.id}",
                            onClick = { selectedKey = "custom:${tab.id}" }
                        )
                    }
                    HomeTabHeading(
                        text = "+",
                        selected = false,
                        onClick = { tryCreateList() }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsIconButton(
                        selected = selectedCustomTab == null && selectedTab == HomeTab.SETTINGS,
                        onClick = { selectedKey = HomeTab.SETTINGS.name }
                    )
                    Spacer(Modifier.width(14.dp))
                    HomeStatusInfo()
                }
            }
            HorizontalDivider()

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (selectedCustomTab != null) {
                    CustomTabContent(
                        tab = selectedCustomTab,
                        games = state.games,
                        platformNameFor = { game -> displayPlatformName(platformsById[game.platformId], game) },
                        onGameClick = { game -> platformsById[game.platformId]?.let { attemptLaunch(it, game) } },
                        onGameLongClick = { game -> actionGame = game },
                        onDelete = {
                            val id = selectedCustomTab.id
                            customTabs = customTabs.filter { it.id != id }
                            repository.saveCustomTabs(customTabs)
                            selectedKey = (listOf(HomeTab.GAMES.name, HomeTab.APPS.name).filter { it !in hiddenDefaults } + customTabs.map { "custom:${it.id}" })
                                .firstOrNull() ?: HomeTab.SETTINGS.name
                        }
                    )
                } else when (selectedTab) {
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
                                    item { RemoveMenuRow { removingDefault = HomeTab.GAMES.name } }
                                }
                            }
                        }
                    }
                    HomeTab.SETTINGS -> PlatformsContent(
                        repository = repository,
                        onDualScreenSupportChanged = onDualScreenSupportChanged,
                        onRomsFolderChanged = { viewModel.rescanAll(context) },
                        systemsOpen = systemsOpen,
                        onToggleSystems = { systemsOpen = !systemsOpen },
                        listsOpen = listsOpen,
                        onToggleLists = { listsOpen = !listsOpen },
                        listsContent = {
                            ListsSettingsContent(
                                hiddenDefaults = hiddenDefaults,
                                customTabs = customTabs,
                                onRemoveDefault = { removingDefault = it },
                                onRestoreDefault = { key ->
                                    hiddenDefaults = hiddenDefaults - key
                                    repository.setHiddenDefaultTabs(hiddenDefaults)
                                },
                                onRemoveCustom = { removingCustom = it },
                                onAdd = { tryCreateList() }
                            )
                        },
                        systemsContent = {
                            SystemsContent(
                                repository = repository,
                                installState = emulatorInstallState,
                                onSetUpPrimeHack = { setUpPrimeHack() },
                                onSetUpEdenDriver = { setUpEdenDriver() },
                                onSetUpCemu = { setUpCemu() },
                                onSetUpRetroArch = { setUpRetroArch() },
                                onSetUpArmsx2 = { setUpArmsx2() }
                            )
                        }
                    )
                    HomeTab.APPS -> AppsContent(onRemoveMenu = { removingDefault = HomeTab.APPS.name })
                }
            }
        }
    }

    removingCustom?.let { tab ->
        ConfirmRemoveDialog(
            title = "Delete this list?",
            message = "\"${tab.name}\" will be removed. The games and apps in it aren't touched.",
            confirmLabel = "Delete",
            keepLabel = "Keep it",
            onConfirm = {
                customTabs = customTabs.filter { it.id != tab.id }
                repository.saveCustomTabs(customTabs)
                removingCustom = null
                if (selectedKey == "custom:${tab.id}") {
                    selectedKey = (listOf(HomeTab.GAMES.name, HomeTab.APPS.name).filter { it !in hiddenDefaults } + customTabs.map { "custom:${it.id}" })
                        .firstOrNull() ?: HomeTab.SETTINGS.name
                }
            },
            onDismiss = { removingCustom = null }
        )
    }

    removingDefault?.let { key ->
        val name = if (key == HomeTab.GAMES.name) "Games" else "Apps"
        ConfirmRemoveDialog(
            title = "Remove the $name list?",
            message = if (key == HomeTab.GAMES.name) "Your games aren't touched. You can bring the list back from Settings > Lists." else "Your apps aren't touched. You can bring the list back from Settings > Lists.",
            confirmLabel = "Remove",
            keepLabel = "Keep it",
            onConfirm = {
                hiddenDefaults = hiddenDefaults + key
                repository.setHiddenDefaultTabs(hiddenDefaults)
                removingDefault = null
                selectedKey = (listOf(HomeTab.GAMES.name, HomeTab.APPS.name).filter { it !in hiddenDefaults } + customTabs.map { "custom:${it.id}" })
                    .firstOrNull() ?: HomeTab.SETTINGS.name
            },
            onDismiss = { removingDefault = null }
        )
    }

    if (creatingTab || tabEditor != null) {
        CustomTabEditor(
            initial = tabEditor,
            games = state.games,
            nameFits = { name -> tabBarFits(customTabs.filter { it.id != tabEditor?.id }.map { it.name } + name) },
            onSave = { saved ->
                customTabs = if (customTabs.any { it.id == saved.id }) customTabs.map { if (it.id == saved.id) saved else it } else customTabs + saved
                repository.saveCustomTabs(customTabs)
                selectedKey = "custom:${saved.id}"
                creatingTab = false
                tabEditor = null
            },
            onCancel = {
                creatingTab = false
                tabEditor = null
            }
        )
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

/** The small pixel-art gear left of the clock that opens Settings; lit while Settings (or Systems inside it) is open, and glows with the controller. */
@Composable
private fun SettingsIconButton(selected: Boolean, onClick: () -> Unit) {
    val interaction = rememberFocusInteractionSource()
    val base = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
    val color = interaction.glowColor(base)
    val glow = interaction.glowShadow()
    Canvas(
        modifier = Modifier
            .size(28.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        // An 11 x 11 grid of square pixels, like the pixel font: a ring with a 3 x 3 hole, a tooth on each side and a 2 x 2 tooth on each diagonal.
        val grid = 11
        val cell = kotlin.math.floor(size.minDimension / grid)
        val offset = (size.minDimension - cell * grid) / 2f
        for (y in 0 until grid) {
            for (x in 0 until grid) {
                val dx = x - 5
                val dy = y - 5
                val r2 = dx * dx + dy * dy
                val ring = r2 in 4..17
                val sideTooth = kotlin.math.abs(dx) <= 1 && kotlin.math.abs(dy) == 5 || kotlin.math.abs(dy) <= 1 && kotlin.math.abs(dx) == 5
                val diagonalTooth = kotlin.math.abs(dx) in 3..4 && kotlin.math.abs(dy) in 3..4
                if (ring || sideTooth || diagonalTooth) {
                    drawRect(color = color, topLeft = Offset(offset + x * cell, offset + y * cell), size = Size(cell, cell))
                }
            }
        }
        // A focused gear gets the same soft bloom the text does (a pixel glow would blur the blocks, so a faint square halo instead).
        if (glow != null) drawRect(color = color.copy(alpha = 0.15f), topLeft = Offset(0f, 0f), size = size)
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
