package com.noryan.romrunner.ui.secondscreen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noryan.romrunner.ui.theme.Silkscreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

private val Danger = Color(0xFFFF4D4D)
private const val RefreshMillis = 1000L
private const val Segments = 24

/**
 * Memory, temperature and CPU-clock meters for the second screen, refreshed every second while shown.
 * [level] scales every bar (1 = the real readings, 0 = all empty), so the whole panel can drain and refill.
 */
@Composable
fun DeviceStatsPanel(modifier: Modifier = Modifier, level: () -> Float = { 1f }) {
    val context = LocalContext.current
    var stats by remember { mutableStateOf<DeviceStats?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            stats = withContext(Dispatchers.IO) { runCatching { DeviceStatsSampler.sample(context) }.getOrNull() } ?: stats
            delay(RefreshMillis)
        }
    }
    val s = stats ?: return
    val accent = MaterialTheme.colorScheme.primary

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 48.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        s.cpuCores?.let { cores ->
            val topGhz = cores.maxOf { it.currentKhz } / 1_000_000f
            StatRow("CPU", String.format(Locale.US, "%.2f GHZ", topGhz), height = 44.dp) {
                CoreBars(cores, accent, level)
            }
        }

        val memFraction = s.memoryUsedBytes.toFloat() / s.memoryTotalBytes.coerceAtLeast(1)
        StatRow("MEM", "${gb(s.memoryUsedBytes)} / ${gb(s.memoryTotalBytes)} GB") {
            SegmentBar(memFraction, if (memFraction >= 0.9f) Danger else accent, level)
        }

        s.tempC?.let { temp ->
            // Chips run hot by design (80+ °C under load is normal), the battery doesn't, so each gets its own scale.
            val fraction = if (s.tempIsChip) (temp - 40f) / 60f else (temp - 25f) / 25f
            val label = when {
                s.thermalStatus >= 4 -> "CRITICAL"
                s.thermalStatus == 3 -> "THROTTLING"
                fraction < 0.33f -> "COOL"
                fraction < 0.6f -> "WARM"
                fraction < 0.8f -> "HOT"
                else -> "VERY HOT"
            }
            StatRow("TEMP", String.format(Locale.US, "%s %.0fC", label, temp)) {
                SegmentBar(fraction, heatColor(fraction), level)
            }
        }
    }
}

/** Green when cool, through yellow and orange, to red when hot. */
private fun heatColor(fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    val stops = listOf(0f to Color(0xFF4CD964), 0.45f to Color(0xFFFFD60A), 0.75f to Color(0xFFFF9500), 1f to Danger)
    val upper = stops.indexOfFirst { f <= it.first }.coerceAtLeast(1)
    val (f0, c0) = stops[upper - 1]
    val (f1, c1) = stops[upper]
    return lerp(c0, c1, (f - f0) / (f1 - f0))
}

private fun gb(bytes: Long) = String.format(Locale.US, "%.1f", bytes / 1_073_741_824.0)

private val StatStyle = TextStyle(fontFamily = Silkscreen, fontSize = 14.sp)

@Composable
private fun StatRow(label: String, value: String, height: Dp = 30.dp, meter: @Composable RowScope.() -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(height), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = StatStyle, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), modifier = Modifier.width(64.dp))
        Box(modifier = Modifier.weight(1f).height(if (height > 30.dp) height else 18.dp)) {
            Row { meter() }
        }
        Text(value, style = StatStyle, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.End, modifier = Modifier.width(150.dp))
    }
}

/** A pixel-style bar of small blocks, filled left to right. */
@Composable
private fun RowScope.SegmentBar(fraction: Float, fill: Color, level: () -> Float) {
    val track = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
    Canvas(modifier = Modifier.weight(1f).fillMaxSize()) {
        val gap = 3.dp.toPx()
        val width = (size.width - gap * (Segments - 1)) / Segments
        val shown = level().coerceIn(0f, 1f)
        val blocks = (fraction.coerceIn(0f, 1f) * shown * Segments).roundToInt()
        // A real reading above zero always shows at least one block, except while the meter is deliberately drained.
        val filled = if (fraction > 0f && shown >= 1f) blocks.coerceAtLeast(1) else blocks
        repeat(Segments) { i ->
            drawRect(
                color = if (i < filled) fill else track,
                topLeft = Offset(i * (width + gap), 0f),
                size = Size(width, size.height)
            )
        }
    }
}

/** One vertical bar per CPU core, as tall as that core's current clock is of its maximum. */
@Composable
private fun RowScope.CoreBars(cores: List<CpuCore>, fill: Color, level: () -> Float) {
    val track = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
    Canvas(modifier = Modifier.weight(1f).fillMaxSize()) {
        val gap = 6.dp.toPx()
        val width = (size.width - gap * (cores.size - 1)) / cores.size
        cores.forEachIndexed { i, core ->
            val x = i * (width + gap)
            drawRect(color = track, topLeft = Offset(x, 0f), size = Size(width, size.height))
            val h = size.height * (core.currentKhz.toFloat() / core.maxKhz).coerceIn(0f, 1f) * level().coerceIn(0f, 1f)
            drawRect(color = fill, topLeft = Offset(x, size.height - h), size = Size(width, h))
        }
    }
}
