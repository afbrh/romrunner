package com.noryan.romrunner.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A "remove this list?" question with two white options that turn orange when selected, like the other dialogs. */
@Composable
fun ConfirmRemoveDialog(title: String, message: String, confirmLabel: String, keepLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Column {
                ConfirmLine(confirmLabel, onConfirm)
                ConfirmLine(keepLabel, onDismiss)
            }
        }
    )
}

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
