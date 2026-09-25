package com.noryan.romrunner.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val TrackWidth = 68.dp
private val TrackHeight = 30.dp
private val ThumbSize = 24.dp
private val ThumbInset = 3.dp

/**
 * A blocky ON/OFF rocker switch, styled to read like a physical toggle on game hardware rather
 * than Material3's default pill-and-circle Switch, which stood out against the app's
 * Silkscreen/pixel-art, OLED-black theme. Used everywhere Settings needs a boolean toggle.
 */
@Composable
fun RetroToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val trackColor by animateColorAsState(
        targetValue = when {
            !enabled -> Color.White.copy(alpha = 0.15f)
            checked -> Color.White
            else -> Color.White.copy(alpha = 0.35f)
        },
        label = "retroToggleTrack"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) TrackWidth - ThumbSize - ThumbInset else ThumbInset,
        label = "retroToggleThumb"
    )
    val labelAlpha = if (enabled) 0.85f else 0.4f
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .size(width = TrackWidth, height = TrackHeight)
            .clip(RoundedCornerShape(4.dp))
            .background(trackColor)
            .then(
                if (isFocused) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                } else {
                    Modifier
                }
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = LocalIndication.current
            ) { onCheckedChange(!checked) }
    ) {
        Text(
            text = if (checked) "ON" else "OFF",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.background.copy(alpha = labelAlpha),
            modifier = Modifier
                .align(if (checked) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(horizontal = 7.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = thumbOffset)
                .size(ThumbSize)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.background)
        )
    }
}
