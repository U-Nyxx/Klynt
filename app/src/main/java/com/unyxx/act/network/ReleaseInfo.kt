package com.unyxx.act.network

/**
 * Normalized update candidate (single build).
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
        /** Strict asset shape: `klynt-<version>-<CODE>.apk`, CODE rotates. */
        private val ASSET_RE = Regex("""^klynt-(.+)-([A-Za-z0-9_-]+)\.apk$""")

        /**
         * Picks the signed `klynt-<version>-<CODE>.apk` asset.
         *
         * Strict on purpose (future-proof for a rotating codename): the name
         * must match [ASSET_RE], `size` must be positive and the URL https.
         * When several match, the one whose embedded version equals the
         * release tag wins; otherwise the first strict match. Null when
         * absent.
         */
        fun from(release: GitHubRelease): ReleaseInfo? {
            val tag = release.tagName.trim().removePrefix("v")
            val strict = release.assets.filter { a ->
                ASSET_RE.matchEntire(a.name) != null &&
                    a.size > 0 &&
                    a.downloadUrl.startsWith("https://")
            }
            if (strict.isEmpty()) return null
            val asset = strict.firstOrNull { a ->
                ASSET_RE.matchEntire(a.name)?.groupValues?.get(1) == tag
            } ?: strict.first()
            return ReleaseInfo(
                version = tag,
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
