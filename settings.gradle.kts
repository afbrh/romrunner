pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "RomRunner"
include(":app")

// Embedded 3DS emulation: our local build of azahar-emu/azahar, consumed as a library module
// rather than a separate installed app. See azahar-src/GAMESHELF_INTEGRATION.md for how to pull
// in a newer Azahar release later.
includeBuild("../azahar-src/src/android") {
    // Both included builds' directories end in "/android" — Gradle derives a composite build's
    // name from that final path segment when none is given, so without an explicit name here
    // this collides with armsx2-src/platforms/android below ("build path :android" clash).
    name = "azahar"
    dependencySubstitution {
        substitute(module("org.azahar_emu:app")).using(project(":app"))
    }
}

// Embedded PS2 emulation: our local build of ARMSX2, consumed as a library module the same way.
includeBuild("../armsx2-src/platforms/android") {
    name = "armsx2"
    dependencySubstitution {
        substitute(module("com.armsx2:app")).using(project(":app"))
    }
}

// Embedded GameCube/Wii emulation: our local build of PrimeHack (shiiion/dolphin), consumed as a
// library module the same way.
includeBuild("../primehack-src/Source/Android") {
    name = "primehack"
    dependencySubstitution {
        substitute(module("org.dolphinemu:app")).using(project(":app"))
    }
}

// Embedded Wii U emulation: our local build of Cemu (SSimco/Cemu, android-port branch), consumed
// as a library module the same way.
includeBuild("../cemu-src/src/android") {
    name = "cemu"
    dependencySubstitution {
        substitute(module("info.cemu:app")).using(project(":app"))
    }
}

// Embedded N64/SNES/NES/GBA/GB·Color/PlayStation emulation: our local build of RetroArch
// (libretro/RetroArch), consumed as a library module the same way. Unlike the other four cores
// (one standalone app per platform), RetroArch is a single multi-system frontend — one embedded
// module covers six platforms via bundled prebuilt libretro cores (see RetroArchEmbeddedLauncher).
// The phoenix module has no settings.gradle of its own (single-project build, no ":app" — its
// root project IS the whole build), hence project(":") rather than project(":app") here.
includeBuild("../retroarch-src/pkg/android/phoenix") {
    name = "retroarch"
    dependencySubstitution {
        substitute(module("com.retroarch:phoenix")).using(project(":"))
    }
}

// Embedded Twilight Princess core: our local build of DuskLight (TwilitRealm/dusklight),
// consumed as a library module for its DuskActivity/Borealis UI layer only — its native
// libmain.so is deliberately excluded from this module's own jniLibs output (collides by
// filename with PrimeHack's own libmain.so) and bundled as a plain RomRunner asset instead;
// see dusklight-src/GAMESHELF_INTEGRATION.md and DuskLightEmbeddedLauncher.kt.
includeBuild("../dusklight-src/platforms/android") {
    name = "dusklight"
    dependencySubstitution {
        substitute(module("dev.twilitrealm:app")).using(project(":app"))
    }
}

// Embedded Nintendo Switch emulation: our local build of Eden (a Yuzu fork), consumed as a
// library module the same way. Gated behind -Promrunner.switchEmbedded (default true, must match
// app/build.gradle.kts's own read of the same property) for legal-risk management — Nintendo has
// sued multiple Switch emulator projects; passing -Promrunner.switchEmbedded=false skips this
// includeBuild entirely so a "lite" release never even configures eden-src (a large native C++
// project) as part of the build.
val switchEmbedded: Boolean = providers.gradleProperty("romrunner.switchEmbedded")
    .orElse("true")
    .map(String::toBoolean)
    .get()

if (switchEmbedded) {
    includeBuild("../eden-src/src/android") {
        name = "eden"
        dependencySubstitution {
            substitute(module("dev.eden_emu:app")).using(project(":app"))
        }
    }
}
