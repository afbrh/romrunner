package com.noryan.romrunner.data.launch

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Finds the download URL of a specific asset in a project's most recent stable release, via the
 * GitHub-compatible "/releases/latest" Releases API — both GitHub itself and a self-hosted Gitea
 * instance (e.g. Eden's git.eden-emu.dev) expose this same endpoint shape (tag_name/prerelease/
 * draft/assets[].name/assets[].browser_download_url), so one implementation covers both hosts.
 * "latest" already excludes prereleases/drafts on both hosts, but prerelease/draft are rechecked
 * explicitly here rather than trusted blindly, in case a host's definition of "latest" ever
 * changes or a project mis-tags a nightly as a full release.
 */
object LatestReleaseFinder {

    /**
     * Blocking network I/O — call this from a background dispatcher (e.g. Dispatchers.IO), never
     * from the main thread. Returns null on any network/parsing failure, or if no asset in the
     * latest stable release satisfies [assetMatches], so callers can fall back to just opening the
     * project's releases page instead.
     */
    fun findStableAssetUrl(releasesLatestUrl: String, assetMatches: (String) -> Boolean): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(releasesLatestUrl).openConnection() as HttpURLConnection).apply {
                setRequestProperty("Accept", "application/json")
                connectTimeout = 15_000
                readTimeout = 15_000
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (release.optBoolean("prerelease") || release.optBoolean("draft")) return null
            val assets = release.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name")
                if (assetMatches(name)) {
                    val url = asset.optString("browser_download_url")
                    if (url.isNotBlank()) return url
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
                // Universal RetroArch.apk (package "com.retroarch", same as the Play Store build and what
                // every RomRunner platform's launchPackage points at) — NOT RetroArch_aarch64.apk,
                // which is published under a different package name ("com.retroarch.aarch64") and
                // so would never register as installed against "com.retroarch".
                return "https://buildbot.libretro.com/stable/$version/android/RetroArch.apk"
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
