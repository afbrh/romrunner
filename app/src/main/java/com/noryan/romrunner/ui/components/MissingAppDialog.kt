package com.noryan.romrunner.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun MissingAppDialog(
    appLabel: String,
    gameTitle: String,
    onInstall: () -> Unit,
    onNotNow: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$appLabel isn't installed") },
        text = { Text("$gameTitle needs $appLabel to run. Would you like to install it now?") },
        confirmButton = {
            TextButton(onClick = onInstall) { Text("Install") }
        },
        dismissButton = {
            TextButton(onClick = onNotNow) { Text("Not now") }
        }
    )
}
