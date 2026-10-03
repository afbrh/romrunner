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
import com.noryan.romrunner.data.launch.InstalledApp
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
import com.noryan.romrunner.ui.components.EdenRestartDialog
import com.noryan.romrunner.ui.components.EdenSetupDialog
import com.noryan.romrunner.ui.components.MissingAppDialog
import com.noryan.romrunner.ui.components.PrimeHackSetupDialog
import com.noryan.romrunner.ui.platforms.EmulatorInstallState
import com.noryan.romrunner.ui.components.RenameDialog
import com.noryan.romrunner.ui.components.glowColor
import com.noryan.romrunner.ui.components.glowShadow
import com.noryan.romrunner.ui.components.HomeStatusInfo
import com.noryan.romrunner.ui.components.rememberFocusInteractionSource
import com.noryan.romrunner.ui.apps.AppsContent
import com.noryan.romrunner.ui.platforms.PlatformsContent
import com.noryan.romrunner.ui.platforms.SystemsContent
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
    val neededEmulators = remember(state.games, state.platforms) {
        RECOMMENDED_EMULATORS.filter { it.isNeeded(state.platforms, state.games) }
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

    var showEdenPrompt by remember { mutableStateOf(false) }
    var showEdenRestartPrompt by remember { mutableStateOf(false) }

    // PrimeHack controller profile: the folder grant comes from the system picker, opened right on
    // PrimeHack's own folder (see PrimeHackControls).
    fun applyPrimeHackProfile(treeUri: Uri) {
        scope.launch {
            val message = when (val result = PrimeHackControls.apply(context, treeUri)) {
                is PrimeHackControls.Result.Applied -> "Loaded the ${result.profile.label} controller profile into PrimeHack."
                is PrimeHackControls.Result.Failed -> result.message
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            emulatorInstallState.bumpRefresh()
        }
    }
    val primeHackPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (!PrimeHackControls.isPrimeHackTree(uri)) {
            Toast.makeText(context, "That isn't PrimeHack's folder — open the menu and choose \"Prime Hack\".", Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        repository.setPrimeHackFolderUri(uri.toString())
        applyPrimeHackProfile(uri)
    }
    fun setUpPrimeHack() {
        val granted = repository.getPrimeHackFolderUri()?.let { Uri.parse(it) }
        if (granted != null) applyPrimeHackProfile(granted) else primeHackPicker.launch(PrimeHackControls.pickerInitialUri())
    }
    var showPrimeHackPrompt by remember { mutableStateOf(false) }
    LaunchedEffect(emulatorInstallState.refreshTick, emulatorInstallState.isInstallingAll, state.games, showEdenPrompt) {
        if (emulatorInstallState.isInstallingAll || showEdenPrompt) return@LaunchedEffect
        if (repository.isPrimeHackSetupPrompted() || repository.getPrimeHackFolderUri() != null) return@LaunchedEffect
        if (PrimeHackControls.detectProfile() == null) return@LaunchedEffect
        if (neededEmulators.none { it.packageName == PrimeHackControls.PACKAGE }) return@LaunchedEffect
        if (!EmulatorLauncher.isPackageInstalled(context, PrimeHackControls.PACKAGE)) return@LaunchedEffect
        repository.markPrimeHackSetupPrompted()
        showPrimeHackPrompt = true
    }

    // Eden graphics driver (see EdenGpuDriver): same one-time folder grant as PrimeHack above.
    fun applyEdenDriver(treeUri: Uri, quiet: Boolean) {
        scope.launch {
            when (val result = EdenGpuDriver.apply(context, treeUri)) {
                is EdenGpuDriver.Result.Applied -> {
                    repository.setEdenDriverApplied(true)
                    showEdenRestartPrompt = true
                }
                is EdenGpuDriver.Result.Failed ->
                    if (!quiet) Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
            }
            emulatorInstallState.bumpRefresh()
        }
    }
    val edenPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (!EdenGpuDriver.isEdenTree(uri)) {
            Toast.makeText(context, "That isn't Eden's folder — open the menu and choose \"Eden\".", Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        repository.setEdenFolderUri(uri.toString())
        applyEdenDriver(uri, quiet = false)
    }
    fun setUpEdenDriver() {
        val granted = repository.getEdenFolderUri()?.let { Uri.parse(it) }
        if (granted != null) applyEdenDriver(granted, quiet = false) else edenPicker.launch(EdenGpuDriver.pickerInitialUri())
    }
    LaunchedEffect(emulatorInstallState.refreshTick, emulatorInstallState.isInstallingAll, state.games, showPrimeHackPrompt) {
        if (emulatorInstallState.isInstallingAll || showPrimeHackPrompt) return@LaunchedEffect
        if (repository.isEdenSetupPrompted() || repository.getEdenFolderUri() != null) return@LaunchedEffect
        if (!EdenGpuDriver.isEligible()) return@LaunchedEffect
        if (neededEmulators.none { it.packageName == EdenGpuDriver.PACKAGE }) return@LaunchedEffect
        if (!EmulatorLauncher.isPackageInstalled(context, EdenGpuDriver.PACKAGE)) return@LaunchedEffect
        repository.markEdenSetupPrompted()
        showEdenPrompt = true
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
        if (repository.isEdenDriverApplied() || !EdenGpuDriver.isEligible()) return@LaunchedEffect
        if (!EmulatorLauncher.isPackageInstalled(context, EdenGpuDriver.PACKAGE)) return@LaunchedEffect
        applyEdenDriver(granted, quiet = true)
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
                when (val prepared = RetroArchLauncher.prepare(context, platform, game)) {
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
                        onSetUpEdenDriver = { setUpEdenDriver() }
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

    if (showEdenRestartPrompt) {
        EdenRestartDialog(
            onOpenAppInfo = {
                showEdenRestartPrompt = false
                context.startActivity(
                    Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${EdenGpuDriver.PACKAGE}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            onDone = { showEdenRestartPrompt = false }
        )
    }

    if (showEdenPrompt) {
        EdenSetupDialog(
            onYes = {
                showEdenPrompt = false
                setUpEdenDriver()
            },
            onNotNow = { showEdenPrompt = false }
        )
    }

    if (showPrimeHackPrompt) {
        PrimeHackSetupDialog(
            profileLabel = PrimeHackControls.detectProfile()?.label.orEmpty(),
            onYes = {
                showPrimeHackPrompt = false
                setUpPrimeHack()
            },
            onNotNow = { showPrimeHackPrompt = false }
        )
    }

    if (showEmulatorSetupPrompt) {
        EmulatorSetupDialog(
            emulatorLabels = neededEmulators
                .filter { !EmulatorLauncher.isPackageInstalled(context, it.packageName) }
                .map { it.appLabel },
            onYes = {
                showEmulatorSetupPrompt = false
                // Jump to Systems so the per-emulator progress is visible while it works.
                selectedTab = HomeTab.SYSTEMS
                scope.launch { emulatorInstallState.installAll(context, neededEmulators) }
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
        Text("Point RomRunner at your ROMs folder", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Pick the top-level folder that holds your games — RomRunner will search it (and every " +
                "subfolder) and list everything it recognizes.",
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onChooseFolder) { Text("Choose folder") }
    }
}
