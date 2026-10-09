package com.noryan.romrunner.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.noryan.romrunner.data.model.CustomTab
import com.noryan.romrunner.ui.components.glowColor
import com.noryan.romrunner.ui.components.glowShadow
import com.noryan.romrunner.ui.components.rememberFocusInteractionSource

/**
 * Settings > Lists, tabbed down: every list on the home screen with a "-" beside it to remove it (or a "+" to bring back Games or Apps
 * once removed), then "+ Add a List". The lists are the tabs in the bar: Games and Apps, which everyone starts with, and the
 * user's own. Removing one only removes the list, never the games or apps in it.
 */
@Composable
fun ListsSettingsContent(
    hiddenDefaults: Set<String>,
    customTabs: List<CustomTab>,
    onRemoveDefault: (String) -> Unit,
    onRestoreDefault: (String) -> Unit,
    onRemoveCustom: (CustomTab) -> Unit,
    onAdd: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 52.dp)) {
        listOf("GAMES" to "Games", "APPS" to "Apps").forEach { (key, label) ->
            if (key in hiddenDefaults) ListLine("$label (removed)", "+") { onRestoreDefault(key) }
            else ListLine(label, "-") { onRemoveDefault(key) }
        }
        customTabs.forEach { tab -> ListLine(tab.name, "-") { onRemoveCustom(tab) } }
        ListLine("+ Add a List", action = null, onClick = onAdd)
    }
}

/** One line: the list's name, and right beside it what pressing it does ("-" removes it, "+" adds or brings it back). Glows with the controller. */
@Composable
private fun ListLine(label: String, action: String?, onClick: () -> Unit) {
    val interaction = rememberFocusInteractionSource()
    val glow = interaction.glowShadow()
    val color = interaction.glowColor(MaterialTheme.colorScheme.onSurface)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(end = 20.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge.copy(shadow = glow), color = color)
        if (action != null) {
            Spacer(Modifier.width(12.dp))
            Text(action, style = MaterialTheme.typography.bodyLarge.copy(shadow = glow), color = color)
        }
    }
}
