package com.noryan.romrunner.ui.platforms

import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.noryan.romrunner.data.launch.EmulatorLauncher
import com.noryan.romrunner.data.launch.EdenGpuDriver
import com.noryan.romrunner.data.launch.PrimeHackControls
import com.noryan.romrunner.data.launch.RECOMMENDED_EMULATORS
import com.noryan.romrunner.data.model.Game
import com.noryan.romrunner.data.model.Platform
import com.noryan.romrunner.data.repository.LibraryRepository
import com.noryan.romrunner.ui.components.RetroToggle
import com.noryan.romrunner.ui.components.glowColor
import com.noryan.romrunner.ui.components.glowShadow
import com.noryan.romrunner.ui.components.rememberFocusInteractionSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Settings tab's content on RomRunner's home screen (see LibraryScreen): the general, app-wide
 * settings. System-specific ones are on the Systems tab ([SystemsContent]). Deliberately has no
 * Scaffold/TopAppBar of its own — it's embedded directly under the tab heading row rather than being
 * a separate navigation destination.
 */
@Composable
fun PlatformsContent(
    repository: LibraryRepository,
    onDualScreenSupportChanged: () -> Unit,
    onRomsFolderChanged: () -> Unit,
    /** The systems (see SystemsContent), listed right in Settings, each with its options open. */
    systemsContent: @Composable () -> Unit,
    /** The Lists settings (see ListsSettingsContent), always shown under the "Lists" heading. */
    listsContent: @Composable () -> Unit
) {
    val context = LocalContext.current

    var rootFolderUri by remember { mutableStateOf(repository.getRootFolderUri()) }
    LaunchedEffect(Unit) { rootFolderUri = repository.getRootFolderUri() }

    var killBackgroundAppsOnLaunch by remember { mutableStateOf(repository.getKillBackgroundAppsOnLaunch()) }
    var dualScreenSupportEnabled by remember { mutableStateOf(repository.getDualScreenSupportEnabled()) }

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
            // Unlike the first-run "Choose folder" prompt on the Games tab (which goes through
            // LibraryViewModel.setRootFolder and rescans immediately), changing the folder from
            // here used to just update the stored Uri with no rescan at all — the Games list
            // wouldn't reflect the new folder until the user separately pulled to refresh.
            onRomsFolderChanged()
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
                    "Roms/BIOS",
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


        // Lists: add and remove the lists (tabs) on the home screen. Always open; the heading is just a label.
        item {
            Text(
                "Lists",
                style = LocalTextStyle.current,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)
            )
        }
        item { listsContent() }

        // Every system, at the bottom of Settings with its options already open (each can still be folded shut).
        item { systemsContent() }

    }
}
