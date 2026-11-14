package com.maxrave.simpmusic.utils

import com.maxrave.domain.data.model.update.UpdateAsset

/** The fork's releases page, the last stop when a release has no file for this platform. */
internal const val FORK_RELEASES_URL = "https://github.com/VQSeGo/SimpMusic/releases/latest"

/**
 * Where the update dialog sends the user: the file of release [assets] built for the running system,
 * else the release page, else the releases list of the fork. The names are the ones
 * `.github/workflows/release.yml` publishes: `SimpMusic-arm64.apk` (with `SimpMusic-universal.apk`
 * as a fallback for a phone that arm64 does not install on), `SimpMusic-x86_64.AppImage` for Linux and
 * `SimpMusic-Windows-installer.zip` for Windows. [osName] is `Platform.osName()`: "android" or the
 * JVM's `os.name`.
 */
internal fun pickUpdateDownloadUrl(
    assets: List<UpdateAsset>,
    pageUrl: String?,
    osName: String,
): String {
    val os = osName.lowercase()
    val file =
        when {
            os == "android" ->
                assets.firstOrNull { it.name.endsWith(".apk", true) && "arm64" in it.name.lowercase() }
                    ?: assets.firstOrNull { it.name.endsWith(".apk", true) }
            "windows" in os -> assets.firstOrNull { it.name.endsWith(".zip", true) && "windows" in it.name.lowercase() }
            "linux" in os -> assets.firstOrNull { it.name.endsWith(".AppImage", true) }
            else -> null
        }
    return file?.url ?: pageUrl?.takeIf { it.isNotBlank() } ?: FORK_RELEASES_URL
}
