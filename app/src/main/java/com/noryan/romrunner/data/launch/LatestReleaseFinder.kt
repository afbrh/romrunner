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
}
