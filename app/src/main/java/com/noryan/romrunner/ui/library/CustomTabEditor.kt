package com.noryan.romrunner.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
 * should be in it. Full screen over the home screen; Save keeps it; Back (B) closes it without saving.
 */
@Composable
fun CustomTabEditor(
    initial: CustomTab?,
    games: List<Game>,
    /** Whether a menu with this name would still fit in the tab bar (which doesn't scroll). */
    nameFits: (String) -> Boolean,
    onSave: (CustomTab) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var editingName by remember { mutableStateOf(false) }
    var pickedGames by remember { mutableStateOf(initial?.gameUris.orEmpty().toSet()) }
    var pickedApps by remember { mutableStateOf(initial?.appPackages.orEmpty().toSet()) }

    val apps by produceState(emptyList<InstalledApp>(), context) {
        value = withContext(Dispatchers.Default) {
            InstalledApps.listLaunchable(context).filter { it.packageName != context.packageName && "launcher" !in it.label.lowercase() }
        }
    }
    val shownGames = games
    val shownApps = apps
    val nameTooLong = name.isNotBlank() && !nameFits(name.trim())
    val canSave = name.isNotBlank() && !nameTooLong

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (initial == null) "NEW MENU" else "EDIT MENU",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f)
                    )
                    // No Cancel button: Back (B on the controller) closes the editor without saving.
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
                // The keyboard stays closed until the name is tapped, or A / confirm is pressed on it (a text field that holds focus
                // would pop it open as soon as the editor appears, or as the controller passes over it). Until then it's a plain line.
                if (editingName) {
                    val focusRequester = remember { FocusRequester() }
                    val keyboard = LocalSoftwareKeyboardController.current
                    var hadFocus by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(MaxNameLength) },
                        label = { Text("Menu name") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { editingName = false }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged {
                                if (it.isFocused) hadFocus = true
                                else if (hadFocus) editingName = false
                            }
                    )
                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                        keyboard?.show()
                    }
                } else {
                    NameLine(name) { editingName = true }
                }
                if (nameTooLong) {
                    Text(
                        "This name doesn't fit in the tab bar. Shorten it, or delete another menu.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFFFF6B6B),
                        modifier = Modifier.padding(top = 6.dp)
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

/** The menu name as a plain line that looks like the field: tap it (or A / confirm on it) to start typing. */
@Composable
private fun NameLine(name: String, onClick: () -> Unit) {
    val interaction = rememberFocusInteractionSource()
    val focused by interaction.collectIsFocusedAsState()
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(if (focused) 2.dp else 1.dp, if (focused) accent else MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(4.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text("Menu name", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            name.ifBlank { "Tap to name this menu" },
            style = MaterialTheme.typography.titleMedium.copy(shadow = interaction.glowShadow()),
            color = if (name.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else interaction.glowColor(MaterialTheme.colorScheme.onSurface)
        )
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
