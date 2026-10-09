package com.noryan.romrunner.ui.theme

import androidx.compose.ui.graphics.Color

// True black rather than a near-black navy — OLED panels turn these pixels off entirely instead
// of just dimming them. Kept as its own named token (not Color.Black) since every embedded core
// duplicates this exact value under the same "Slate90" name (their own modules can't depend on
// this one) — see e.g. armsx2-src's RomRunnerPauseMenu.kt.
val Slate90 = Color(0xFF000000)
val Slate10 = Color(0xFFF4F5FA)
val Amber80 = Color(0xFFFFD9A6)
val Amber40 = Color(0xFFB4690E)

// The app's own brand mark colors (see branding/mark.svg, res/values/colors.xml's
// brand_orange/brand_navy) — kept as separate Compose-side constants since Compose code doesn't
// reach XML color resources without an explicit colorResource() call at each use site.
val BrandOrange = Color(0xFFFF6B4A)
val BrandNavy = Color(0xFF0E0A22)

// The highlight color for text the controller is on (and the app's accent): the brand orange nudged lighter and toward gold.
// Only text and UI use it; the cartridge mark itself stays the brand orange.
val HighlightColor = Color(0xFFFF9050)
