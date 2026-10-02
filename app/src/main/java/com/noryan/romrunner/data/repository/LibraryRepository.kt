package com.noryan.romrunner.data.repository

import com.noryan.romrunner.data.dao.GameDao
import com.noryan.romrunner.data.dao.PlatformDao
import com.noryan.romrunner.data.launch.DefaultPlatforms
import com.noryan.romrunner.data.model.Game
import com.noryan.romrunner.data.model.Platform
import com.noryan.romrunner.data.settings.LibrarySettings
import kotlinx.coroutines.flow.Flow

class LibraryRepository(
    private val platformDao: PlatformDao,
    private val gameDao: GameDao,
    private val settings: LibrarySettings
) {
    fun observePlatforms(): Flow<List<Platform>> = platformDao.observeAll()
    fun observeGames(): Flow<List<Game>> = gameDao.observeAll()

    suspend fun getPlatform(id: Long): Platform? = platformDao.getById(id)
    suspend fun getAllPlatformsOnce(): List<Platform> = platformDao.getAllOnce()

    /** Inserts a new platform, or updates it in place if it already has an id. Returns its id. */
    suspend fun savePlatform(platform: Platform): Long =
        if (platform.id == 0L) {
            platformDao.insert(platform)
        } else {
            platformDao.update(platform)
            platform.id
        }

    suspend fun deletePlatform(platform: Platform) {
        gameDao.deleteAllForPlatform(platform.id)
        platformDao.delete(platform)
    }

    /** Seeds a starter set of common systems on first run, so a chosen folder shows results immediately. */
    suspend fun seedDefaultPlatformsIfEmpty() {
        if (platformDao.getAllOnce().isEmpty()) {
            DefaultPlatforms.ALL.forEach { platformDao.insert(it) }
        }
    }

    /**
     * One-time, idempotent fixups for past default-seed mistakes, applied in place so installs that
     * already scanned a library don't need to be wiped. Currently: `.ciso` was originally (wrongly)
     * grouped under PSP; it belongs to GameCube/Wii (Dolphin). Also: "PlayStation 2" was originally
     * seeded with the Platform default mimeType ("application/octet-stream") and no launchActivity —
     * ARMSX2's own VIEW intent-filters match by Uri scheme only with no declared mimeType, and it
     * declares three equally-matching activities for that filter, so Android can't resolve either
     * an explicit type or a package-only (no explicit activity) launch Intent against it at all
     * ("unable to resolve Intent", confirmed directly), even with the app installed. Also:
     * "Nintendo DS" was originally seeded with DraStic (com.dsemu.drastic) as its launchPackage,
     * before the recommendation changed to MelonDS (me.magnum.melondualds, actually published by
     * WatermelonDS) — already-seeded installs never picked up that change on their own.
     */
    suspend fun repairKnownMisclassifications() {
        val platforms = platformDao.getAllOnce()

        val ps2 = platforms.find { it.name == "PlayStation 2" }
        if (ps2 != null && (ps2.mimeType == "application/octet-stream" || ps2.launchActivity.isBlank())) {
            platformDao.update(ps2.copy(mimeType = "", launchActivity = "com.armsx2.MainActivity"))
        }

        val ds = platforms.find { it.name == "Nintendo DS" }
        if (ds != null && ds.launchPackage == "com.dsemu.drastic") {
            platformDao.update(ds.copy(launchPackage = "me.magnum.melondualds"))
        }

        val psp = platforms.find { it.name == "PSP" } ?: return
        if ("ciso" !in psp.extensions) return

        platformDao.update(psp.copy(extensionsCsv = Platform.extensionsToCsv(psp.extensions - "ciso")))

        val gcWii = platforms.find { it.name == "GameCube / Wii" }
        val gcWiiId = if (gcWii != null) {
            if ("ciso" !in gcWii.extensions) {
                platformDao.update(gcWii.copy(extensionsCsv = Platform.extensionsToCsv(gcWii.extensions + "ciso")))
            }
            gcWii.id
        } else {
            platformDao.insert(Platform(name = "GameCube / Wii", extensionsCsv = "rvz,ciso"))
        }

        gameDao.reassignByExtension(fromPlatformId = psp.id, toPlatformId = gcWiiId, extension = "ciso")
    }

    /**
     * Fills in a platform's default launch package if it's still blank and a default now exists for
     * it (matched by name). Only touches platforms that haven't been customized yet, so it's safe to
     * run on every startup and won't clobber anything the user configured in Settings.
     */
    suspend fun applyDefaultLaunchPackagesIfMissing() {
        val defaultsByName = DefaultPlatforms.ALL.associateBy { it.name }
        for (platform in platformDao.getAllOnce()) {
            val default = defaultsByName[platform.name] ?: continue
            if (platform.launchPackage.isBlank() && default.launchPackage.isNotBlank()) {
                platformDao.update(platform.copy(launchPackage = default.launchPackage))
            }
        }
    }

    /**
     * One-time merge of the formerly-separate "Game Boy Advance" and "Game Boy / Color" default
     * platforms into a single "GameBoy (Color + Advance)" platform, since both play through the
     * same emulator app anyway — no reason to make the user manage two toggles for one emulator.
     * Keeps one of the two existing rows (renamed/re-extensioned) so already-scanned games and any
     * custom settings on it survive; moves the other's games over by extension, then deletes it.
     * Idempotent: no-ops once the merged platform already exists, or if neither old platform was
     * ever seeded to begin with.
     */
    suspend fun mergeGameBoyPlatforms() {
        val mergedName = "GameBoy (Color + Advance)"
        val platforms = platformDao.getAllOnce()
        if (platforms.any { it.name == mergedName }) return

        val gbc = platforms.find { it.name == "Game Boy / Color" }
        val gba = platforms.find { it.name == "Game Boy Advance" }
        val keep = gbc ?: gba ?: return
        val drop = if (keep === gbc) gba else null

        val mergedExtensions = Platform.extensionsToCsv((keep.extensions + (drop?.extensions ?: emptyList())).distinct())
        platformDao.update(keep.copy(name = mergedName, extensionsCsv = mergedExtensions))

        if (drop != null) {
            for (extension in drop.extensions) {
                gameDao.reassignByExtension(fromPlatformId = drop.id, toPlatformId = keep.id, extension = extension)
            }
            platformDao.delete(drop)
        }
    }

    /**
     * Inserts any [DefaultPlatforms.ALL] entry whose name isn't already present, e.g. "PlayStation"
     * being added to the starter set after an install has already seeded/scanned a library. Doesn't
     * touch existing platforms, so it's safe to run on every startup.
     */
    suspend fun seedMissingDefaultPlatforms() {
        val existingNames = platformDao.getAllOnce().map { it.name }.toSet()
        DefaultPlatforms.ALL.filter { it.name !in existingNames }.forEach { platformDao.insert(it) }
    }

    suspend fun allExistingFileUris(): Set<String> = gameDao.getAllFileUris().toSet()

    suspend fun addGames(games: List<Game>) = gameDao.insertAll(games)
    suspend fun updateGame(game: Game) = gameDao.update(game)
    suspend fun deleteGame(game: Game) = gameDao.delete(game)

    suspend fun markPlayed(game: Game) =
        gameDao.update(game.copy(lastPlayedAt = System.currentTimeMillis(), playCount = game.playCount + 1))

    fun getRootFolderUri(): String? = settings.rootFolderUri
    fun setRootFolderUri(uri: String?) {
        settings.rootFolderUri = uri
    }

    fun getKillBackgroundAppsOnLaunch(): Boolean = settings.killBackgroundAppsOnLaunch
    fun setKillBackgroundAppsOnLaunch(value: Boolean) {
        settings.killBackgroundAppsOnLaunch = value
    }

    fun getDualScreenSupportEnabled(): Boolean = settings.dualScreenSupportEnabled
    fun setDualScreenSupportEnabled(value: Boolean) {
        settings.dualScreenSupportEnabled = value
    }

    fun getBiosKeysFolderUri(): String? = settings.biosKeysFolderUri
    fun setBiosKeysFolderUri(uri: String?) {
        settings.biosKeysFolderUri = uri
    }
}
