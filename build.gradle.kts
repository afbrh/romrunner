plugins {
    id("com.android.application") version "9.2.1" apply false
    // AGP 9's built-in Kotlin support supersedes the standalone org.jetbrains.kotlin.android
    // plugin (applying it is now a hard error) — see app/build.gradle.kts.
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
    // kapt (used for Room) doesn't work at all under Kotlin 2.4.0 (kaptDebugKotlin fails outright,
    // not just the "falling back to language version 1.9" warning it printed before) — it's been
    // effectively unmaintained for a while now. Switched to KSP, Room's actively-maintained
    // annotation processor.
    id("com.google.devtools.ksp") version "2.3.12" apply false
}
