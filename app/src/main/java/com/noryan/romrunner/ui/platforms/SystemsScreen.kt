package com.noryan.romrunner.ui.platforms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noryan.romrunner.data.launch.CemuSetup
import com.noryan.romrunner.data.launch.DefaultPlatforms
import com.noryan.romrunner.data.launch.EdenGpuDriver
import com.noryan.romrunner.data.launch.EmulatorLauncher
import com.noryan.romrunner.data.launch.GameLaunchOverrides
import com.noryan.romrunner.data.launch.PrimeHackControls
import com.noryan.romrunner.data.launch.RECOMMENDED_EMULATORS
import com.noryan.romrunner.data.launch.RecommendedEmulator
import com.noryan.romrunner.data.launch.SystemOrder
import com.noryan.romrunner.data.model.Platform
import com.noryan.romrunner.data.repository.LibraryRepository
import com.noryan.romrunner.ui.components.AppPickerDialog
import com.noryan.romrunner.ui.components.EmulatorChoiceDialog
import com.noryan.romrunner.ui.components.FocusGlowColor
import com.noryan.romrunner.ui.components.glowColor
import com.noryan.romrunner.ui.components.glowShadow
import com.noryan.romrunner.ui.components.rememberFocusInteractionSource
import kotlinx.coroutines.launch

private val Good = Color(0xFF6BCB77)
private val Bad = Color(0xFFFF6B6B)
private val Neutral = Color(0xFF9A9A9A)

// Fixed column widths, so every row's emulator, install status and setup status line up.
private val EmulatorColumn = 210.dp
private val StatusColumn = 190.dp
private val SetupColumn = 170.dp

/** One line of the Systems tab: a system with games in the library (or a per-title override like Twilight Princess) and the app that plays it. */
private data class SystemRow(
    val key: String,
    val label: String,
    val sortRank: Int,
    /** Null for a per-title override line, whose app can't be swapped here. */
    val platform: Platform?,
    val packageName: String,
    val appLabel: String
)

/** The short names the system lines use. */
private fun shortName(platformName: String): String = when (platformName) {
    "GameBoy (Color + Advance)" -> "GameBoy"
    "Nintendo 64" -> "N64"
    "PlayStation" -> "PS1"
    "PlayStation 2" -> "PS2"
    "Nintendo 3DS" -> "3DS"
    "Nintendo DS" -> "DS"
    "Nintendo Switch" -> "Switch"
    "Sega Genesis" -> "Genesis"
    "Sega Master System" -> "Master System"
    "Sega Game Gear" -> "Game Gear"
    "Sega 32X" -> "32X"
    "PC Engine / TurboGrafx-16" -> "PC Engine"
    else -> platformName
}

