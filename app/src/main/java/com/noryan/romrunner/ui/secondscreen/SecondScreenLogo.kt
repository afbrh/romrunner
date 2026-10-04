package com.noryan.romrunner.ui.secondscreen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import com.noryan.romrunner.R
import kotlin.math.roundToInt

/**
 * Fills a connected second display with RomRunner's own mark. While a game is running
 * ([gameRunning]) the cartridge slides down until only its top [PeekFraction] still shows above the
 * bottom edge, like a cartridge sticking out of a Game Boy Advance. The mark's own drawable has
 * empty margin around the cartridge, hence the viewport constants.
 */
@Composable
fun SecondScreenLogo(gameRunning: Boolean = false) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val density = LocalDensity.current
        val screenHeight = with(density) { maxHeight.toPx() }
        val markSize = minOf(maxWidth, maxHeight) * 0.4f
        val markPx = with(density) { markSize.toPx() }

        val restingTop = (screenHeight - markPx) / 2f
        val cartridgeTop = markPx * CartridgeTop / Viewport
        val cartridgeHeight = markPx * (CartridgeBottom - CartridgeTop) / Viewport
        val insertedTop = screenHeight - cartridgeTop - cartridgeHeight * PeekFraction

        val slide by animateFloatAsState(
            targetValue = if (gameRunning) 1f else 0f,
            animationSpec = tween(durationMillis = 900, delayMillis = if (gameRunning) 250 else 0, easing = FastOutSlowInEasing),
            label = "cartridgeSlide"
        )
        val top = restingTop + (insertedTop - restingTop) * slide

        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, top.roundToInt()) }
                .size(markSize)
        )
    }
}

/** Share of the cartridge's height still visible once it has slid into the bottom edge. */
private const val PeekFraction = 0.45f

// ic_launcher_foreground.xml's viewport, and the vertical extent of the cartridge inside it.
private const val Viewport = 108f
private const val CartridgeTop = 29f
private const val CartridgeBottom = 79f
