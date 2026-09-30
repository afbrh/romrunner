package com.noryan.romrunner.ui.platforms

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noryan.romrunner.data.embedded.BuiltInPlatforms
import com.noryan.romrunner.data.launch.BiosKeysImporter
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
 * The Settings tab's content on RomRunner's home screen (see LibraryScreen). Deliberately has no
 * Scaffold/TopAppBar of its own — it's embedded directly under the GAMES/SETTINGS tab heading row
 * rather than being a separate navigation destination.
 */
@Composable
fun PlatformsContent(
    repository: LibraryRepository,
    onEditPlatform: (Long) -> Unit,
    onOpenControllerMapping: () -> Unit,
    onOpenControllerMappingForPlatform: (Long) -> Unit,
    onDualScreenSupportChanged: () -> Unit
) {
    val context = LocalContext.current
    val platforms by repository.observePlatforms().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    var rootFolderUri by remember { mutableStateOf(repository.getRootFolderUri()) }
    LaunchedEffect(Unit) { rootFolderUri = repository.getRootFolderUri() }

    var biosKeysFolderUri by remember { mutableStateOf(repository.getBiosKeysFolderUri()) }
    LaunchedEffect(Unit) { biosKeysFolderUri = repository.getBiosKeysFolderUri() }

    var killBackgroundAppsOnLaunch by remember { mutableStateOf(repository.getKillBackgroundAppsOnLaunch()) }
    var dualScreenSupportEnabled by remember { mutableStateOf(repository.getDualScreenSupportEnabled()) }

    var appPickerFor by remember { mutableStateOf<Platform?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }
    var newPlatformName by remember { mutableStateOf("") }
    var systemSettingsExpanded by remember { mutableStateOf(false) }
    var expandedPlatformIds by remember { mutableStateOf(setOf<Long>()) }

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
            scope.launch {
                val message = BiosKeysImporter.scanImportAndDescribe(context, uri)
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
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
            val biosKeysFolderInteractionSource = rememberFocusInteractionSource()
            val biosKeysFolderGlow = biosKeysFolderInteractionSource.glowShadow()
            val biosKeysFolderColor = biosKeysFolderInteractionSource.glowColor(MaterialTheme.colorScheme.onSurface)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = biosKeysFolderInteractionSource,
                        indication = null
                    ) {
                        biosKeysFolderPicker.launch(biosKeysFolderUri?.let { Uri.parse(it) })
                    }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "BIOS/Keys folder",
                    style = LocalTextStyle.current.copy(shadow = biosKeysFolderGlow),
                    color = biosKeysFolderColor,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = biosKeysFolderUri?.let { Uri.parse(it).lastPathSegment ?: it } ?: "Not set",
                    style = LocalTextStyle.current.copy(shadow = biosKeysFolderGlow),
                    color = biosKeysFolderColor,
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

        item {
            val controllerMappingInteractionSource = rememberFocusInteractionSource()
            val controllerMappingGlow = controllerMappingInteractionSource.glowShadow()
            val controllerMappingColor = controllerMappingInteractionSource.glowColor(MaterialTheme.colorScheme.onSurface)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = controllerMappingInteractionSource,
                        indication = null,
                        onClick = onOpenControllerMapping
                    )
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Controller Mapping",
                    style = LocalTextStyle.current.copy(shadow = controllerMappingGlow),
                    color = controllerMappingColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            val headerInteractionSource = rememberFocusInteractionSource()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = headerInteractionSource,
                        indication = null
                    ) { systemSettingsExpanded = !systemSettingsExpanded }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    if (systemSettingsExpanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                    contentDescription = null
                )
                Text(
                    "System-Specific Settings",
                    style = LocalTextStyle.current.copy(shadow = headerInteractionSource.glowShadow()),
                    color = headerInteractionSource.glowColor(MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (!systemSettingsExpanded) return@LazyColumn

            items(platforms, key = { it.id }) { platform ->
                val hasBuiltIn = platform.name in BuiltInPlatforms.NAMES
                val expanded = platform.id in expandedPlatformIds
                Column(modifier = Modifier.fillMaxWidth()) {
                    val platformInteractionSource = rememberFocusInteractionSource()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = platformInteractionSource,
                                indication = null
                            ) {
                                expandedPlatformIds = if (expanded) {
                                    expandedPlatformIds - platform.id
                                } else {
                                    expandedPlatformIds + platform.id
                                }
                            }
                            // Extra start padding beyond the header's 20.dp — visually nests each
                            // system "one tab over" under the System-Specific Settings folder.
                            .padding(start = 36.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = platformInteractionSource.glowColor(MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                        Text(
                            text = platform.name,
                            style = LocalTextStyle.current.copy(shadow = platformInteractionSource.glowShadow()),
                            color = platformInteractionSource.glowColor(MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // A non-local `return@items` here (skipping the rest of this Column when
                    // collapsed) crashes Compose's LazyLayout prefetch with a slot-table
                    // corruption (ArrayIndexOutOfBoundsException in IntStack.peek2/GapComposer,
                    // and separately IllegalArgumentException in endReuseFromRoot) once it tries
                    // to precompose/reuse this off-screen item — confirmed on-device, and matches
                    // a known Compose runtime bug class ("early return statements throw internal
                    // Compose error"). Wrapping the conditional content in a plain `if` block
                    // instead avoids the non-local return entirely.
                    if (expanded) {
                        // A further-nested "two tabs over" look for this platform's own settings,
                        // matching the same unfold pattern System-Specific Settings itself uses.
                        if (hasBuiltIn) {
                            val useBuiltInInteractionSource = rememberFocusInteractionSource()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = useBuiltInInteractionSource,
                                        indication = null
                                    ) {
                                        scope.launch {
                                            repository.savePlatform(platform.copy(useBuiltIn = !platform.useBuiltIn))
                                        }
                                    }
                                    // Aligned with the platform row's own text (36.dp icon start +
                                    // 24.dp icon + 8.dp spacing = 68.dp), not just its icon, so
                                    // these read as clearly nested under that platform's label
                                    // rather than under the "System-Specific Settings" folder.
                                    .padding(start = 68.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Use Embedded Emulator",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        shadow = useBuiltInInteractionSource.glowShadow()
                                    ),
                                    color = useBuiltInInteractionSource.glowColor(MaterialTheme.colorScheme.onSurface),
                                    modifier = Modifier.weight(1f)
                                )
                                RetroToggle(
                                    checked = platform.useBuiltIn,
                                    onCheckedChange = { checked ->
                                        scope.launch { repository.savePlatform(platform.copy(useBuiltIn = checked)) }
                                    }
                                )
                            }
                            if (platform.useBuiltIn) {
                                val useGlobalMappingInteractionSource = rememberFocusInteractionSource()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            interactionSource = useGlobalMappingInteractionSource,
                                            indication = null
                                        ) {
                                            scope.launch {
                                                repository.savePlatform(
                                                    platform.copy(useGlobalControllerMapping = !platform.useGlobalControllerMapping)
                                                )
                                            }
                                        }
                                        .padding(start = 68.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Use Global Controller Mapping (recommended)",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            shadow = useGlobalMappingInteractionSource.glowShadow()
                                        ),
                                        color = useGlobalMappingInteractionSource.glowColor(MaterialTheme.colorScheme.onSurface),
                                        modifier = Modifier.weight(1f)
                                    )
                                    RetroToggle(
                                        checked = platform.useGlobalControllerMapping,
                                        onCheckedChange = { checked ->
                                            scope.launch {
                                                repository.savePlatform(platform.copy(useGlobalControllerMapping = checked))
                                            }
                                        }
                                    )
                                }
                                if (!platform.useGlobalControllerMapping) {
                                    PlatformSubRow(
                                        label = "Map Controller",
                                        onClick = { onOpenControllerMappingForPlatform(platform.id) }
                                    )
                                }
                            } else {
                                PlatformAppChoiceRow(platform, onClick = { appPickerFor = platform })
                            }
                        } else {
                            PlatformAppChoiceRow(platform, onClick = { appPickerFor = platform })
                        }

                        PlatformSubRow(
                            label = "Edit Extensions & Launch Settings",
                            onClick = { onEditPlatform(platform.id) }
                        )
                    }
                }
            }

            item {
                if (isAddingNew) {
                    val canonicalMatch = BuiltInPlatforms.canonicalNameOrNull(newPlatformName)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 36.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = newPlatformName,
                            onValueChange = { newPlatformName = it },
                            label = { Text("System name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        RetroToggle(checked = canonicalMatch != null, onCheckedChange = {}, enabled = canonicalMatch != null)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 36.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            isAddingNew = false
                            newPlatformName = ""
                        }) { Text("Cancel") }
                        TextButton(
                            onClick = {
                                val canonical = BuiltInPlatforms.canonicalNameOrNull(newPlatformName)
                                val trimmed = newPlatformName.trim()
                                if (trimmed.isNotEmpty()) {
                                    scope.launch {
                                        repository.savePlatform(
                                            Platform(name = canonical ?: trimmed, useBuiltIn = canonical != null)
                                        )
                                    }
                                }
                                isAddingNew = false
                                newPlatformName = ""
                            },
                            enabled = newPlatformName.isNotBlank()
                        ) { Text("Add") }
                    }
                } else {
                    val addSystemInteractionSource = rememberFocusInteractionSource()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = addSystemInteractionSource,
                                indication = null
                            ) { isAddingNew = true }
                            .padding(start = 36.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(
                            "Add System",
                            style = LocalTextStyle.current.copy(shadow = addSystemInteractionSource.glowShadow()),
                            color = addSystemInteractionSource.glowColor(MaterialTheme.colorScheme.onSurface)
                        )
                    }
                }
            }
        }

    appPickerFor?.let { platform ->
        AppPickerDialog(
            title = "Which app should play ${platform.name} games?",
            onDismiss = { appPickerFor = null },
            onPick = { app: InstalledApp ->
                appPickerFor = null
                scope.launch { repository.savePlatform(platform.copy(launchPackage = app.packageName)) }
            }
        )
    }
}

@Composable
private fun PlatformSubRow(label: String, onClick: () -> Unit) {
    val interactionSource = rememberFocusInteractionSource()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            // Aligned with the platform row's own text — see the matching comment on the
            // "Use Embedded Emulator" row above.
            .padding(start = 68.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = LocalTextStyle.current.copy(shadow = interactionSource.glowShadow()),
            color = interactionSource.glowColor(MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PlatformAppChoiceRow(platform: Platform, onClick: () -> Unit) {
    val interactionSource = rememberFocusInteractionSource()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            // Aligned with the platform row's own text — see the matching comment on the
            // "Use Embedded Emulator" row above.
            .padding(start = 68.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("App", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            text = platform.launchPackage.ifBlank { "Choose app" },
            style = LocalTextStyle.current.copy(shadow = interactionSource.glowShadow()),
            color = interactionSource.glowColor(MaterialTheme.colorScheme.onSurface)
        )
    }
}