/**
 * The Systems tab: a line per system in the library, in a fixed set of columns — the system, the emulator that plays it
 * (tap to change it), whether that app is installed (tap to install or open it) and, for the apps that have a one-time
 * setup (PrimeHack, Cemu, Eden, RetroArch), its setup state (tap to run it). General app settings live in [PlatformsContent].
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

    // Safety net alongside the installer's own post-install poll: re-checks every row whenever RomRunner comes
    // back to the foreground (e.g. returning from the system installer).
    val lifecycleOwner = LocalLifecycleOwner.current
    var installCheckTick by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) installCheckTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val games by repository.observeGames().collectAsStateWithLifecycle(initialValue = emptyList())
    val platforms by repository.observePlatforms().collectAsStateWithLifecycle(initialValue = emptyList())

    val rows = remember(games, platforms, installState.refreshTick) {
        val platformsById = platforms.associateBy { it.id }
        val gamePlatformIds = games.mapNotNull { it.platformId }.toSet()
        val systemRows = platforms.filter { it.id in gamePlatformIds }.map { platform ->
            SystemRow(
                key = "p${platform.id}",
                label = shortName(platform.name),
                sortRank = SystemOrder.rank(platform.name),
                platform = platform,
                packageName = platform.launchPackage,
                appLabel = appName(context, platform.launchPackage)
            )
        }
        // A game with a per-title override (Twilight Princess) gets its own line, since it plays in a different app.
        val overrideRows = GameLaunchOverrides.ALL.filter { override ->
            games.any { game -> platformsById[game.platformId]?.name?.let { GameLaunchOverrides.find(game.title, it) } == override }
        }.map { override ->
            SystemRow(
                key = "o${override.titleKeyword}",
                label = if ("twilight princess" in override.titleKeyword) "Twilight Princess" else override.titleKeyword.replaceFirstChar { it.uppercase() },
                sortRank = SystemOrder.OVERRIDE_RANK,
                platform = null,
                packageName = override.packageName.orEmpty(),
                appLabel = override.appLabel
            )
        }
        (systemRows + overrideRows).sortedBy { it.sortRank }
    }

    val primeHackLinked = remember(installState.refreshTick) { repository.getPrimeHackFolderUri() != null || repository.isSetUp("primehack") }
    val cemuLinked = remember(installState.refreshTick) { repository.getCemuFolderUri() != null || repository.isSetUp("cemu") }
    val retroArchLinked = remember(installState.refreshTick) { repository.getRetroArchFolderUri() != null || repository.isSetUp("retroarch") }
    val edenDriverApplied = remember(installState.refreshTick) { repository.isEdenDriverApplied() }

    // The emulators that have a one-time setup, as: whether it's done, and what to run to do it.
    val setups: Map<String, Pair<Boolean, () -> Unit>> = mapOf(
        PrimeHackControls.PACKAGE to (primeHackLinked to onSetUpPrimeHack),
        CemuSetup.PACKAGE to (cemuLinked to onSetUpCemu),
        EdenGpuDriver.PACKAGE to (edenDriverApplied to onSetUpEdenDriver),
        "com.retroarch" to (retroArchLinked to onSetUpRetroArch)
    )

    var choosingFor by remember { mutableStateOf<SystemRow?>(null) }
    var pickingAppFor by remember { mutableStateOf<SystemRow?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                ColumnTitle("System", Modifier.weight(1f))
                ColumnTitle("Emulator", Modifier.width(EmulatorColumn))
                ColumnTitle("Status", Modifier.width(StatusColumn))
                ColumnTitle("Setup", Modifier.width(SetupColumn))
            }
        }
        items(rows, key = { it.key }) { row ->
            val recommended = recommendedEmulator(row.packageName)
            val setupKey = if (row.packageName.startsWith("com.retroarch")) "com.retroarch" else row.packageName
            val isInstalled = remember(row.packageName, installCheckTick, installState.refreshTick) {
                row.packageName.isNotBlank() && EmulatorLauncher.isPackageInstalled(context, row.packageName)
            }
            val installing = (recommended?.packageName ?: row.packageName).let { installState.statuses[it] }

            val emulatorFocus = rememberFocusInteractionSource()
            val statusFocus = rememberFocusInteractionSource()
            val setupFocus = rememberFocusInteractionSource()
            val anyFocused = listOf(emulatorFocus, statusFocus, setupFocus).any { it.collectIsFocusedAsState().value }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    row.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (anyFocused) FocusGlowColor else Color.Unspecified,
                    modifier = Modifier.weight(1f)
                )

                // Emulator: tap to change it (not for a per-title override).
                Cell(
                    modifier = Modifier.width(EmulatorColumn),
                    interaction = emulatorFocus,
                    text = if (row.platform != null) "${row.appLabel.ifBlank { "Choose app" }}  >" else row.appLabel,
                    onClick = if (row.platform != null) ({ choosingFor = row }) else null
                )

                // Install status: tap to open the app, or to install it.
                val canInstall = recommended != null
                when {
                    row.packageName.isBlank() -> Cell(Modifier.width(StatusColumn), statusFocus, "—", color = Neutral)
                    installing != null -> Cell(Modifier.width(StatusColumn), statusFocus, installing, dot = Neutral)
                    isInstalled -> Cell(
                        Modifier.width(StatusColumn), statusFocus, "Installed", dot = Good,
                        onClick = {
                            val launchPackage = EmulatorLauncher.installedPackageFor(context, row.packageName) ?: row.packageName
                            context.packageManager.getLaunchIntentForPackage(launchPackage)?.let { context.startActivity(it) }
                        }
                    )
                    else -> Cell(
                        Modifier.width(StatusColumn), statusFocus, "Not installed", dot = Bad,
                        onClick = if (canInstall) ({ scope.launch { installState.installOne(context, recommended!!) } }) else null
                    )
                }

                // Setup: only for the apps that have one, and only once installed.
                val setup = setups[setupKey]
                if (setup != null && isInstalled) {
                    val (done, run) = setup
                    Cell(Modifier.width(SetupColumn), setupFocus, if (done) "Loaded" else "Needed", dot = if (done) Good else Bad, onClick = run)
                } else {
                    Cell(Modifier.width(SetupColumn), setupFocus, "—", color = Neutral)
                }
            }
        }
    }

    choosingFor?.let { row ->
        val platform = row.platform ?: return@let
        val defaults = DefaultPlatforms.ALL.firstOrNull { it.name == platform.name }
        val currentIsRecommended = defaults != null && defaults.launchPackage == platform.launchPackage
        EmulatorChoiceDialog(
            systemLabel = row.label,
            recommendedLabel = defaults?.let { appName(context, it.launchPackage) },
            currentLabel = row.appLabel,
            currentIsRecommended = currentIsRecommended,
            onUseRecommended = {
                // Restores the app, activity and file type the starter set uses for this system (some need an exact activity).
                if (defaults != null) {
                    scope.launch {
                        repository.savePlatform(platform.copy(launchPackage = defaults.launchPackage, launchActivity = defaults.launchActivity, mimeType = defaults.mimeType))
                    }
                }
                choosingFor = null
            },
            onChooseOther = {
                choosingFor = null
                pickingAppFor = row
            },
            onDismiss = { choosingFor = null }
        )
    }

    pickingAppFor?.let { row ->
        val platform = row.platform ?: return@let
        AppPickerDialog(
            title = "Choose an app for ${row.label}",
            onPick = { app ->
                pickingAppFor = null
                // Plain file handoff: the starter set's special activity / file type belong to the original app.
                scope.launch {
                    repository.savePlatform(platform.copy(launchPackage = app.packageName, launchActivity = "", mimeType = "application/octet-stream"))
                }
            },
            onDismiss = { pickingAppFor = null }
        )
    }
}

/** The recommended emulator entry (install source) for [packageName], RetroArch's variant packages included. */
private fun recommendedEmulator(packageName: String): RecommendedEmulator? =
    RECOMMENDED_EMULATORS.firstOrNull { packageName == it.packageName || packageName.startsWith(it.packageName + ".") }

/** A readable name for an app: the one we know it by, else its label on this device, else the package. */
private fun appName(context: android.content.Context, packageName: String): String {
    if (packageName.isBlank()) return ""
    EmulatorLauncher.labelFor(packageName).takeIf { it.isNotBlank() }?.let { return it }
    return runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)
}

@Composable
private fun ColumnTitle(text: String, modifier: Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        modifier = modifier
    )
}

/**
 * One column cell: an optional status dot and text. With an [onClick] it's a controller stop and glows when focused,
 * otherwise it's plain text in the same place.
 */
@Composable
private fun Cell(
    modifier: Modifier,
    interaction: MutableInteractionSource,
    text: String,
    dot: Color? = null,
    color: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null
) {
    val clickable = if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick) else Modifier
    Row(modifier = modifier.then(clickable).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (dot != null) {
            val focused = interaction.collectIsFocusedAsState().value
            Box(modifier = Modifier.size(10.dp).background(if (focused) FocusGlowColor else dot, CircleShape))
            Spacer(Modifier.width(10.dp))
        }
        val glow = if (onClick != null) interaction.glowShadow() else null
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(shadow = glow),
            color = if (onClick != null) interaction.glowColor(color) else color
        )
    }
}
