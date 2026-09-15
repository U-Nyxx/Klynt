package com.unyxx.act.manager.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.unyxx.act.BuildConfig
import com.unyxx.act.network.GitHubApi
import com.unyxx.act.network.ReleaseInfo
import com.unyxx.act.xposed.prefs.PrefsSchema
import java.io.File

/**
 * Structured cause for update failures. Each case maps to a
 * specific user-facing hint in the Settings UI.
 */
sealed interface UpdateErrorCause {
    data object Network : UpdateErrorCause
    data object RateLimited : UpdateErrorCause
    data object NotFound : UpdateErrorCause
    data object AssetMismatch : UpdateErrorCause
    data object Unknown : UpdateErrorCause

    companion object {
        fun from(message: String?, httpCode: Int? = null): UpdateErrorCause {
            val msg = message ?: ""
            return when {
                httpCode == 404 || msg.contains("404", ignoreCase = true) ||
                    msg.contains("not found", ignoreCase = true) -> NotFound
                httpCode == 403 || msg.contains("403", ignoreCase = true) ||
                    msg.contains("rate limit", ignoreCase = true) -> RateLimited
                msg.contains("resolve", ignoreCase = true) ||
                    msg.contains("UnknownHost", ignoreCase = true) ||
                    msg.contains("Connect", ignoreCase = true) ||
                    msg.contains("timeout", ignoreCase = true) ||
                    msg.contains("Network", ignoreCase = true) ||
                    msg.contains("No connection", ignoreCase = true) ||
                    msg.contains("Tidak ada koneksi", ignoreCase = true) -> Network
                msg.contains("asset", ignoreCase = true) ||
                    msg.contains("signing", ignoreCase = true) ||
                    msg.contains("No signed KLYNT", ignoreCase = true) -> AssetMismatch
                else -> Unknown
            }
        }
    }
}

/** UI states for the update flow. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val version: String) : UpdateState
    data class Available(val info: ReleaseInfo) : UpdateState
    data class Downloading(val progress: Float?) : UpdateState
    data class Downloaded(val file: File) : UpdateState
    data class Failed(val message: String, val cause: UpdateErrorCause = UpdateErrorCause.Unknown) : UpdateState
}

/**
 * GitHub-release update source of truth.
 *
 * Realtime ETag-conditional check on every call (foreground + manual):
 * a 304 costs ~no quota, so there is no 24h cache gate. Last-known
 * release stays in prefs as offline fallback. Downloads go through the
 * system DownloadManager; installs via our FileProvider.
 */
object UpdateRepository {

    private const val KEY_CHECK_MS = "update_last_check_ms"
    private const val KEY_TAG = "update_cached_tag"
    private const val KEY_NOTES = "update_cached_notes"
    private const val KEY_URL = "update_cached_url"
    private const val KEY_SIZE = "update_cached_size"
    private const val KEY_ETAG = "update_etag"
    private const val APK_FILE = "klynt-update.apk"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PrefsSchema.PREFS_FILE, Context.MODE_PRIVATE)

    fun localVersion(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.1"
        } catch (_: Exception) {
            "1.0.1"
        }
    }

    /** Epoch ms of the last check (cache stamp), 0 when never checked. */
    fun lastCheckMs(context: Context): Long {
        return try {
            prefs(context).getLong(KEY_CHECK_MS, 0L)
        } catch (_: Throwable) {
            0L
        }
    }

    /**
     * Realtime check on every call (foreground + manual): conditional GET
     * via ETag, so unchanged releases cost ~no quota. Throws nothing.
     * [force] bypasses the ETag and issues a full fetch when true.
     */
    suspend fun check(context: Context, force: Boolean): UpdateState {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        return try {
            val result = GitHubApi.fetchLatest(p.getString(KEY_ETAG, null), force)
            val info = if (result.notModified) {
                cachedInfo(p) ?: run {
                    p.edit().putLong(KEY_CHECK_MS, now).apply()
                    return UpdateState.UpToDate(localVersion(context))
                }
            } else {
                val release = result.release
                    ?: return UpdateState.Failed("Release has no build attached", UpdateErrorCause.Unknown)
                val fresh = ReleaseInfo.from(release)
                    ?: return UpdateState.Failed("No signed KLYNT APK in latest release", UpdateErrorCause.AssetMismatch)
                p.edit()
                    .putString(KEY_TAG, fresh.version)
                    .putString(KEY_NOTES, fresh.notes)
                    .putString(KEY_URL, fresh.apkUrl)
                    .putLong(KEY_SIZE, fresh.sizeBytes)
                    .putString(KEY_ETAG, result.etag ?: "")
                    .apply()
                fresh
            }
            p.edit().putLong(KEY_CHECK_MS, now).apply()
            if (info.apkUrl.isBlank()) {
                UpdateState.UpToDate(localVersion(context))
            } else if (info.isNewerThan(localVersion(context))) {
                UpdateState.Available(info)
            } else {
                UpdateState.UpToDate(localVersion(context))
            }
        } catch (t: Throwable) {
            val cause = UpdateErrorCause.from(t.message)
            val stale = cachedInfo(p)
            if (stale != null && stale.apkUrl.isNotBlank() &&
                stale.isNewerThan(localVersion(context)) && !force
            ) {
                UpdateState.Available(stale)
            } else {
                UpdateState.Failed(t.message ?: "Network error", cause)
            }
        }
    }

    private fun cachedInfo(p: android.content.SharedPreferences): ReleaseInfo? {
        val tag = p.getString(KEY_TAG, null) ?: return null
        return ReleaseInfo(
            version = tag,
            notes = p.getString(KEY_NOTES, "").orEmpty(),
            apkUrl = p.getString(KEY_URL, "").orEmpty(),
            sizeBytes = p.getLong(KEY_SIZE, 0L)
        )
    }

    /** Enqueues the APK download, returns the DownloadManager id. */
    fun enqueueDownload(context: Context, info: ReleaseInfo): Long {
        val file = updateFile(context)
        if (file.exists()) file.delete()
        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("KLYNT v${info.version}")
            .setDescription("Downloading update")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationUri(Uri.fromFile(file))
            .setMimeType("application/vnd.android.package-archive")
            .setAllowedOverMetered(true)
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return dm.enqueue(request)
    }

    /** 0..1 progress, or null when indeterminate. */
    fun queryProgress(context: Context, downloadId: Long): Float? {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (!cursor.moveToFirst()) return null
            val downloaded = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            )
            val total = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            )
            if (total <= 0L) return null
            return (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }
    }

    /** True when the download finished successfully. */
    fun isComplete(context: Context, downloadId: Long): Boolean {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (!cursor.moveToFirst()) return false
            val status = cursor.getInt(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            )
            return status == DownloadManager.STATUS_SUCCESSFUL
        }
    }

    /** True when the download failed (then it is removed). */
    fun isFailed(context: Context, downloadId: Long): Boolean {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (!cursor.moveToFirst()) return false
            val status = cursor.getInt(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            )
            return status == DownloadManager.STATUS_FAILED
        }
    }

    fun cancel(context: Context, downloadId: Long) {
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.remove(downloadId)
        } catch (_: Exception) {
        }
    }

    /** Launches the package installer for the downloaded file. */
    fun installIntent(context: Context): Intent? {
        val file = updateFile(context)
        if (!file.exists()) return null
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Location of the downloaded APK (shared with the installer intent). */
    fun updateFile(context: Context): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        if (!dir.exists()) dir.mkdirs()
        return File(dir, APK_FILE)
    }
}
