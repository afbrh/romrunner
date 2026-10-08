package com.noryan.romrunner.ui.platforms

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.produceState
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
import com.noryan.romrunner.data.launch.Armsx2Setup
import com.noryan.romrunner.data.launch.CemuSetup
import com.noryan.romrunner.data.launch.DefaultPlatforms
import com.noryan.romrunner.data.launch.EdenGpuDriver
import com.noryan.romrunner.data.launch.EmulatorDownloader
import com.noryan.romrunner.data.launch.EmulatorLauncher
import com.noryan.romrunner.data.launch.GameLaunchOverrides
import com.noryan.romrunner.data.launch.InstalledApp
import com.noryan.romrunner.data.launch.InstalledApps
import com.noryan.romrunner.data.launch.PrimeHackControls
import com.noryan.romrunner.data.launch.RECOMMENDED_EMULATORS
import com.noryan.romrunner.data.launch.RetroArchCores
import com.noryan.romrunner.data.launch.RetroArchLauncher
import com.noryan.romrunner.data.launch.RecommendedEmulator
import com.noryan.romrunner.data.launch.SystemOrder
import com.noryan.romrunner.data.model.Platform
import com.noryan.romrunner.data.repository.LibraryRepository
import com.noryan.romrunner.ui.components.FocusGlowColor
import com.noryan.romrunner.ui.components.glowColor
import com.noryan.romrunner.ui.components.glowShadow
import com.noryan.romrunner.ui.components.rememberFocusInteractionSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Good = Color(0xFF6BCB77)
private val Bad = Color(0xFFFF6B6B)
private val Warn = Color(0xFFFFD60A)
private val Neutral = Color(0xFF9A9A9A)

