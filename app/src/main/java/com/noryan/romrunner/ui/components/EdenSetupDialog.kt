package com.noryan.romrunner.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** Offered once after Eden is installed: install a graphics driver that stops Switch games crashing. */
@Composable
fun EdenSetupDialog(
    onYes: () -> Unit,
    onNotNow: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onNotNow,
        title = { Text("Set up Eden graphics?") },
        text = {
            Text(
                "Eden's default graphics driver crashes Switch games on this device. RomRunner can " +
                    "install a driver that fixes it.\n\n" +
                    "Android will ask you to pick Eden's folder once. In the file picker, open the " +
                    "menu (top left), choose \"Eden\", then tap \"Use this folder\" and Allow."
            )
        },
        confirmButton = { TextButton(onClick = onYes) { Text("Set up") } },
        dismissButton = { TextButton(onClick = onNotNow) { Text("Not now") } }
    )
}
