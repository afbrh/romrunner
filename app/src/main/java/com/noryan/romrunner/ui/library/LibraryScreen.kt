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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noryan.romrunner.data.embedded.AzaharEmbeddedLauncher
import com.noryan.romrunner.data.embedded.PS2EmbeddedLauncher
import com.noryan.romrunner.data.embedded.CemuEmbeddedLauncher
import com.noryan.romrunner.data.embedded.DuskLightEmbeddedLauncher
import com.noryan.romrunner.data.embedded.EdenIntegration
import com.noryan.romrunner.data.embedded.PrimeHackEmbeddedLauncher
import com.noryan.romrunner.data.embedded.RetroArchEmbeddedLauncher
import com.noryan.romrunner.data.launch.BackgroundAppCleaner
import com.noryan.romrunner.data.launch.EmulatorLauncher
import com.noryan.romrunner.data.launch.InstalledApp
import com.noryan.romrunner.data.model.Game
import com.noryan.romrunner.data.model.Platform
import com.noryan.romrunner.data.repository.LibraryRepository
import com.noryan.romrunner.ui.components.AppPickerDialog
import com.noryan.romrunner.ui.components.GameActionSheet
import com.noryan.romrunner.ui.components.GameRow
import com.noryan.romrunner.ui.components.MissingAppDialog
import com.noryan.romrunner.ui.components.RenameDialog
import com.noryan.romrunner.ui.components.glowColor
import com.noryan.romrunner.ui.components.glowShadow
import com.noryan.romrunner.ui.components.HomeStatusInfo
import com.noryan.romrunner.ui.components.rememberFocusInteractionSource
import com.noryan.romrunner.ui.apps.AppsContent
import com.noryan.romrunner.ui.platforms.PlatformsContent
import kotlinx.coroutines.launch

private data class MissingAppRequest(val platform: Platform, val game: Game, val target: EmulatorLauncher.Target)

private enum class HomeTab { GAMES, SETTINGS, APPS }

