package com.noryan.romrunner.data.embedded

import android.app.Application
import me.magnum.melonds.MelonDSApplication

/**
 * RomRunner integration shim for WatermelonDS — mirrors DolphinApplication/CemuApplication's own
 * initializeForEmbedding pattern, except split into two calls because WatermelonDS's onCreate
 * logic needs a live Hilt component (see MelonDSApplication.kt's companion for why): [loadNativeLib]
 * runs from RomRunnerApp.attachBaseContext (before any Hilt component exists), and
 * [initializeForEmbedding] runs from RomRunnerApp.onCreate after super.onCreate() (once it does).
 */
object WatermelonDSApplication {
    fun loadNativeLib() = MelonDSApplication.loadNativeLib()
    fun initializeForEmbedding(hostApplication: Application) = MelonDSApplication.initializeForEmbedding(hostApplication)
}
