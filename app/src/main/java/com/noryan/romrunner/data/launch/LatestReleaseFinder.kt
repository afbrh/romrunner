package com.noryan.romrunner.data.launch

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Finds the download URL of a specific asset in a project's most recent stable release that has one,
 * via the GitHub-compatible "/releases" list API — both GitHub itself and a self-hosted Gitea
 * instance (e.g. Eden's git.eden-emu.dev) expose this same endpoint shape (tag_name/prerelease/
 * draft/assets[].name/assets[].browser_download_url), so one implementation covers both hosts.
 *
 * Deliberately scans the release list instead of using "/releases/latest": a repo that publishes
 * more than one platform's releases can have its newest stable release be for a different platform
 * (ARMSX2 shipped an iOS-only "iOSv2.6.0" that "latest" then returned, with no Android APK at all).
 * Prereleases and drafts (nightlies) are skipped explicitly.
 */
object LatestReleaseFinder {

    /**
     * Blocking network I/O — call this from a background dispatcher (e.g. Dispatchers.IO), never
     * from the main thread. [releasesUrl] is the repo's ".../releases" list endpoint. Returns null
     * on any network/parsing failure, or if no recent stable release has an asset satisfying
     * [assetMatches], so callers can fall back to just opening the project's releases page instead.
     */
    fun findStableAssetUrl(releasesUrl: String, assetMatches: (String) -> Boolean): String? {
        var connection: HttpURLConnection? = null
        return try {
            // per_page is GitHub's name for the page size, limit is Gitea's; each host ignores the other's.
            connection = (URL("$releasesUrl?per_page=30&limit=30").openConnection() as HttpURLConnection).apply {
                setRequestProperty("Accept", "application/json")
                connectTimeout = 15_000
                readTimeout = 15_000
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val releases = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
            for (r in 0 until releases.length()) {
                val release = releases.getJSONObject(r)
                if (release.optBoolean("prerelease") || release.optBoolean("draft")) continue
                val assets = release.optJSONArray("assets") ?: continue
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (assetMatches(asset.optString("name"))) {
                        val url = asset.optString("browser_download_url")
                        if (url.isNotBlank()) return url
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * RetroArch specifically: it isn't distributed via GitHub Releases at all (its latest GitHub
     * release ships only a source tarball, confirmed directly) — real builds live at
     * buildbot.libretro.com instead, as a plain version-numbered directory tree with no
     * "/stable/latest/" alias. buildbot.libretro.com/stable/altstore.json (meant for iOS AltStore,
     * but version-agnostic) is the one place that states the current stable version number in a
     * parseable form, so it's used here just to read that number rather than scraping the HTML
     * directory listing. Blocking network I/O — call from a background dispatcher.
     */
    fun findRetroArchStableApkUrl(): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL("https://buildbot.libretro.com/stable/altstore.json").openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val apps = root.optJSONArray("apps") ?: return null
            for (i in 0 until apps.length()) {
                val app = apps.getJSONObject(i)
                if (app.optString("name") != "RetroArch") continue
                val version = app.optJSONArray("versions")?.optJSONObject(0)?.optString("version")
                if (version.isNullOrBlank()) return null
                // The aarch64 build (package "com.retroarch.aarch64"): matches the arm64 cores RomRunner
                // downloads for it, and EmulatorLauncher counts it as the same app as "com.retroarch".
                return "https://buildbot.libretro.com/stable/$version/android/RetroArch_aarch64.apk"
            }
            null
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * PPSSPP specifically: its GitHub releases ship no Android APK at all (confirmed: only source,
     * desktop and iOS builds). The official site serves the stable Android build as a plain static
     * file at ppsspp.org/files/<major_minor_patch>/ppsspp.apk, so this reads the latest stable
     * release tag from GitHub (e.g. "v1.20.4" -> "1_20_4") and verifies the constructed URL
     * actually exists before returning it. Blocking network I/O — call from a background dispatcher.
     */
    fun findPpssppStableApkUrl(): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL("https://api.github.com/repos/hrydgard/ppsspp/releases/latest").openConnection() as HttpURLConnection).apply {
                setRequestProperty("Accept", "application/json")
                connectTimeout = 15_000
                readTimeout = 15_000
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (release.optBoolean("prerelease") || release.optBoolean("draft")) return null
            val version = release.optString("tag_name").removePrefix("v").replace('.', '_')
            if (!Regex("""\d+_\d+_\d+""").matches(version)) return null
            val apkUrl = "https://www.ppsspp.org/files/$version/ppsspp.apk"
            connection.disconnect()
            connection = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = 15_000
                readTimeout = 15_000
            }
            if (connection.responseCode == HttpURLConnection.HTTP_OK) apkUrl else null
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
