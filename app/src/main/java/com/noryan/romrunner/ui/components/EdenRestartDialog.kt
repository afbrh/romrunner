package com.noryan.romrunner.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Shown after Eden's graphics driver is installed. Eden chooses its Vulkan driver once, when its
 * process starts, and RomRunner's own folder access to Eden starts that process in the background —
 * so it has already made its choice by the time the driver is written. Android doesn't let a normal
 * app stop another app's process (confirmed on-device: every kill is refused), so the user has to.
 */
@Composable
fun EdenRestartDialog(
    onOpenAppInfo: () -> Unit,
    onDone: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("One last step") },
        text = {
            Text(
                "Eden's graphics driver is installed. Eden has to be fully restarted once to start using it.\n\n" +
                    "Tap \"Open Eden settings\", then tap \"Force stop\" and OK. After that, Switch games " +
                    "should run without crashing."
            )
        },
        confirmButton = { TextButton(onClick = onOpenAppInfo) { Text("Open Eden settings") } },
        dismissButton = { TextButton(onClick = onDone) { Text("Done") } }
    )
}
