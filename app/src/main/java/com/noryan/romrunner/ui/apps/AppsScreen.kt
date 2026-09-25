package com.noryan.romrunner.ui.apps

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.noryan.romrunner.data.launch.InstalledApps
import com.noryan.romrunner.ui.components.glowColor
import com.noryan.romrunner.ui.components.glowShadow
import com.noryan.romrunner.ui.components.rememberFocusInteractionSource

/**
 * The Apps tab's content on RomRunner's home screen (see LibraryScreen) — every launchable
 * installed app (same definition Android's own app drawer uses: has its own launcher-category
 * activity, which naturally excludes hidden/disabled ones), so this doubles as a general launcher
 * on a handheld with no other easy way to reach non-game apps. Deliberately has no
 * Scaffold/TopAppBar of its own, matching PlatformsContent — embedded directly under the
 * GAMES/SETTINGS/APPS tab heading row rather than being a separate navigation destination.
 */
@Composable
fun AppsContent() {
    val context = LocalContext.current
    val apps = remember {
        InstalledApps.listLaunchable(context).filter {
            it.packageName != context.packageName && "launcher" !in it.label.lowercase()
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(apps, key = { it.packageName }) { app ->
            val interactionSource = rememberFocusInteractionSource()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(interactionSource = interactionSource, indication = null) {
                        val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                        if (intent != null) {
                            context.startActivity(intent)
                        } else {
                            Toast.makeText(context, "Couldn't open ${app.label}.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleMedium.copy(shadow = interactionSource.glowShadow()),
                    color = interactionSource.glowColor(MaterialTheme.colorScheme.onSurface),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
