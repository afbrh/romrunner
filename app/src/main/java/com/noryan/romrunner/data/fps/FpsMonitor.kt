package com.noryan.romrunner.data.fps

import android.os.Environment
import com.noryan.romrunner.data.launch.EmulatorFolders
import com.noryan.romrunner.data.launch.IniEdit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.SocketTimeoutException

/**
 * Live frames-per-second of the game running in another emulator, for the second screen. No emulator offers an
 * API for this, so each one that can give us a number does it its own way, all without adb:
 *
 *  - **PrimeHack** (GameCube/Wii): its "log render time to file" setting writes the time of every frame to
 *    `Logs/render_times.txt`; we turn that on in `GFX.ini` and average the latest lines.
 *  - **PPSSPP** (PSP): its remote debugger (switched on in `ppsspp.ini`, on a fixed port) answers a `gpu.stats.get`
 *    request over a local WebSocket with the same FPS its own counter shows.
 *  - **ARMSX2** (PS2): logs `PerfLog: N fps | ...` to `logs/emulog.txt` every 30 seconds — coarse, but it's all there is.
 *
 * Everything else (Eden, Cemu, Azahar, Flycast, RetroArch, WatermelonDS, Dusklight) keeps its FPS inside its own overlay,
 * so [fps] stays null for them. Settings are read when the emulator starts, so [prepare] runs before every launch; if
 * the emulator was already running, the reading starts the next time it is.
 */
object FpsMonitor {
    private const val PRIMEHACK = "org.dolphinemu.primehack"
    private const val PPSSPP = "org.ppsspp.ppsspp"
    private const val ARMSX2 = "com.armsx2"

    /** The fixed port we ask PPSSPP's debugger to listen on (it falls back to a random one if this is taken). */
    private const val PPSSPP_PORT = 51234

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _fps = MutableStateFlow<Float?>(null)
    /** The latest reading, or null when there isn't one (no game, or an emulator that can't report it). */
    val fps: StateFlow<Float?> = _fps

    private var job: Job? = null

    /** Switches on whatever [packageName] needs to report its FPS. Call just before launching it. Quiet on any failure. */
    fun prepare(packageName: String) {
        if (!Environment.isExternalStorageManager()) return
        runCatching {
            when (packageName) {
                PRIMEHACK -> enablePrimeHackLogging()
                PPSSPP -> enablePpssppDebugger()
            }
        }
    }

    /** Starts reading FPS for the game just launched in [packageName]; a no-op for an emulator we can't read. */
    fun start(packageName: String) {
        stop()
        val since = System.currentTimeMillis()
        val reader: suspend (Long, (Float?) -> Unit) -> Unit = when (packageName) {
            PRIMEHACK -> ::readPrimeHack
            PPSSPP -> ::readPpsspp
            ARMSX2 -> ::readArmsx2
            else -> return
        }
        job = scope.launch { reader(since) { _fps.value = it } }
    }

    fun stop() {
        job?.cancel()
        job = null
        _fps.value = null
    }

    // ---- PrimeHack -------------------------------------------------------------------------------------------

    private fun primeHackDir() = EmulatorFolders.directDir(PRIMEHACK)

    private fun enablePrimeHackLogging() {
        val ini = File(primeHackDir(), "Config/GFX.ini")
        ini.parentFile?.mkdirs()
        val before = ini.takeIf { it.exists() }?.readText().orEmpty()
        val after = IniEdit.setValue(before, "Settings", "LogRenderTimeToFile", "True")
        if (after != before) ini.writeText(after)
    }

    private suspend fun readPrimeHack(since: Long, emit: (Float?) -> Unit) {
        val file = File(primeHackDir(), "Logs/render_times.txt")
        while (currentCoroutineContext().isActive) {
            val fresh = file.lastModified().let { it >= since - 1000 && System.currentTimeMillis() - it < 3000 }
            emit(if (fresh) runCatching { primeHackFps(file) }.getOrNull() else null)
            delay(1000)
        }
    }

