package com.noryan.romrunner.ui.secondscreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.noryan.romrunner.R

/**
 * Fills a connected second display with RomRunner's own mark — the only thing asked for on a
 * dual-screen device for now (see DualScreenController), not an actual second-screen emulator
 * rendering.
 */
@Composable
fun SecondScreenLogo() {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val markSize = minOf(maxWidth, maxHeight) * 0.4f
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .size(markSize)
        )
    }
}
