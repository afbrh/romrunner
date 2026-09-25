package com.noryan.romrunner.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.noryan.romrunner.data.model.Game

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameActionSheet(
    game: Game,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = game.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            ActionRow(Icons.Filled.PlayArrow, "Play", onPlay)
            ActionRow(
                icon = if (game.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                label = if (game.isFavorite) "Remove favorite" else "Add to favorites",
                onClick = onToggleFavorite
            )
            ActionRow(Icons.Filled.Edit, "Rename", onRename)
            ActionRow(Icons.Filled.Delete, "Remove from library", onRemove)
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null)
        Spacer(modifier = Modifier.width(20.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
