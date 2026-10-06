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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * "Which emulator plays this system?" — the one RomRunner picked (when there is one), the app currently chosen if
 * that's a different one, and a way to pick any other installed app. The current choice is marked with a filled dot.
 */
@Composable
fun EmulatorChoiceDialog(
    systemLabel: String,
    recommendedLabel: String?,
    currentLabel: String,
    currentIsRecommended: Boolean,
    onUseRecommended: () -> Unit,
    onChooseOther: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Emulator for $systemLabel") },
        text = {
            Column {
                if (recommendedLabel != null) {
                    ChoiceRow("$recommendedLabel (recommended)", selected = currentIsRecommended, onClick = onUseRecommended)
                }
                if (!currentIsRecommended && currentLabel.isNotBlank()) {
                    ChoiceRow("$currentLabel (your choice)", selected = true, onClick = onDismiss)
                }
                ChoiceRow("Choose another app…", selected = false, onClick = onChooseOther)
                Text(
                    "Other apps are handed the game file directly, so one that needs special handling may not open it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = rememberFocusInteractionSource()
    val glow = interaction.glowShadow()
    val color = interaction.glowColor(MaterialTheme.colorScheme.onSurface)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = LocalTextStyle.current.copy(shadow = glow), color = color, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier.size(12.dp).background(if (selected) FocusGlowColor else Color.Transparent, CircleShape)
        )
    }
}
