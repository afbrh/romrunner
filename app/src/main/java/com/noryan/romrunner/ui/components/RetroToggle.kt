package com.noryan.romrunner.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A plain "ON"/"OFF" text toggle, matching the style the embedded emulator cores' own in-game
 * menus already use for their toggle rows (e.g. ARMSX2's "Show FPS" row: the label stays full
 * brightness, only this value text dims when off) — replaces this component's earlier blocky
 * rocker-switch look, which didn't match. Focus feedback reuses the same glow convention as
 * every other row in this screen (see FocusGlow.kt) rather than a border, for consistency.
 */
@Composable
fun RetroToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = rememberFocusInteractionSource()
    val glowShadow = interactionSource.glowShadow()
    val baseColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        checked -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    }
    val color = interactionSource.glowColor(baseColor)

    Text(
        text = if (checked) "ON" else "OFF",
        style = MaterialTheme.typography.bodyLarge.copy(shadow = glowShadow),
        color = color,
        modifier = modifier
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = { onCheckedChange(!checked) }
            )
            .padding(horizontal = 4.dp, vertical = 2.dp)
    )
}
