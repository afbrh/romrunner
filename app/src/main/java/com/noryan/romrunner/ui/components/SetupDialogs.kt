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
 * Walks the user through the one-time folder permission that PrimeHack and Eden need before RomRunner
 * can write their settings. Android hides another app's files, and the only way in is its own file
 * picker, which RomRunner can't drive (it ignores where we ask it to open, and the "Use this folder" /
 * "Allow" screens are the system's) — so the steps are spelled out here instead.
 *
 * @param appName the app, exactly as its entry is labelled in the file picker's menu.
 * @param why one or two sentences on what this setup does for the user.
 */
@Composable
fun FolderAccessDialog(
    title: String,
    why: String,
    appName: String,
    onChooseFolder: () -> Unit,
    onNotNow: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onNotNow,
        title = { Text(title) },
        text = {
            Column {
                Text(why)
                Spacer(Modifier.height(12.dp))
                Text("Android needs you to give permission yourself, once. Three steps:")
                Spacer(Modifier.height(8.dp))
                Text("1.  Tap \"Choose folder\" below. Android's file picker opens.")
                Spacer(Modifier.height(6.dp))
                Text("2.  Tap the menu icon (three lines, top left) and pick \"$appName\" from the list.")
                Spacer(Modifier.height(6.dp))
                Text("3.  Tap \"Use this folder\" at the bottom, then \"Allow\".")
            }
        },
        confirmButton = { TextButton(onClick = onChooseFolder) { Text("Choose folder") } },
        dismissButton = { TextButton(onClick = onNotNow) { Text("Not now") } }
    )
}

/**
 * Eden only creates its settings file the first time it runs, and RomRunner can't set up its driver
 * without that file. The folder permission is already saved at this point and RomRunner finishes the
 * job by itself when the user comes back, so all that's needed is for Eden to be opened once.
 */
@Composable
fun EdenOpenFirstDialog(
    onOpenEden: () -> Unit,
    onLater: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("Open Eden once") },
        text = {
            Text(
                "Permission saved. Eden hasn't been opened yet, so it hasn't created its settings.\n\n" +
                    "Tap \"Open Eden\", wait for it to load, then come back to RomRunner. " +
                    "RomRunner will finish setting up the graphics driver by itself."
            )
        },
        confirmButton = { TextButton(onClick = onOpenEden) { Text("Open Eden") } },
        dismissButton = { TextButton(onClick = onLater) { Text("Later") } }
    )
}
