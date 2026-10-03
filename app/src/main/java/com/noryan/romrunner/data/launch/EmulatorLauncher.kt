package com.noryan.romrunner.data.launch

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.noryan.romrunner.data.model.Game
import com.noryan.romrunner.data.model.Platform
import com.noryan.romrunner.data.scanner.SafPathUtils

/** Builds the Intent that hands a scanned ROM off to whatever emulator a platform is configured with. */
object EmulatorLauncher {

    /** Packages we're confident are distributed on the Play Store, for the "Install" shortcut. */
    val KNOWN_PLAY_STORE_PACKAGES = setOf("org.dolphinemu.dolphinemu")

    /** Friendly names for packages that usually aren't installed yet, so prompts read nicely. */
    private val KNOWN_APP_LABELS = mapOf(
        "com.armsx2" to "ARMSX2",
        "org.dolphinemu.dolphinemu" to "Dolphin",
        "dev.eden.eden_emulator" to "Eden",
        "org.azahar_emu.azahar" to "Azahar",
        "dev.twilitrealm.dusk" to "DuskLight",
        "org.dolphinemu.primehack" to "PrimeHack",
        "me.magnum.melondualds" to "MelonDS",
        "dev.twilitrealm.dusk" to "Dusklight",
        "org.ppsspp.ppsspp" to "PPSSPP",
        "com.flycast.emulator" to "Flycast",
        "com.retroarch" to "RetroArch"
    )

    data class Target(
        val packageName: String,
        val label: String,
        val launchUri: String? = null,
        val openAppOnly: Boolean = false,
        val argvLaunch: ArgvLaunch? = null
    )

    /** Which app should play this game: a hard-coded per-title exception if one matches, else the platform's default. */
    fun resolveTarget(platform: Platform, game: Game): Target? {
        GameLaunchOverrides.find(game.title, platform.name)?.let { override ->
            return Target(override.packageName.orEmpty(), override.appLabel, override.launchUri, override.openAppOnly, override.argvLaunch)
        }
        if (platform.launchPackage.isBlank()) return null
        return Target(platform.launchPackage, KNOWN_APP_LABELS[platform.launchPackage] ?: platform.launchPackage)
    }

    /**
     * Some apps ship under more than one package name. RetroArch's buildbot publishes the same app
     * as "com.retroarch" (universal, and the Play Store build), "com.retroarch.aarch64" and
     * "com.retroarch.ra32" — a platform configured with "com.retroarch" should count as installed,
     * and launch, whichever of them is actually on the device.
     */
    private val PACKAGE_VARIANTS = mapOf(
        "com.retroarch" to listOf("com.retroarch", "com.retroarch.aarch64", "com.retroarch.ra32")
    )

    /** The package actually installed for [packageName] (itself, or one of its known variants), or null if none is. */
    fun installedPackageFor(context: Context, packageName: String): String? {
        if (packageName.isBlank()) return null
        return (PACKAGE_VARIANTS[packageName] ?: listOf(packageName)).firstOrNull { candidate ->
            try {
                context.packageManager.getPackageInfo(candidate, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    fun isPackageInstalled(context: Context, packageName: String): Boolean =
        installedPackageFor(context, packageName) != null

    fun buildIntent(context: Context, platform: Platform, game: Game): Intent {
        val target = resolveTarget(platform, game)
        if (target?.openAppOnly == true) {
            // No generic file-handoff Intent filter exists for this app (confirmed per-app, e.g.
            // PrimeHack) — just open it; the user picks the game from its own library/UI.
            return context.packageManager.getLaunchIntentForPackage(target.packageName)
                ?: Intent(Intent.ACTION_MAIN).apply {
                    setPackage(target.packageName)
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
        }
        target?.argvLaunch?.let { launch ->
            // The app takes the disc on its command line (see ArgvLaunch). It needs a real file path,
            // not a content:// Uri; if one can't be derived (e.g. an SD card), just open the app.
            val realPath = SafPathUtils.realPathFromDocumentUri(Uri.parse(game.fileUri))
            if (realPath != null) {
                return Intent().apply {
                    component = ComponentName(target.packageName, launch.activity)
                    putExtra(launch.extraKey, (launch.argsBeforeRomPath + realPath).toTypedArray())
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            return context.packageManager.getLaunchIntentForPackage(target.packageName)
                ?: Intent(Intent.ACTION_MAIN).apply {
                    setPackage(target.packageName)
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
        }
        target?.launchUri?.let { uri ->
            // A per-game deep link (e.g. PrimeHack's home-screen-shortcut scheme) that launches
            // straight into that title, bypassing the generic file-handoff below entirely —
            // the target app already knows this exact game from its own library scan.
            return Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                setPackage(target.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        return buildIntentForPackage(context, platform, game, target?.packageName.orEmpty())
    }

    /** Builds the launch Intent using an explicit package, bypassing override resolution — used right after the user picks a replacement app. */
    fun buildIntentForPackage(context: Context, platform: Platform, game: Game, targetPackage: String): Intent {
        val fileUri = Uri.parse(game.fileUri)
        val realPath = SafPathUtils.realPathFromDocumentUri(fileUri)

        val intent = Intent(platform.launchAction.ifBlank { Intent.ACTION_VIEW })
        if (targetPackage.isNotBlank()) {
            intent.setPackage(targetPackage)
        }
        if (platform.launchActivity.isNotBlank() && targetPackage == platform.launchPackage) {
            intent.component = ComponentName(targetPackage, platform.launchActivity)
        }
        if (platform.passAsIntentData) {
            // Some real emulator apps (confirmed for ARMSX2 — its VIEW intent-filters declare a
            // content/file scheme but no <data android:mimeType> at all) only match an implicit
            // Intent whose type is null; forcing one via setDataAndType breaks resolution for them
            // ("Invalid packageName" from ActivityManager) even though the package is installed.
            // Only force a type when a platform has actually been configured with one.
            if (platform.mimeType.isBlank()) {
                intent.setData(fileUri)
            } else {
                intent.setDataAndType(fileUri, platform.mimeType)
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        platform.extras.forEach { (key, value) ->
            val resolved = value
                .replace("{FILE_PATH}", realPath ?: "")
                .replace("{FILE_URI}", fileUri.toString())
            intent.putExtra(key, resolved)
        }

        return intent
    }
}
