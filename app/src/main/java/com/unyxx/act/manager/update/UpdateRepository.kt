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

/** UI states for the update flow. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val version: String) : UpdateState
    data class Available(val info: ReleaseInfo) : UpdateState
    data class Downloading(val progress: Float?) : UpdateState
    data class Downloaded(val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
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
     * [force] retries the network even when a fresh state was just
     * computed; offline it still surfaces last-known state.
     */
    suspend fun check(context: Context, force: Boolean): UpdateState {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        return try {
            val result = GitHubApi.fetchLatest(p.getString(KEY_ETAG, null))
            val info = if (result.notModified) {
                // 304 with no cache (prefs wiped, ETag survived): stamp and
                // report local truth instead of a false "no update" state.
                cachedInfo(p) ?: run {
                    p.edit().putLong(KEY_CHECK_MS, now).apply()
                    return UpdateState.UpToDate(localVersion(context))
                }
            } else {
                val release = result.release
                    ?: return UpdateState.Failed("Release has no build attached")
                val fresh = ReleaseInfo.from(release)
                    ?: return UpdateState.Failed("No signed KLYNT APK in latest release")
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
            // Offline: fall back to last known state instead of erroring,
            // so the section degrades to stale-info, not a dead end.
            // Manual retry included: a known-newer build is worth showing
            // even when the user explicitly asked.
            val stale = cachedInfo(p)
            if (stale != null && stale.apkUrl.isNotBlank() &&
                stale.isNewerThan(localVersion(context))
            ) {
                UpdateState.Available(stale)
            } else {
                UpdateState.Failed(t.message ?: "Network error")
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
