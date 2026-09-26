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
import androidx.compose.runtime.collectAsState
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
import com.noryan.romrunner.data.embedded.BuiltInPlatforms
import com.noryan.romrunner.data.launch.BiosKeysImporter
import com.noryan.romrunner.data.model.Platform
import com.noryan.romrunner.data.repository.LibraryRepository
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
    onOpenControllerMapping: () -> Unit,
    onOpenPlatformSettings: (Long) -> Unit
) {
    val context = LocalContext.current
    val platforms by repository.observePlatforms().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var rootFolderUri by remember { mutableStateOf(repository.getRootFolderUri()) }
    LaunchedEffect(Unit) { rootFolderUri = repository.getRootFolderUri() }

    var biosKeysFolderUri by remember { mutableStateOf(repository.getBiosKeysFolderUri()) }
    LaunchedEffect(Unit) { biosKeysFolderUri = repository.getBiosKeysFolderUri() }

    var killBackgroundAppsOnLaunch by remember { mutableStateOf(repository.getKillBackgroundAppsOnLaunch()) }

    var isAddingNew by remember { mutableStateOf(false) }
    var newPlatformName by remember { mutableStateOf("") }
    var systemSettingsExpanded by remember { mutableStateOf(false) }

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

    Column(modifier = Modifier.fillMaxSize()) {
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

        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
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
                val platformInteractionSource = rememberFocusInteractionSource()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = platformInteractionSource,
                            indication = null
                        ) { onOpenPlatformSettings(platform.id) }
                        // Extra start padding beyond the header's 20.dp — visually nests each
                        // system "one tab over" under the System-Specific Settings folder.
                        .padding(start = 36.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = platform.name,
                        style = LocalTextStyle.current.copy(shadow = platformInteractionSource.glowShadow()),
                        color = platformInteractionSource.glowColor(MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = platformInteractionSource.glowColor(MaterialTheme.colorScheme.onSurfaceVariant)
                    )
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
    }
}
