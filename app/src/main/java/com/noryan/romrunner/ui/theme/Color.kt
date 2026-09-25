package com.noryan.romrunner.ui.theme

import androidx.compose.ui.graphics.Color

val Indigo80 = Color(0xFFC5CAFF)
val Indigo40 = Color(0xFF4B54C7)
// True black rather than a near-black navy — OLED panels turn these pixels off entirely instead
// of just dimming them. Kept as its own named token (not Color.Black) since every embedded core
// duplicates this exact value under the same "Slate90" name (their own modules can't depend on
// this one) — see e.g. armsx2-src's RomRunnerPauseMenu.kt.
val Slate90 = Color(0xFF000000)
val Slate10 = Color(0xFFF4F5FA)
val Amber80 = Color(0xFFFFD9A6)
val Amber40 = Color(0xFFB4690E)
