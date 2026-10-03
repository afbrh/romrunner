package com.noryan.romrunner.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** Offered once after PrimeHack is installed: load the controller profiles and graphics defaults. */
@Composable
fun PrimeHackSetupDialog(
    /** The handheld whose profile gets made active, or null on a device we do not recognise. */
    profileLabel: String?,
    onYes: () -> Unit,
    onNotNow: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onNotNow,
        title = { Text("Set up PrimeHack?") },
        text = {
            Text(
                "RomRunner can add the Odin and Retroid controller profiles to PrimeHack" +
                    (if (profileLabel != null) " (using the one for your $profileLabel)" else "") +
                    " and apply the graphics settings that run best on this device.\n\n" +
                    "Android will ask you to pick PrimeHack's folder once. In the file picker, open the " +
                    "menu (top left), choose \"Prime Hack\", then tap \"Use this folder\" and Allow."
            )
        },
        confirmButton = { TextButton(onClick = onYes) { Text("Set up") } },
        dismissButton = { TextButton(onClick = onNotNow) { Text("Not now") } }
    )
}
