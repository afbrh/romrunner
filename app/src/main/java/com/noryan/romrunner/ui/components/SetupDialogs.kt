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

/**
 * Offered once, just before the first batch of emulator downloads: lets RomRunner look in Downloads for
 * emulator APKs that are already there. Android only shows an app the files it downloaded itself during
 * the current install, so seeing the rest takes the "All files access" setting, which only the user can
 * switch on.
 */
@Composable
fun StorageAccessDialog(
    onAllow: () -> Unit,
    onSkip: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("Reuse downloaded emulators?") },
        text = {
            Text(
                "If you already have these emulators in your Downloads folder, RomRunner can install those instead of " +
                    "downloading them again.\n\n" +
                    "For that, Android needs you to switch on \"Allow access to manage all files\" for RomRunner. " +
                    "Tap \"Open settings\", turn it on, then come back."
            )
        },
        confirmButton = { TextButton(onClick = onAllow) { Text("Open settings") } },
        dismissButton = { TextButton(onClick = onSkip) { Text("Just download") } }
    )
}
