package com.noryan.romrunner.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shown after RomRunner has changed a setting that [appName] only reads when its process starts. RomRunner's
 * own folder access to the app is what starts that process in the background, so it has already read its
 * settings by the time they're written. Android doesn't let a normal app stop another app's process
 * (confirmed on-device: every kill is refused), so the user has to.
 */
@Composable
fun ForceStopDialog(
    appName: String,
    onOpenAppInfo: () -> Unit,
    onDone: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("One last step") },
        text = {
            Text(
                "RomRunner has finished setting up $appName. It has to be fully restarted once to start using the new settings.\n\n" +
                    "Tap \"Open $appName settings\", then tap \"Force stop\" and OK."
            )
        },
        confirmButton = { TextButton(onClick = onOpenAppInfo) { Text("Open $appName settings") } },
        dismissButton = { TextButton(onClick = onDone) { Text("Done") } }
    )
}

