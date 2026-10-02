package com.noryan.romrunner.ui.platforms

import android.net.Uri
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.noryan.romrunner.data.launch.EmulatorLauncher
import com.noryan.romrunner.data.repository.LibraryRepository
import com.noryan.romrunner.ui.components.RetroToggle
import com.noryan.romrunner.ui.components.glowColor
import com.noryan.romrunner.ui.components.glowShadow
import com.noryan.romrunner.ui.components.rememberFocusInteractionSource

private val InstalledGreen = Color(0xFF6BCB77)
private val NotInstalledRed = Color(0xFFFF6B6B)

/** System name, the app that plays it best, and that app's package name (for the installed check). */
private val RECOMMENDED_EMULATORS = listOf(
    Triple("3DS", "Azahar", "org.azahar_emu.azahar"),
    Triple("PS2", "ARMSX2", "com.armsx2"),
    Triple("Switch", "Eden", "dev.eden.eden_emulator"),
    Triple("Nintendo DS", "MelonDS", "me.magnum.melondualds")
)

/**
 * The Settings tab's content on RomRunner's home screen (see LibraryScreen). Deliberately has no
 * Scaffold/TopAppBar of its own — it's embedded directly under the GAMES/SETTINGS tab heading row
 * rather than being a separate navigation destination.
 */
@Composable
fun PlatformsContent(
    repository: LibraryRepository,
    onDualScreenSupportChanged: () -> Unit
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
            Text(
                "Recommended Emulators",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)
            )
        }

        RECOMMENDED_EMULATORS.forEach { (systemName, appLabel, packageName) ->
            item(key = packageName) {
                val isInstalled = remember(packageName) { EmulatorLauncher.isPackageInstalled(context, packageName) }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        systemName,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = buildAnnotatedString {
                            append("$appLabel — ")
                            withStyle(SpanStyle(color = if (isInstalled) InstalledGreen else NotInstalledRed)) {
                                append(if (isInstalled) "Installed" else "Not Installed")
                            }
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}
