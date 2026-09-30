package com.noryan.romrunner.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.noryan.romrunner.data.model.Game

/** A single compact text row: title left-justified, platform right-justified, same line. Long-press opens play/favorite/rename/remove. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameRow(
    game: Game,
    platformName: String,
    onClick: (Game) -> Unit,
    onLongClick: (Game) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = rememberFocusInteractionSource()
    val glow = interactionSource.glowShadow()
    val titleColor = interactionSource.glowColor(MaterialTheme.colorScheme.onSurface)
    val platformColor = interactionSource.glowColor(MaterialTheme.colorScheme.onSurfaceVariant)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onClick(game) },
                onLongClick = { onLongClick(game) }
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = game.title,
            style = MaterialTheme.typography.titleMedium.copy(shadow = glow),
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = platformName,
            style = MaterialTheme.typography.titleMedium.copy(shadow = glow),
            color = platformColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End
        )
    }
}