    /** One line per presented frame, holding the time since the last in ms; average the latest second or so. */
    private fun primeHackFps(file: File): Float? {
        val tail = readTail(file, 4096)
        val frameTimes = tail.lineSequence().drop(1) // the first line may be cut off
            .mapNotNull { it.trim().toFloatOrNull() }.filter { it > 0f }.toList().takeLast(60)
        if (frameTimes.size < 5) return null
        return 1000f / frameTimes.average().toFloat()
    }

    // ---- ARMSX2 ----------------------------------------------------------------------------------------------

    private suspend fun readArmsx2(since: Long, emit: (Float?) -> Unit) {
        val file = File(EmulatorFolders.directDir(ARMSX2), "logs/emulog.txt")
        val perfLog = Regex("""PerfLog:\s*([0-9]+(?:\.[0-9]+)?)\s*fps""")
        while (currentCoroutineContext().isActive) {
            // The log is rewritten at every start, so anything older than this launch is the previous session's.
            val value = if (file.lastModified() >= since - 1000) {
                runCatching { perfLog.findAll(readTail(file, 16384)).lastOrNull()?.groupValues?.get(1)?.toFloat() }.getOrNull()
            } else null
            emit(value)
            delay(2000)
        }
    }

    // ---- PPSSPP ----------------------------------------------------------------------------------------------

    /** `[General] RemoteDebuggerOnStartup` and `RemoteISOPort` in every ppsspp.ini that exists (the app rewrites it when it exits). */
    private fun enablePpssppDebugger() {
        val candidates = listOf(
            File(Environment.getExternalStorageDirectory(), "PSP/SYSTEM/ppsspp.ini"),
            File(EmulatorFolders.directDir(PPSSPP), "PSP/SYSTEM/ppsspp.ini")
        )
        for (ini in candidates.filter { it.isFile }) {
            val before = ini.readText()
            val after = IniEdit.setValue(IniEdit.setValue(before, "General", "RemoteDebuggerOnStartup", "True"), "General", "RemoteISOPort", PPSSPP_PORT.toString())
            if (after != before) ini.writeText(after)
        }
    }

    private suspend fun readPpsspp(since: Long, emit: (Float?) -> Unit) {
        while (currentCoroutineContext().isActive) {
            var socket: MiniWebSocket? = null
            val closer = job?.invokeOnCompletion { socket?.close() }
            try {
                val ws = MiniWebSocket.connect("127.0.0.1", PPSSPP_PORT, "/debugger", "debugger.ppsspp.org", readTimeoutMs = 2500)
                socket = ws
                ws.sendText("""{"event":"version","name":"RomRunner","version":"1"}""")
                var ticket = 0
                while (currentCoroutineContext().isActive) {
                    ticket++
                    ws.sendText("""{"event":"gpu.stats.get","ticket":$ticket}""")
                    emit(awaitFps(ws, ticket))
                    delay(1000)
                }
            } catch (e: IOException) {
                emit(null) // not running yet, debugger off, or the game closed: try again shortly
            } finally {
                closer?.dispose()
                socket?.close()
            }
            delay(1500)
        }
    }

    /** Reads messages until the reply to [ticket]; its `fps.actual`, or null if PPSSPP says it isn't running a game yet. */
    private fun awaitFps(ws: MiniWebSocket, ticket: Int): Float? {
        val deadline = System.currentTimeMillis() + 4000
        while (System.currentTimeMillis() < deadline) {
            val message = try {
                JSONObject(ws.readText())
            } catch (e: SocketTimeoutException) {
                return null // no frame came in time, e.g. the game is paused
            }
            if (message.optInt("ticket", -1) != ticket) continue // a broadcast, or an old reply
            return message.optJSONObject("fps")?.optDouble("actual")?.toFloat()?.takeIf { !it.isNaN() }
        }
        return null
    }

    // ---- shared ----------------------------------------------------------------------------------------------

    /** The last [bytes] of [file] as text. */
    private fun readTail(file: File, bytes: Int): String = RandomAccessFile(file, "r").use { raf ->
        val length = raf.length()
        val start = (length - bytes).coerceAtLeast(0)
        raf.seek(start)
        ByteArray((length - start).toInt()).also { raf.readFully(it) }.toString(Charsets.UTF_8)
    }
}
