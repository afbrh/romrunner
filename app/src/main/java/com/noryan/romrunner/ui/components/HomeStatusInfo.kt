package com.noryan.romrunner.ui.components

import android.content.Context
import android.os.BatteryManager
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A battery-percentage + clock readout for the home screen's upper-right corner. RomRunner hides
 * the system status bar entirely (see MainActivity.hideStatusBar) and can be set as the device's
 * default launcher, so without this there's nowhere left to glance at the battery or time.
 * Refreshes every 30s on a plain polling loop — good enough for a clock/battery reading, no need
 * for a live per-second tick or a battery-changed broadcast receiver.
 */
@Composable
fun HomeStatusInfo() {
    val context = LocalContext.current
    var batteryPercent by remember { mutableIntStateOf(batteryPercent(context)) }
    var time by remember { mutableStateOf(currentTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            batteryPercent = batteryPercent(context)
            time = currentTime()
            delay(30_000)
        }
    }
    Row {
        Text(text = time, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 12.dp))
        Text(text = "$batteryPercent%", style = MaterialTheme.typography.titleMedium)
    }
}

private fun batteryPercent(context: Context): Int {
    val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
}

private fun currentTime(): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
