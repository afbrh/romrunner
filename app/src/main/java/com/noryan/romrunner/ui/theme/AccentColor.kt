package com.noryan.romrunner.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** The highlight color choices in Settings > Accent Color. [RomRunnerOrange] is the original brand color and the default. */
enum class AccentColor(val id: String, val label: String, val color: Color) {
    RomRunnerOrange("romrunner_orange", "RomRunner Orange", Color(0xFFFF6B4A)),
    MarioRed("mario_red", "Mario Red", Color(0xFFE52521)),
    TriforceGold("triforce_gold", "Triforce Gold", Color(0xFFF7D21E)),
    LinkGreen("link_green", "Link Green", Color(0xFF3E9B3F)),
    DarkEcoPurple("dark_eco_purple", "Dark Eco Purple", Color(0xFF9B3DFF)),
    Ps2Blue("ps2_blue", "PS2 Blue", Color(0xFF2B63E8));

    companion object {
        val Default = RomRunnerOrange

        fun fromId(id: String?): AccentColor = entries.firstOrNull { it.id == id } ?: Default
    }
}

/**
 * The accent color in use right now. It's compose state, so anything that reads [color] while composing (the theme's
 * primary color, the focus glow, the logo tint) redraws the moment [selected] changes. The second screen's window
 * lives in the same process, so it follows too.
 */
object Accent {
    var selected by mutableStateOf(AccentColor.Default)

    val color: Color get() = selected.color
}
