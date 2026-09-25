package com.noryan.romrunner.ui.platforms

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.noryan.romrunner.data.launch.EmulatorPreset
import com.noryan.romrunner.data.launch.EmulatorPresets
import com.noryan.romrunner.data.model.Platform
import com.noryan.romrunner.data.repository.LibraryRepository
import com.noryan.romrunner.data.scanner.RomScanner
import com.noryan.romrunner.ui.components.RetroToggle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformEditScreen(
    repository: LibraryRepository,
    platformId: Long,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isNew = platformId <= 0

    var name by remember { mutableStateOf("") }
    var extensions by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var activityName by remember { mutableStateOf("") }
    var action by remember { mutableStateOf(Intent.ACTION_VIEW) }
    var mimeType by remember { mutableStateOf("application/octet-stream") }
    var passAsIntentData by remember { mutableStateOf(true) }
    var extrasText by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(false) }
    var presetExpanded by remember { mutableStateOf(false) }
    var presetNote by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(isNew) }
    var useBuiltIn by remember { mutableStateOf(true) }
    var sortOrder by remember { mutableStateOf(0) }
    var loadedPlatform by remember { mutableStateOf<Platform?>(null) }

    LaunchedEffect(platformId) {
        if (!isNew) {
            repository.getPlatform(platformId)?.let { p ->
                name = p.name
                extensions = p.extensions.joinToString(", ")
                packageName = p.launchPackage
                activityName = p.launchActivity
                action = p.launchAction
                mimeType = p.mimeType
                passAsIntentData = p.passAsIntentData
                extrasText = p.extras.entries.joinToString("\n") { "${it.key}=${it.value}" }
                useBuiltIn = p.useBuiltIn
                sortOrder = p.sortOrder
                loadedPlatform = p
            }
            loaded = true
        }
    }

    fun applyPreset(preset: EmulatorPreset) {
        packageName = preset.packageName
        activityName = preset.activity
        action = preset.action
        mimeType = preset.mimeType
        passAsIntentData = preset.passAsIntentData
        extrasText = preset.extras.entries.joinToString("\n") { "${it.key}=${it.value}" }
        presetNote = preset.note
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Add platform" else "Edit platform") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        if (!loaded) return@Scaffold
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Platform name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = extensions,
                onValueChange = { extensions = it },
                label = { Text("File extensions (comma separated, e.g. gba, gbc)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))
            Text("Emulator", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Box {
                OutlinedButton(onClick = { presetExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Choose emulator preset…")
                }
                DropdownMenu(expanded = presetExpanded, onDismissRequest = { presetExpanded = false }) {
                    EmulatorPresets.ALL.forEach { preset ->
                        DropdownMenuItem(
                            text = { Text(preset.label) },
                            onClick = {
                                applyPreset(preset)
                                presetExpanded = false
                            }
                        )
                    }
                }
            }
            if (presetNote.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(presetNote, style = MaterialTheme.typography.labelSmall)
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = packageName,
                onValueChange = { packageName = it },
                label = { Text("Emulator package name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            TextButton(onClick = { showAdvanced = !showAdvanced }) {
                Text(if (showAdvanced) "Hide advanced options" else "Show advanced options")
            }

            if (showAdvanced) {
                OutlinedTextField(
                    value = activityName,
                    onValueChange = { activityName = it },
                    label = { Text("Activity (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = action,
                    onValueChange = { action = it },
                    label = { Text("Intent action") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = mimeType,
                    onValueChange = { mimeType = it },
                    label = { Text("MIME type") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RetroToggle(checked = passAsIntentData, onCheckedChange = { passAsIntentData = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pass ROM as intent data (uncheck if your emulator only reads the extras below)")
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = extrasText,
                    onValueChange = { extrasText = it },
                    label = { Text("Extra intent params, one per line: KEY=VALUE") },
                    supportingText = { Text("Use {FILE_PATH} or {FILE_URI} as placeholders for the ROM location") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    val extrasMap = extrasText.lines()
                        .mapNotNull { line ->
                            val idx = line.indexOf('=')
                            if (idx <= 0) null else line.substring(0, idx).trim() to line.substring(idx + 1).trim()
                        }
                        .toMap()

                    val platform = Platform(
                        id = if (isNew) 0 else platformId,
                        name = name.trim().ifBlank { "Unnamed platform" },
                        extensionsCsv = Platform.extensionsToCsv(extensions.split(",")),
                        launchPackage = packageName.trim(),
                        launchActivity = activityName.trim(),
                        launchAction = action.trim().ifBlank { Intent.ACTION_VIEW },
                        mimeType = mimeType.trim().ifBlank { "application/octet-stream" },
                        passAsIntentData = passAsIntentData,
                        extrasRaw = Platform.extrasToRaw(extrasMap),
                        sortOrder = sortOrder,
                        useBuiltIn = useBuiltIn
                    )

                    scope.launch {
                        repository.savePlatform(platform)
                        // Re-scan the whole root against every platform in case new/changed
                        // extensions now match files that were previously skipped.
                        val rootUri = repository.getRootFolderUri()
                        if (!rootUri.isNullOrBlank()) {
                            val allPlatforms = repository.getAllPlatformsOnce()
                            val existing = repository.allExistingFileUris()
                            val found = RomScanner.scanRoot(context, Uri.parse(rootUri), allPlatforms, existing)
                            if (found.isNotEmpty()) repository.addGames(found)
                        }
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank()
            ) {
                Text("Save platform")
            }

            if (!isNew) {
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = {
                        loadedPlatform?.let { platform ->
                            scope.launch {
                                repository.deletePlatform(platform)
                                onDone()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete platform", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
