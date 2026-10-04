package com.noryan.romrunner.data.launch

/** Small helpers for the `Key = Value` ini files several emulators keep (Dolphin/PrimeHack, PPSSPP). */
object IniEdit {
    /**
     * Sets `key = value` inside `[section]` of [ini], adding the section or key if missing and leaving
     * every other line alone — these files already hold settings the emulator wrote itself.
     */
    fun setValue(ini: String, section: String, key: String, value: String): String {
        val lines = ini.lines().toMutableList()
        val header = "[$section]"
        val start = lines.indexOfFirst { it.trim() == header }
        if (start == -1) return ini.trimEnd() + (if (ini.isBlank()) "" else "\n\n") + "$header\n$key = $value\n"
        var end = lines.size
        for (i in start + 1 until lines.size) {
            if (lines[i].trim().startsWith("[")) {
                end = i
                break
            }
        }
        val keyIndex = (start + 1 until end).firstOrNull { lines[it].trim().startsWith("$key ") || lines[it].trim().startsWith("$key=") }
        if (keyIndex != null) lines[keyIndex] = "$key = $value" else lines.add(end, "$key = $value")
        return lines.joinToString("\n")
    }
}
