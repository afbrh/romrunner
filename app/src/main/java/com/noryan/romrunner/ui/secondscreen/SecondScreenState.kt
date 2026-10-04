package com.noryan.romrunner.ui.secondscreen

import com.noryan.romrunner.data.fps.FpsMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether a game launched from RomRunner is (presumably) running, which is what tells the second
 * screen to slide the logo down into the bottom edge like a cartridge in a slot. There is no signal
 * from the emulator itself, so this follows RomRunner's own visibility: [gameStarted] right after
 * a successful launch, and [gameEnded] the next time RomRunner is back in front (MainActivity.onResume,
 * which also covers a launch that never took over the screen).
 */
object SecondScreenState {
    private val _gameRunning = MutableStateFlow(false)
    val gameRunning: StateFlow<Boolean> = _gameRunning

    private val _title = MutableStateFlow("")
    /** The running game's title, shown as "Now Playing"; stays at its last value after the game ends so it can fade out. */
    val title: StateFlow<String> = _title

    private val _emulator = MutableStateFlow("")
    /** The emulator the game runs in ("" if unknown); like [title], kept after the game ends. */
    val emulator: StateFlow<String> = _emulator

    fun gameStarted(gameTitle: String, emulatorName: String, packageName: String) {
        FpsMonitor.start(packageName)
        _title.value = gameTitle
        _emulator.value = emulatorName
        _gameRunning.value = true
    }

    fun gameEnded() {
        FpsMonitor.stop()
        _gameRunning.value = false
    }
}
