package com.noryan.romrunner.ui.library

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
    onEdit: () -> Unit
) {
    val context = LocalContext.current
    val apps by produceState(emptyList<InstalledApp>(), context) {
        value = withContext(Dispatchers.Default) { InstalledApps.listLaunchable(context) }
    }
    val tabGames = games.filter { it.fileUri in tab.gameUris }
    val tabApps = apps.filter { it.packageName in tab.appPackages }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            val interaction = rememberFocusInteractionSource()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(interactionSource = interaction, indication = null, onClick = onEdit)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    "EDIT MENU",
                    style = MaterialTheme.typography.titleMedium.copy(shadow = interaction.glowShadow()),
                    color = interaction.glowColor(MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }
        }
        if (tabGames.isEmpty() && tabApps.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Nothing in this menu yet. Choose Edit Menu to add games or apps.")
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
    }
}