/** How far an opened system's emulator, status and configured lines are tabbed in under its heading. */
private val ItemIndent = 52.dp
private val DetailLabelWidth = 140.dp

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
    onSetUpRetroArch: () -> Unit,
    onSetUpArmsx2: () -> Unit
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
    val armsx2Loaded = remember(installState.refreshTick) { repository.isSetUp("armsx2") }

    // The emulators that have a one-time setup, as: whether it's done, and what to run to do it.
    val setups: Map<String, Pair<Boolean, () -> Unit>> = mapOf(
        PrimeHackControls.PACKAGE to (primeHackLinked to onSetUpPrimeHack),
        CemuSetup.PACKAGE to (cemuLinked to onSetUpCemu),
        EdenGpuDriver.PACKAGE to (edenDriverApplied to onSetUpEdenDriver),
        "com.retroarch" to (retroArchLinked to onSetUpRetroArch),
        Armsx2Setup.PACKAGE to (armsx2Loaded to onSetUpArmsx2)
    )

    // "Tabbing down": everything on this tab that opens does so inline, as more tabbed-in lines under the thing you picked,
    // never as a pop-up. Each tab-down has a key ("<system>" for the system itself, "<system>:emulator" for its emulator
    // choices, "<system>:apps" for the list of other apps) and everything starts collapsed.
    var expanded by remember { mutableStateOf(setOf<String>()) }
    // Bumped when a RetroArch core is changed, so the line showing it re-reads the saved choice.
    var coreTick by remember { mutableStateOf(0) }
    fun toggle(key: String) { expanded = if (key in expanded) expanded - key else expanded + key }

    // The installed apps offered under "Choose another app", read once the first time that's tabbed down.
    val installedApps by produceState<List<InstalledApp>?>(null, expanded.any { it.endsWith(":apps") }) {
        if (value == null && expanded.any { it.endsWith(":apps") }) {
            value = withContext(Dispatchers.Default) { InstalledApps.listLaunchable(context) }
        }
    }

    // Emulators whose APK is already in Downloads (so "Downloaded": fetched but not yet installed).
    val downloaded by produceState(emptySet<String>(), rows, installCheckTick, installState.refreshTick) {
        value = rows.mapNotNull { recommendedEmulator(it.packageName) }.distinctBy { it.packageName }
            .filter { !EmulatorLauncher.isPackageInstalled(context, it.packageName) && EmulatorDownloader.findLocalApk(context, it) != null }
            .map { it.packageName }.toSet()
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(rows, key = { it.key }) { row ->
            val recommended = recommendedEmulator(row.packageName)
            val setupKey = if (row.packageName.startsWith("com.retroarch")) "com.retroarch" else row.packageName
            val isInstalled = remember(row.packageName, installCheckTick, installState.refreshTick) {
                row.packageName.isNotBlank() && EmulatorLauncher.isPackageInstalled(context, row.packageName)
            }
            val installing = (recommended?.packageName ?: row.packageName).let { installState.statuses[it] }
            val hasDownload = recommended?.packageName in downloaded
            val setup = setups[setupKey]

            // The one dot a collapsed system shows: green = installed and configured (or nothing to configure), yellow = on
            // its way (downloading, downloaded, or installed but not yet configured), red = not installed, gray = no emulator.
            val dot = when {
                row.packageName.isBlank() -> Neutral
                installing != null -> Warn
                !isInstalled -> if (hasDownload) Warn else Bad
                setup != null && !setup.first -> Warn
                else -> Good
            }

            val headingFocus = rememberFocusInteractionSource()
            val emulatorFocus = rememberFocusInteractionSource()
            val statusFocus = rememberFocusInteractionSource()
            val configuredFocus = rememberFocusInteractionSource()
            val isOpen = row.key in expanded

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                // The heading: status dot and system name; tap it to fan the system open or closed.
                val headingFocused = headingFocus.collectIsFocusedAsState().value
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(interactionSource = headingFocus, indication = null) { toggle(row.key) }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(12.dp).background(dot, CircleShape))
                    Spacer(Modifier.width(14.dp))
                    Text(
                        row.label,
                        style = MaterialTheme.typography.titleMedium.copy(shadow = headingFocus.glowShadow()),
                        color = headingFocus.glowColor(Color.Unspecified)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (isOpen) "-" else "+",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (headingFocused) FocusGlowColor else Neutral
                    )
                }

                if (isOpen) {
                    Column(modifier = Modifier.padding(start = ItemIndent, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Emulator: tap to tab down its choices (not for a per-title override).
                        val emulatorKey = "${row.key}:emulator"
                        val emulatorOpen = emulatorKey in expanded
                        DetailLine("Emulator") {
                            Cell(
                                interaction = emulatorFocus,
                                text = if (row.platform != null) "${row.appLabel.ifBlank { "Choose app" }}  ${if (emulatorOpen) "-" else "+"}" else row.appLabel,
                                onClick = if (row.platform != null) ({ toggle(emulatorKey) }) else null
                            )
                        }
                        if (emulatorOpen && row.platform != null) {
                            val platform = row.platform
                            val defaults = DefaultPlatforms.ALL.firstOrNull { it.name == platform.name }
                            val currentIsRecommended = defaults != null && defaults.launchPackage == platform.launchPackage
                            val appsKey = "${row.key}:apps"
                            val appsOpen = appsKey in expanded
                            Column(modifier = Modifier.padding(start = ItemIndent), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                // Only there to undo a change: while the recommended app is in use, the emulator name above already says so.
                                if (defaults != null && !currentIsRecommended) {
                                    OptionLine("Use recommended (${appName(context, defaults.launchPackage)})", marked = null) {
                                        // Restores the app, activity and file type the starter set uses for this system (some need an exact activity).
                                        scope.launch {
                                            repository.savePlatform(platform.copy(launchPackage = defaults.launchPackage, launchActivity = defaults.launchActivity, mimeType = defaults.mimeType))
                                        }
                                        expanded = expanded - emulatorKey - appsKey
                                    }
                                }
                                OptionLine("Choose another app", marked = null, toggledOpen = appsOpen) { toggle(appsKey) }
                                if (appsOpen) {
                                    Column(modifier = Modifier.padding(start = ItemIndent), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        val apps = installedApps
                                        if (apps == null) {
                                            Text("Loading apps…", style = MaterialTheme.typography.bodyLarge, color = Neutral)
                                        } else {
                                            apps.forEach { app ->
                                                OptionLine(app.label, marked = app.packageName == platform.launchPackage) {
                                                    // Plain file handoff: the starter set's special activity / file type belong to the original app.
                                                    scope.launch {
                                                        repository.savePlatform(platform.copy(launchPackage = app.packageName, launchActivity = "", mimeType = "application/octet-stream"))
                                                    }
                                                    expanded = expanded - emulatorKey - appsKey
                                                }
                                            }
                                        }
                                    }
                                }

                                // RetroArch plays many systems with a different libretro core each: show the one in use and let it be changed.
                                val defaultCore = RetroArchLauncher.defaultCore(platform.name)
                                if (row.packageName.startsWith("com.retroarch") && defaultCore != null) {
                                    val coreKey = "${row.key}:core"
                                    val coreOpen = coreKey in expanded
                                    val options = RetroArchCores.optionsFor(platform.name)
                                    val currentCore = coreTick.let { repository.getRetroArchCore(platform.name) } ?: defaultCore
                                    OptionLine(
                                        "Core: ${RetroArchCores.label(currentCore)}",
                                        marked = null,
                                        toggledOpen = if (options.size > 1) coreOpen else null
                                    ) { if (options.size > 1) toggle(coreKey) }
                                    if (coreOpen && options.size > 1) {
                                        Column(modifier = Modifier.padding(start = ItemIndent), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            options.forEach { option ->
                                                OptionLine(option.label, marked = option.id == currentCore) {
                                                    // The default core is stored as "no choice", so it keeps following RomRunner's default.
                                                    repository.setRetroArchCore(platform.name, option.id.takeIf { it != defaultCore })
                                                    coreTick++
                                                    expanded = expanded - coreKey
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Status: installed, downloaded (but not installed), or neither. Tap to open, install or download it.
                        DetailLine("Status") {
                            val canInstall = recommended != null
                            when {
                                row.packageName.isBlank() -> Cell(statusFocus, "—", color = Neutral)
                                installing != null -> Cell(statusFocus, installing, dot = Warn)
                                isInstalled -> Cell(
                                    statusFocus, "Installed", dot = Good,
                                    onClick = {
                                        val launchPackage = EmulatorLauncher.installedPackageFor(context, row.packageName) ?: row.packageName
                                        // Opening ARMSX2 before it has finished its wizard: say what to pick there.
                                        if (launchPackage == Armsx2Setup.PACKAGE) {
                                            scope.launch {
                                                if (Armsx2Setup.wizardPending()) {
                                                    val hint = Armsx2Setup.wizardHint(context, repository.getRootFolderUri()?.let { Uri.parse(it) })
                                                    Toast.makeText(context, hint, Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                        context.packageManager.getLaunchIntentForPackage(launchPackage)?.let { context.startActivity(it) }
                                    }
                                )
                                hasDownload -> Cell(
                                    statusFocus, "Downloaded", dot = Warn,
                                    onClick = if (canInstall) ({ scope.launch { installState.installOne(context, recommended!!) } }) else null
                                )
                                else -> Cell(
                                    statusFocus, "Not installed", dot = Bad,
                                    onClick = if (canInstall) ({ scope.launch { installState.installOne(context, recommended!!) } }) else null
                                )
                            }
                        }

                        // Configured: whether what can be set up ahead of time for this emulator has been. N/A when there's nothing to set up.
                        DetailLine("Configured") {
                            when {
                                setup == null -> Cell(configuredFocus, "N/A", dot = Neutral)
                                !isInstalled -> Cell(configuredFocus, "—", color = Neutral)
                                setup.first -> Cell(configuredFocus, "Yes", dot = Good, onClick = setup.second)
                                else -> Cell(configuredFocus, "Not yet", dot = Warn, onClick = setup.second)
                            }
                        }
                    }
                }
            }
        }
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

/**
 * One tabbed-down choice: its label, a filled dot when it's the one in use ([marked] null for a line that's an action, not
 * a choice) and a +/- when it tabs down further ([toggledOpen] non-null). Glows when the controller is on it.
 */
@Composable
private fun OptionLine(label: String, marked: Boolean?, toggledOpen: Boolean? = null, onClick: () -> Unit) {
    val interaction = rememberFocusInteractionSource()
    Row(
        modifier = Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (marked != null) {
            Box(modifier = Modifier.size(10.dp).background(if (marked) FocusGlowColor else Color.Transparent, CircleShape))
            Spacer(Modifier.width(12.dp))
        }
        Text(
            if (toggledOpen == null) label else "$label  ${if (toggledOpen) "-" else "+"}",
            style = MaterialTheme.typography.bodyLarge.copy(shadow = interaction.glowShadow()),
            color = interaction.glowColor(MaterialTheme.colorScheme.onBackground)
        )
    }
}

/** One "Label: value" line under an opened system, the labels in a fixed-width column so the values line up. */
@Composable
private fun DetailLine(label: String, value: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$label:",
            style = MaterialTheme.typography.bodyLarge,
            color = Neutral,
            modifier = Modifier.width(DetailLabelWidth)
        )
        value()
    }
}

/**
 * One column cell: an optional status dot and text. With an [onClick] it's a controller stop and glows when focused,
 * otherwise it's plain text in the same place.
 */
@Composable
private fun Cell(
    interaction: MutableInteractionSource,
    text: String,
    dot: Color? = null,
    color: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null
) {
    val clickable = if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick) else Modifier
    Row(modifier = clickable.padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
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