private fun cycleTab(current: HomeTab, delta: Int): HomeTab {
    val tabs = HomeTab.entries
    val nextIndex = (tabs.indexOf(current) + delta + tabs.size) % tabs.size
    return tabs[nextIndex]
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    repository: LibraryRepository,
    onEditPlatform: (Long) -> Unit
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

    val snackbarHostState = remember { SnackbarHostState() }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) viewModel.setRootFolder(context, uri)
    }

    // PS2 BIOS import: a one-time RomRunner-native prompt in place of ARMSX2's own onboarding
    // wizard. pendingPs2Launch holds the game to resume launching once the BIOS is in place.
    var pendingPs2Launch by remember { mutableStateOf<Game?>(null) }
    val biosPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val game = pendingPs2Launch
        pendingPs2Launch = null
        val romsRootUri = state.romsRootUri
        if (uri == null || game == null || romsRootUri == null) return@rememberLauncherForActivityResult
        if (PS2EmbeddedLauncher.importBios(context, uri)) {
            PS2EmbeddedLauncher.launch(context, game, romsRootUri)
            viewModel.markPlayed(game)
        } else {
            Toast.makeText(context, "Couldn't import that BIOS file.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    // Nintendo Switch (Eden, "full" flavor only — a no-op stub on "lite"): keys/firmware import
    // state and dialogs live behind this seam so this file never references Eden types directly.
    val edenState = EdenIntegration.rememberState(context, state.romsRootUri, viewModel::markPlayed)

    val platformsById = remember(state.platforms) { state.platforms.associateBy { it.id } }

    fun attemptLaunch(platform: Platform, game: Game) {
        // Best-effort memory reclaim before any launch path below — see BackgroundAppCleaner's
        // own doc comment for exactly what this can and can't do.
        if (repository.getKillBackgroundAppsOnLaunch()) {
            BackgroundAppCleaner.killNonEssentialApps(context)
        }
        if (platform.name == AzaharEmbeddedLauncher.PLATFORM_NAME && platform.useBuiltIn) {
            val romsRootUri = state.romsRootUri
            if (romsRootUri == null) {
                Toast.makeText(context, "Choose a ROMs folder first.", Toast.LENGTH_LONG).show()
                return
            }
            AzaharEmbeddedLauncher.launch(context, game, romsRootUri)
            viewModel.markPlayed(game)
            return
        }
        if (platform.name == PS2EmbeddedLauncher.PLATFORM_NAME && platform.useBuiltIn) {
            val romsRootUri = state.romsRootUri
            if (romsRootUri == null) {
                Toast.makeText(context, "Choose a ROMs folder first.", Toast.LENGTH_LONG).show()
                return
            }
            if (!PS2EmbeddedLauncher.isBiosImported(context)) {
                pendingPs2Launch = game
                return
            }
            PS2EmbeddedLauncher.launch(context, game, romsRootUri)
            viewModel.markPlayed(game)
            return
        }
        // Twilight Princess routes to its own embedded DuskLight core — a decompilation-based
        // native reimplementation of this one title — instead of the generic GameCube/Wii
        // (PrimeHack) branch below. Checked first and unconditionally (no useBuiltIn gate: this
        // isn't a platform-level toggle, it's a per-title override, same as GameLaunchOverrides
        // used to be for this exact title before DuskLight was embedded — see that file).
        if (DuskLightEmbeddedLauncher.matches(game.title)) {
            DuskLightEmbeddedLauncher.launch(context, game)
            viewModel.markPlayed(game)
            return
        }
        if (platform.name == PrimeHackEmbeddedLauncher.PLATFORM_NAME && platform.useBuiltIn) {
            val romsRootUri = state.romsRootUri
            if (romsRootUri == null) {
                Toast.makeText(context, "Choose a ROMs folder first.", Toast.LENGTH_LONG).show()
                return
            }
            PrimeHackEmbeddedLauncher.launch(context, game, romsRootUri)
            viewModel.markPlayed(game)
            return
        }
        if (platform.name == CemuEmbeddedLauncher.PLATFORM_NAME && platform.useBuiltIn) {
            val romsRootUri = state.romsRootUri
            if (romsRootUri == null) {
                Toast.makeText(context, "Choose a ROMs folder first.", Toast.LENGTH_LONG).show()
                return
            }
            CemuEmbeddedLauncher.launch(context, game, romsRootUri)
            viewModel.markPlayed(game)
            return
        }
        if (platform.name in RetroArchEmbeddedLauncher.PLATFORM_NAMES && platform.useBuiltIn) {
            if (state.romsRootUri == null) {
                Toast.makeText(context, "Choose a ROMs folder first.", Toast.LENGTH_LONG).show()
                return
            }
            RetroArchEmbeddedLauncher.launch(context, game, platform.name)
            viewModel.markPlayed(game)
            return
        }
        if (edenState.attemptLaunch(platform, game)) return
        val target = EmulatorLauncher.resolveTarget(platform, game)
        if (target != null && !EmulatorLauncher.isPackageInstalled(context, target.packageName)) {
            installPrompt = MissingAppRequest(platform, game, target)
            return
        }
        val intent = EmulatorLauncher.buildIntent(context, platform, game)
        try {
            context.startActivity(intent)
            viewModel.markPlayed(game)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                context,
                "No app could open this file. Configure an emulator for ${platform.name} in Settings.",
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
                // With three tabs now (was two, one fixed jump per shoulder button), L1/R1 cycle
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
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(state.games, key = { it.id }) { game ->
                                        val platform = platformsById[game.platformId]
                                        GameRow(
                                            game = game,
                                            platformName = displayPlatformName(platform, game),
                                            onClick = {
                                                platform?.let { attemptLaunch(it, game) }
                                            },
                                            onLongClick = { actionGame = game }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    HomeTab.SETTINGS -> PlatformsContent(repository = repository, onEditPlatform = onEditPlatform)
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

    if (pendingPs2Launch != null) {
        ImportBiosDialog(
            onDismiss = { pendingPs2Launch = null },
            onChooseFile = { biosPicker.launch(arrayOf("*/*")) }
        )
    }

    edenState.Dialogs()
}

/** One-time prompt for PS2's required BIOS dump, shown in place of ARMSX2's own onboarding
 *  wizard the first time a PS2 game is launched. The picked file is imported into RomRunner's
 *  app-private storage and never asked for again. */
@Composable
private fun ImportBiosDialog(onDismiss: () -> Unit, onChooseFile: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import PS2 BIOS") },
        text = {
            Text(
                "PlayStation 2 emulation needs a BIOS file dumped from your own console. " +
                    "Choose it once — RomRunner keeps a private copy and won't ask again."
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onChooseFile) { Text("Choose File") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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
            "rvz" -> "Wii"
            else -> platform.name
        }
    }
    return when (platform.name) {
        "PlayStation 2" -> "PS2"
        "PlayStation" -> "PS1"
        "Nintendo 3DS" -> "3DS"
        "Nintendo Switch" -> "Switch"
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
