package com.noryan.romrunner.data.fps

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.util.Base64

/**
 * Just enough of a WebSocket client (RFC 6455) to talk to PPSSPP's debugger on the local machine: one text
 * message out, text messages in. No TLS, no extensions, no fragmentation of outgoing messages. Not thread-safe;
 * [close] may be called from another thread to unblock a read.
 */
class MiniWebSocket private constructor(private val socket: Socket) : Closeable {
    private val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
    private val output = BufferedOutputStream(socket.getOutputStream())

    private fun handshake(host: String, port: Int, path: String, protocol: String) {
        val key = Base64.getEncoder().encodeToString(ByteArray(16).also { random.nextBytes(it) })
        val request = "GET $path HTTP/1.1\r\nHost: $host:$port\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
            "Sec-WebSocket-Key: $key\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Protocol: $protocol\r\n\r\n"
        output.write(request.toByteArray(Charsets.ISO_8859_1))
        output.flush()
        val header = StringBuilder()
        while (!header.endsWith("\r\n\r\n")) {
            val b = input.read()
            if (b < 0) throw EOFException("closed during handshake")
            header.append(b.toChar())
            if (header.length > 8192) throw IOException("oversized handshake response")
        }
        if (!header.startsWith("HTTP/1.1 101")) throw IOException("upgrade refused: ${header.lineSequence().first()}")
    }

    fun sendText(text: String) {
        val payload = text.toByteArray(Charsets.UTF_8)
        val mask = ByteArray(4).also { random.nextBytes(it) }
        output.write(0x81) // FIN + text
        when {
            payload.size < 126 -> output.write(0x80 or payload.size)
            payload.size < 65536 -> {
                output.write(0x80 or 126)
                output.write(payload.size shr 8)
                output.write(payload.size and 0xFF)
            }
            else -> {
                output.write(0x80 or 127)
                repeat(4) { output.write(0) }
                output.write((payload.size ushr 24) and 0xFF)
                output.write((payload.size ushr 16) and 0xFF)
                output.write((payload.size ushr 8) and 0xFF)
                output.write(payload.size and 0xFF)
            }
        }
        output.write(mask)
        for (i in payload.indices) output.write(payload[i].toInt() xor mask[i % 4].toInt())
        output.flush()
    }

    /**
     * The next text message. Pings are answered and other frames skipped. Throws [SocketTimeoutException] if nothing
     * at all arrives in time (the connection is still good), and [IOException] if it breaks or closes.
     */
    fun readText(): String {
        var message: ByteArray? = null
        while (true) {
            val b0 = input.read() // a timeout here, before a frame starts, leaves the stream in step
            if (b0 < 0) throw EOFException("closed")
            try {
                val b1 = input.read()
                val opcode = b0 and 0x0F
                val fin = b0 and 0x80 != 0
                var length = (b1 and 0x7F).toLong()
                if (length == 126L) length = input.readUnsignedShort().toLong() else if (length == 127L) length = input.readLong()
                if (length > MAX_FRAME) throw IOException("frame too large")
                val mask = if (b1 and 0x80 != 0) ByteArray(4).also { input.readFully(it) } else null
                val payload = ByteArray(length.toInt()).also { input.readFully(it) }
                if (mask != null) for (i in payload.indices) payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
                when (opcode) {
                    0x1, 0x0 -> {
                        message = if (opcode == 0x1) payload else (message ?: ByteArray(0)) + payload
                        if (fin) return String(message, Charsets.UTF_8)
                    }
                    0x8 -> throw EOFException("closed by server")
                    0x9 -> sendControl(0xA, payload)
                }
            } catch (e: SocketTimeoutException) {
                throw IOException("timed out mid-frame", e) // the stream is no longer in step
            }
        }
    }

    private fun sendControl(opcode: Int, payload: ByteArray) {
        val mask = ByteArray(4).also { random.nextBytes(it) }
        output.write(0x80 or opcode)
        output.write(0x80 or payload.size)
        output.write(mask)
        for (i in payload.indices) output.write(payload[i].toInt() xor mask[i % 4].toInt())
        output.flush()
    }

    override fun close() {
        runCatching { socket.close() }
    }

    companion object {
        private val random = SecureRandom()
        private const val MAX_FRAME = 4L * 1024 * 1024

        /** Connects and upgrades; throws on any failure. [readTimeoutMs] bounds every later [readText]. */
        fun connect(host: String, port: Int, path: String, protocol: String, readTimeoutMs: Int): MiniWebSocket {
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(host, port), 500)
                socket.soTimeout = readTimeoutMs
                return MiniWebSocket(socket).also { it.handshake(host, port, path, protocol) }
            } catch (e: Exception) {
                runCatching { socket.close() }
                throw e
            }
        }
    }
}
