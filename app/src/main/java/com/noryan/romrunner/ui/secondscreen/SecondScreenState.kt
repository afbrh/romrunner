package com.noryan.romrunner.ui.secondscreen

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

    fun gameStarted() {
        _gameRunning.value = true
    }

    fun gameEnded() {
        _gameRunning.value = false
    }
}
