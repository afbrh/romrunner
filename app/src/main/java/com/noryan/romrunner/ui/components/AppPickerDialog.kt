package com.noryan.romrunner.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.noryan.romrunner.data.launch.InstalledApp
import com.noryan.romrunner.data.launch.InstalledApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppPickerDialog(
    title: String,
    onPick: (InstalledApp) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    // RomRunner integration: see AppsScreen.kt's matching comment — this was a synchronous
    // PackageManager query on the main thread via remember{}, moved off it the same way.
    val apps by produceState(initialValue = emptyList<InstalledApp>(), context) {
        value = withContext(Dispatchers.Default) { InstalledApps.listLaunchable(context) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(apps, key = { it.packageName }) { app ->
                    Text(
                        text = app.label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(app) }
                            .padding(vertical = 12.dp)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
