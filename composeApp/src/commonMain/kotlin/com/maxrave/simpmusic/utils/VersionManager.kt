package com.maxrave.simpmusic.utils

import com.maxrave.simpmusic.BuildKonfig

object VersionManager {
    private var versionName: String? = null

    fun initialize() {
        if (versionName == null) {
            versionName =
                try {
                    BuildKonfig.versionName
                } catch (_: Exception) {
                    String()
                }
        }
    }

    fun getVersionName(): String = removeDevSuffix(versionName ?: String())

    /** True when [tag] names a release newer than this build. */
    fun isBehind(tag: String): Boolean = isNewerRelease(current = getVersionName(), tag = tag)

    private fun removeDevSuffix(versionName: String): String {
        return if (versionName.endsWith("-dev")) {
            versionName.replace("-dev", "")
        } else {
            versionName
        }
    }
}

/**
 * True when release [tag] is newer than a build running [current]. The fork's releases are tagged
 * `v<version>` (see `.github/workflows/release.yml`) and the build carries the bare `<version>` from
 * `libs.versions.toml`, so a leading "v" is ignored. Only the dotted numbers decide, compared part by
 * part ("2.10.0" is newer than "2.9.0"); a suffix such as "-1" or "-hf" does not, since the build
 * cannot carry it, and a tag that differs from the build only by a suffix would otherwise be offered
 * on every launch. The one exception is a pre-release build ("2.5.0-beta") and the final release
 * with the same numbers. An empty or unparsable tag or version is never newer.
 */
internal fun isNewerRelease(
    current: String,
    tag: String,
): Boolean {
    val installed = parseVersion(current) ?: return false
    val release = parseVersion(tag) ?: return false
    val diff = compareVersions(installed.numbers, release.numbers)
    return diff < 0 || (diff == 0 && installed.suffix != null && release.suffix == null)
}

internal class ParsedVersion(
    val numbers: List<Int>,
    val suffix: String?,
)

private val versionPattern = Regex("""\d+(\.\d+)*""")

/** "v2.5.1-hf" to ([2, 5, 1], "hf"); null for anything that does not start with dotted numbers. */
internal fun parseVersion(raw: String): ParsedVersion? {
    val version = raw.trim().removePrefix("v").removePrefix("V")
    val numbers = version.substringBefore('-')
    if (!versionPattern.matches(numbers)) return null
    val suffix = if ('-' in version) version.substringAfter('-').ifBlank { null } else null
    return ParsedVersion(numbers.split('.').map { it.toIntOrNull() ?: return null }, suffix)
}

private fun compareVersions(
    a: List<Int>,
    b: List<Int>,
): Int {
    for (i in 0 until maxOf(a.size, b.size)) {
        val diff = a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 })
        if (diff != 0) return diff
    }
    return 0
}
