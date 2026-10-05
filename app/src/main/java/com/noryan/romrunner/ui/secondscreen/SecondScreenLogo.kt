package com.noryan.romrunner.ui.secondscreen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.noryan.romrunner.R
import com.noryan.romrunner.ui.components.HomeStatusInfo
import com.noryan.romrunner.ui.theme.Accent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The second display: RomRunner's mark near the top over the device's live stats. When a game starts
 * ([gameRunning]) the stat bars drain to empty, the cartridge slides down through them until only its top
 * [PeekFraction] still shows above the bottom edge (like a cartridge sticking out of a Game Boy Advance),
 * then the bars refill and "Now playing on [emulatorName]" with [gameTitle] fades in where the logo was.
 * Closing the game plays the same thing backwards. The mark's own drawable has empty margin around the
 * cartridge, hence the viewport constants.
 */
@Composable
fun SecondScreenLogo(gameRunning: Boolean = false, gameTitle: String = "", emulatorName: String = "") {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val density = LocalDensity.current
        val screenHeight = with(density) { maxHeight.toPx() }
        val markSize = minOf(maxWidth, maxHeight) * 0.4f
        val markPx = with(density) { markSize.toPx() }

        val cartridgeTop = markPx * CartridgeTop / Viewport
        val cartridgeHeight = markPx * (CartridgeBottom - CartridgeTop) / Viewport
        val restingTop = with(density) { IdleCartridgeTop.toPx() } - cartridgeTop
        val insertedTop = screenHeight - cartridgeTop - cartridgeHeight * PeekFraction

        // Starts at the resting values for the current state, so a presentation created mid-game doesn't replay the show.
        val slide = remember { Animatable(if (gameRunning) 1f else 0f) }
        val textAlpha = remember { Animatable(if (gameRunning) 1f else 0f) }
        val statsLevel = remember { Animatable(1f) }
        LaunchedEffect(gameRunning) {
            val target = if (gameRunning) 1f else 0f
            if (slide.value == target && textAlpha.value == target && statsLevel.value == 1f) return@LaunchedEffect
            coroutineScope {
                launch { textAlpha.animateTo(0f, tween(300)) }
                statsLevel.animateTo(0f, tween(450))
            }
            slide.animateTo(target, tween(900, easing = FastOutSlowInEasing))
            coroutineScope {
                launch { statsLevel.animateTo(1f, tween(700)) }
                if (gameRunning) launch { textAlpha.animateTo(1f, tween(700)) }
            }
        }

        // Clock and battery in the upper-right corner, where they sit on the main screen.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 28.dp)) {
                HomeStatusInfo()
            }
        }

        // Always on; sits just above where the cartridge ends up.
        DeviceStatsPanel(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp),
            level = { statsLevel.value }
        )

        // Drawn after the stats so the cartridge slides over them.
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Accent.color), // the mark follows the chosen accent color
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, (restingTop + (insertedTop - restingTop) * slide.value).roundToInt()) }
                .size(markSize)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(start = 32.dp, end = 32.dp, top = 56.dp)
                .graphicsLayer {
                    alpha = textAlpha.value
                    translationY = -(1f - textAlpha.value) * 24.dp.toPx()
                }
        ) {
            Text(
                text = if (emulatorName.isBlank()) "NOW PLAYING" else "NOW PLAYING ON ${emulatorName.uppercase()}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = gameTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Share of the cartridge's height still visible once it has slid into the bottom edge. */
private const val PeekFraction = 0.45f

/** Where the cartridge's top edge sits on the idle screen, about where "Now playing" is once a game runs. */
private val IdleCartridgeTop = 44.dp

// ic_launcher_foreground.xml's viewport, and the vertical extent of the cartridge inside it.
private const val Viewport = 108f
private const val CartridgeTop = 29f
private const val CartridgeBottom = 79f
