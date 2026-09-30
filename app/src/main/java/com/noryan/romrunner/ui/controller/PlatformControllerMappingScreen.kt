package com.noryan.romrunner.ui.controller

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.noryan.romrunner.data.input.ControllerMappingSerializer
import com.noryan.romrunner.data.model.Platform
import com.noryan.romrunner.data.repository.LibraryRepository
import kotlinx.coroutines.launch

/**
 * Loads [platformId]'s own Platform row, then renders [ControllerMappingScreen] scoped to that one
 * platform's [Platform.controllerMappingJson] override instead of RomRunner's global mapping —
 * the "Map Controller" row shown under a platform's "Use global controller mapping" toggle when
 * it's off (see PlatformsScreen.kt). Starts from the current effective mapping (this platform's
 * override if it already has one, otherwise the global mapping) so a first-time visit shows
 * sensible values to edit rather than a blank slate.
 */
@Composable
fun PlatformControllerMappingScreen(repository: LibraryRepository, platformId: Long, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var platform by remember { mutableStateOf<Platform?>(null) }

    LaunchedEffect(platformId) {
        platform = repository.getPlatform(platformId)
    }

    val current = platform ?: return
    ControllerMappingScreen(
        title = "Controller Mapping — ${current.name}",
        initialMapping = remember(current.id) { repository.getEffectiveControllerMapping(current) },
        onSave = { mapping ->
            val json = ControllerMappingSerializer.toJson(mapping)
            scope.launch {
                val latest = repository.getPlatform(platformId) ?: current
                repository.savePlatform(latest.copy(controllerMappingJson = json))
                platform = repository.getPlatform(platformId)
            }
        },
        onDone = onDone,
        showDuskLightNote = false
    )
}
