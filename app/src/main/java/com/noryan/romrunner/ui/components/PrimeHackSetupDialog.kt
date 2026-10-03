package com.noryan.romrunner.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** Offered once after PrimeHack is installed: load the controller profile for this handheld. */
@Composable
fun PrimeHackSetupDialog(
    profileLabel: String,
    onYes: () -> Unit,
    onNotNow: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onNotNow,
        title = { Text("Set up PrimeHack controls?") },
        text = {
            Text(
                "RomRunner can load the PrimeHack controller profile for your $profileLabel.\n\n" +
                    "Android will ask you to pick PrimeHack's folder once. In the file picker, open the " +
                    "menu (top left), choose \"Prime Hack\", then tap \"Use this folder\" and Allow."
            )
        },
        confirmButton = { TextButton(onClick = onYes) { Text("Set up") } },
        dismissButton = { TextButton(onClick = onNotNow) { Text("Not now") } }
    )
}
