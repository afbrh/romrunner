package com.noryan.romrunner.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.noryan.romrunner.R

// RomRunner's pixel/retro brand font. Only ships one weight, so every role below uses
// FontWeight.Normal — asking for Bold/SemiBold on a single-weight font just triggers ugly
// synthetic-bold rendering.
val Silkscreen = FontFamily(Font(R.font.silkscreen_regular))

// Sizes bumped up across the board per request, titleLarge (the "RomRunner" app title) by more
// than the rest — Silkscreen's narrower than Press Start 2P (this project's original pixel font),
// which left headroom these didn't have before, though it's still wider per character than a
// normal proportional font at the same nominal size.
//
// Every slot is overridden (not just the ones RomRunner's own screens reach for directly) so
// Material3 defaults — e.g. Button/TextButton text (labelLarge) and AlertDialog titles
// (headlineSmall) — can't silently fall back to the system sans-serif font, which is what left
// the Settings tab's row labels, buttons and dialog titles mismatched from the rest of the app.
val RomRunnerTypography = Typography(
    displayLarge = TextStyle(fontFamily = Silkscreen, fontWeight = FontWeight.Normal, fontSize = 40.sp),
    displayMedium = TextStyle(fontFamily = Silkscreen, fontWeight = FontWeight.Normal, fontSize = 34.sp),
    displaySmall = TextStyle(fontFamily = Silkscreen, fontWeight = FontWeight.Normal, fontSize = 30.sp),
    headlineLarge = TextStyle(fontFamily = Silkscreen, fontWeight = FontWeight.Normal, fontSize = 28.sp),
    headlineMedium = TextStyle(fontFamily = Silkscreen, fontWeight = FontWeight.Normal, fontSize = 24.sp),
    headlineSmall = TextStyle(fontFamily = Silkscreen, fontWeight = FontWeight.Normal, fontSize = 20.sp),
    titleLarge = TextStyle(fontFamily = Silkscreen, fontWeight = FontWeight.Normal, fontSize = 26.sp),
    titleMedium = TextStyle(fontFamily = Silkscreen, fontWeight = FontWeight.Normal, fontSize = 17.sp),
    titleSmall = TextStyle(fontFamily = Silkscreen, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodyLarge = TextStyle(fontFamily = Silkscreen, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = Silkscreen, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodySmall = TextStyle(fontFamily = Silkscreen, fontWeight = FontWeight.Normal, fontSize = 13.sp),
    labelLarge = TextStyle(fontFamily = Silkscreen, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    labelMedium = TextStyle(fontFamily = Silkscreen, fontWeight = FontWeight.Normal, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = Silkscreen, fontWeight = FontWeight.Normal, fontSize = 12.sp)
)
