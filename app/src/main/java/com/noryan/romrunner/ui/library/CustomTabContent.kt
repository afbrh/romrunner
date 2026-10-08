package com.noryan.romrunner.ui.library

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.noryan.romrunner.data.launch.InstalledApp
import com.noryan.romrunner.data.launch.InstalledApps
import com.noryan.romrunner.data.model.CustomTab
import com.noryan.romrunner.data.model.Game
import com.noryan.romrunner.ui.components.GameRow
import com.noryan.romrunner.ui.components.glowColor
import com.noryan.romrunner.ui.components.glowShadow
import com.noryan.romrunner.ui.components.rememberFocusInteractionSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The page for one of the user's own menus: the games and apps they put in it, with an Edit line on top. Games open and
 * long-press exactly as on the Games tab; apps open as on the Apps tab. Anything that's gone since (a deleted game, an
 * uninstalled app) is just left out.
 */
@Composable
fun CustomTabContent(
    tab: CustomTab,
    games: List<Game>,
    platformNameFor: (Game) -> String,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val apps by produceState(emptyList<InstalledApp>(), context) {
        value = withContext(Dispatchers.Default) { InstalledApps.listLaunchable(context) }
    }
    var askDelete by remember { mutableStateOf(false) }
    val tabGames = games.filter { it.fileUri in tab.gameUris }
    val tabApps = apps.filter { it.packageName in tab.appPackages }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (tabGames.isEmpty() && tabApps.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Nothing in this menu.")
                }
            }
        }
        items(tabGames, key = { "g${it.id}" }) { game ->
            GameRow(game = game, platformName = platformNameFor(game), onClick = onGameClick, onLongClick = onGameLongClick)
        }
        items(tabApps, key = { "a${it.packageName}" }) { app ->
            val interaction = rememberFocusInteractionSource()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(interactionSource = interaction, indication = null) {
                        val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                        if (intent != null) context.startActivity(intent)
                        else Toast.makeText(context, "Couldn't open ${app.label}.", Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    app.label,
                    style = MaterialTheme.typography.titleMedium.copy(shadow = interaction.glowShadow()),
                    color = interaction.glowColor(MaterialTheme.colorScheme.onSurface),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // A "-" at the bottom right deletes the menu, after asking.
        item {
            val interaction = rememberFocusInteractionSource()
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.End) {
                Text(
                    "-",
                    style = MaterialTheme.typography.headlineSmall.copy(shadow = interaction.glowShadow()),
                    color = interaction.glowColor(MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier
                        .clickable(interactionSource = interaction, indication = null) { askDelete = true }
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }

    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text("Delete this menu?") },
            text = { Text("\"${tab.name}\" will be removed. The games and apps in it aren't touched.") },
            confirmButton = {
                Column {
                    ConfirmLine("Delete") {
                        askDelete = false
                        onDelete()
                    }
                    ConfirmLine("Keep it") { askDelete = false }
                }
            }
        )
    }
}

/** A white line in the delete question that turns orange when selected, matching the other dialogs' options. */
@Composable
private fun ConfirmLine(text: String, onClick: () -> Unit) {
    val interaction = rememberFocusInteractionSource()
    Text(
        text,
        style = MaterialTheme.typography.titleMedium.copy(shadow = interaction.glowShadow()),
        color = interaction.glowColor(MaterialTheme.colorScheme.onSurface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 12.dp)
    )
}
