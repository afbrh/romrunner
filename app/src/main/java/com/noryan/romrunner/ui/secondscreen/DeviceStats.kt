package com.noryan.romrunner.ui.secondscreen

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.os.PowerManager
import java.io.File

/** One CPU core's current and maximum clock, in kHz, and its relative processing capacity (big cores count for more than little ones). */
private data class CpuCore(val currentKhz: Long, val maxKhz: Long, val capacity: Float)

/**
 * A snapshot of the device's own vitals, shown on the second screen while a game runs. Only things any
 * app is allowed to read; anything this device won't hand over comes back null and its meter is hidden.
 */
data class DeviceStats(
    /** Overall CPU use, 0 (idle) to 1 (every core flat out); null when this device won't let us measure it. */
    val cpuUsage: Float?,
    /** The fastest core's current clock in kHz; null when the clocks can't be read. */
    val cpuTopKhz: Long?,
    /** GPU busy share, 0 to 1; null when this device doesn't expose it (so far only Qualcomm Adreno's does). */
    val gpuUsage: Float?,
    /** The GPU's current clock in MHz, when readable. */
    val gpuClockMhz: Int?,
    val memoryUsedBytes: Long,
    val memoryTotalBytes: Long,
    /** The user's storage (the shared storage volume the games live on): how much is used and its total size. */
    val storageUsedBytes: Long,
    val storageTotalBytes: Long,
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
        val cores = readCores()
        // The real thing (time the cores spent busy) when /proc/stat can be read, else how hard the cores are clocked.
        val cpuUsage = readCpuLoad()
        val clockUsage = cores?.let { all ->
            // A sleeping core (clock 0) isn't part of the answer; each awake core counts by its capacity.
            val awake = all.filter { it.currentKhz > 0 }
            val capacity = awake.sumOf { it.capacity.toDouble() }
            if (capacity <= 0) null
            else (awake.sumOf { it.capacity * it.currentKhz.toDouble() / it.maxKhz } / capacity).toFloat().coerceIn(0f, 1f)
        }
        val storage = readStorage()
        val chipTemp = readChipTempC()
        val batteryTemp = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?.takeIf { it != Int.MIN_VALUE }?.let { it / 10f }
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        return DeviceStats(
            cpuUsage = cpuUsage ?: clockUsage,
            cpuTopKhz = cores?.maxOfOrNull { it.currentKhz },
            gpuUsage = readGpuUsage(),
            gpuClockMhz = readGpuClockMhz(),
            memoryUsedBytes = memory.totalMem - memory.availMem,
            memoryTotalBytes = memory.totalMem,
            storageUsedBytes = storage.first,
            storageTotalBytes = storage.second,
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

    /** (used, total) bytes of the shared storage volume. */
    private fun readStorage(): Pair<Long, Long> = runCatching {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        val total = stat.blockCountLong * stat.blockSizeLong
        (total - stat.availableBlocksLong * stat.blockSizeLong) to total
    }.getOrDefault(0L to 0L)

    private fun readCores(): List<CpuCore>? {
        val count = (0 until 32).takeWhile { File("$CPU_DIR/cpu$it").exists() }.size
            .takeIf { it > 0 } ?: Runtime.getRuntime().availableProcessors()
        val cores = (0 until count).map { i ->
            val current = readKhz("$CPU_DIR/cpu$i/cpufreq/scaling_cur_freq")
            val max = maxClocks[i] ?: readKhz("$CPU_DIR/cpu$i/cpufreq/cpuinfo_max_freq")?.also { maxClocks[i] = it }
            if (max == null || max <= 0) null else CpuCore(current ?: 0L, max, capacityOf(i, max))
        }
        // Cores whose max can't be read are skipped; if none can, the clocks aren't available here.
        return cores.filterNotNull().takeIf { it.isNotEmpty() }
    }

    private const val ADRENO_DIR = "/sys/class/kgsl/kgsl-3d0"

    /** Qualcomm Adreno's busy figure, a file holding e.g. "20 %". */
    private fun readGpuUsage(): Float? = runCatching {
        File("$ADRENO_DIR/gpu_busy_percentage").readText().trim().substringBefore('%').trim().toFloat() / 100f
    }.getOrNull()?.coerceIn(0f, 1f)

    private fun readGpuClockMhz(): Int? = readKhz("$ADRENO_DIR/gpuclk")?.let { (it / 1_000_000).toInt() }

    /** The kernel's own per-core capacity rating where it publishes one (1024 = the biggest core), else the core's top clock. */
    private fun capacityOf(core: Int, maxKhz: Long): Float {
        val rated = runCatching { File("$CPU_DIR/cpu$core/cpu_capacity").readText().trim().toFloat() }.getOrNull()
        return rated?.takeIf { it > 0 } ?: maxKhz.toFloat()
    }

    private var lastBusy = -1L
    private var lastTotal = -1L

    /** Share of CPU time spent busy since the previous call (so null on the first one), from /proc/stat's summary line. */
    private fun readCpuLoad(): Float? {
        val fields = runCatching {
            File("/proc/stat").useLines { lines -> lines.first { it.startsWith("cpu ") } }
                .trim().split(Regex("\\s+")).drop(1).map { it.toLong() }
        }.getOrNull() ?: return null
        if (fields.size < 5) return null
        val total = fields.take(8).sum()
        val idle = fields[3] + fields[4] // idle + iowait
        val busy = total - idle
        val load = if (lastTotal >= 0 && total > lastTotal) ((busy - lastBusy).toFloat() / (total - lastTotal)).coerceIn(0f, 1f) else null
        lastBusy = busy
        lastTotal = total
        return load
    }

    private fun readKhz(path: String): Long? = runCatching { File(path).readText().trim().toLongOrNull() }.getOrNull()
}
