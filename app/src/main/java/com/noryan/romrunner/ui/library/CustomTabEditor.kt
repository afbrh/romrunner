package com.noryan.romrunner.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.noryan.romrunner.data.launch.InstalledApp
import com.noryan.romrunner.data.launch.InstalledApps
import com.noryan.romrunner.data.model.CustomTab
import com.noryan.romrunner.data.model.Game
import com.noryan.romrunner.ui.components.glowColor
import com.noryan.romrunner.ui.components.glowShadow
import com.noryan.romrunner.ui.components.rememberFocusInteractionSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

private const val MaxNameLength = 24

/**
 * Making or editing one of the user's own menus (the "+" in the tab bar): a name, then ticking the games and apps that
 * should be in it. Full screen over the home screen; Save keeps it, Cancel drops the changes, Delete (when editing) removes the menu.
 */
@Composable
fun CustomTabEditor(
    initial: CustomTab?,
    games: List<Game>,
    onSave: (CustomTab) -> Unit,
    onDelete: (() -> Unit)?,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var search by remember { mutableStateOf("") }
    var pickedGames by remember { mutableStateOf(initial?.gameUris.orEmpty().toSet()) }
    var pickedApps by remember { mutableStateOf(initial?.appPackages.orEmpty().toSet()) }

    val apps by produceState(emptyList<InstalledApp>(), context) {
        value = withContext(Dispatchers.Default) {
            InstalledApps.listLaunchable(context).filter { it.packageName != context.packageName && "launcher" !in it.label.lowercase() }
        }
    }
    val query = search.trim().lowercase()
    val shownGames = if (query.isEmpty()) games else games.filter { query in it.title.lowercase() }
    val shownApps = if (query.isEmpty()) apps else apps.filter { query in it.label.lowercase() }
    val canSave = name.isNotBlank()

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (initial == null) "NEW MENU" else "EDIT MENU",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f)
                    )
                    if (onDelete != null) {
                        EditorAction("DELETE", enabled = true, onClick = onDelete)
                        Spacer(Modifier.width(24.dp))
                    }
                    EditorAction("CANCEL", enabled = true, onClick = onCancel)
                    Spacer(Modifier.width(24.dp))
                    EditorAction("SAVE", enabled = canSave) {
                        onSave(
                            CustomTab(
                                id = initial?.id ?: UUID.randomUUID().toString(),
                                name = name.trim(),
                                gameUris = games.map { it.fileUri }.filter { it in pickedGames },
                                appPackages = apps.map { it.packageName }.filter { it in pickedApps }
                            )
                        )
                    }
                }
                Spacer(Modifier.size(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(MaxNameLength) },
                        label = { Text("Menu name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        label = { Text("Search") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.size(8.dp))
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item { SectionTitle("GAMES  (${pickedGames.size} chosen)") }
                    if (shownGames.isEmpty()) item { Note("No games to show.") }
                    items(shownGames, key = { "g${it.id}" }) { game ->
                        CheckLine(game.title, game.fileUri in pickedGames) {
                            pickedGames = if (game.fileUri in pickedGames) pickedGames - game.fileUri else pickedGames + game.fileUri
                        }
                    }
                    item { SectionTitle("APPS  (${pickedApps.size} chosen)") }
                    if (shownApps.isEmpty()) item { Note(if (apps.isEmpty()) "Loading apps…" else "No apps to show.") }
                    items(shownApps, key = { "a${it.packageName}" }) { app ->
                        CheckLine(app.label, app.packageName in pickedApps) {
                            pickedApps = if (app.packageName in pickedApps) pickedApps - app.packageName else pickedApps + app.packageName
                        }
                    }
                }
            }
        }
    }
}

/** A text action in the editor's top row; glows when the controller is on it, grayed out when [enabled] is false. */
@Composable
private fun EditorAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    val interaction = rememberFocusInteractionSource()
    val base = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
    Text(
        text,
        style = MaterialTheme.typography.titleMedium.copy(shadow = if (enabled) interaction.glowShadow() else null),
        color = if (enabled) interaction.glowColor(base) else base,
        modifier = Modifier
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp)
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
    )
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
}

/** One tickable line: a box that fills when chosen, then the name. Glows when the controller is on it. */
@Composable
private fun CheckLine(label: String, checked: Boolean, onToggle: () -> Unit) {
    val interaction = rememberFocusInteractionSource()
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interaction, indication = null, onClick = onToggle)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .border(2.dp, accent)
                .background(if (checked) accent else Color.Transparent)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleMedium.copy(shadow = interaction.glowShadow()),
            color = interaction.glowColor(MaterialTheme.colorScheme.onSurface),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
