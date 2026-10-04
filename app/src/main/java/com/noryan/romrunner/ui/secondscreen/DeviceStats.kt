package com.noryan.romrunner.ui.secondscreen

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import java.io.File

/** One CPU core's current and maximum clock, in kHz. A core that's asleep reports a current of 0. */
data class CpuCore(val currentKhz: Long, val maxKhz: Long)

/**
 * A snapshot of the device's own vitals, shown on the second screen while a game runs. Only things any
 * app is allowed to read; anything this device won't hand over comes back null and its meter is hidden.
 */
data class DeviceStats(
    /** Null when the clocks can't be read on this device. */
    val cpuCores: List<CpuCore>?,
    val memoryUsedBytes: Long,
    val memoryTotalBytes: Long,
    /** The hottest chip (CPU/GPU) temperature, or the battery's when the chip sensors can't be read; null if neither can. */
    val tempC: Float?,
    /** Whether [tempC] is a chip temperature (hot is normal for those) rather than the battery's. */
    val tempIsChip: Boolean,
    /** [PowerManager]'s THERMAL_STATUS_* (0 = none ... 6 = shutdown). */
    val thermalStatus: Int
)

object DeviceStatsSampler {
    private const val CPU_DIR = "/sys/devices/system/cpu"

    private val maxClocks = HashMap<Int, Long>()

    /** Blocking file reads: call off the main thread. */
    fun sample(context: Context): DeviceStats {
        val memory = ActivityManager.MemoryInfo().also {
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)
        }
        val chipTemp = readChipTempC()
        val batteryTemp = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?.takeIf { it != Int.MIN_VALUE }?.let { it / 10f }
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        return DeviceStats(
            cpuCores = readCores(),
            memoryUsedBytes = memory.totalMem - memory.availMem,
            memoryTotalBytes = memory.totalMem,
            tempC = chipTemp ?: batteryTemp,
            tempIsChip = chipTemp != null,
            thermalStatus = power.currentThermalStatus
        )
    }

    /** The thermal zones that sit on the CPU and GPU, found once (a zone's name never changes). */
    private val chipZones: List<File> by lazy {
        File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") }.orEmpty()
            .filter { zone ->
                val type = runCatching { File(zone, "type").readText().trim() }.getOrDefault("")
                type.startsWith("cpu") || type.startsWith("gpu")
            }
    }

    /** The hottest CPU/GPU zone in °C. Some zones refuse reads or report junk (0, negatives), so only 10–130 °C counts. */
    private fun readChipTempC(): Float? = chipZones
        .mapNotNull { runCatching { File(it, "temp").readText().trim().toFloat() }.getOrNull() }
        .map { if (it > 1000f) it / 1000f else it }
        .filter { it in 10f..130f }
        .maxOrNull()

    private fun readCores(): List<CpuCore>? {
        val count = (0 until 32).takeWhile { File("$CPU_DIR/cpu$it").exists() }.size
            .takeIf { it > 0 } ?: Runtime.getRuntime().availableProcessors()
        val cores = (0 until count).map { i ->
            val current = readKhz("$CPU_DIR/cpu$i/cpufreq/scaling_cur_freq")
            val max = maxClocks[i] ?: readKhz("$CPU_DIR/cpu$i/cpufreq/cpuinfo_max_freq")?.also { maxClocks[i] = it }
            if (max == null || max <= 0) null else CpuCore(current ?: 0L, max)
        }
        // Cores whose max can't be read are skipped; if none can, the clocks aren't available here.
        return cores.filterNotNull().takeIf { it.isNotEmpty() }
    }

    private fun readKhz(path: String): Long? = runCatching { File(path).readText().trim().toLongOrNull() }.getOrNull()
}
