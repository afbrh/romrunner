package com.noryan.romrunner.ui.platforms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.noryan.romrunner.data.embedded.BuiltInPlatforms
import com.noryan.romrunner.data.launch.InstalledApp
import com.noryan.romrunner.data.model.Platform
import com.noryan.romrunner.data.repository.LibraryRepository
import com.noryan.romrunner.ui.components.AppPickerDialog
import com.noryan.romrunner.ui.components.RetroToggle
import com.noryan.romrunner.ui.components.glowColor
import com.noryan.romrunner.ui.components.glowShadow
import com.noryan.romrunner.ui.components.rememberFocusInteractionSource
import kotlinx.coroutines.launch

/**
 * The "folder" a platform opens into from System-Specific Settings' list (see PlatformsScreen.kt):
 * "Use Embedded Emulator" and, when that's on, "Use Global Controller Mapping" — plus whichever of
 * the app-choice row / "Map Controller" row those two decisions call for. Full editing (extensions,
 * launch package/activity/action, delete) still lives on the separate PlatformEditScreen, reached
 * from here via [onEditDetails] rather than duplicated on this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformSettingsScreen(
    repository: LibraryRepository,
    platformId: Long,
    onEditDetails: () -> Unit,
    onOpenControllerMapping: () -> Unit,
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var platform by remember { mutableStateOf<Platform?>(null) }
    var showAppPicker by remember { mutableStateOf(false) }

    LaunchedEffect(platformId) {
        platform = repository.getPlatform(platformId)
    }

    val current = platform ?: return
    val hasBuiltIn = current.name in BuiltInPlatforms.NAMES

    fun update(transform: (Platform) -> Platform) {
        val updated = transform(current)
        platform = updated
        scope.launch { repository.savePlatform(updated) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current.name) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (hasBuiltIn) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Use Embedded Emulator", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    RetroToggle(
                        checked = current.useBuiltIn,
                        onCheckedChange = { checked -> update { it.copy(useBuiltIn = checked) } }
                    )
                }

                if (current.useBuiltIn) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Use Global Controller Mapping (recommended)",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        RetroToggle(
                            checked = current.useGlobalControllerMapping,
                            onCheckedChange = { checked -> update { it.copy(useGlobalControllerMapping = checked) } }
                        )
                    }
                    if (!current.useGlobalControllerMapping) {
                        val mapControllerInteractionSource = rememberFocusInteractionSource()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = mapControllerInteractionSource,
                                    indication = null,
                                    onClick = onOpenControllerMapping
                                )
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Map Controller",
                                style = LocalTextStyle.current.copy(shadow = mapControllerInteractionSource.glowShadow()),
                                color = mapControllerInteractionSource.glowColor(MaterialTheme.colorScheme.onSurface),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    AppChoiceRow(current, onClick = { showAppPicker = true })
                }
            } else {
                AppChoiceRow(current, onClick = { showAppPicker = true })
            }

            HorizontalDivider()

            val editInteractionSource = rememberFocusInteractionSource()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(interactionSource = editInteractionSource, indication = null, onClick = onEditDetails)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Edit Extensions & Launch Settings",
                    style = LocalTextStyle.current.copy(shadow = editInteractionSource.glowShadow()),
                    color = editInteractionSource.glowColor(MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            title = "Which app should play ${current.name} games?",
            onDismiss = { showAppPicker = false },
            onPick = { app: InstalledApp ->
                showAppPicker = false
                update { it.copy(launchPackage = app.packageName) }
            }
        )
    }
}

@Composable
private fun AppChoiceRow(platform: Platform, onClick: () -> Unit) {
    val interactionSource = rememberFocusInteractionSource()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("App", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            text = platform.launchPackage.ifBlank { "Choose app" },
            style = LocalTextStyle.current.copy(shadow = interactionSource.glowShadow()),
            color = interactionSource.glowColor(MaterialTheme.colorScheme.onSurface)
        )
    }
}
