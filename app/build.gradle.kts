plugins {
    id("com.android.application")
    // RomRunner integration: AGP 9's built-in Kotlin support (needed for ARMSX2's AGP 9.2.1
    // requirement) makes the standalone org.jetbrains.kotlin.android plugin not just redundant
    // but a hard error to apply. jvmTarget is now driven by compileOptions below instead of a
    // separate `kotlin { compilerOptions {} }` block. See https://kotl.in/gradle/agp-built-in-kotlin.
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Embedded Nintendo Switch emulation (Eden, a Yuzu fork) is gated behind this property rather
// than always-on like the other embedded cores, for legal-risk management — Nintendo has sued
// multiple Switch emulator projects. Default true (a plain build includes Eden); passing
// -Promrunner.switchEmbedded=false switches to the "lite" flavor instead, which excludes Eden's
// code entirely (see the "switchSupport" flavor dimension below and settings.gradle.kts, where
// the same property also skips configuring eden-src as part of the build at all).
val switchEmbedded: Boolean = providers.gradleProperty("romrunner.switchEmbedded")
    .orElse("true")
    .map(String::toBoolean)
    .get()

android {
    namespace = "com.noryan.romrunner"
    // compileSdk bumped to satisfy the embedded ARMSX2 library module's requirement (compileSdk
    // 37) — a consuming app can't target lower than its dependencies.
    compileSdk = 37

    flavorDimensions += "switchSupport"
    productFlavors {
        create("full") { dimension = "switchSupport" }
        create("lite") { dimension = "switchSupport" }
    }

    defaultConfig {
        applicationId = "com.noryan.romrunner"
        // 30, not 29: Cemu's own module requires minSdk 30.
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // The embedded Azahar library has a "version" flavor dimension (vanilla/googlePlay) that
        // RomRunner doesn't declare itself; pick "vanilla" (the non-Play-Store-restricted variant).
        missingDimensionStrategy("version", "vanilla")
        // Same idea for the embedded ARMSX2 library's "store" dimension (github/play) — pick
        // "github", the sideload-capable variant with MANAGE_EXTERNAL_STORAGE, since RomRunner
        // isn't a Play Store build either.
        missingDimensionStrategy("store", "github")
        // Same idea again for the embedded RetroArch library's "variant" dimension
        // (normal/aarch64/ra32/playStoreNormal/playStorePlus) — pick "aarch64", matching this
        // project's arm64-v8a-only ABI targeting elsewhere and avoiding the Play Store flavors'
        // extra Play Feature/Asset Delivery dependencies RomRunner doesn't want.
        missingDimensionStrategy("variant", "aarch64")
        // Same idea again for the embedded Eden library's "edenVersion" dimension
        // (chromeOS/genshinSpoof/legacy/mainline) — pick "legacy", the standard build (the other
        // three are Eden's own special-purpose variants, not relevant to RomRunner).
        missingDimensionStrategy("edenVersion", "legacy")
    }

    buildTypes {
        // RomRunner integration: R8 (isMinifyEnabled) is required — not for the reason it was
        // first tried (see below), but because ARMSX2's Settings data class (~250 fields, all
        // defaulted) makes kotlinc emit a genuine public no-arg constructor unconditionally
        // (verified directly in the compiled .class — no source-level Settings() call is needed
        // to trigger it), and that constructor's own body hits the same dex register-encoding
        // limit as copy$default calling the real constructor. Nothing in source calls it, so it
        // can only be neutralized by letting R8's shrinker strip it as dead code — see
        // armsx2-src's proguard-rules.pro, whose Settings keep rule was narrowed to just
        // copy()/copy$default (needed by ext/ReflectiveCopy.kt's copyWith) specifically so this
        // constructor stays eligible for removal.
        debug {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // RomRunner integration: the embedded PrimeHack library enables core library desugaring
        // (for Java 8+ APIs some of its dependencies use) — AGP requires the consuming app to
        // enable it too, not just the library that needs it.
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // Required by the embedded Azahar library for its libadrenotools custom GPU driver
        // loading (it sets this on its own module too, but jniLibs packaging is decided by the
        // final app module, not a dependency's). Without it, native driver/Vulkan setup crashes.
        jniLibs {
            useLegacyPackaging = true
            // Azahar and ARMSX2 each vendor their own shaderc build, which bundles this same
            // SPIRV-Tools shared library — a standard shared-third-party-dep collision (like
            // libc++_shared.so), resolved by picking one arbitrarily; both call the same stable
            // public SPIRV-Tools C API.
            pickFirsts += "**/libSPIRV-Tools-shared.so"
            // Same story for libadrenotools' custom-Vulkan-driver-loading hook libraries — both
            // emulators bundle their own copy of the same upstream adrenotools project.
            pickFirsts += "**/libfile_redirect_hook.so"
            pickFirsts += "**/libgsl_alloc_hook.so"
            pickFirsts += "**/libhook_impl.so"
            pickFirsts += "**/libmain_hook.so"
            // Cemu also vendors its own libc++_shared.so (standard NDK C++ runtime) — same
            // collision class as above, any one copy works since it's ABI-stable across builds.
            pickFirsts += "**/libc++_shared.so"
            // Eden and Azahar each bundle their own Vulkan validation layer for debug builds —
            // same collision class again, debug-only tooling with a stable public ABI.
            pickFirsts += "**/libVkLayer_khronos_validation.so"
        }
    }
}

// Only one of "full"/"lite" is ever enabled for a given build (see romrunner.switchEmbedded
// above) — this is what makes a plain "assembleDebug"/"assembleRelease" produce exactly one APK
// instead of Gradle trying to build both flavors.
androidComponents {
    beforeVariants(selector().withFlavor("switchSupport" to "full")) { variant ->
        variant.enable = switchEmbedded
    }
    beforeVariants(selector().withFlavor("switchSupport" to "lite")) { variant ->
        variant.enable = !switchEmbedded
    }
}

// Eden declares com.google.android.material:material:1.12.0, but some other transitive
// dependency elsewhere in the composite build floors it higher (1.14.0 resolves by default) —
// Material apparently dropped its own R.attr.colorPrimary field somewhere after 1.12.0 (relying on
// the platform/appcompat's copy instead), which crashes Eden's EmulationFragment
// (NoSuchFieldError) since its compiled bytecode references Material's own copy directly. Forcing
// the version it was actually built against, project-wide, matches the same class of fix already
// used for shared native libs (SPIRV-Tools, libc++_shared, adrenotools) — same "multiple cores
// bundle a shared third-party dependency" collision, at the Gradle dependency level instead.
configurations.all {
    resolutionStrategy {
        force("com.google.android.material:material:1.12.0")
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    // Needed to touch Cemu's own AppSettingsStore (DataStore-backed hotkey settings) from
    // CemuEmbeddedLauncher.kt — cemu-src's own build.gradle.kts declares this as
    // `implementation`, which isn't visible to a consuming module, so it's re-declared here at
    // the same version (androidx-datastore = "1.2.1" in cemu-src's libs.versions.toml).
    implementation("androidx.datastore:datastore:1.2.1")

    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.13.1")
    // Consistent branded cold-start splash (RomRunner's mark on its navy brand background) across
    // minSdk 30 up — API 31+ has this built into the OS already, but only if a splash theme is
    // declared; below 31 this library draws the same look itself.
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // Bumped from 2.6.1: that version's annotation processor isn't compatible with KSP2
    // ("unexpected jvm signature V"), which is what current KSP versions use.
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    implementation("androidx.documentfile:documentfile:1.0.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Embedded 3DS emulation — our local library build of azahar-emu/azahar (see
    // ../azahar-src and settings.gradle.kts for the composite-build wiring).
    implementation("org.azahar_emu:app:embedded")

    // Embedded PS2 emulation — our local library build of ARMSX2 (see ../armsx2-src and
    // settings.gradle.kts for the composite-build wiring).
    implementation("com.armsx2:app:embedded")

    // Embedded GameCube/Wii emulation — our local library build of PrimeHack (see
    // ../primehack-src and settings.gradle.kts for the composite-build wiring).
    implementation("org.dolphinemu:app:embedded")

    // Embedded Wii U emulation — our local library build of Cemu (see ../cemu-src and
    // settings.gradle.kts for the composite-build wiring).
    implementation("info.cemu:app:embedded")

    // Embedded N64/SNES/NES/GBA/GB·Color/PlayStation emulation — our local library build of
    // RetroArch (see ../retroarch-src and settings.gradle.kts for the composite-build wiring).
    implementation("com.retroarch:phoenix:embedded")

    // Embedded Twilight Princess emulation — our local library build of DuskLight (see
    // ../dusklight-src and settings.gradle.kts for the composite-build wiring). Only its
    // DuskActivity/Borealis UI layer comes through this dependency; its native libmain.so is
    // bundled separately as a plain asset (see DuskLightEmbeddedLauncher.kt).
    implementation("dev.twilitrealm:app:embedded")

    // Embedded Nintendo Switch emulation — our local library build of Eden (see ../eden-src and
    // settings.gradle.kts for the composite-build wiring). Only present on the "full" flavor's
    // classpath — see romrunner.switchEmbedded above and EdenIntegration.kt.
    if (switchEmbedded) {
        "fullImplementation"("dev.eden_emu:app:embedded")
    }
}
