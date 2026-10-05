package com.noryan.romrunner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.noryan.romrunner.ui.theme.AccentColor

/** The Accent Color picker: each choice with a swatch of its color; the one in use is marked. Controller-navigable like the other lists. */
@Composable
fun AccentColorDialog(selected: AccentColor, onPick: (AccentColor) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Accent Color") },
        text = {
            Column {
                AccentColor.entries.forEach { option ->
                    val interaction = rememberFocusInteractionSource()
                    val glow = interaction.glowShadow()
                    val textColor = interaction.glowColor(MaterialTheme.colorScheme.onSurface)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(interactionSource = interaction, indication = null) { onPick(option) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(24.dp).background(option.color, CircleShape))
                        Text(
                            text = option.label,
                            style = LocalTextStyle.current.copy(shadow = glow),
                            color = textColor,
                            modifier = Modifier.weight(1f).padding(start = 16.dp)
                        )
                        if (option == selected) Text("✓", style = TextStyle(shadow = glow), color = textColor)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
