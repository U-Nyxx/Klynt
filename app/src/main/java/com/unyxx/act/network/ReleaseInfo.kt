package com.unyxx.act.network

/**
 * Normalized update candidate for one build flavor.
 *
 * @param version tag without leading 'v' (e.g. "1.0.2").
 */
data class ReleaseInfo(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long
) {
    companion object {
        /** Picks the asset matching [flavor] (api101/api102), null when absent. */
        fun from(release: GitHubRelease, flavor: String): ReleaseInfo? {
            val asset = release.assets.firstOrNull { a ->
                a.name.startsWith("klynt-") && a.name.contains("-$flavor-") && a.name.endsWith(".apk")
            } ?: return null
            return ReleaseInfo(
                version = release.tagName.trim().removePrefix("v"),
                notes = release.body.orEmpty(),
                apkUrl = asset.downloadUrl,
                sizeBytes = asset.size
            )
        }

        /** Semantic compare tolerant of missing parts ("1.0" == "1.0.0"). */
        fun compareVersions(a: String, b: String): Int {
            val pa = a.trim().removePrefix("v").split(".", "-", "_").map { it.toIntOrNull() ?: 0 }
            val pb = b.trim().removePrefix("v").split(".", "-", "_").map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val diff = (pa.getOrElse(i) { 0 }).compareTo(pb.getOrElse(i) { 0 })
                if (diff != 0) return diff
            }
            return 0
        }
    }

    /** True when this release is strictly newer than [localVersion]. */
    fun isNewerThan(localVersion: String): Boolean =
        compareVersions(version, localVersion) > 0
}
