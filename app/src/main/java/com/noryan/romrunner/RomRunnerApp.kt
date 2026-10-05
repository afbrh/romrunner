package com.noryan.romrunner

import android.app.Application
import com.noryan.romrunner.data.db.AppDatabase
import com.noryan.romrunner.data.repository.LibraryRepository
import com.noryan.romrunner.data.settings.LibrarySettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RomRunnerApp : Application() {

    lateinit var repository: LibraryRepository
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getInstance(this)
        val settings = LibrarySettings(this)
        repository = LibraryRepository(db.platformDao(), db.gameDao(), settings)
        appScope.launch {
            repository.seedDefaultPlatformsIfEmpty()
            // Must run before seedMissingDefaultPlatforms() below — otherwise that call would see
            // "GameBoy (Color + Advance)" as a missing default and insert it as a brand-new
            // platform before this has a chance to merge the old GBA/GBC rows into it.
            repository.mergeGameBoyPlatforms()
            // Adds any DefaultPlatforms.ALL entry that isn't already present, e.g. "PlayStation"
            // being added to the starter set after an install has already seeded/scanned a library.
            repository.seedMissingDefaultPlatforms()
            repository.repairKnownMisclassifications()
            repository.ensureWiiWadExtension()
            repository.applyDefaultLaunchPackagesIfMissing()
        }
    }
}
