package com.noryan.romrunner.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** One-time prompt shown after the first ROMs folder scan, offering to install the emulators the library needs. */
@Composable
fun EmulatorSetupDialog(
    emulatorLabels: List<String>,
    onYes: () -> Unit,
    onNotNow: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onNotNow,
        title = { Text("Download emulators?") },
        text = {
            Text(
                "Would you like to download the needed applications for emulating the games found in your library?\n\n" +
                    emulatorLabels.joinToString(", ")
            )
        },
        confirmButton = { TextButton(onClick = onYes) { Text("Yes") } },
        dismissButton = { TextButton(onClick = onNotNow) { Text("Not now") } }
    )
}
