package com.noryan.romrunner.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.noryan.romrunner.ui.theme.Accent

@Composable
fun rememberFocusInteractionSource(): MutableInteractionSource = remember { MutableInteractionSource() }

/**
 * The accent used for D-pad/joystick focus feedback — the user's chosen accent color (brand orange by default, see
 * [Accent] and branding/mark.svg) rather than white, because a white glow on top of this theme's already
 * near-white text was too subtle to see on the actual handheld screen (it only showed up zoomed
 * into a screenshot on a monitor). This reads clearly against both light text and this theme's
 * OLED-black background, and doubles as the app's one unified highlight color alongside
 * MaterialTheme.colorScheme.primary (see Theme.kt), which is set to the same color.
 */
val FocusGlowColor: Color get() = Accent.color

/**
 * A colored bloom to apply to a [androidx.compose.ui.text.TextStyle]'s `shadow` while whatever's
 * wired to [interactionSource] holds D-pad/joystick focus — the only feedback a controller user
 * gets for "this is the item you're currently on," since Compose's default ripple indication
 * barely reacts to focus alone. Null (no shadow) when not focused.
 */
@Composable
fun MutableInteractionSource.glowShadow(): Shadow? {
    val isFocused by collectIsFocusedAsState()
    if (!isFocused) return null
    val blurRadiusPx = with(LocalDensity.current) { GlowBlurRadius.toPx() }
    return Shadow(color = FocusGlowColor, blurRadius = blurRadiusPx)
}

/** Pairs with [glowShadow] — swaps the text itself to [FocusGlowColor] while focused, since a
 *  shadow alone can be too subtle; the color change makes "this is the selected one" unmissable. */
@Composable
fun MutableInteractionSource.glowColor(base: Color): Color {
    val isFocused by collectIsFocusedAsState()
    return if (isFocused) FocusGlowColor else base
}

private val GlowBlurRadius = 24.dp
