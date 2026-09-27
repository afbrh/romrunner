plugins {
    // Aligned with the embedded Azahar/ARMSX2 libraries' AGP version (azahar-src/build.gradle.kts,
    // armsx2-src/.../libs.versions.toml) — AGP enforces a single version across an entire
    // composite build, and ARMSX2 requires 9.2.1.
    id("com.android.application") version "9.2.1" apply false
    // RomRunner integration: AGP 9's built-in Kotlin support supersedes the standalone
    // org.jetbrains.kotlin.android plugin (applying it is now a hard error) — see app/build.gradle.kts.
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
    // kapt (used for Room) doesn't work at all under Kotlin 2.4.0 (kaptDebugKotlin fails outright,
    // not just the "falling back to language version 1.9" warning it printed before) — it's been
    // effectively unmaintained for a while now. Switched to KSP, Room's actively-maintained
    // annotation processor.
    id("com.google.devtools.ksp") version "2.3.12" apply false
    // Required once RomRunnerApp becomes @HiltAndroidApp for the embedded WatermelonDS core (see
    // RomRunnerApp.kt) — pinned to the exact version watermelonds-src/gradle/libs.versions.toml
    // declares, since Hilt requires one identical version across every module in the whole
    // composite build (a mismatch is a known source of "cannot find ComponentTreeDeps" failures
    // at the final aggregation step). Bumped from 2.59.2: that version's bundled kotlin-metadata-jvm
    // only supports reading metadata up to version 2.3.0, but RomRunner's own Kotlin plugin (2.4.0)
    // stamps classes with metadata version 2.4.0, which hiltJavaCompile can't parse otherwise.
    id("com.google.dagger.hilt.android") version "2.60.1" apply false
}
