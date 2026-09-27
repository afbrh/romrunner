# Add project specific ProGuard rules here.
# Minification (R8) is required, not optional, for both debug and release builds — see the
# buildTypes comment in app/build.gradle.kts for why (a D8-only dexer bug with the embedded
# ARMSX2 library's large Settings data class).

# Markwon (pulled in transitively via the embedded WatermelonDS module, me.magnum:app:embedded)
# renders Markdown by walking commonmark's AST reflectively; R8 can't see those call sites, so it
# reports the commonmark node/parser classes as missing. -keep (not just -dontwarn, which is all
# R8's own generated missing_rules.txt suggests) since commonmark itself must also survive intact.
-keep class org.commonmark.** { *; }
-dontwarn org.commonmark.**
